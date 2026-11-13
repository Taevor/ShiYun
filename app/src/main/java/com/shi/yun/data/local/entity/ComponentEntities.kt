package com.shi.yun.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 五维内容组件类型受控枚举 (ComponentType)
 *
 * 严格对应诗云数据库 V1.0 规范五维业务定义（定点调整后）：
 * - CONTENT: 正文组件 (原文底本)
 * - TRANSLATION: 译文组件 (白话或现代翻译)
 * - ANNOTATION: 注释组件 (词句典故训诂)
 * - BACKGROUND: 创作背景组件 (时代环境、作者处境、创作缘由)
 * - APPRECIATION: 赏析组件 (作品思想、艺术手法、文学价值)
 */
enum class ComponentType(val code: String) {
    CONTENT("CONTENT"),
    TRANSLATION("TRANSLATION"),
    ANNOTATION("ANNOTATION"),
    BACKGROUND("BACKGROUND"),
    APPRECIATION("APPRECIATION");

    companion object {
        fun fromCode(code: String?): ComponentType? {
            if (code.isNullOrBlank()) return null
            return entries.find { it.code.equals(code.trim(), ignoreCase = true) }
        }
    }
}

/**
 * 内容组件实体表 (content_components)
 *
 * 存储原子化的正文、译文、注释、创作背景、赏析组件。
 * 包含 component_id 与 component_type 的联合唯一约束，支撑复合外键强约束。
 */
@Entity(
    tableName = "content_components",
    indices = [
        Index(value = ["component_id", "component_type"], unique = true, name = "idx_cc_id_type"),
        Index(value = ["component_type"], name = "idx_cc_type"),
        Index(value = ["content_hash"], name = "idx_cc_hash")
    ]
)
data class ContentComponentEntity(
    @PrimaryKey
    @ColumnInfo(name = "component_id")
    val componentId: String,

    @ColumnInfo(name = "component_type")
    val componentType: String, // CONTENT, TRANSLATION, ANNOTATION, BACKGROUND, APPRECIATION

    @ColumnInfo(name = "content_payload")
    val contentPayload: String, // 核心文本或结构化 JSON

    @ColumnInfo(name = "raw_format")
    val rawFormat: String = "PLAIN_TEXT", // PLAIN_TEXT, STRUCTURED_JSON

    @ColumnInfo(name = "origin_type")
    val originType: String = "HUMAN_CURATED", // HUMAN_CURATED, ALGORITHMIC, EXTERNAL_SOURCE

    @ColumnInfo(name = "content_hash")
    val contentHash: String, // SHA-256

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long
)

/**
 * 作品版本与内容组件装配表 (work_version_contents)
 *
 * 记录版本与组件的挂载关系，复合主键确保同版本同维度仅挂载一个组件，
 * 复合外键杜绝组件维度类型不一致，并独立维护当前版本的审核状态。
 */
