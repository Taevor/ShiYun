package com.shi.yun.data.local.importer

import com.shi.yun.data.local.dao.CategoryDao
import com.shi.yun.data.local.dao.WorkDao
import com.shi.yun.data.local.importer.detector.WorkDuplicateDetector
import com.shi.yun.data.local.importer.model.BatchPrecheckSummary
import com.shi.yun.data.local.importer.model.PrecheckDisposition
import com.shi.yun.data.local.importer.model.TitleSourceType
import com.shi.yun.data.local.importer.model.WorkImportItem
import com.shi.yun.data.local.importer.model.WorkPrecheckReportItem
import com.shi.yun.data.local.importer.validator.WorkImportValidator

/**
 * 作品导入综合预检服务 (WorkImportPrechecker)
 *
 * 在实际写入数据库之前，对整批作品执行纯只读的前置校验、内部去重与库内重复识别：
 * 1. 严格遵守只读原则：全流程绝不执行任何 INSERT / UPDATE / DELETE 写入；
 * 2. 依次经历：字段业务校验 -> 批次内重复识别 -> 数据库跨表比对判定；
 * 3. 产出包含状态（可导入/可跳过/待确认/校验失败）及明确中文原因的完整报告；
 * 4. 稳妥处理宋词题名：若成功提取首句作为题名 (GENERATED_FIRST_LINE)，且满足其他校验，正常进入预检通过/查重流；
 *    仅当无法可靠获得任何题名 (title 为空且 titleSourceType 为 UNKNOWN) 时，保留待确认 (NEEDS_CONFIRMATION) 状态。
 */
class WorkImportPrechecker(
    private val duplicateDetector: WorkDuplicateDetector
) {

    constructor(workDao: WorkDao, categoryDao: CategoryDao? = null) : this(WorkDuplicateDetector(workDao, categoryDao))
    constructor(workDao: WorkDao) : this(workDao, null)

    /**
     * 对一批待导入作品执行综合预检并返回结构化汇总报告
     *
     * @param items 待导入作品列表
     * @return 预检汇总报告（含各分类统计数与每篇作品处置建议）
     */
    suspend fun precheckBatch(items: List<WorkImportItem>): BatchPrecheckSummary {
        val reportItems = ArrayList<WorkPrecheckReportItem>(items.size)

        // 用于检测当前批次内部的重复记录
        val seenExternalKeys = mutableSetOf<String>()
        val seenTitleAuthorContent = mutableSetOf<String>()

        for ((index, item) in items.withIndex()) {
            val authorName = item.author?.name?.trim() ?: "佚名"

            // 1. 前置业务与格式校验
            val validationResult = WorkImportValidator.validate(item)
            if (!validationResult.isValid) {
                val failureReasons = validationResult.issues
                    .filter { it.isFatal }
                    .joinToString("；") { it.message }

                reportItems.add(
                    WorkPrecheckReportItem(
                        index = index,
                        title = item.title,
                        author = authorName,
                        disposition = PrecheckDisposition.VALIDATION_FAILED,
                        reason = "前置校验不通过: $failureReasons",
                        issues = validationResult.issues
                    )
                )
                continue
            }

            // 检查是否存在无独立标题且无法可靠提取首句（仅含词牌）的宋词或特殊词作：保留待确认状态，坚决不编造假标题
            if (item.title.isBlank() && !item.cipai.isNullOrBlank()) {
                reportItems.add(
                    WorkPrecheckReportItem(
                        index = index,
                        title = "[词牌] ${item.cipai}",
                        author = authorName,
                        disposition = PrecheckDisposition.NEEDS_CONFIRMATION,
                        reason = "作品无独立标题且无法可靠提取正文首句（仅含词牌 [${item.cipai}]），保留待确认状态，需人工确认标题或核准为无题",
                        issues = validationResult.issues
                    )
                )
                continue
            }

            // 2. 批次内部查重（防止待导入列表中本身存在重复项）
            val extSource = item.externalSourceCode?.trim()
            val extId = item.externalWorkId?.trim()
            if (!extSource.isNullOrEmpty() && !extId.isNullOrEmpty()) {
                val extKey = "$extSource:$extId"
                if (!seenExternalKeys.add(extKey)) {
                    reportItems.add(
                        WorkPrecheckReportItem(
                            index = index,
                            title = item.title,
                            author = authorName,
                            disposition = PrecheckDisposition.CAN_SKIP,
                            reason = "当前批次内部已存在相同外部来源与编号 ($extKey)，可安全跳过重复项",
                            issues = validationResult.issues
                        )
                    )
                    continue
                }
            }

            val normContent = duplicateDetector.normalizeClassicalText(item.content)
            val batchKey = "${item.title.trim()}||$authorName||$normContent"
            if (!seenTitleAuthorContent.add(batchKey)) {
                reportItems.add(
                    WorkPrecheckReportItem(
                        index = index,
                        title = item.title,
                        author = authorName,
                        disposition = PrecheckDisposition.CAN_SKIP,
                        reason = "当前批次内部已包含标题、作者与正文完全相同的条目，可安全跳过",
                        issues = validationResult.issues
                    )
                )
                continue
            }

            // 3. 库内多维重复与异文判定
            val decision = duplicateDetector.detectDuplicate(item)
            reportItems.add(
                WorkPrecheckReportItem(
                    index = index,
                    title = item.title,
                    author = authorName,
                    disposition = decision.disposition,
                    reason = decision.reason,
                    matchedWorkId = decision.matchedWorkId,
                    matchedVersionId = decision.matchedVersionId,
                    issues = validationResult.issues
                )
            )
        }

        val readyCount = reportItems.count { it.disposition == PrecheckDisposition.READY_FOR_IMPORT }
        val skipCount = reportItems.count { it.disposition == PrecheckDisposition.CAN_SKIP }
        val needsConfirmationCount = reportItems.count { it.disposition == PrecheckDisposition.NEEDS_CONFIRMATION }
        val failedCount = reportItems.count { it.disposition == PrecheckDisposition.VALIDATION_FAILED }

        return BatchPrecheckSummary(
            totalCount = items.size,
            readyCount = readyCount,
            skipCount = skipCount,
            needsConfirmationCount = needsConfirmationCount,
            failedCount = failedCount,
            items = reportItems
        )
    }
}
