package com.shi.yun.data.local.mapper

import com.shi.yun.data.local.entity.AuthorEntity
import com.shi.yun.data.local.entity.CipaiEntity
import com.shi.yun.data.local.entity.ComponentType
import com.shi.yun.data.local.entity.ContentAttributionEntity
import com.shi.yun.data.local.entity.ContentComponentEntity
import com.shi.yun.data.local.entity.DynastyEntity
import com.shi.yun.data.local.entity.ExternalSourceLinkEntity
import com.shi.yun.data.local.entity.FormEntity
import com.shi.yun.data.local.entity.GenreEntity
import com.shi.yun.data.local.entity.QupaiEntity
import com.shi.yun.data.local.entity.WorkAuthorEntity
import com.shi.yun.data.local.entity.WorkEntity
import com.shi.yun.data.local.entity.WorkSourceLinkEntity
import com.shi.yun.data.local.entity.WorkVersionClassificationEntity
import com.shi.yun.data.local.entity.WorkVersionContentEntity
import com.shi.yun.data.local.entity.WorkVersionEntity
import com.shi.yun.data.model.AnnotationItem
import com.shi.yun.data.model.AttributionInfo
import com.shi.yun.data.model.AttributionStatus
import com.shi.yun.data.model.AuxiliaryContent
import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.LayoutType
import com.shi.yun.data.model.LicenseType
import com.shi.yun.data.model.SourceInfo
import com.shi.yun.data.model.SourceOrigin
import com.shi.yun.data.model.Work
import com.shi.yun.data.model.WorkSourceTrace
import org.json.JSONArray

/**
 * 完整组装单部作品所需的数据载体 (WorkEntityBundle)
 */
data class WorkEntityBundle(
    val work: WorkEntity,
    val version: WorkVersionEntity,
    val authors: List<AuthorEntity> = emptyList(),
    val workAuthors: List<WorkAuthorEntity> = emptyList(),
    val components: List<ContentComponentEntity> = emptyList(),
    val versionContents: List<WorkVersionContentEntity> = emptyList(),
    val classifications: List<WorkVersionClassificationEntity> = emptyList(),
    val dynasty: DynastyEntity? = null,
    val genre: GenreEntity? = null,
    val cipai: CipaiEntity? = null,
    val qupai: QupaiEntity? = null,
    val form: FormEntity? = null,
    val attributions: List<ContentAttributionEntity> = emptyList(),
    val externalSourceLinks: List<ExternalSourceLinkEntity> = emptyList(),
    val workSourceLinks: List<WorkSourceLinkEntity> = emptyList()
)

/**
 * 实体映射器 (WorkEntityMapper)
 *
 * 严格按照诗云数据库 V1.0 规范，将 Room 多表实体安全、无损、结构化地映射为 UI 领域模型 [Work]。
 *
 * 核心规则遵从：
 * 1. 严格使用调整后的五维内容体系（CONTENT, TRANSLATION, ANNOTATION, BACKGROUND, APPRECIATION），不复引入已移除的 PHONETIC。
 * 2. 创作背景 BACKGROUND 与文学赏析 APPRECIATION 严格隔离，绝不相互合并。
 * 3. 作者生平 (authors.biography) 属于作者文人档案独立属性，绝不泄露映射入作品内容组件。
 * 4. 辅助内容 [AuxiliaryContent] 强一致性约束：若内容非空，其对应的来源记录必有完备结构。
 */
object WorkEntityMapper {

    /**
     * 将数据载体 [WorkEntityBundle] 映射为领域模型 [Work]
     */
    fun toDomainWork(bundle: WorkEntityBundle): Work {
        return toDomainWork(
            work = bundle.work,
            version = bundle.version,
            authors = bundle.authors,
            workAuthors = bundle.workAuthors,
            components = bundle.components,
            versionContents = bundle.versionContents,
            classifications = bundle.classifications,
            dynasty = bundle.dynasty,
            genre = bundle.genre,
            cipai = bundle.cipai,
            qupai = bundle.qupai,
            form = bundle.form,
            attributions = bundle.attributions,
            externalSourceLinks = bundle.externalSourceLinks,
            workSourceLinks = bundle.workSourceLinks
        )
    }