@Entity(
    tableName = "work_version_contents",
    primaryKeys = ["work_version_id", "component_type"],
    foreignKeys = [
        ForeignKey(
            entity = WorkVersionEntity::class,
            parentColumns = ["work_version_id"],
            childColumns = ["work_version_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ContentComponentEntity::class,
            parentColumns = ["component_id", "component_type"],
            childColumns = ["component_id", "component_type"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["component_id", "component_type"], name = "idx_wvc_cid_type"),
        Index(value = ["review_status"], name = "idx_wvc_rev")
    ]
)
data class WorkVersionContentEntity(
    @ColumnInfo(name = "work_version_id")
    val workVersionId: String,

    @ColumnInfo(name = "component_type")
    val componentType: String,

    @ColumnInfo(name = "component_id")
    val componentId: String,

    @ColumnInfo(name = "review_status")
    val reviewStatus: String = "PENDING", // PENDING, VERIFIED, REJECTED, REVIEW_REQUIRED

    @ColumnInfo(name = "review_note")
    val reviewNote: String? = null,

    @ColumnInfo(name = "reviewed_at_epoch_ms")
    val reviewedAtEpochMs: Long? = null
)

/**
 * 作品版本单值分类判定表 (work_version_classifications)
 *
 * 维护朝代、体裁、形式、词牌、曲牌五个单值维度的考订、状态与考据依据。
 * （注：具体字典外键约束在步骤 3 通过 SQLite 触发器强保证）
 */
@Entity(
    tableName = "work_version_classifications",
    primaryKeys = ["work_version_id", "dimension_type"],
    foreignKeys = [
        ForeignKey(
            entity = WorkVersionEntity::class,
            parentColumns = ["work_version_id"],
            childColumns = ["work_version_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["classification_id"], name = "idx_wcls_cid")
    ]
)
data class WorkVersionClassificationEntity(
    @ColumnInfo(name = "work_version_id")
    val workVersionId: String,

    @ColumnInfo(name = "dimension_type")
    val dimensionType: String, // DYNASTY, GENRE, FORM, CIPAI, QUPAI

    @ColumnInfo(name = "classification_id")
    val classificationId: String? = null,

    @ColumnInfo(name = "determination_status")
    val determinationStatus: String = "CONFIRMED", // CONFIRMED, TRADITIONAL, DISPUTED, UNKNOWN, INAPPLICABLE

    @ColumnInfo(name = "evidence")
    val evidence: String? = null,

    @ColumnInfo(name = "review_status")
    val reviewStatus: String = "PENDING", // PENDING, VERIFIED

    @ColumnInfo(name = "review_note")
    val reviewNote: String? = null,

    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)

/**
 * 作品版本与专题标签关联表 (work_version_tags)
 */
@Entity(
    tableName = "work_version_tags",
    primaryKeys = ["work_version_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = WorkVersionEntity::class,
            parentColumns = ["work_version_id"],
            childColumns = ["work_version_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["tag_id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["tag_id"], name = "idx_wvt_tagid")
    ]
)
data class WorkVersionTagEntity(
    @ColumnInfo(name = "work_version_id")
    val workVersionId: String,

    @ColumnInfo(name = "tag_id")
    val tagId: String,

    @ColumnInfo(name = "review_status")
    val reviewStatus: String = "PENDING", // PENDING, VERIFIED

    @ColumnInfo(name = "review_note")
    val reviewNote: String? = null
)

/**
 * 外部数据源定义与索引表 (external_source_links)
 */
@Entity(
    tableName = "external_source_links",
    indices = [
        Index(value = ["source_code", "external_item_id"], unique = true, name = "idx_esl_source_item")
    ]
)
data class ExternalSourceLinkEntity(
    @PrimaryKey
    @ColumnInfo(name = "link_id")
    val linkId: String,

    @ColumnInfo(name = "source_code")
    val sourceCode: String, // SHI_CI_CN, PALEMOKY, POEMS_DB, POETRY_CN

    @ColumnInfo(name = "external_item_id")
    val externalItemId: String,

    @ColumnInfo(name = "item_url")
    val itemUrl: String? = null,

    @ColumnInfo(name = "license_desc")
    val licenseDesc: String? = null
)

/**
 * 作品身份与外部来源追溯表 (work_source_links)
 */
@Entity(
    tableName = "work_source_links",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["work_id"],
            childColumns = ["work_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExternalSourceLinkEntity::class,
            parentColumns = ["link_id"],
            childColumns = ["external_source_link_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["work_id", "external_source_link_id"], unique = true, name = "idx_wsl_wid_eslid"),
        Index(value = ["external_source_link_id"], name = "idx_wsl_eslid")
    ]
)
data class WorkSourceLinkEntity(
    @PrimaryKey
    @ColumnInfo(name = "link_id")
    val linkId: String,

    @ColumnInfo(name = "work_id")
    val workId: String,

    @ColumnInfo(name = "external_source_link_id")
    val externalSourceLinkId: String,

    @ColumnInfo(name = "is_primary")
    val isPrimary: Int = 0 // 1 是, 0 否
)

/**
 * 内容组件版权与溯源凭证表 (content_attributions)
 */
@Entity(
    tableName = "content_attributions",
    foreignKeys = [
        ForeignKey(
            entity = ContentComponentEntity::class,
            parentColumns = ["component_id"],
            childColumns = ["component_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExternalSourceLinkEntity::class,
            parentColumns = ["link_id"],
            childColumns = ["external_source_link_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["component_id"], name = "idx_ca_cid"),
        Index(value = ["external_source_link_id"], name = "idx_ca_eslid")
    ]
)
data class ContentAttributionEntity(
    @PrimaryKey
    @ColumnInfo(name = "attribution_id")
    val attributionId: String,

    @ColumnInfo(name = "component_id")
    val componentId: String,

    @ColumnInfo(name = "external_source_link_id")
    val externalSourceLinkId: String? = null,

    @ColumnInfo(name = "curator_name")
    val curatorName: String? = null,

    @ColumnInfo(name = "license_type")
    val licenseType: String = "PUBLIC_DOMAIN",

    @ColumnInfo(name = "license_note")
    val licenseNote: String? = null
)
