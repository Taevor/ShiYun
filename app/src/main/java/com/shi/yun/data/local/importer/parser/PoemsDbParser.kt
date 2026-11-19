package com.shi.yun.data.local.importer.parser

import com.shi.yun.data.local.importer.merger.SourceWorkCandidate
import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.TitleSourceType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * yxcs/poems-db 离线数据解析器 (PoemsDbParser)
 *
 * 专门解析 yxcs/poems-db 仓库导出的 MongoDB JSON Array / Lines 格式：
 * 1. 字段映射独立实现，绝不与其他来源结构混淆；
 * 2. 提取并保留原始记录 _id、文件路径、来源代码 "POEMS_DB" 及完整原始 JSON 文本 (rawPayload)；
 * 3. 严格遵循宋词题名规范：
 *    - 若属于词（format="词" 或 title 匹配已知词牌），进行词牌与词题分离；
 *    - 若 title 为「词牌·词题」格式，拆分为 cipai 与独立词题 (SOURCE_TITLE)；
 *    - 若 title 完全等于词牌名，cipai 取该词牌，title 从正文首句生成默认词题 (GENERATED_FIRST_LINE)；
 *    - 保留原始 rawTitle 确保 100% 来源可追溯；
 * 4. 仅当记录中明确包含古诗文网来源凭据 (如 sourceLink 为 gushiwen.org/cn) 时标记 isGushiwenConfirmed = true。
 */
class PoemsDbParser {

    companion object {
        const val SOURCE_NAME = "yxcs/poems-db"
        const val SOURCE_CODE = "POEMS_DB"
    }

    /**
     * 解析本地原始 JSON 文件
     */
    fun parseFile(file: File, sourceVersion: String? = null): List<SourceWorkCandidate> {
        require(file.exists() && file.isFile) { "原始文件不存在或不是标准文件: ${file.absolutePath}" }
        val text = file.readText(Charsets.UTF_8)
        return parseJson(text, rawFilePath = file.path, sourceVersion = sourceVersion)
    }

    /**
     * 解析 JSON 字符串（支持 JSON 数组或单对象）
     */
    fun parseJson(jsonStr: String, rawFilePath: String? = null, sourceVersion: String? = null): List<SourceWorkCandidate> {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<SourceWorkCandidate>()

        if (trimmed.startsWith("[")) {
            val jsonArray = JSONArray(trimmed)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                results.add(parseRecord(obj, rawFilePath = rawFilePath, sourceVersion = sourceVersion))
            }
        } else if (trimmed.startsWith("{")) {
            // 处理单个对象或以换行分隔的多行 JSON (JSON Lines)
            if (trimmed.contains("\n{")) {
                trimmed.lines().filter { it.isNotBlank() }.forEach { line ->
                    val lineTrim = line.trim()
                    if (lineTrim.startsWith("{") && lineTrim.endsWith("}")) {
                        val obj = JSONObject(lineTrim)
                        results.add(parseRecord(obj, rawFilePath = rawFilePath, sourceVersion = sourceVersion))
                    }
                }
            } else {
                val obj = JSONObject(trimmed)
                results.add(parseRecord(obj, rawFilePath = rawFilePath, sourceVersion = sourceVersion))
            }
        }

