package com.shi.yun.data.remote

import com.shi.yun.data.model.ContentAttribution
import com.shi.yun.data.model.ContentProvenance
import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.LegalLicense
import com.shi.yun.data.model.LicenseType
import com.shi.yun.data.model.ReviewStatus
import com.shi.yun.data.model.SourceInfo
import com.shi.yun.data.model.SourceOrigin
import com.shi.yun.data.model.Work
import com.shi.yun.data.model.WorkSourceTrace
import com.shi.yun.data.util.WorkJsonSerializer
import org.json.JSONObject

/**
 * 诗泉 (poetry.palemoky.com) API 响应数据向诗云 Work 实体的映射器
 *
 * 遵循规范要求：
 * 1. 严格映射 id、title、author、dynasty、content 及 type 体裁；
 * 2. 诗泉不提供辅助内容（译文、注释、赏析），其 auxiliary 字段恒为 null，对应凭证为 null；
 * 3. 正文标记为公有领域古典作品（POETRY_PALEMOKY / ORIGINAL_ANCIENT / PUBLIC_DOMAIN / UNVERIFIED_DRAFT）；
 * 4. 兼容 content 字段为字符串数组或纯字符串的情形；
 * 5. 支持传入 JSON 字符串、JSONObject 或解析后的 Map，纯 Kotlin 无缝运行。
 */
object PalemokyWorkMapper {

    private const val BASE_URL = "https://poetry.palemoky.com/api/poems"

    fun mapToWork(jsonStr: String): Work {
        val map = WorkJsonSerializer.parseJsonObject(jsonStr)
        return mapToWork(map)
    }

    fun mapToWork(json: JSONObject): Work {
        return mapToWork(json.toString())
    }

    fun mapToWork(map: Map<String, Any?>): Work {
        val rawId = map["id"]?.toString()?.trim() ?: ""
        require(rawId.isNotEmpty()) { "诗泉数据缺失必要字段 'id'" }

        val title = map["title"]?.toString()?.trim() ?: ""
        require(title.isNotEmpty()) { "诗泉数据缺失必要字段 'title'" }

        val author = map["author"]?.toString()?.trim() ?: ""
        val dynasty = map["dynasty"]?.toString()?.trim() ?: ""

        val content = extractContent(map)
        require(content.isNotEmpty()) { "诗泉作品正文不能为空" }

        val rawType = map["type"]?.toString() ?: ""
        val genre = mapTypeToGenre(rawType)

        val excerpt = extractExcerpt(map, content)
        val externalUrl = "$BASE_URL/$rawId"
        val now = System.currentTimeMillis()

        val contentAttr = ContentAttribution(
            origin = SourceOrigin.POETRY_PALEMOKY,
            externalId = rawId,
            provenance = ContentProvenance.ORIGINAL_ANCIENT,
            license = LegalLicense.UNSPECIFIED,
            reviewStatus = ReviewStatus.UNVERIFIED_DRAFT,
            licenseNote = "中国古典诗词原作，网络接口转录版本未核实整理版权",
            fetchedAtEpochMs = now
        )

        val trace = WorkSourceTrace(
            source = SourceOrigin.POETRY_PALEMOKY,
            externalWorkId = rawId,
            externalUrl = externalUrl,
            externalTitle = title,
            externalAuthor = author,
            externalDynasty = dynasty,
            fetchedAtEpochMs = now,
            contentAttribution = contentAttr,
            translationAttribution = null,
            annotationAttribution = null,
            appreciationAttribution = null,
            rubyPinyinAttribution = null
        )

        return Work(
            id = "palemoky_$rawId",
            title = title,
            author = author,
            dynasty = dynasty,
            genre = genre,
            content = content,
            excerpt = excerpt,
            auxiliary = null,
            source = "poetry.palemoky.com",
            sourceId = rawId,
            licenseNote = "经由 poetry.palemoky.com 诗泉接口获取",
            textSource = SourceInfo(
                sourceName = "诗泉 (poetry.palemoky.com)",
                licenseType = LicenseType.PUBLIC_DOMAIN,
                referenceUrl = externalUrl,
                licenseNote = "公有领域古诗文"
            ),
            sourceTraces = listOf(trace)
        )
    }

    private fun extractContent(map: Map<String, Any?>): String {
        val contentObj = map["content"]
        if (contentObj is List<*>) {
            val lines = contentObj.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) return lines.joinToString("\n")
        } else if (contentObj is String && contentObj.isNotBlank()) {
            return contentObj.trim()
        }

        val paragraphs = map["paragraphs"]
        if (paragraphs is List<*>) {
            val lines = paragraphs.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) return lines.joinToString("\n")
        }

        return ""
    }

    private fun mapTypeToGenre(rawType: String): Genre {
        val trimmed = rawType.trim()
        return when {
            trimmed.contains("词") -> Genre.CI
            trimmed.contains("曲") -> Genre.QU
            trimmed.contains("文") || trimmed.contains("赋") -> Genre.WEN
            else -> Genre.POEM
        }
    }

    private fun extractExcerpt(map: Map<String, Any?>, content: String): String {
        val rawExcerpt = map["excerpt"]?.toString()?.trim() ?: ""
        if (rawExcerpt.isNotEmpty()) return rawExcerpt

        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        return if (firstLine.length > 20) {
            firstLine.take(20)
        } else {
            firstLine
        }
    }
}
