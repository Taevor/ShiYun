package com.shi.yun.data.local.importer.parser

import com.shi.yun.data.local.importer.merger.SourceWorkCandidate
import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.TitleSourceType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * GitHub chinese-poetry/chinese-poetry 离线数据解析器 (ChinesePoetryParser)
 *
 * 专门解析 chinese-poetry 开源古籍正文字库（唐诗、宋词等分卷 JSON 结构）：
 * 1. 分别处理唐诗（含 title、paragraphs、id）与宋词（含 rhythmic、paragraphs、无 title）的不同数据结构；
 * 2. 提取并保留原始记录 UUID、来源代码 "CHINESE_POETRY"、文件路径及完整原始 JSON 文本 (rawPayload)；
 * 3. 严格遵循宋词题名规范：
 *    - rhythmic 只映射 cipai，绝不把词牌直接当作 title；
 *    - 原始记录提供独立 title 时映射为 SOURCE_TITLE；
 *    - 原始记录没有 title 时，从正文提取首句作为默认词题，标记为 GENERATED_FIRST_LINE；
 *    - 无法可靠提取首句时保持为 null，标记为 UNKNOWN；
 * 4. 辅助内容规范：chinese-poetry 纯收录古籍正文，不含现代译注评，辅助字段严格置空 (null)；
 * 5. 代码采用 MIT 协议，正文底本为公有领域。
 */
class ChinesePoetryParser {

    companion object {
        const val SOURCE_NAME = "chinese-poetry"
        const val SOURCE_CODE = "CHINESE_POETRY"
    }

    /**
     * 解析本地原始 JSON 文件
     *
     * @param file 本地文件对象 (如 poet.tang.0.json 或 ci.song.0.json)
     * @param dynastyHint 外部目录/朝代提示 (如 "唐", "宋")
     * @param sourceVersion 仓库版本或 Git Commit
     */
    fun parseFile(file: File, dynastyHint: String? = null, sourceVersion: String? = null): List<SourceWorkCandidate> {
        require(file.exists() && file.isFile) { "原始文件不存在或不是标准文件: ${file.absolutePath}" }
        val inferredDynasty = dynastyHint ?: inferDynastyFromPath(file.path)
        val text = file.readText(Charsets.UTF_8)
        return parseJson(text, rawFilePath = file.path, dynastyHint = inferredDynasty, sourceVersion = sourceVersion)
    }

