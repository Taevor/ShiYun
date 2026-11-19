package com.shi.yun.data.local.importer.pipeline

import com.shi.yun.data.local.importer.WorkImportPrechecker
import com.shi.yun.data.local.importer.merger.MergeDimension
import com.shi.yun.data.local.importer.merger.MultiSourceMergeAudit
import com.shi.yun.data.local.importer.merger.MultiSourceWorkMerger
import com.shi.yun.data.local.importer.merger.SourceWorkCandidate
import com.shi.yun.data.local.importer.model.BatchPrecheckSummary
import com.shi.yun.data.local.importer.model.WorkImportItem
import com.shi.yun.data.local.importer.parser.ChinesePoetryParser
import com.shi.yun.data.local.importer.parser.PoemsDbParser
import com.shi.yun.data.local.importer.parser.PoetryCnParser
import java.io.File

/**
 * 输入数据文件描述
 */
data class PipelineInputFile(
    val file: File,
    val sourceCode: String,
    val dynastyHint: String? = null,
    val sourceVersion: String? = null
)

/**
 * 字符串形式的原始数据输入描述 (便于测试与内存流式处理)
 */
data class RawInputSource(
    val rawContent: String,
    val sourceCode: String,
    val rawFilePath: String = "in_memory",
    val dynastyHint: String? = null,
    val sourceVersion: String? = null
)

/**
 * 流水线统计报告
 *
 * 严格区分各项独立指标，绝不混淆：
 * 1. parsedSuccessRate：解析成功率
 * 2. rawFileArchiveRate：原始文件完整归档与哈希生成率
 * 3. rawRecordLocatableRate：原始记录定位率（通过归档索引可成功恢复完整原始记录的比例）
 * 4. sourceIndexPresenceRate：来源索引非空率
 */
data class PipelineStats(
    val filesReadPerSource: Map<String, Int>,
    val recordsReadPerSource: Map<String, Int>,
    val totalCandidatesParsed: Int,
    val failedRecordsCount: Int,
    val failureReasons: List<String>,
    val missingTitleCount: Int,
    val missingContentCount: Int,
    val missingAuthorCount: Int,
    val unknownCategoryCount: Int,
    val translationCoverageCount: Int,
    val annotationCoverageCount: Int,
    val appreciationCoverageCount: Int,
    val backgroundCoverageCount: Int,
    val duplicateWorksCount: Int,
    val variantNeedsConfirmationCount: Int,

    // ==================== 四大约束与追溯指标 ====================
    /** 解析成功率：成功解析记录数 / (成功解析记录数 + 失败记录数) */
    val parsedSuccessRate: Double,
    /** 原始文件完整归档率：成功完成 SHA-256 哈希计算与文件级归档的输入比例 */
    val rawFileArchiveRate: Double,
    /** 原始记录定位率：合并后所有候选引用能在归档中精准找到完整原始 JSON 记录的比例 */
    val rawRecordLocatableRate: Double,
    /** 来源索引非空率：具备外部来源代码与载荷引用索引的作品比例 */
    val sourceIndexPresenceRate: Double,

    /** 兼容历史命名字段（等同于 sourceIndexPresenceRate，明确为来源索引非空率） */
    val traceabilitySuccessRate: Double = sourceIndexPresenceRate
)

/**
 * 流水线执行最终结果集
 */
data class PipelineResult(
    val stats: PipelineStats,
    val candidates: List<SourceWorkCandidate>,
    val mergedWorks: List<WorkImportItem>,
    val mergeAudits: List<MultiSourceMergeAudit>,
    val archive: RawRecordArchive,
    val precheckSummary: BatchPrecheckSummary? = null
)

/**
 * 离线数据完整处理流水线 (OfflineDataPipeline)
 *
 * 严格践行阶段 4.2B 完整真实流程：
 * 原始数据文件
 * → 原始文件 SHA-256 哈希归档
 * → 对应来源解析器 (PoemsDbParser, PoetryCnParser, ChinesePoetryParser)
 * → SourceWorkCandidate (含原始正文 rawContent 与原始完整 JSON rawPayload)
 * → 保守式正文特征聚类 (杜绝同词牌不同作品误合并)
 * → MultiSourceWorkMerger 细粒度决策
 * → WorkImportItem (保留多源溯源索引与可空 sourceVersion)
 * → WorkImportPrechecker 预检 (无独立标题作品稳健保留待确认状态)
 * → 标准导入包
 */