    /**
     * 将分散的多表数据组装映射为领域模型 [Work]
     */
    fun toDomainWork(
        work: WorkEntity,
        version: WorkVersionEntity,
        authors: List<AuthorEntity> = emptyList(),
        workAuthors: List<WorkAuthorEntity> = emptyList(),
        components: List<ContentComponentEntity> = emptyList(),
        versionContents: List<WorkVersionContentEntity> = emptyList(),
        classifications: List<WorkVersionClassificationEntity> = emptyList(),
        dynasty: DynastyEntity? = null,
        genre: GenreEntity? = null,
        cipai: CipaiEntity? = null,
        qupai: QupaiEntity? = null,
        form: FormEntity? = null,
        attributions: List<ContentAttributionEntity> = emptyList(),
        externalSourceLinks: List<ExternalSourceLinkEntity> = emptyList(),
        workSourceLinks: List<WorkSourceLinkEntity> = emptyList()
    ): Work {
        // 1. 作者姓名串联（以 author_order 排序）
        val authorName = if (authors.isNotEmpty()) {
            authors.joinToString("、") { it.standardName }
        } else {
            ""
        }

        // 2. 朝代解析（优先分类实体，其次分类维度记录，再次主作者所属朝代）
        val dynastyName = dynasty?.standardName
            ?: classifications.firstOrNull { it.dimensionType == "DYNASTY" }?.classificationId
            ?: authors.firstOrNull()?.dynastyId
            ?: ""

        // 3. 体裁枚举安全解析（优先分类实体/维度，后备词曲推断）
        val resolvedGenre = resolveGenre(genre, cipai, qupai, classifications)

        // 4. 正文内容提取
        val contentComponent = components.firstOrNull { it.componentType == ComponentType.CONTENT.code }
        val contentText = contentComponent?.contentPayload ?: ""

        // 5. 摘要提取（优先版本明确标注的 excerpt，次选正文首行）
        val excerptText = version.excerpt?.takeIf { it.isNotBlank() }
            ?: contentText.lines().firstOrNull { it.isNotBlank() }
            ?: ""

        // 6. 词牌/曲牌名称
        val cipaiName = cipai?.standardName
            ?: qupai?.standardName
            ?: classifications.firstOrNull { it.dimensionType == "CIPAI" }?.classificationId
            ?: classifications.firstOrNull { it.dimensionType == "QUPAI" }?.classificationId

        // 7. 排版枚举解析
        val layoutType = LayoutType.fromCodeOrNull(version.layoutType)

        // 8. 外部数据源映射表缓存
        val sourceLinkMap = externalSourceLinks.associateBy { it.linkId }

        // 9. 正文规范底本来源 (textSource) 组装
        val textSource = resolveTextSource(contentComponent, attributions, workSourceLinks, sourceLinkMap)

        // 10. 作者归属争议考订 (attribution) 解析
        val attributionInfo = resolveAttribution(work, workAuthors, authors)

        // 11. 辅助内容 (auxiliary) 组装：译文、注释、创作背景、文学赏析
        val auxiliary = resolveAuxiliary(components, attributions, sourceLinkMap)

        // 12. 旧来源兼容字段与多源追溯列表
        val primaryWorkSourceLink = workSourceLinks.firstOrNull { it.isPrimary == 1 } ?: workSourceLinks.firstOrNull()
        val primaryExternalLink = primaryWorkSourceLink?.let { sourceLinkMap[it.externalSourceLinkId] }
        val source = primaryExternalLink?.sourceCode ?: ""
        val sourceId = primaryExternalLink?.externalItemId ?: ""
        val licenseNote = primaryExternalLink?.licenseDesc ?: ""

        val sourceTraces = resolveSourceTraces(work, authorName, dynastyName, workSourceLinks, sourceLinkMap)

        return Work(
            id = work.workId,
            title = work.standardTitle,
            author = authorName,
            dynasty = dynastyName,
            genre = resolvedGenre,
            content = contentText,
            excerpt = excerptText,
            cipai = cipaiName,
            subtitle = version.subtitle,
            layout = layoutType,
            preface = version.preface,
            textSource = textSource,
            attribution = attributionInfo,
            auxiliary = auxiliary,
            source = source,
            sourceId = sourceId,
            licenseNote = licenseNote,
            sourceTraces = sourceTraces
        )
    }

