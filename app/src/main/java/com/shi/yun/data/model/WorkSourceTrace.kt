package com.shi.yun.data.model

/**
 * 作品来源追溯记录
 *
 * 记录作品在某个外部数据源中的原始标识、元数据及各内容维度的独立来源凭证。
 * 一部 Work 可以包含多条 WorkSourceTrace（例如同时追溯至本地经典底本、shi-ci.cn、诗泉）。
 */
data class WorkSourceTrace(
    val source: SourceOrigin,
    val externalWorkId: String,
    val externalUrl: String? = null,
    val externalTitle: String? = null,
    val externalAuthor: String? = null,
    val externalDynasty: String? = null,
    val fetchedAtEpochMs: Long = System.currentTimeMillis(),
    val contentAttribution: ContentAttribution? = null,
    val translationAttribution: ContentAttribution? = null,
    val annotationAttribution: ContentAttribution? = null,
    val appreciationAttribution: ContentAttribution? = null,
    val rubyPinyinAttribution: ContentAttribution? = null
)
