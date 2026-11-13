package com.shi.yun.data.local.importer.model

/**
 * 批量作品导入明细结果条目 (WorkImportResultItem)
 */
data class WorkImportResultItem(
    val index: Int,
    val title: String,
    val author: String,
    val disposition: PrecheckDisposition,
    val isSuccess: Boolean,
    val workId: String? = null,
    val workVersionId: String? = null,
    val message: String
)

/**
 * 批量导入执行结果 (BatchImportResult)
 *
 * 汇报批量作品导入的真实操作统计和各条目导入状态：
 * 包含批次号、批次代码、总数、成功写入数、跳过数、待人工确认数、校验失败数、事务执行状态及详细明细。
 */
data class BatchImportResult(
    val batchId: String,
    val batchCode: String,
    val totalInputCount: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val needsConfirmationCount: Int,
    val failedCount: Int,
    val isSuccessful: Boolean,
    val errorMessage: String? = null,
    val batchStatus: String = "IMPORTED",
    val items: List<WorkImportResultItem> = emptyList()
)