    /**
     * 解析作品体裁受控枚举
     */
    private fun resolveGenre(
        genreEntity: GenreEntity?,
        cipaiEntity: CipaiEntity?,
        qupaiEntity: QupaiEntity?,
        classifications: List<WorkVersionClassificationEntity> = emptyList()
    ): Genre {
        if (genreEntity != null) {
            val fromCode = Genre.fromCodeOrNull(genreEntity.genreId)
                ?: Genre.fromCodeOrNull(genreEntity.standardName)
            if (fromCode != null) return fromCode

            val fromName = Genre.entries.find { it.displayName == genreEntity.standardName.trim() }
            if (fromName != null) return fromName
        }

        val classGenre = classifications.firstOrNull { it.dimensionType == "GENRE" }?.classificationId
        if (classGenre != null) {
            val fromCode = Genre.fromCodeOrNull(classGenre)
            if (fromCode != null) return fromCode
            val fromName = Genre.entries.find { it.displayName == classGenre.trim() }
            if (fromName != null) return fromName
        }

        if (cipaiEntity != null || classifications.any { it.dimensionType == "CIPAI" }) return Genre.CI
        if (qupaiEntity != null || classifications.any { it.dimensionType == "QUPAI" }) return Genre.QU
        return Genre.POEM
    }

    /**
     * 解析正文底本来源
     */
    private fun resolveTextSource(
        contentComponent: ContentComponentEntity?,
        attributions: List<ContentAttributionEntity>,
        workSourceLinks: List<WorkSourceLinkEntity>,
        sourceLinkMap: Map<String, ExternalSourceLinkEntity>
    ): SourceInfo? {
        if (contentComponent != null) {
            val contentAttr = attributions.firstOrNull { it.componentId == contentComponent.componentId }
            if (contentAttr != null) {
                val externalLink = contentAttr.externalSourceLinkId?.let { sourceLinkMap[it] }
                val sourceName = contentAttr.curatorName?.takeIf { it.isNotBlank() }
                    ?: externalLink?.sourceCode
                    ?: "权威底本"
                val licenseType = LicenseType.fromCodeOrNull(contentAttr.licenseType) ?: LicenseType.UNKNOWN
                val referenceUrl = externalLink?.itemUrl
                val licenseNote = contentAttr.licenseNote ?: externalLink?.licenseDesc ?: "规范底本"
                return SourceInfo(
                    sourceName = sourceName,
                    licenseType = licenseType,
                    referenceUrl = referenceUrl,
                    licenseNote = licenseNote
                )
            }
        }

        // 回退至作品级外部来源
        val primaryLink = workSourceLinks.firstOrNull { it.isPrimary == 1 } ?: workSourceLinks.firstOrNull()
        val primaryExternal = primaryLink?.let { sourceLinkMap[it.externalSourceLinkId] }
        if (primaryExternal != null) {
            return SourceInfo(
                sourceName = primaryExternal.sourceCode,
                licenseType = LicenseType.UNKNOWN,
                referenceUrl = primaryExternal.itemUrl,
                licenseNote = primaryExternal.licenseDesc ?: "规范底本"
            )
        }

        return null
    }

    /**
     * 解析作者归属争议
     */
    private fun resolveAttribution(
        work: WorkEntity,
        workAuthors: List<WorkAuthorEntity>,
        authors: List<AuthorEntity>
    ): AttributionInfo? {
        val disputedAuthorRel = workAuthors.firstOrNull {
            it.determinationStatus.equals("DISPUTED", ignoreCase = true) ||
            it.determinationStatus.equals("DOUBTFUL", ignoreCase = true)
        }

        if (disputedAuthorRel != null || work.identityStatus.equals("DISPUTED", ignoreCase = true)) {
            val status = if (disputedAuthorRel?.determinationStatus.equals("DOUBTFUL", ignoreCase = true)) {
                AttributionStatus.DOUBTFUL
            } else {
                AttributionStatus.DISPUTED
            }
            val candidateNames = authors.drop(1).map { it.standardName }
            val note = disputedAuthorRel?.evidence?.takeIf { it.isNotBlank() }
                ?: disputedAuthorRel?.reviewNote?.takeIf { it.isNotBlank() }
                ?: "作者归属存疑，学术界尚存分歧"
            return AttributionInfo(
                status = status,
                candidateAuthors = candidateNames,
                note = note
            )
        }
        return null
    }

