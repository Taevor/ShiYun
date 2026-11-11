package com.shi.yun.data.remote

import com.shi.yun.data.model.AnnotationItem
import com.shi.yun.data.model.AuxiliaryContent
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
 * shi-ci.cn API 响应数据向诗云 Work 实体的映射器
 *
 * 遵循阶段一来源追溯与权限审慎原则：
 * 1. 古代诗词正文归属公有领域（PUBLIC_DOMAIN / ORIGINAL_ANCIENT / UNVERIFIED_DRAFT）；
 * 2. 现代白话译文、条目注释、文学赏析未见明确版权声明前，严禁推定为公有领域，
 *    严格标记为 UNKNOWN_PROVENANCE / UNSPECIFIED / UNVERIFIED_DRAFT；
 * 3. 拼音数据标记为 HUMAN_CURATED / UNSPECIFIED / UNVERIFIED_DRAFT；
 * 4. 辅助内容存在时，严格满足 AuxiliaryContent 的 SourceInfo 强一致性要求。
 * 5. 支持传入 JSON 字符串、JSONObject 或解析后的 Map，纯 Kotlin 无缝运行。
 */
object ShiCiWorkMapper {

    private const val BASE_URL = "https://shi-ci.cn/api/poems"

    fun mapToWork(jsonStr: String): Work {
        val map = WorkJsonSerializer.parseJsonObject(jsonStr)
        return mapToWork(map)
    }

    fun mapToWork(json: JSONObject): Work {
        return mapToWork(json.toString())
    }

    @Suppress("UNCHECKED_CAST")
    fun mapToWork(map: Map<String, Any?>): Work {
        val slug = map["slug"]?.toString()?.trim() ?: ""
        require(slug.isNotEmpty()) { "shi-ci.cn 数据缺失必要字段 'slug'" }

        val title = map["title"]?.toString()?.trim() ?: ""
        require(title.isNotEmpty()) { "shi-ci.cn 数据缺失必要字段 'title'" }

        val author = map["author"]?.toString()?.trim() ?: ""
        val dynasty = map["dynasty"]?.toString()?.trim() ?: ""

        val content = extractContent(map)
        require(content.isNotEmpty()) { "shi-ci.cn 作品正文不能为空" }

        val rawGenre = map["genre"]?.toString() ?: ""
        val genre = mapGenre(rawGenre)

        val excerpt = extractExcerpt(map, content)
        val externalUrl = "$BASE_URL/$slug"

        val translation = map["translation"]?.toString()?.trim()?.ifEmpty { null }
        val appreciation = map["appreciation"]?.toString()?.trim()?.ifEmpty { null }
        val annotations = extractAnnotations(map)
        val hasRuby = map["paragraph_ruby"] != null

        val auxiliary = if (translation != null || appreciation != null || !annotations.isNullOrEmpty()) {
            AuxiliaryContent(
                translation = translation,
                translationSource = translation?.let {
                    SourceInfo(
                        sourceName = "shi-ci.cn 译文",
                        licenseType = LicenseType.UNKNOWN,
                        referenceUrl = externalUrl,
                        licenseNote = "未明确标注版权声明，授权状态待核实"
                    )
                },
                annotations = annotations,
                annotationSource = if (!annotations.isNullOrEmpty()) {
                    SourceInfo(
                        sourceName = "shi-ci.cn 注释",
                        licenseType = LicenseType.UNKNOWN,
                        referenceUrl = externalUrl,
                        licenseNote = "未明确标注整理底本，授权状态待核实"
                    )
                } else null,
                appreciation = appreciation,
                appreciationSource = appreciation?.let {
                    SourceInfo(
                        sourceName = "shi-ci.cn 赏析",
                        licenseType = LicenseType.UNKNOWN,
                        referenceUrl = externalUrl,
                        licenseNote = "现代文学评论，授权状态待核实"
                    )
                }
            )
        } else null

        val now = System.currentTimeMillis()
        val contentAttr = ContentAttribution(
            origin = SourceOrigin.SHI_CI_CN,
            externalId = slug,
            provenance = ContentProvenance.ORIGINAL_ANCIENT,
            license = LegalLicense.UNSPECIFIED,
            reviewStatus = ReviewStatus.UNVERIFIED_DRAFT,
            licenseNote = "中国古典文学原作，网络接口转录版本未核实整理版权",
            fetchedAtEpochMs = now
        )

        val translationAttr = translation?.let {
            ContentAttribution(
                origin = SourceOrigin.SHI_CI_CN,
                externalId = "$slug#translation",
                provenance = ContentProvenance.UNKNOWN_PROVENANCE,
                license = LegalLicense.UNSPECIFIED,
                reviewStatus = ReviewStatus.UNVERIFIED_DRAFT,
                licenseNote = "shi-ci.cn 现代汉语译文，权利状态未指定",
                fetchedAtEpochMs = now
            )
        }

        val annotationAttr = if (!annotations.isNullOrEmpty()) {
            ContentAttribution(
                origin = SourceOrigin.SHI_CI_CN,
                externalId = "$slug#annotation",
                provenance = ContentProvenance.UNKNOWN_PROVENANCE,
                license = LegalLicense.UNSPECIFIED,
                reviewStatus = ReviewStatus.UNVERIFIED_DRAFT,
                licenseNote = "shi-ci.cn 词句注释，整理来源未指定",
                fetchedAtEpochMs = now
            )
        } else null

        val appreciationAttr = appreciation?.let {
            ContentAttribution(
                origin = SourceOrigin.SHI_CI_CN,
                externalId = "$slug#appreciation",
                provenance = ContentProvenance.UNKNOWN_PROVENANCE,
                license = LegalLicense.UNSPECIFIED,
                reviewStatus = ReviewStatus.UNVERIFIED_DRAFT,
                licenseNote = "shi-ci.cn 赏析文本，可能包含现代批评著作引用或机器生成，权利状态未指定",
                fetchedAtEpochMs = now
            )
        }

        val rubyAttr = if (hasRuby) {
            ContentAttribution(
                origin = SourceOrigin.SHI_CI_CN,
                externalId = "$slug#ruby",
                provenance = ContentProvenance.UNKNOWN_PROVENANCE,
                license = LegalLicense.UNSPECIFIED,
                reviewStatus = ReviewStatus.UNVERIFIED_DRAFT,
                licenseNote = "外部接口逐字拼音标注数据，无人工校核凭证",
                fetchedAtEpochMs = now
            )
        } else null

        val trace = WorkSourceTrace(
            source = SourceOrigin.SHI_CI_CN,
            externalWorkId = slug,
            externalUrl = externalUrl,
            externalTitle = title,
            externalAuthor = author,
            externalDynasty = dynasty,
            fetchedAtEpochMs = now,
            contentAttribution = contentAttr,
            translationAttribution = translationAttr,
            annotationAttribution = annotationAttr,
            appreciationAttribution = appreciationAttr,
            rubyPinyinAttribution = rubyAttr
        )

        return Work(
            id = "shici_$slug",
            title = title,
            author = author,
            dynasty = dynasty,
            genre = genre,
            content = content,
            excerpt = excerpt,
            auxiliary = auxiliary,
            source = "shi-ci.cn",
            sourceId = slug,
            licenseNote = "经由 shi-ci.cn 接口获取",
            textSource = SourceInfo(
                sourceName = "shi-ci.cn",
                licenseType = LicenseType.PUBLIC_DOMAIN,
                referenceUrl = externalUrl,
                licenseNote = "公有领域古诗文"
            ),
            sourceTraces = listOf(trace)
        )
    }

