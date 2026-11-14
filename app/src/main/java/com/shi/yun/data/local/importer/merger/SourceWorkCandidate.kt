package com.shi.yun.data.local.importer.merger

import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.TitleSourceType
import com.shi.yun.data.model.AnnotationItem

/**
 * 单一来源作品候选数据模型 (SourceWorkCandidate)
 *
 * 承载从单一数据源（如 yxcs/poems-db, Poetry_CN, chinese-poetry, shi-ci.cn, 诗泉等）
 * 提取或解析出的原始作品候选信息，供多来源合并器 (MultiSourceWorkMerger) 进行细粒度独立决策。
 */
data class SourceWorkCandidate(
    /** 来源名称，例如 "yxcs/poems-db", "Poetry_CN", "chinese-poetry", "gushiwen.cn" */
    val sourceName: String,

    /** 来源唯一标识代码，例如 "POEMS_DB", "POETRY_CN", "CHINESE_POETRY", "GUSHIWEN", "SHI_CI_CN", "PALEMOKY" */
    val sourceCode: String,

    /** 来源版本号（Git Commit Hash、数据集版本号或发布日期） */
    val sourceVersion: String? = null,

    /** 来源原始作品标识 (ID, slug, UUID 等) */
    val externalWorkId: String? = null,

    /** 原始数据文件路径或归档位置 (例如 "raw_data/poems_db_samples.json", "全唐诗/poet.tang.0.json") */
    val rawFilePath: String? = null,

    /** 来源参考 URL */
    val referenceUrl: String? = null,

    /** 许可类型代码 (如 "PUBLIC_DOMAIN", "UNKNOWN", "MIT" 等) */
    val licenseType: String? = "PUBLIC_DOMAIN",

    /** 许可补充说明与免责备注 */
    val licenseNote: String? = null,

    /** 整理人 / 归档组织名称 */
    val curatorName: String? = null,

    /** 是否具有明确、可追溯的古诗文网 (gushiwen.cn) 来源凭证 */
    val isGushiwenConfirmed: Boolean = false,

    /** 数据获取时间戳 (Epoch ms) */
    val fetchedAtEpochMs: Long? = null,

    // ==================== 作品核心元数据 ====================
    val title: String? = null,
    /** 来源原始未经解析/拆分的原始 title 字符串 */
    val rawTitle: String? = null,
    /** 题名来源类型：SOURCE_TITLE, GENERATED_FIRST_LINE, UNKNOWN */
    val titleSourceType: TitleSourceType = TitleSourceType.SOURCE_TITLE,
    val subtitle: String? = null,
    val preface: String? = null,
    val layoutType: String? = null,
    val excerpt: String? = null,

    // ==================== 作者维度 ====================
    val author: AuthorImportItem? = null,
    val coAuthors: List<AuthorImportItem> = emptyList(),

    // ==================== 分类维度 ====================
    val dynasty: String? = null,
    val genre: String? = null,
    val form: String? = null,
    val cipai: String? = null,
    val qupai: String? = null,
    val tags: List<String> = emptyList(),

    // ==================== 五维受控内容组件 ====================
    val content: String? = null,
    /** 原始正文（完整保留所有原始空行、换行、首尾空格，未做任何剔除或清洗） */
    val rawContent: String? = null,
    val translation: String? = null,
    val annotations: String? = null,
    val annotationItems: List<AnnotationItem>? = null,
    val background: String? = null, // 仅允许来源显式提供，严禁从赏析中强行自动截取
    val appreciation: String? = null,

    // ==================== 原始数据留存 ====================
    val rawFormat: String = "PLAIN_TEXT",
    val rawPayload: String? = null
)