    /**
     * 解析辅助内容集合 (AuxiliaryContent)
     */
    private fun resolveAuxiliary(
        components: List<ContentComponentEntity>,
        attributions: List<ContentAttributionEntity>,
        sourceLinkMap: Map<String, ExternalSourceLinkEntity>
    ): AuxiliaryContent? {
        val attrMap = attributions.associateBy { it.componentId }

        // 1. 译文 (TRANSLATION)
        val transComp = components.firstOrNull { it.componentType == ComponentType.TRANSLATION.code }
        val translationText = transComp?.contentPayload?.trim()?.takeIf { it.isNotEmpty() }
        val translationSource = if (translationText != null) {
            buildComponentSourceInfo(transComp.componentId, attrMap, sourceLinkMap, defaultName = "译文底本", defaultNote = "收录译文")
        } else null

        // 2. 注释 (ANNOTATION)
        val annotComp = components.firstOrNull { it.componentType == ComponentType.ANNOTATION.code }
        val annotationsList = annotComp?.let { parseAnnotations(it.contentPayload, it.rawFormat) }
        val annotationSource = if (!annotationsList.isNullOrEmpty()) {
            buildComponentSourceInfo(annotComp?.componentId, attrMap, sourceLinkMap, defaultName = "注释底本", defaultNote = "收录注释")
        } else null

        // 3. 创作背景 (BACKGROUND) - 严格保持独立，绝不混入文学赏析，也绝不读取作者生平
        val bgComp = components.firstOrNull { it.componentType == ComponentType.BACKGROUND.code }
        val backgroundText = bgComp?.contentPayload?.trim()?.takeIf { it.isNotEmpty() }
        val backgroundSource = if (backgroundText != null) {
            buildComponentSourceInfo(bgComp?.componentId, attrMap, sourceLinkMap, defaultName = "创作背景资料", defaultNote = "收录创作背景")
        } else null

        // 4. 文学赏析 (APPRECIATION) - 严格保持独立，绝不与背景合并
        val apprecComp = components.firstOrNull { it.componentType == ComponentType.APPRECIATION.code }
        val appreciationText = apprecComp?.contentPayload?.trim()?.takeIf { it.isNotEmpty() }
        val appreciationSource = if (appreciationText != null) {
            buildComponentSourceInfo(apprecComp?.componentId, attrMap, sourceLinkMap, defaultName = "文学赏析资料", defaultNote = "收录文学赏析")
        } else null

        // 若四项辅助内容均为空，则整个 auxiliary 为 null
        if (translationText == null && annotationsList.isNullOrEmpty() && backgroundText == null && appreciationText == null) {
            return null
        }

        return AuxiliaryContent(
            translation = translationText,
            translationSource = translationSource,
            annotations = annotationsList,
            annotationSource = annotationSource,
            appreciation = appreciationText,
            appreciationSource = appreciationSource,
            background = backgroundText,
            backgroundSource = backgroundSource
        )
    }

    /**
     * 为内容组件构建强一致性的来源记录
     */
    private fun buildComponentSourceInfo(
        componentId: String?,
        attrMap: Map<String, ContentAttributionEntity>,
        sourceLinkMap: Map<String, ExternalSourceLinkEntity>,
        defaultName: String,
        defaultNote: String
    ): SourceInfo {
        val attr = componentId?.let { attrMap[it] }
        val externalLink = attr?.externalSourceLinkId?.let { sourceLinkMap[it] }

        val sourceName = attr?.curatorName?.takeIf { it.isNotBlank() }
            ?: externalLink?.sourceCode?.takeIf { it.isNotBlank() }
            ?: defaultName
        val licenseType = LicenseType.fromCodeOrNull(attr?.licenseType) ?: LicenseType.UNKNOWN
        val referenceUrl = externalLink?.itemUrl
        val licenseNote = attr?.licenseNote?.takeIf { it.isNotBlank() }
            ?: externalLink?.licenseDesc?.takeIf { it.isNotBlank() }
            ?: defaultNote

        return SourceInfo(
            sourceName = sourceName,
            licenseType = licenseType,
            referenceUrl = referenceUrl,
            licenseNote = licenseNote
        )
    }