    private fun extractContent(map: Map<String, Any?>): String {
        val paragraphs = map["paragraphs"]
        if (paragraphs is List<*>) {
            val lines = paragraphs.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) return lines.joinToString("\n")
        }
        return map["content"]?.toString()?.trim() ?: ""
    }

    private fun mapGenre(rawGenre: String): Genre {
        val trimmed = rawGenre.trim()
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

    private fun extractAnnotations(map: Map<String, Any?>): List<AnnotationItem>? {
        val raw = map["annotation"] ?: return null
        val items = mutableListOf<AnnotationItem>()

        if (raw is List<*>) {
            raw.forEachIndexed { i, elem ->
                val text = elem?.toString()?.trim() ?: ""
                if (text.isNotBlank()) {
                    val (quote, note) = parseAnnotationString(text, i + 1)
                    items.add(AnnotationItem(index = i + 1, quote = quote, note = note))
                }
            }
        } else if (raw is String && raw.isNotBlank()) {
            val lines = raw.lines().filter { it.isNotBlank() }
            lines.forEachIndexed { i, line ->
                val (quote, note) = parseAnnotationString(line.trim(), i + 1)
                items.add(AnnotationItem(index = i + 1, quote = quote, note = note))
            }
        }

        return if (items.isNotEmpty()) items else null
    }

    private fun parseAnnotationString(text: String, fallbackIndex: Int): Pair<String, String> {
        val colonPos = text.indexOfFirst { it == '：' || it == ':' }
        return if (colonPos > 0 && colonPos < text.length - 1) {
            val quote = text.substring(0, colonPos).trim()
            val note = text.substring(colonPos + 1).trim()
            if (quote.isNotBlank() && note.isNotBlank()) {
                quote to note
            } else {
                "注$fallbackIndex" to text
            }
        } else {
            "注$fallbackIndex" to text
        }
    }
}
