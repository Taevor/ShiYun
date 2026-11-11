package com.shi.yun.data.model

/**
 * 内容生产方式/渊源类型
 */
enum class ContentProvenance {
    /** 古籍原文/历史注本 */
    ORIGINAL_ANCIENT,

    /** 人工整理/现代学者校勘、注译 */
    HUMAN_CURATED,

    /** 人工智能辅助生成 */
    AI_GENERATED,

    /** 来源不明/未标明生产渊源 */
    UNKNOWN_PROVENANCE
}

/**
 * 法律授权与许可状态
 */
enum class LegalLicense {
    /** 公有领域 (已过版权保护期) */
    PUBLIC_DOMAIN,

    /** 知识共享 署名-非商业性使用 4.0 国际许可 (CC BY-NC 4.0) */
    CC_BY_NC_4_0,

    /** 专有版权/保留所有权利 */
    PROPRIETARY_RESERVED,

    /** 未指定/未知许可 */
    UNSPECIFIED
}

/**
 * 内容审核校核状态
 */
enum class ReviewStatus {
    /** 已校核/人工审定通过 */
    VERIFIED,

    /** 待审核/待人工校验 */
    PENDING_REVIEW,

    /** 未审核草稿/外部接口直接导入未校验 */
    UNVERIFIED_DRAFT
}

/**
 * 细粒度内容维度授权与来源凭证
 *
 * 针对正文、译文、注释、赏析、拼音等独立内容维度，记录其生产方式、法律许可及审核状态，
 * 避免整篇作品笼统标记单一授权状态。
 */
data class ContentAttribution(
    val origin: SourceOrigin,
    val externalId: String? = null,
    val provenance: ContentProvenance = ContentProvenance.UNKNOWN_PROVENANCE,
    val license: LegalLicense = LegalLicense.UNSPECIFIED,
    val reviewStatus: ReviewStatus = ReviewStatus.UNVERIFIED_DRAFT,
    val licenseNote: String? = null,
    val fetchedAtEpochMs: Long = System.currentTimeMillis()
)