        return results
    }

    /**
     * 解析单条 JSON 记录为统一的 SourceWorkCandidate
     */
    fun parseRecord(obj: JSONObject, rawFilePath: String? = null, sourceVersion: String? = null): SourceWorkCandidate {
        val rawId = when (val idObj = obj.opt("_id")) {
            is JSONObject -> idObj.optString("\$oid").trim()
            is String -> idObj.trim()
            null -> obj.optString("id").trim()
            else -> obj.optString("id").trim()
        }.ifEmpty { obj.optString("onlyId").trim() }
        val rawTitle = obj.optString("title").ifEmpty { obj.optString("name") }.trim()

        val rawAuthor = obj.optString("author").trim()
        val dynasty = obj.optString("dynasty").trim().ifEmpty { null }

        // 未知作者规范：空、佚名、未知均置空为 null
        val author = if (rawAuthor.isNotEmpty() && rawAuthor != "佚名" && rawAuthor != "未知") {
            AuthorImportItem(name = rawAuthor, dynasty = dynasty)
        } else {
            null
        }

        // 正文解析：同时提取完整原始正文 (rawContent) 与安全清洗后候选正文 (content)
        val rawContent = extractRawStringOrArray(obj, "content")
        val rawContentStr = extractStringOrArray(obj, "content")
        val content = ClassicalTextHtmlCleaner.clean(rawContentStr)

        // 辅助内容解析（同样执行安全 HTML 标签清洗）
        val rawTranslation = extractStringOrArray(obj, "translation").ifEmpty {
            extractStringOrArray(obj, "translate")
        }
        val translation = ClassicalTextHtmlCleaner.clean(rawTranslation).ifEmpty { null }

        val rawAnnotations = extractStringOrArray(obj, "notes").ifEmpty {
            extractStringOrArray(obj, "annotation")
        }
        val annotations = ClassicalTextHtmlCleaner.clean(rawAnnotations).ifEmpty { null }

        val rawAppreciation = extractStringOrArray(obj, "shangxi").ifEmpty {
            extractStringOrArray(obj, "appreciation")
        }
        val appreciation = ClassicalTextHtmlCleaner.clean(rawAppreciation).ifEmpty { null }

        // 标签解析
        val tags = mutableListOf<String>()
        val categoryObj = obj.opt("category") ?: obj.opt("tags")
        if (categoryObj is JSONArray) {
            for (i in 0 until categoryObj.length()) {
                val tagStr = categoryObj.optString(i).trim()
                if (tagStr.isNotEmpty()) tags.add(tagStr)
            }
        } else if (categoryObj is String && categoryObj.isNotBlank()) {
            tags.addAll(categoryObj.split(",", "，", " ").map { it.trim() }.filter { it.isNotEmpty() })
        }

        // 来源出处与严格古诗文网官方有效链接判定（杜绝包含 gushiwen 子串的弱校验）
        val sourceLink = obj.optString("sourceLink").trim()
        val isGushiwen = GushiwenSourceVerifier.isValidGushiwenWorkUrl(sourceLink)

        val format = obj.optString("format").trim().ifEmpty { null }
        val type = obj.optString("type").trim().ifEmpty { null }
        val rawGenre = format ?: type

        // 体裁多维智能识别与确权（判定顺序：散文文言文 -> 元曲散曲 -> 宋词 -> 诗歌默认）
        val isWen = ClassicalProseHelper.isReliableProse(rawGenre = rawGenre, tags = tags, rawTitle = rawTitle)
        val isQu = if (!isWen) YuanQuQupaiHelper.isReliableQu(rawGenre = rawGenre, tags = tags, rawTitle = rawTitle, dynasty = dynasty) else false
        val isCi = if (!isWen && !isQu) SongCiCipaiHelper.isReliableCi(rawGenre = rawGenre, tags = tags, rawTitle = rawTitle, dynasty = dynasty) else false

        var cipai: String? = null
        var qupai: String? = null
        var finalTitle: String? = rawTitle.ifEmpty { null }
        var titleSourceType = if (rawTitle.isNotEmpty()) TitleSourceType.SOURCE_TITLE else TitleSourceType.UNKNOWN

        val candidateGenre = when {
            isWen -> "WEN"
            isQu -> "QU"
            isCi -> "CI"
            else -> rawGenre
        }

        if (isWen) {
            // 散文：标题保持来源原题，不提取首句，词牌曲牌恒为 null
            finalTitle = rawTitle.ifEmpty { null }
            titleSourceType = if (rawTitle.isNotEmpty()) TitleSourceType.SOURCE_TITLE else TitleSourceType.UNKNOWN
        } else if (isQu) {
            // 元曲：曲牌与曲题分离及首句提取
            val parsedQu = YuanQuQupaiHelper.parseTitleAndQupai(rawTitle)
            if (parsedQu.isMatched) {
                qupai = parsedQu.qupai
                if (parsedQu.quti != null) {
                    finalTitle = parsedQu.quti
                    titleSourceType = TitleSourceType.SOURCE_TITLE
                } else {
                    val firstLine = YuanQuQupaiHelper.extractFirstLineAsTitle(content)
                    if (firstLine != null) {
                        finalTitle = firstLine
                        titleSourceType = TitleSourceType.GENERATED_FIRST_LINE
                    } else {
                        finalTitle = rawTitle.ifEmpty { null }
                        titleSourceType = TitleSourceType.SOURCE_TITLE
                    }
                }
            }
        } else if (isCi) {
            val parsedCipai = SongCiCipaiHelper.parseTitleAndCipai(rawTitle)
            if (parsedCipai.isMatched) {
                cipai = parsedCipai.cipai
                if (parsedCipai.citi != null) {
                    // 「词牌·词题」格式：词题为来源题名
                    finalTitle = parsedCipai.citi
                    titleSourceType = TitleSourceType.SOURCE_TITLE
                } else {
                    // title 仅为词牌名称：不能把词牌作为词题，尝试正文首句生成
                    val firstLine = SongCiCipaiHelper.extractFirstLineAsTitle(content)
                    if (firstLine != null) {
                        finalTitle = firstLine
                        titleSourceType = TitleSourceType.GENERATED_FIRST_LINE
                    } else {
                        finalTitle = null
                        titleSourceType = TitleSourceType.UNKNOWN
                    }
                }
            }
        }

        return SourceWorkCandidate(
            sourceName = SOURCE_NAME,
            sourceCode = SOURCE_CODE,
            sourceVersion = sourceVersion,
            externalWorkId = rawId.ifEmpty { null },
            rawFilePath = rawFilePath,
            referenceUrl = sourceLink.ifEmpty { null },
            licenseType = "UNKNOWN",
            licenseNote = "来自 yxcs/poems-db 整理数据",
            isGushiwenConfirmed = isGushiwen,
            title = finalTitle,
            rawTitle = rawTitle.ifEmpty { null },
            titleSourceType = titleSourceType,
            author = author,
            dynasty = dynasty,
            genre = candidateGenre,
            cipai = cipai,
            qupai = qupai,
            tags = tags,
            content = content.ifEmpty { null },
            rawContent = rawContent?.ifEmpty { null },
            translation = translation,
            annotations = annotations,
            appreciation = appreciation,
            background = null, // 严格保持为 null，严禁从赏析强拆
            rawFormat = "JSON",
            rawPayload = obj.toString()
        )
    }

    private fun extractRawStringOrArray(obj: JSONObject, key: String): String? {
        val target = obj.opt(key) ?: return null
        return when (target) {
            is JSONArray -> {
                val list = mutableListOf<String>()
                for (i in 0 until target.length()) {
                    list.add(target.optString(i))
                }
                list.joinToString("\n")
            }
            is String -> target
            else -> target.toString()
        }
    }

    private fun extractStringOrArray(obj: JSONObject, key: String): String {
        val target = obj.opt(key) ?: return ""
        return when (target) {
            is JSONArray -> {
                val list = mutableListOf<String>()
                for (i in 0 until target.length()) {
                    val s = target.optString(i).trim()
                    if (s.isNotEmpty()) list.add(s)
                }
                list.joinToString("\n")
            }
            is String -> target.trim()
            else -> target.toString().trim()
        }
    }
}