class OfflineDataPipeline(
    private val merger: MultiSourceWorkMerger = MultiSourceWorkMerger(),
    private val prechecker: WorkImportPrechecker? = null,
    private val poemsDbParser: PoemsDbParser = PoemsDbParser(),
    private val poetryCnParser: PoetryCnParser = PoetryCnParser(),
    private val chinesePoetryParser: ChinesePoetryParser = ChinesePoetryParser()
) {

    /**
     * 处理离线文件列表
     */
    fun processFiles(inputs: List<PipelineInputFile>): PipelineResult {
        val rawSources = inputs.map { input ->
            require(input.file.exists() && input.file.isFile) { "输入文件不存在: ${input.file.absolutePath}" }
            RawInputSource(
                rawContent = input.file.readText(Charsets.UTF_8),
                sourceCode = input.sourceCode,
                rawFilePath = input.file.path,
                dynastyHint = input.dynastyHint,
                sourceVersion = input.sourceVersion
            )
        }
        return processRawSources(rawSources)
    }

    /**
     * 处理原始数据字符串列表（核心流水线执行体）
     */
    fun processRawSources(inputs: List<RawInputSource>): PipelineResult {
        val filesReadPerSource = mutableMapOf<String, Int>()
        val recordsReadPerSource = mutableMapOf<String, Int>()
        val failureReasons = mutableListOf<String>()
        var failedCount = 0

        val archive = RawRecordArchive()
        val allCandidates = mutableListOf<SourceWorkCandidate>()

        // 1 & 2 & 3. 原始记录读取、哈希归档并分派对应来源解析器 -> SourceWorkCandidate
        for (input in inputs) {
            filesReadPerSource[input.sourceCode] = (filesReadPerSource[input.sourceCode] ?: 0) + 1

            try {
                val parsed = when (input.sourceCode.uppercase()) {
                    PoemsDbParser.SOURCE_CODE -> {
                        poemsDbParser.parseJson(
                            jsonStr = input.rawContent,
                            rawFilePath = input.rawFilePath,
                            sourceVersion = input.sourceVersion
                        )
                    }
                    PoetryCnParser.SOURCE_CODE -> {
                        poetryCnParser.parseJson(
                            jsonStr = input.rawContent,
                            rawFilePath = input.rawFilePath,
                            sourceVersion = input.sourceVersion
                        )
                    }
                    ChinesePoetryParser.SOURCE_CODE -> {
                        chinesePoetryParser.parseJson(
                            jsonStr = input.rawContent,
                            rawFilePath = input.rawFilePath,
                            dynastyHint = input.dynastyHint,
                            sourceVersion = input.sourceVersion
                        )
                    }
                    else -> {
                        failedCount++
                        failureReasons.add("未知的来源代码: ${input.sourceCode}")
                        emptyList()
                    }
                }

                // 文件级 SHA-256 归档
                val fileHash = archive.archiveFile(
                    sourceCode = input.sourceCode,
                    filePath = input.rawFilePath,
                    rawContent = input.rawContent,
                    recordCount = parsed.size
                )

                // 记录级原始 JSON 索引登记
                for (candidate in parsed) {
                    archive.archiveRecord(
                        sourceCode = candidate.sourceCode,
                        externalWorkId = candidate.externalWorkId,
                        rawFilePath = candidate.rawFilePath,
                        fileHashSha256 = fileHash,
                        rawJsonPayload = candidate.rawPayload
                    )
                }

                recordsReadPerSource[input.sourceCode] = (recordsReadPerSource[input.sourceCode] ?: 0) + parsed.size
                allCandidates.addAll(parsed)
            } catch (e: Exception) {
                failedCount++
                failureReasons.add("解析来源 [${input.sourceCode}] 发生异常: ${e.message}")
            }
        }

        // 4. 作品保守式聚类：禁止仅凭相同词牌和作者合并作品！
        // 无可靠独立标题的作品必须正文完全一致且身份相符才允许合并，正文不同坚决独立聚类
        val candidateClusters = clusterCandidates(allCandidates)

        // 5. 调用 MultiSourceWorkMerger 细粒度决策
        val mergeAudits = mutableListOf<MultiSourceMergeAudit>()
        val mergedWorks = mutableListOf<WorkImportItem>()

        for (candidateList in candidateClusters) {
            val audit = merger.merge(candidateList)
            mergeAudits.add(audit)
            mergedWorks.add(audit.resolvedItem)
        }

        // 6. 调用 WorkImportPrechecker 执行导入前预检 (若已装配)
        val precheckSummary = prechecker?.let { pc ->
            runBlockingCatching { pc.precheckBatch(mergedWorks) }
        }

        // 7. 统计指标计算与四类追溯指标严格核算
        var missingTitle = 0
        var missingContent = 0
        var missingAuthor = 0
        var unknownCategory = 0
        var transCount = 0
        var noteCount = 0
        var apprecCount = 0
        var bgCount = 0
        var duplicateCount = 0
        var variantCount = 0
        var sourceIndexPresentCount = 0
        var locatableRecordCount = 0

        for (item in mergedWorks) {
            if (item.title.isBlank()) missingTitle++
            if (item.content.isBlank()) missingContent++
            if (item.author == null) missingAuthor++
            if (item.dynasty.isNullOrBlank() || item.genre.isNullOrBlank()) unknownCategory++

            if (!item.translation.isNullOrBlank()) transCount++
            if (!item.annotations.isNullOrBlank() || !item.annotationItems.isNullOrEmpty()) noteCount++
            if (!item.appreciation.isNullOrBlank()) apprecCount++
            if (!item.background.isNullOrBlank()) bgCount++

            // 来源索引非空核验
            if (!item.externalSourceCode.isNullOrBlank() && !item.rawPayload.isNullOrBlank()) {
                sourceIndexPresentCount++
            }
        }

        // 原始记录定位率核验：检查所有候选条目在 archive 中是否能准确定位完整原始 JSON
        for (candidate in allCandidates) {
            if (candidate.externalWorkId != null && archive.hasRecord(candidate.sourceCode, candidate.externalWorkId)) {
                locatableRecordCount++
            }
        }

        for (audit in mergeAudits) {
            for (diff in audit.diffRecords) {
                if (diff.dimension == MergeDimension.CONTENT) {
                    if (diff.identicalSourceCount > 1) duplicateCount++
                    if (diff.alternativeSources.isNotEmpty()) variantCount++
                }
            }
        }

        val totalRecordsAttempted = allCandidates.size + failedCount
        val parsedSuccessRate = if (totalRecordsAttempted > 0) {
            (allCandidates.size.toDouble() / totalRecordsAttempted) * 100.0
        } else {
            0.0
        }

        val rawFileArchiveRate = if (inputs.isNotEmpty()) {
            (archive.totalArchivedFiles().toDouble() / inputs.size) * 100.0
        } else {
            0.0
        }

        val rawRecordLocatableRate = if (allCandidates.isNotEmpty()) {
            (locatableRecordCount.toDouble() / allCandidates.size) * 100.0
        } else {
            0.0
        }

        val sourceIndexPresenceRate = if (mergedWorks.isNotEmpty()) {
            (sourceIndexPresentCount.toDouble() / mergedWorks.size) * 100.0
        } else {
            0.0
        }

        val stats = PipelineStats(
            filesReadPerSource = filesReadPerSource,
            recordsReadPerSource = recordsReadPerSource,
            totalCandidatesParsed = allCandidates.size,
            failedRecordsCount = failedCount,
            failureReasons = failureReasons,
            missingTitleCount = missingTitle,
            missingContentCount = missingContent,
            missingAuthorCount = missingAuthor,
            unknownCategoryCount = unknownCategory,
            translationCoverageCount = transCount,
            annotationCoverageCount = noteCount,
            appreciationCoverageCount = apprecCount,
            backgroundCoverageCount = bgCount,
            duplicateWorksCount = duplicateCount,
            variantNeedsConfirmationCount = variantCount,
            parsedSuccessRate = parsedSuccessRate,
            rawFileArchiveRate = rawFileArchiveRate,
            rawRecordLocatableRate = rawRecordLocatableRate,
            sourceIndexPresenceRate = sourceIndexPresenceRate,
            traceabilitySuccessRate = sourceIndexPresenceRate
        )

        return PipelineResult(
            stats = stats,
            candidates = allCandidates,
            mergedWorks = mergedWorks,
            mergeAudits = mergeAudits,
            archive = archive,
            precheckSummary = precheckSummary
        )
    }

    /**
     * 作品保守式聚类算法：
     * 1. 作者有明确信息且不一致时，绝对不合并；
     * 2. 词牌有明确信息且不一致时，绝对不合并；
     * 3. 词作保护核心规则：正文不同，绝不能仅凭标题相同合并；正文一致才允许合并；
     * 4. 非词体裁若双方有独立有效标题且相同时正常合并；
     * 5. 无法确认是否同一作品时，坚决保留独立聚类。
     */
    private fun clusterCandidates(candidates: List<SourceWorkCandidate>): List<List<SourceWorkCandidate>> {
        val clusters = mutableListOf<MutableList<SourceWorkCandidate>>()

        for (candidate in candidates) {
            var matchedCluster: MutableList<SourceWorkCandidate>? = null
            for (cluster in clusters) {
                val representative = cluster.first()
                if (canMergeCandidates(representative, candidate)) {
                    matchedCluster = cluster
                    break
                }
            }

            if (matchedCluster != null) {
                matchedCluster.add(candidate)
            } else {
                clusters.add(mutableListOf(candidate))
            }
        }

        return clusters
    }

    private fun canMergeCandidates(c1: SourceWorkCandidate, c2: SourceWorkCandidate): Boolean {
        // 1. 作者比对：双方均有作者时必须一致
        val author1 = c1.author?.name?.trim()
        val author2 = c2.author?.name?.trim()
        if (!author1.isNullOrEmpty() && !author2.isNullOrEmpty() && author1 != author2) {
            return false
        }

        // 2. 词牌比对：双方均有词牌时必须一致
        val cipai1 = c1.cipai?.trim()
        val cipai2 = c2.cipai?.trim()
        if (!cipai1.isNullOrEmpty() && !cipai2.isNullOrEmpty() && cipai1 != cipai2) {
            return false
        }

        val isCi = c1.genre == "CI" || c2.genre == "CI" || !cipai1.isNullOrBlank() || !cipai2.isNullOrBlank()

        val normContent1 = c1.content?.let { normalizeClassicalText(it) }.orEmpty()
        val normContent2 = c2.content?.let { normalizeClassicalText(it) }.orEmpty()
        val hasIdenticalContent = normContent1.isNotEmpty() && normContent1 == normContent2

        // 3. 词作专有防误合并核心规则：
        // 词作体裁下，正文不同绝不能仅凭标题或词牌相同合并！正文必须一致才允许进入同作品合并判断
        if (isCi) {
            if (!hasIdenticalContent) {
                return false
            }
            // 正文一致前提下，词牌不冲突（上面已检查），允许合并
            return true
        }

        // 4. 非词体裁（如诗、文）：
        val hasTitle1 = !c1.title.isNullOrBlank()
        val hasTitle2 = !c2.title.isNullOrBlank()

        if (hasTitle1 && hasTitle2) {
            val title1 = c1.title?.trim().orEmpty()
            val title2 = c2.title?.trim().orEmpty()
            if (title1 == title2) {
                return true
            }
            if (hasIdenticalContent && (title1.contains(title2) || title2.contains(title1))) {
                return true
            }
            return false
        }

        return hasIdenticalContent
    }

    private fun normalizeClassicalText(text: String): String {
        return text.replace(Regex("[\\s\\p{Punct}，。！？；：“”‘’（）《》、·—…]+"), "").trim()
    }

    private fun <T> runBlockingCatching(block: suspend () -> T): T? {
        return try {
            kotlinx.coroutines.runBlocking { block() }
        } catch (e: Exception) {
            null
        }
    }
}
