package com.shi.yun.data.local.importer.merger

import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.SourceImportInfo
import com.shi.yun.data.local.importer.model.TitleSourceType
import com.shi.yun.data.local.importer.model.WorkImportItem
import com.shi.yun.data.model.AnnotationItem

/**
 * 维度枚举 (MergeDimension)
 */
enum class MergeDimension(val code: String, val displayName: String) {
    TITLE("TITLE", "作品题名"),
    CONTENT("CONTENT", "作品正文"),
    TRANSLATION("TRANSLATION", "白话译文"),
    ANNOTATION("ANNOTATION", "词句注释"),
    BACKGROUND("BACKGROUND", "创作背景"),
    APPRECIATION("APPRECIATION", "文学赏析"),
    AUTHOR("AUTHOR", "作者档案"),
    DYNASTY("DYNASTY", "朝代属性"),
    GENRE_FORM("GENRE_FORM", "体裁与形式")
}

/**
 * 备选差异内容条目 (AlternativeSourceItem)
 */
data class AlternativeSourceItem(
    val sourceName: String,
    val sourceCode: String,
    val contentSnippet: String,
    val isIdenticalToAdopted: Boolean
)

/**
 * 单维内容决策与差异审计记录 (ContentDiffRecord)
 */
data class ContentDiffRecord(
    val dimension: MergeDimension,
    val adoptedSource: String,
    val adoptedSourceCode: String,
    val adoptedContentSummary: String?,
    val identicalSourceCount: Int,
    val alternativeSources: List<AlternativeSourceItem>,
    val decisionReason: String
)

/**
 * 多来源合并审计报告 (MultiSourceMergeAudit)
 */
data class MultiSourceMergeAudit(
    val workTitle: String,
    val candidateCount: Int,
    val diffRecords: List<ContentDiffRecord>,
    val resolvedItem: WorkImportItem
)

/** 内部体裁与形式解析结果载体 */
private data class GenreFormResolution(
    val genre: String?,
    val form: String?,
    val cipai: String?,
    val qupai: String?,
    val diffRecord: ContentDiffRecord
)

/** 内部注释解析结果载体 */
private data class AnnotationResolution(
    val notes: String?,
    val noteItems: List<AnnotationItem>?,
    val sourceInfo: SourceImportInfo?,
    val diffRecord: ContentDiffRecord
)

/** 内部标题决策结果载体 */
private data class TitleResolution(
    val title: String,
    val rawTitle: String?,
    val titleSourceType: TitleSourceType,
    val diffRecord: ContentDiffRecord
)

/**
 * 多来源数据整合与来源优先级合并器 (MultiSourceWorkMerger)
 *
 * 严格践行阶段 4.2 及宋词题名决策原则：
 * 1. 题名优先级：SOURCE_TITLE > GENERATED_FIRST_LINE > UNKNOWN；
 *    - 绝不允许 GENERATED_FIRST_LINE 覆盖明确的 SOURCE_TITLE；
 *    - 多来源提供不同真实题名时，按优先级采纳并记录差异备选供人工审核；
 * 2. 细粒度独立管理：同一作品的正文、译文、注释、背景、赏析、作者、朝代分别独立确定采用来源；
 * 3. 相同内容去重采用：多来源提供完全一致的内容时，仅保留一份并记录单个采用来源；
 * 4. 解释性内容分歧处理：译注评优先采用来源明确、证据确凿的古诗文网 (gushiwen.cn) 内容；证据不足时严格依既定优先级排序；
 * 5. 严禁自动从赏析中拆分创作背景；
 * 6. 作者档案独立管理，未知作者保持空关联，坚决不创建“佚名”伪作者。
 */
