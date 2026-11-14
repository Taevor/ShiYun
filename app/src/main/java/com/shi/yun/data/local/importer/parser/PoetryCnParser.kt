package com.shi.yun.data.local.importer.parser

import com.shi.yun.data.local.importer.merger.SourceWorkCandidate
import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.TitleSourceType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * OpenDataLab Poetry_CN 离线数据解析器 (PoetryCnParser)
 *
 * 专门解析面向 NLP 任务的 Poetry_CN 结构化衍生数据集：
 * 1. 字段映射独立实现，解析 title, author, dynasty, content, translate, notes, shangxi, tags 等；
 * 2. 提取并保留原始记录 id、文件路径、来源代码 "POETRY_CN" 及完整原始文本 (rawPayload)；
 * 3. 严格遵循宋词题名规范：
 *    - 识别「词牌·词题」格式或纯词牌 title；
 *    - 若 title 为词牌名称，cipai 取该词牌，title 从正文首句提取生成默认词题；
 *    - 保留 rawTitle 原始字符串；
 * 4. 字段缺失时允许为空，绝不随意猜测词牌或朝代；
 * 5. 辅助内容未见明确授权，标注许可状态为 UNKNOWN。
 */
class PoetryCnParser {

    companion object {
        const val SOURCE_NAME = "Poetry_CN"
        const val SOURCE_CODE = "POETRY_CN"
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
     * 解析 JSON 字符串（支持 JSON 数组或 JSON Lines）
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
        val rawId = obj.optString("id").ifEmpty { obj.optString("_id") }.trim()
        val rawTitle = obj.optString("title").trim()

        val rawAuthor = obj.optString("author").trim()
        val dynasty = obj.optString("dynasty").trim().ifEmpty { null }
        val genre = obj.optString("genre").trim().ifEmpty { null }

        // 未知作者规范：空、佚名、未知均置空为 null
        val author = if (rawAuthor.isNotEmpty() && rawAuthor != "佚名" && rawAuthor != "未知") {
            AuthorImportItem(name = rawAuthor, dynasty = dynasty)
        } else {
            null
        }

        // 正文解析：同时提取完整原始正文 (rawContent) 与清洗后候选正文 (content)
        val rawContent = extractRawStringOrArray(obj, "content")
        val content = extractStringOrArray(obj, "content")

        val translation = extractStringOrArray(obj, "translate").ifEmpty {
            extractStringOrArray(obj, "translation")
        }.ifEmpty { null }

        val annotations = extractStringOrArray(obj, "notes").ifEmpty {
            extractStringOrArray(obj, "annotation")
        }.ifEmpty { null }

        val appreciation = extractStringOrArray(obj, "shangxi").ifEmpty {
            extractStringOrArray(obj, "appreciation")
        }.ifEmpty { null }

        val tags = mutableListOf<String>()
        val tagsObj = obj.opt("tags") ?: obj.opt("category")
        if (tagsObj is JSONArray) {
            for (i in 0 until tagsObj.length()) {
                val tagStr = tagsObj.optString(i).trim()
                if (tagStr.isNotEmpty()) tags.add(tagStr)
            }
        } else if (tagsObj is String && tagsObj.isNotBlank()) {
            tags.addAll(tagsObj.split(",", "，", " ").map { it.trim() }.filter { it.isNotEmpty() })
        }

        val isCi = SongCiCipaiHelper.isReliableCi(rawGenre = genre, tags = tags, rawTitle = rawTitle, dynasty = dynasty)

        var cipai: String? = null
        var finalTitle: String? = rawTitle.ifEmpty { null }
        var titleSourceType = if (rawTitle.isNotEmpty()) TitleSourceType.SOURCE_TITLE else TitleSourceType.UNKNOWN

        if (isCi) {
            val parsedCipai = SongCiCipaiHelper.parseTitleAndCipai(rawTitle)
            if (parsedCipai.isMatched) {
                cipai = parsedCipai.cipai
                if (parsedCipai.citi != null) {
                    finalTitle = parsedCipai.citi
                    titleSourceType = TitleSourceType.SOURCE_TITLE
                } else {
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
            referenceUrl = obj.optString("url").trim().ifEmpty { null },
            licenseType = "UNKNOWN",
            licenseNote = "来自 OpenDataLab Poetry_CN 衍生数据集",
            isGushiwenConfirmed = false, // Poetry_CN 数据不直接等同于古诗文网，不预设凭据
            title = finalTitle,
            rawTitle = rawTitle.ifEmpty { null },
            titleSourceType = titleSourceType,
            author = author,
            dynasty = dynasty,
            genre = if (isCi) "CI" else genre,
            cipai = cipai,
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