    /**
     * 解析 JSON 字符串
     */
    fun parseJson(
        jsonStr: String,
        rawFilePath: String? = null,
        dynastyHint: String? = null,
        sourceVersion: String? = null
    ): List<SourceWorkCandidate> {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<SourceWorkCandidate>()

        if (trimmed.startsWith("[")) {
            val jsonArray = JSONArray(trimmed)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                results.add(parseRecord(obj, rawFilePath = rawFilePath, dynastyHint = dynastyHint, sourceVersion = sourceVersion))
            }
        } else if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            results.add(parseRecord(obj, rawFilePath = rawFilePath, dynastyHint = dynastyHint, sourceVersion = sourceVersion))
        }

        return results
    }

    /**
     * 解析单条 JSON 记录为统一的 SourceWorkCandidate
     */
    fun parseRecord(
        obj: JSONObject,
        rawFilePath: String? = null,
        dynastyHint: String? = null,
        sourceVersion: String? = null
    ): SourceWorkCandidate {
        val rawId = obj.optString("id").trim()
        // 若原始记录无原生 id（如宋词分卷），基于原始完整 JSON 自动生成确定性内容哈希，确保 100% 可索引与可追溯
        val externalId = if (rawId.isNotEmpty()) {
            rawId
        } else {
            "hash_" + com.shi.yun.data.local.importer.pipeline.RawRecordArchive.calculateSha256(obj.toString()).take(16)
        }
        val rawAuthor = obj.optString("author").trim()
        val inferredAuthor = inferAuthorFromPath(rawFilePath ?: "")
        val effectiveAuthorName = rawAuthor.ifEmpty { inferredAuthor ?: "" }

        val rhythmic = obj.optString("rhythmic").trim().ifEmpty { null }
        val rawTitle = obj.optString("title").trim()

        val isCi = rhythmic != null
        val rawDynasty = obj.optString("dynasty").trim().ifEmpty { null }
        val mappedRawDynasty = when (rawDynasty?.lowercase()) {
            "tang" -> "唐"
            "song" -> "宋"
            "yuan" -> "元"
            "ming" -> "明"
            "qing" -> "清"
            "han" -> "汉"
            else -> rawDynasty
        }
        val inferredDynasty = inferDynastyFromPath(rawFilePath ?: "")
        val dynasty = mappedRawDynasty ?: dynastyHint ?: if (isCi) "宋" else inferredDynasty
        val genre = if (isCi) "CI" else if (dynasty == "元" || dynasty == "元代" || rawFilePath?.contains("yuanqu") == true || rawFilePath?.contains("元曲") == true) "QU" else "SHI"
        val cipai = rhythmic

        // 未知作者规范：空、佚名、未知均置空为 null
        val author = if (effectiveAuthorName.isNotEmpty() && effectiveAuthorName != "佚名" && effectiveAuthorName != "未知") {
            AuthorImportItem(name = effectiveAuthorName, dynasty = dynasty)
        } else {
            null
        }

        // 正文解析：区分原始正文 (rawContent，保留空行与空格) 与清洗后的候选正文 (content)
        val paragraphsObj = obj.opt("paragraphs") ?: obj.opt("content")
        val rawContent = when (paragraphsObj) {
            is JSONArray -> {
                val list = mutableListOf<String>()
                for (i in 0 until paragraphsObj.length()) {
                    list.add(paragraphsObj.optString(i))
                }
                list.joinToString("\n")
            }
            is String -> paragraphsObj
            else -> null
        }

        val content = when (paragraphsObj) {
            is JSONArray -> {
                val list = mutableListOf<String>()
                for (i in 0 until paragraphsObj.length()) {
                    val s = paragraphsObj.optString(i).trim()
                    if (s.isNotEmpty()) list.add(s)
                }
                list.joinToString("\n")
            }
            is String -> paragraphsObj.trim()
            else -> ""
        }

        // 宋词题名处理与词牌分离规则
        val finalTitle: String?
        val titleSourceType: TitleSourceType
        if (isCi) {
            if (rawTitle.isNotEmpty() && rawTitle != rhythmic) {
                // 来源明确提供了独立词题
                finalTitle = rawTitle
                titleSourceType = TitleSourceType.SOURCE_TITLE
            } else {
                // 来源无 title 或 title 等于词牌：从正文提取首句作为默认词题
                val firstLine = SongCiCipaiHelper.extractFirstLineAsTitle(content)
                if (firstLine != null) {
                    finalTitle = firstLine
                    titleSourceType = TitleSourceType.GENERATED_FIRST_LINE
                } else {
                    finalTitle = null
                    titleSourceType = TitleSourceType.UNKNOWN
                }
            }
        } else {
            // 非词体裁（如唐诗）：正常映射来源标题
            if (rawTitle.isNotEmpty()) {
                finalTitle = rawTitle
                titleSourceType = TitleSourceType.SOURCE_TITLE
            } else {
                finalTitle = null
                titleSourceType = TitleSourceType.UNKNOWN
            }
        }

        // 标签解析
        val tags = mutableListOf<String>()
        val tagsObj = obj.opt("tags")
        if (tagsObj is JSONArray) {
            for (i in 0 until tagsObj.length()) {
                val tagStr = tagsObj.optString(i).trim()
                if (tagStr.isNotEmpty()) tags.add(tagStr)
            }
        }

        return SourceWorkCandidate(
            sourceName = SOURCE_NAME,
            sourceCode = SOURCE_CODE,
            sourceVersion = sourceVersion,
            externalWorkId = externalId,
            rawFilePath = rawFilePath,
            referenceUrl = "https://github.com/chinese-poetry/chinese-poetry",
            licenseType = "PUBLIC_DOMAIN",
            licenseNote = "中国古典诗词原作底本，代码遵循 MIT 协议",
            isGushiwenConfirmed = false,
            title = finalTitle,
            rawTitle = rawTitle.ifEmpty { null },
            titleSourceType = titleSourceType,
            author = author,
            dynasty = dynasty,
            genre = genre,
            cipai = cipai,
            tags = tags,
            content = content.ifEmpty { null },
            rawContent = rawContent?.ifEmpty { null },
            // chinese-poetry 严格无辅助内容
            translation = null,
            annotations = null,
            background = null,
            appreciation = null,
            rawFormat = "JSON",
            rawPayload = obj.toString()
        )
    }

    private fun inferDynastyFromPath(path: String): String? {
        val lower = path.lowercase()
        return when {
            lower.contains("tang") || lower.contains("全唐诗") -> "唐"
            lower.contains("song") || lower.contains("全宋词") || lower.contains("宋词") || lower.contains("ci.") -> "宋"
            lower.contains("yuan") || lower.contains("元曲") -> "元"
            lower.contains("shijing") || lower.contains("诗经") -> "先秦"
            lower.contains("chuci") || lower.contains("楚辞") -> "先秦"
            lower.contains("caocao") || lower.contains("曹操") -> "汉"
            else -> null
        }
    }

    private fun inferAuthorFromPath(path: String): String? {
        val lower = path.lowercase()
        return when {
            lower.contains("caocao") || lower.contains("曹操") -> "曹操"
            else -> null
        }
    }
}
