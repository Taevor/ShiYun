package com.shi.yun.data.local.importer.model

import com.shi.yun.data.model.AnnotationItem

/**
 * 导入作者数据模型 (AuthorImportItem)
 *
 * 记录作者的姓名、别名、朝代以及独立的生平介绍与来源出处。
 * 遵循架构规范：作者生平档案独立，绝不作为作品内容组件。
 */
data class AuthorImportItem(
    val name: String,
    val dynasty: String? = null,
    val aliases: List<String> = emptyList(),
    val biography: String? = null,
    val biographySource: String? = null,
    val biographySourceUrl: String? = null,
    val externalAuthorId: String? = null
)

/**
 * 来源凭证与版权导入模型 (SourceImportInfo)
 */
data class SourceImportInfo(
    val sourceName: String,
    val licenseType: String? = "PUBLIC_DOMAIN",
    val referenceUrl: String? = null,
    val licenseNote: String? = null,
    val curatorName: String? = null
)

/**
 * 待导入作品标准数据模型 (WorkImportItem)
 *
 * 适合批量录入或解析自外部数据的结构化载体，完整涵盖：
 * 1. 作品标题及辅助信息（副标题、序言、排版建议、摘录短句）；
 * 2. 创作时代与七维分类（朝代、体裁、形式、词牌、曲牌、标签）；
 * 3. 署名作者与合著者档案；
 * 4. 五维受控内容体系（CONTENT, TRANSLATION, ANNOTATION, BACKGROUND, APPRECIATION），不含已废弃的拼音；
 * 5. 细粒度来源信息（整作外部来源、正文底本来源、辅助内容各维度的独立凭证）。
 */
data class WorkImportItem(
    val title: String,
    val subtitle: String? = null,
    val preface: String? = null,
    val layoutType: String? = null,
    val excerpt: String? = null,
    val author: AuthorImportItem? = null,
    val coAuthors: List<AuthorImportItem> = emptyList(),
    val dynasty: String? = null,
    val genre: String? = null,
    val form: String? = null,
    val cipai: String? = null,
    val qupai: String? = null,
    val tags: List<String> = emptyList(),
    val content: String,
    val contentSource: SourceImportInfo? = null,
    val translation: String? = null,
    val translationSource: SourceImportInfo? = null,
    val annotations: String? = null,
    val annotationItems: List<AnnotationItem>? = null,
    val annotationSource: SourceImportInfo? = null,
    val background: String? = null,
    val backgroundSource: SourceImportInfo? = null,
    val appreciation: String? = null,
    val appreciationSource: SourceImportInfo? = null,
    val workSource: SourceImportInfo? = null,
    val externalSourceCode: String? = null, // 如 "SHI_CI_CN", "PALEMOKY"
    val externalWorkId: String? = null,
    val externalUrl: String? = null,
    val rawFormat: String = "PLAIN_TEXT",
    val rawPayload: String? = null
)

/**
 * 预检处置结论受控枚举 (PrecheckDisposition)
 */
enum class PrecheckDisposition(val code: String, val displayName: String) {
    /** 校验通过且无冲突，可直接导入 */
    READY_FOR_IMPORT("READY_FOR_IMPORT", "可导入"),

    /** 明确为完全重复作品（同源同编号同文本），已有完整记录，可安全跳过 */
    CAN_SKIP("CAN_SKIP", "可跳过"),

    /** 疑似异文、疑似重复或存疑分歧，需人工确认，不可自动覆盖 */
    NEEDS_CONFIRMATION("NEEDS_CONFIRMATION", "待确认"),

    /** 校验失败（缺失必填字段、格式损坏、规则违背等），无法入库 */
    VALIDATION_FAILED("VALIDATION_FAILED", "校验失败")
}

/**
 * 校验问题明细 (ValidationIssue)
 */
data class ValidationIssue(
    val field: String,
    val message: String,
    val isFatal: Boolean = true
)

/**
 * 单条作品前置校验结果 (ValidationResult)
 */
data class ValidationResult(
    val isValid: Boolean,
    val issues: List<ValidationIssue> = emptyList()
)

/**
 * 单篇作品预检报告明细条目 (WorkPrecheckReportItem)
 */
data class WorkPrecheckReportItem(
    val index: Int,
    val title: String,
    val author: String,
    val disposition: PrecheckDisposition,
    val reason: String,
    val matchedWorkId: String? = null,
    val matchedVersionId: String? = null,
    val issues: List<ValidationIssue> = emptyList()
)

/**
 * 批量预检统计汇总 (BatchPrecheckSummary)
 */
data class BatchPrecheckSummary(
    val totalCount: Int,
    val readyCount: Int,
    val skipCount: Int,
    val needsConfirmationCount: Int,
    val failedCount: Int,
    val items: List<WorkPrecheckReportItem>
)