    /**
     * 解析注释正文（支持结构化 JSON 数组与纯文本格式）
     */
    fun parseAnnotations(payload: String, rawFormat: String = "PLAIN_TEXT"): List<AnnotationItem>? {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return null

        if (rawFormat.equals("STRUCTURED_JSON", ignoreCase = true) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                val array = JSONArray(trimmed)
                val list = mutableListOf<AnnotationItem>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val quote = obj.optString("quote", "").trim()
                    val note = obj.optString("note", "").trim()
                    if (quote.isNotEmpty() && note.isNotEmpty()) {
                        val index = obj.optInt("index", i + 1)
                        val phonetic = obj.optString("phonetic", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                        val targetLine = if (obj.has("targetLine") && !obj.isNull("targetLine")) obj.getInt("targetLine") else null
                        list.add(AnnotationItem(index, quote, note, phonetic, targetLine))
                    }
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {
                // 回退到文本行解析
            }
        }

        // 纯文本按行解析
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val list = mutableListOf<AnnotationItem>()
        for ((idx, line) in lines.withIndex()) {
            val colonIdx = line.indexOfAny(charArrayOf(':', '：'))
            if (colonIdx in 1 until line.length - 1) {
                val rawQuote = line.substring(0, colonIdx).trim()
                val rawNote = line.substring(colonIdx + 1).trim()
                val cleanQuote = rawQuote.replace(Regex("^\\[?\\d+[]\\.\\s、]+"), "").trim()
                val effectiveQuote = cleanQuote.ifEmpty { rawQuote }
                if (effectiveQuote.isNotEmpty() && rawNote.isNotEmpty()) {
                    list.add(AnnotationItem(index = idx + 1, quote = effectiveQuote, note = rawNote))
                }
            } else {
                val quote = line.take(10).trim()
                if (quote.isNotEmpty()) {
                    list.add(AnnotationItem(index = idx + 1, quote = quote, note = line))
                }
            }
        }
        return list.takeIf { it.isNotEmpty() }
    }

    /**
     * 构建多数据源追溯列表
     */
    private fun resolveSourceTraces(
        work: WorkEntity,
        authorName: String,
        dynastyName: String,
        workSourceLinks: List<WorkSourceLinkEntity>,
        sourceLinkMap: Map<String, ExternalSourceLinkEntity>
    ): List<WorkSourceTrace> {
        if (workSourceLinks.isEmpty()) return emptyList()

        return workSourceLinks.mapNotNull { link ->
            val externalLink = sourceLinkMap[link.externalSourceLinkId] ?: return@mapNotNull null
            WorkSourceTrace(
                source = parseSourceOrigin(externalLink.sourceCode),
                externalWorkId = externalLink.externalItemId,
                externalUrl = externalLink.itemUrl,
                externalTitle = work.standardTitle,
                externalAuthor = authorName,
                externalDynasty = dynastyName
            )
        }
    }

    /**
     * 数据源编码转受控枚举
     */
    private fun parseSourceOrigin(code: String?): SourceOrigin {
        if (code.isNullOrBlank()) return SourceOrigin.LOCAL_CANONICAL
        return when (code.trim().uppercase()) {
            "SHI_CI_CN", "SHI-CI.CN", "SHICI" -> SourceOrigin.SHI_CI_CN
            "POETRY_PALEMOKY", "PALEMOKY", "POEMS_DB", "POETRY_CN" -> SourceOrigin.POETRY_PALEMOKY
            "USER_CUSTOM", "CUSTOM" -> SourceOrigin.USER_CUSTOM
            else -> SourceOrigin.LOCAL_CANONICAL
        }
    }
}