class MultiSourceWorkMerger(
    /** 可配置的来源优先级权重表，数值越小优先级越高 */
    private val priorityRanks: Map<String, Int> = DEFAULT_PRIORITY_RANKS
) {

    companion object {
        val DEFAULT_PRIORITY_RANKS = mapOf(
            "GUSHIWEN" to 0,        // 古诗文网 (证据确凿时顶级优先)
            "POEMS_DB" to 1,        // yxcs/poems-db (离线第一优先级)
            "POETRY_CN" to 2,       // Poetry_CN (离线第二优先级)
            "CHINESE_POETRY" to 3,  // chinese-poetry (古籍底本对照)
            "SHI_CI_CN" to 4,       // shi-ci.cn (在线源)
            "PALEMOKY" to 5,        // 诗泉 (在线源)
            "SNOWTRACES" to 6       // Snowtraces (候选源)
        )
    }

    /**
     * 对同一首作品来自多个数据源的候选列表进行合并整合
     */
    fun merge(candidates: List<SourceWorkCandidate>): MultiSourceMergeAudit {
        require(candidates.isNotEmpty()) { "合并候选列表不能为空" }

        val diffRecords = mutableListOf<ContentDiffRecord>()

        // 1. 独立决策：作品题名 (TITLE)
        val titleRes = resolveTitle(candidates)
        diffRecords.add(titleRes.diffRecord)

        val standardTitle = titleRes.title
        val subtitle = candidates.firstNotNullOfOrNull { it.subtitle?.trim()?.ifEmpty { null } }
        val preface = candidates.firstNotNullOfOrNull { it.preface?.trim()?.ifEmpty { null } }
        val layoutType = candidates.firstNotNullOfOrNull { it.layoutType?.trim()?.ifEmpty { null } }
        val excerpt = candidates.firstNotNullOfOrNull { it.excerpt?.trim()?.ifEmpty { null } }

        // 2. 独立决策：作者档案 (AUTHOR)
        val (adoptedAuthor, authorDiff) = resolveAuthor(candidates)
        diffRecords.add(authorDiff)

        // 3. 独立决策：朝代与分类 (DYNASTY, GENRE_FORM)
        val (adoptedDynasty, dynastyDiff) = resolveDynasty(candidates)
        diffRecords.add(dynastyDiff)

        val genreRes = resolveGenreAndForm(candidates)
        diffRecords.add(genreRes.diffRecord)

        // 合并专题标签 (去重保留所有来源给出的有效标签)
        val allTags = candidates.flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        // 4. 独立决策：五维内容组件
        // 4.1 正文 (CONTENT)
        val (adoptedContent, adoptedContentSource, contentDiff) = resolveContent(candidates)
        diffRecords.add(contentDiff)

        // 4.2 译文 (TRANSLATION)
        val (adoptedTranslation, adoptedTranslationSource, transDiff) = resolveTranslation(candidates)
        diffRecords.add(transDiff)

        // 4.3 注释 (ANNOTATION)
        val noteRes = resolveAnnotations(candidates)
        diffRecords.add(noteRes.diffRecord)

        // 4.4 创作背景 (BACKGROUND) - 严禁自动从 APPRECIATION 拆分
        val (adoptedBackground, adoptedBgSource, bgDiff) = resolveBackground(candidates)
        diffRecords.add(bgDiff)

        // 4.5 文学赏析 (APPRECIATION)
        val (adoptedAppreciation, adoptedApprecSource, apprecDiff) = resolveAppreciation(candidates)
        diffRecords.add(apprecDiff)

        // 5. 组装整篇作品级来源凭证 (workSource)
        val primaryCandidate = candidates.minByOrNull { getRank(it, isAuxiliary = false) } ?: candidates.first()
        val workSource = SourceImportInfo(
            sourceName = primaryCandidate.sourceName,
            licenseType = primaryCandidate.licenseType ?: "PUBLIC_DOMAIN",
            referenceUrl = primaryCandidate.referenceUrl,
            licenseNote = primaryCandidate.licenseNote ?: "多来源细粒度整合作品",
            curatorName = primaryCandidate.curatorName
        )

        // 原始 Payload 汇总归档，防止原始信息丢失（包含来源代码、外部ID、原始文件路径及版本标识）
        val aggregatedRawPayload = buildString {
            append("MULTI_SOURCE_MERGE[candidates=${candidates.size}];")
            candidates.forEach { c ->
                val fileInfo = if (!c.rawFilePath.isNullOrBlank()) "@${c.rawFilePath}" else ""
                val verInfo = if (!c.sourceVersion.isNullOrBlank()) "#${c.sourceVersion}" else ""
                append("(${c.sourceCode}:${c.externalWorkId ?: "no_id"}$fileInfo$verInfo);")
            }
        }

        val resolvedWorkItem = WorkImportItem(
            title = standardTitle,
            rawTitle = titleRes.rawTitle,
            titleSourceType = titleRes.titleSourceType,
            subtitle = subtitle,
            preface = preface,
            layoutType = layoutType,
            excerpt = excerpt,
            author = adoptedAuthor,
            coAuthors = candidates.firstNotNullOfOrNull { it.coAuthors.takeIf { list -> list.isNotEmpty() } } ?: emptyList(),
            dynasty = adoptedDynasty,
            genre = genreRes.genre,
            form = genreRes.form,
            cipai = genreRes.cipai,
            qupai = genreRes.qupai,
            tags = allTags,
            content = adoptedContent,
            contentSource = adoptedContentSource,
            translation = adoptedTranslation,
            translationSource = adoptedTranslationSource,
            annotations = noteRes.notes,
            annotationItems = noteRes.noteItems,
            annotationSource = noteRes.sourceInfo,
            background = adoptedBackground,
            backgroundSource = adoptedBgSource,
            appreciation = adoptedAppreciation,
            appreciationSource = adoptedApprecSource,
            workSource = workSource,
            externalSourceCode = primaryCandidate.sourceCode,
            externalWorkId = primaryCandidate.externalWorkId,
            externalUrl = primaryCandidate.referenceUrl,
            sourceVersion = primaryCandidate.sourceVersion,
            rawFormat = "MULTI_SOURCE_JSON",
            rawPayload = aggregatedRawPayload
        )

        return MultiSourceMergeAudit(
            workTitle = standardTitle,
            candidateCount = candidates.size,
            diffRecords = diffRecords,
            resolvedItem = resolvedWorkItem
        )
    }

    /**
     * 独立决策作品题名 (TITLE)
     *
     * 题名优先级：
     * 1. SOURCE_TITLE：优先从具备来源真实题名的候选挑选（按来源优先级高低排序）；
     * 2. GENERATED_FIRST_LINE：所有来源均无真实题名时，才采用首句生成的默认题名；
     * 3. UNKNOWN：无法获得任何题名时保持为空。
     */
    private fun resolveTitle(candidates: List<SourceWorkCandidate>): TitleResolution {
        val sourceTitleCandidates = candidates.filter {
            !it.title.isNullOrBlank() && it.titleSourceType == TitleSourceType.SOURCE_TITLE
        }

        if (sourceTitleCandidates.isNotEmpty()) {
            val best = sourceTitleCandidates.minByOrNull { getRank(it, isAuxiliary = false) }!!
            val adoptedTitle = best.title!!.trim()

            val identicalCount = sourceTitleCandidates.count { it.title!!.trim() == adoptedTitle }
            val alternatives = sourceTitleCandidates.filter { it.title!!.trim() != adoptedTitle }.map {
                AlternativeSourceItem(
                    sourceName = it.sourceName,
                    sourceCode = it.sourceCode,
                    contentSnippet = "来源题名: ${it.title}",
                    isIdenticalToAdopted = false
                )
            }

            val reason = if (alternatives.isNotEmpty()) {
                "存在不同来源真实题名分歧，依据来源优先级采纳 [${best.sourceName}] 题名: '$adoptedTitle'，备选题名已归档供复核"
            } else {
                "采纳来源 [${best.sourceName}] 明确提供的真实题名"
            }

            val record = ContentDiffRecord(
                dimension = MergeDimension.TITLE,
                adoptedSource = best.sourceName,
                adoptedSourceCode = best.sourceCode,
                adoptedContentSummary = adoptedTitle,
                identicalSourceCount = identicalCount,
                alternativeSources = alternatives,
                decisionReason = reason
            )
            return TitleResolution(
                title = adoptedTitle,
                rawTitle = best.rawTitle,
                titleSourceType = TitleSourceType.SOURCE_TITLE,
                diffRecord = record
            )
        }

        // 次选：GENERATED_FIRST_LINE
        val generatedCandidates = candidates.filter {
            !it.title.isNullOrBlank() && it.titleSourceType == TitleSourceType.GENERATED_FIRST_LINE
        }

        if (generatedCandidates.isNotEmpty()) {
            val best = generatedCandidates.minByOrNull { getRank(it, isAuxiliary = false) }!!
            val adoptedTitle = best.title!!.trim()

            val record = ContentDiffRecord(
                dimension = MergeDimension.TITLE,
                adoptedSource = best.sourceName,
                adoptedSourceCode = best.sourceCode,
                adoptedContentSummary = adoptedTitle,
                identicalSourceCount = generatedCandidates.count { it.title!!.trim() == adoptedTitle },
                alternativeSources = emptyList(),
                decisionReason = "所有来源均无独立题名，依规范采用从正文首句提取的默认题名 (来源: [${best.sourceName}])"
            )
            return TitleResolution(
                title = adoptedTitle,
                rawTitle = best.rawTitle,
                titleSourceType = TitleSourceType.GENERATED_FIRST_LINE,
                diffRecord = record
            )
        }

        // 兜底：所有来源均无任何题名
        val fallback = candidates.first()
        val record = ContentDiffRecord(
            dimension = MergeDimension.TITLE,
            adoptedSource = "NONE",
            adoptedSourceCode = "NONE",
            adoptedContentSummary = null,
            identicalSourceCount = candidates.size,
            alternativeSources = emptyList(),
            decisionReason = "所有来源均无独立题名且无法提取正文首句，题名保持为空待人工确认"
        )
        return TitleResolution(
            title = "",
            rawTitle = fallback.rawTitle,
            titleSourceType = TitleSourceType.UNKNOWN,
            diffRecord = record
        )
    }

    /**
     * 决策作者档案：未知作者保持为 null，不创建匿名实体；
     * 作者生平及来源严格独立于作品内容组件。
     */
    private fun resolveAuthor(candidates: List<SourceWorkCandidate>): Pair<AuthorImportItem?, ContentDiffRecord> {
        val candidatesWithAuthor = candidates.filter {
            it.author != null && it.author.name.isNotBlank() && it.author.name.trim() != "佚名" && it.author.name.trim() != "未知"
        }

        if (candidatesWithAuthor.isEmpty()) {
            val record = ContentDiffRecord(
                dimension = MergeDimension.AUTHOR,
                adoptedSource = "NONE",
                adoptedSourceCode = "NONE",
                adoptedContentSummary = null,
                identicalSourceCount = candidates.size,
                alternativeSources = emptyList(),
                decisionReason = "所有来源均无已知作者或标记为佚名，依据规范保持空关联 (null)，坚决不创建虚假作者实体"
            )
            return null to record
        }

        // 按优先级排序挑选最佳作者档案
        val sorted = candidatesWithAuthor.sortedBy { getRank(it, isAuxiliary = false) }
        val best = sorted.first()
        val adopted = best.author!!

        val alternatives = mutableListOf<AlternativeSourceItem>()
        var identicalCount = 0

        candidatesWithAuthor.forEach { c ->
            val other = c.author!!
            if (other.name.trim() == adopted.name.trim()) {
                identicalCount++
            } else {
                alternatives.add(
                    AlternativeSourceItem(
                        sourceName = c.sourceName,
                        sourceCode = c.sourceCode,
                        contentSnippet = "作者姓名: ${other.name}",
                        isIdenticalToAdopted = false
                    )
                )
            }
        }

        val record = ContentDiffRecord(
            dimension = MergeDimension.AUTHOR,
            adoptedSource = best.sourceName,
            adoptedSourceCode = best.sourceCode,
            adoptedContentSummary = "${adopted.name} (朝代: ${adopted.dynasty ?: "未知"}, 生平来源: ${adopted.biographySource ?: "独立保存"})",
            identicalSourceCount = identicalCount,
            alternativeSources = alternatives,
            decisionReason = "依据来源优先级由 [${best.sourceName}] 确定作者，作者生平档案完全独立"
        )

        return adopted to record
    }

    /**
     * 决策朝代属性
     */
    private fun resolveDynasty(candidates: List<SourceWorkCandidate>): Pair<String?, ContentDiffRecord> {
        val valid = candidates.filter { !it.dynasty.isNullOrBlank() }
        if (valid.isEmpty()) {
            return null to ContentDiffRecord(
                dimension = MergeDimension.DYNASTY,
                adoptedSource = "NONE",
                adoptedSourceCode = "NONE",
                adoptedContentSummary = null,
                identicalSourceCount = candidates.size,
                alternativeSources = emptyList(),
                decisionReason = "所有来源均未提供明确朝代，保持为空"
            )
        }

        val best = valid.minByOrNull { getRank(it, isAuxiliary = false) }!!
        val adopted = best.dynasty!!.trim()

        val alternatives = valid.filter { it.dynasty!!.trim() != adopted }.map {
            AlternativeSourceItem(it.sourceName, it.sourceCode, it.dynasty!!.trim(), false)
        }

        val record = ContentDiffRecord(
            dimension = MergeDimension.DYNASTY,
            adoptedSource = best.sourceName,
            adoptedSourceCode = best.sourceCode,
            adoptedContentSummary = adopted,
            identicalSourceCount = valid.count { it.dynasty!!.trim() == adopted },
            alternativeSources = alternatives,
            decisionReason = "由 [${best.sourceName}] 独立确定朝代属性"
        )
        return adopted to record
    }

    /**
     * 决策体裁与形式属性
     */
    private fun resolveGenreAndForm(candidates: List<SourceWorkCandidate>): GenreFormResolution {
        val genreCandidate = candidates.firstOrNull { !it.genre.isNullOrBlank() }
        val formCandidate = candidates.firstOrNull { !it.form.isNullOrBlank() }
        val cipaiCandidate = candidates.firstOrNull { !it.cipai.isNullOrBlank() }
        val qupaiCandidate = candidates.firstOrNull { !it.qupai.isNullOrBlank() }

        val adoptedGenre = genreCandidate?.genre?.trim()
        val adoptedForm = formCandidate?.form?.trim()
        val adoptedCipai = cipaiCandidate?.cipai?.trim()
        val adoptedQupai = qupaiCandidate?.qupai?.trim()

        val record = ContentDiffRecord(
            dimension = MergeDimension.GENRE_FORM,
            adoptedSource = genreCandidate?.sourceName ?: "NONE",
            adoptedSourceCode = genreCandidate?.sourceCode ?: "NONE",
            adoptedContentSummary = "体裁: $adoptedGenre, 形式: $adoptedForm, 词牌: $adoptedCipai, 曲牌: $adoptedQupai",
            identicalSourceCount = 1,
            alternativeSources = emptyList(),
            decisionReason = "各分类属性独立从具备有效元数据的来源采用，缺失则保持 null，不盲目推测"
        )

        return GenreFormResolution(adoptedGenre, adoptedForm, adoptedCipai, adoptedQupai, record)
    }

    /**
     * 决策正文 (CONTENT)
     */
    private fun resolveContent(candidates: List<SourceWorkCandidate>): Triple<String, SourceImportInfo, ContentDiffRecord> {
        val valid = candidates.filter { !it.content.isNullOrBlank() }
        require(valid.isNotEmpty()) { "作品正文在所有来源中均为空，无法建立有效作品" }

        val best = valid.minByOrNull { getRank(it, isAuxiliary = false) }!!
        val adopted = best.content!!.trim()

        val identicalCount = valid.count { it.content!!.trim() == adopted }
        val alternatives = valid.filter { it.content!!.trim() != adopted }.map {
            AlternativeSourceItem(it.sourceName, it.sourceCode, it.content!!.trim().take(40) + "...", false)
        }

        val sourceInfo = SourceImportInfo(
            sourceName = best.sourceName,
            licenseType = best.licenseType ?: "PUBLIC_DOMAIN",
            referenceUrl = best.referenceUrl,
            licenseNote = best.licenseNote ?: "中国古典文学原作底本",
            curatorName = best.curatorName
        )

        val record = ContentDiffRecord(
            dimension = MergeDimension.CONTENT,
            adoptedSource = best.sourceName,
            adoptedSourceCode = best.sourceCode,
            adoptedContentSummary = adopted.take(30) + "...",
            identicalSourceCount = identicalCount,
            alternativeSources = alternatives,
            decisionReason = if (identicalCount > 1) {
                "多个来源正文完全相同，依据优先级仅采纳 [${best.sourceName}] 单一来源并去重保留一份"
            } else if (alternatives.isNotEmpty()) {
                "存在异文或分歧版本，依据离线底本优先级采纳 [${best.sourceName}]，其余版本归档备查"
            } else {
                "由 [${best.sourceName}] 独家提供"
            }
        )

        return Triple(adopted, sourceInfo, record)
    }

    /**
     * 决策译文 (TRANSLATION)
     */
    private fun resolveTranslation(candidates: List<SourceWorkCandidate>): Triple<String?, SourceImportInfo?, ContentDiffRecord> {
        val valid = candidates.filter { !it.translation.isNullOrBlank() }
        if (valid.isEmpty()) {
            return Triple(
                null,
                null,
                ContentDiffRecord(
                    dimension = MergeDimension.TRANSLATION,
                    adoptedSource = "NONE",
                    adoptedSourceCode = "NONE",
                    adoptedContentSummary = null,
                    identicalSourceCount = candidates.size,
                    alternativeSources = emptyList(),
                    decisionReason = "所有来源均无译文，该维度置空"
                )
            )
        }

        val best = valid.minByOrNull { getRank(it, isAuxiliary = true) }!!
        val adopted = best.translation!!.trim()

        val identicalCount = valid.count { it.translation!!.trim() == adopted }
        val alternatives = valid.filter { it.translation!!.trim() != adopted }.map {
            AlternativeSourceItem(it.sourceName, it.sourceCode, it.translation!!.trim().take(40) + "...", false)
        }

        // 真实标注来源，绝不伪造
        val sourceInfo = SourceImportInfo(
            sourceName = if (best.isGushiwenConfirmed) "古诗文网 (gushiwen.cn)" else best.sourceName,
            licenseType = if (best.isGushiwenConfirmed) "UNKNOWN" else (best.licenseType ?: "UNKNOWN"),
            referenceUrl = best.referenceUrl,
            licenseNote = if (best.isGushiwenConfirmed) "来源链接已核验为古诗文网官方有效页面，版权状态未知" else "现代白话翻译，来源: ${best.sourceName}"
        )

        val record = ContentDiffRecord(
            dimension = MergeDimension.TRANSLATION,
            adoptedSource = sourceInfo.sourceName,
            adoptedSourceCode = best.sourceCode,
            adoptedContentSummary = adopted.take(30) + "...",
            identicalSourceCount = identicalCount,
            alternativeSources = alternatives,
            decisionReason = if (best.isGushiwenConfirmed) {
                "具有明确古诗文网来源凭据（来源链接已核验为古诗文网官方有效作品页面），依据规则优先采用该译文"
            } else if (identicalCount > 1) {
                "多源译文完全相同，由优先级来源 [${best.sourceName}] 采纳一份"
            } else {
                "由 [${best.sourceName}] 独立提供译文"
            }
        )

        return Triple(adopted, sourceInfo, record)
    }

    /**
     * 决策注释 (ANNOTATION)
     */
    private fun resolveAnnotations(candidates: List<SourceWorkCandidate>): AnnotationResolution {
        val valid = candidates.filter {
            !it.annotations.isNullOrBlank() || !it.annotationItems.isNullOrEmpty()
        }

        if (valid.isEmpty()) {
            val record = ContentDiffRecord(
                dimension = MergeDimension.ANNOTATION,
                adoptedSource = "NONE",
                adoptedSourceCode = "NONE",
                adoptedContentSummary = null,
                identicalSourceCount = candidates.size,
                alternativeSources = emptyList(),
                decisionReason = "所有来源均无注释，该维度置空"
            )
            return AnnotationResolution(null, null, null, record)
        }

        val best = valid.minByOrNull { getRank(it, isAuxiliary = true) }!!
        val adoptedNotes = best.annotations?.trim()
        val adoptedItems = best.annotationItems

        val identicalCount = valid.count {
            (it.annotations?.trim() == adoptedNotes && adoptedNotes != null) ||
            (it.annotationItems == adoptedItems && adoptedItems != null)
        }

        val alternatives = valid.filter {
            it !== best && (it.annotations?.trim() != adoptedNotes || it.annotationItems != adoptedItems)
        }.map {
            AlternativeSourceItem(it.sourceName, it.sourceCode, it.annotations?.take(30) ?: "结构化词条(${it.annotationItems?.size})", false)
        }

        val sourceInfo = SourceImportInfo(
            sourceName = if (best.isGushiwenConfirmed) "古诗文网 (gushiwen.cn)" else best.sourceName,
            licenseType = "UNKNOWN",
            referenceUrl = best.referenceUrl,
            licenseNote = if (best.isGushiwenConfirmed) "来源链接已核验为古诗文网官方有效页面，版权状态未知" else "词句注释，来源: ${best.sourceName}"
        )

        val record = ContentDiffRecord(
            dimension = MergeDimension.ANNOTATION,
            adoptedSource = sourceInfo.sourceName,
            adoptedSourceCode = best.sourceCode,
            adoptedContentSummary = adoptedNotes?.take(30) ?: "结构化注释 ${adoptedItems?.size ?: 0} 条",
            identicalSourceCount = identicalCount,
            alternativeSources = alternatives,
            decisionReason = if (best.isGushiwenConfirmed) {
                "具有明确古诗文网来源凭据（来源链接已核验为古诗文网官方有效作品页面），依据规则优先采用该注释"
            } else {
                "依据辅助内容优先级由 [${best.sourceName}] 采纳注释"
            }
        )

        return AnnotationResolution(adoptedNotes, adoptedItems, sourceInfo, record)
    }

    /**
     * 决策创作背景 (BACKGROUND) - 严格保留显式独立字段，严禁从赏析拆分
     */
    private fun resolveBackground(candidates: List<SourceWorkCandidate>): Triple<String?, SourceImportInfo?, ContentDiffRecord> {
        val valid = candidates.filter { !it.background.isNullOrBlank() }
        if (valid.isEmpty()) {
            return Triple(
                null,
                null,
                ContentDiffRecord(
                    dimension = MergeDimension.BACKGROUND,
                    adoptedSource = "NONE",
                    adoptedSourceCode = "NONE",
                    adoptedContentSummary = null,
                    identicalSourceCount = candidates.size,
                    alternativeSources = emptyList(),
                    decisionReason = "来源均无显式独立的创作背景字段；严禁自动从赏析中强行拆分，该维度保持为空"
                )
            )
        }

        val best = valid.minByOrNull { getRank(it, isAuxiliary = true) }!!
        val adopted = best.background!!.trim()

        val sourceInfo = SourceImportInfo(
            sourceName = best.sourceName,
            licenseType = "UNKNOWN",
            referenceUrl = best.referenceUrl,
            licenseNote = "历史创作背景说明"
        )

        val record = ContentDiffRecord(
            dimension = MergeDimension.BACKGROUND,
            adoptedSource = best.sourceName,
            adoptedSourceCode = best.sourceCode,
            adoptedContentSummary = adopted.take(30) + "...",
            identicalSourceCount = valid.count { it.background!!.trim() == adopted },
            alternativeSources = emptyList(),
            decisionReason = "从 [${best.sourceName}] 提取显式独立的创作背景，未作任何机械拆分"
        )

        return Triple(adopted, sourceInfo, record)
    }

    /**
     * 决策文学赏析 (APPRECIATION)
     */
    private fun resolveAppreciation(candidates: List<SourceWorkCandidate>): Triple<String?, SourceImportInfo?, ContentDiffRecord> {
        val valid = candidates.filter { !it.appreciation.isNullOrBlank() }
        if (valid.isEmpty()) {
            return Triple(
                null,
                null,
                ContentDiffRecord(
                    dimension = MergeDimension.APPRECIATION,
                    adoptedSource = "NONE",
                    adoptedSourceCode = "NONE",
                    adoptedContentSummary = null,
                    identicalSourceCount = candidates.size,
                    alternativeSources = emptyList(),
                    decisionReason = "所有来源均无文学赏析，该维度置空"
                )
            )
        }

        val best = valid.minByOrNull { getRank(it, isAuxiliary = true) }!!
        val adopted = best.appreciation!!.trim()

        val identicalCount = valid.count { it.appreciation!!.trim() == adopted }
        val alternatives = valid.filter { it.appreciation!!.trim() != adopted }.map {
            AlternativeSourceItem(it.sourceName, it.sourceCode, it.appreciation!!.trim().take(40) + "...", false)
        }

        val sourceInfo = SourceImportInfo(
            sourceName = if (best.isGushiwenConfirmed) "古诗文网 (gushiwen.cn)" else best.sourceName,
            licenseType = "UNKNOWN",
            referenceUrl = best.referenceUrl,
            licenseNote = if (best.isGushiwenConfirmed) "来源链接已核验为古诗文网官方有效页面，版权状态未知" else "文学评论与思想艺术赏析，来源: ${best.sourceName}"
        )

        val record = ContentDiffRecord(
            dimension = MergeDimension.APPRECIATION,
            adoptedSource = sourceInfo.sourceName,
            adoptedSourceCode = best.sourceCode,
            adoptedContentSummary = adopted.take(30) + "...",
            identicalSourceCount = identicalCount,
            alternativeSources = alternatives,
            decisionReason = if (best.isGushiwenConfirmed) {
                "具有明确古诗文网来源凭据（来源链接已核验为古诗文网官方有效作品页面），依据规则优先采纳该赏析"
            } else if (identicalCount > 1) {
                "多源赏析内容一致，由 [${best.sourceName}] 采纳一份"
            } else {
                "依据辅助内容优先级由 [${best.sourceName}] 采纳赏析，分歧版本归档"
            }
        )

        return Triple(adopted, sourceInfo, record)
    }

    /**
     * 计算优先级排序键：辅助内容在古诗文网明确时享受最高优先级 (0)
     */
    private fun getRank(candidate: SourceWorkCandidate, isAuxiliary: Boolean): Int {
        if (isAuxiliary && (candidate.isGushiwenConfirmed || candidate.sourceCode == "GUSHIWEN")) {
            return 0
        }
        return priorityRanks[candidate.sourceCode] ?: 99
    }
}
