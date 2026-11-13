package com.shi.yun.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 作品核心身份表 (works)
 *
 * 记录客观文学作品的唯一身份标识，永恒稳定，不随版本、文本校勘或排版变化而变更。
 */
@Entity(
    tableName = "works",
    indices = [
        Index(value = ["standard_title"], name = "idx_works_title"),
        Index(value = ["sort_order"], name = "idx_works_sort")
    ]
)
data class WorkEntity(
    @PrimaryKey
    @ColumnInfo(name = "work_id")
    val workId: String,

    @ColumnInfo(name = "standard_title")
    val standardTitle: String,

    @ColumnInfo(name = "identity_status")
    val identityStatus: String = "NORMAL", // NORMAL, DISPUTED, MERGED, DEPRECATED

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,

    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)

/**
 * 作品内容版本表 (work_versions)
 *
 * 记录作品在具体底本、文字、排版和诗序维度的具体版本。
 */
@Entity(
    tableName = "work_versions",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["work_id"],
            childColumns = ["work_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["work_id", "version_code"], unique = true, name = "idx_wv_work_version"),
        Index(value = ["work_id"], name = "idx_wv_work_id")
    ]
)
data class WorkVersionEntity(
    @PrimaryKey
    @ColumnInfo(name = "work_version_id")
    val workVersionId: String,

    @ColumnInfo(name = "work_id")
    val workId: String,

    @ColumnInfo(name = "version_code")
    val versionCode: Int,

    @ColumnInfo(name = "edition_name")
    val editionName: String,

    @ColumnInfo(name = "subtitle")
    val subtitle: String? = null,

    @ColumnInfo(name = "preface")
    val preface: String? = null,

    @ColumnInfo(name = "layout_type")
    val layoutType: String, // 对应 LayoutType.code

    @ColumnInfo(name = "excerpt")
    val excerpt: String? = null,

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long
)

/**
 * 作者标准化实体表 (authors)
 *
 * 记录文人的标准姓名、所属朝代与生平排序基准。
 */
@Entity(
    tableName = "authors",
    foreignKeys = [
        ForeignKey(
            entity = DynastyEntity::class,
            parentColumns = ["dynasty_id"],
            childColumns = ["dynasty_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ExternalSourceLinkEntity::class,
            parentColumns = ["link_id"],
            childColumns = ["biography_source_link_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["standard_name"], name = "idx_authors_name"),
        Index(value = ["dynasty_id"], name = "idx_authors_dynasty"),
        Index(value = ["biography_source_link_id"], name = "idx_authors_bio_source"),
        Index(value = ["birth_year_sort"], name = "idx_authors_byear"),
        Index(value = ["sort_order"], name = "idx_authors_sort")
    ]
)
data class AuthorEntity(
    @PrimaryKey
    @ColumnInfo(name = "author_id")
    val authorId: String,

    @ColumnInfo(name = "standard_name")
    val standardName: String,

    @ColumnInfo(name = "dynasty_id")
    val dynastyId: String? = null,

    @ColumnInfo(name = "birth_year_raw")
    val birthYearRaw: String? = null,

    @ColumnInfo(name = "birth_year_sort")
    val birthYearSort: Int? = null,

    @ColumnInfo(name = "death_year_sort")
    val deathYearSort: Int? = null,

    @ColumnInfo(name = "biography")
    val biography: String? = null,

    @ColumnInfo(name = "biography_source")
    val biographySource: String? = null,

    @ColumnInfo(name = "biography_source_link_id")
    val biographySourceLinkId: String? = null,

    @ColumnInfo(name = "biography_review_status")
    val biographyReviewStatus: String = "PENDING", // PENDING, VERIFIED, REJECTED

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * 作者异名字典表 (author_aliases)
 *
 * 记录作者的历史字、号、谥号、别名。
 */
@Entity(
    tableName = "author_aliases",
    foreignKeys = [
        ForeignKey(
            entity = AuthorEntity::class,
            parentColumns = ["author_id"],
            childColumns = ["author_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["author_id"], name = "idx_aa_author_id"),
        Index(value = ["alias_name"], name = "idx_aa_alias_name")
    ]
)
data class AuthorAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "alias_id")
    val aliasId: String,

    @ColumnInfo(name = "author_id")
    val authorId: String,

    @ColumnInfo(name = "alias_name")
    val aliasName: String,

    @ColumnInfo(name = "alias_type")
    val aliasType: String // ZI, HAO, SHI_HAO, BIE_CHENG
)

/**
 * 作品版本与作者关联表 (work_authors)
 *
 * 挂载于 work_version_id，支持合著、考订争议与独立审核状态。
 */
@Entity(
    tableName = "work_authors",
    primaryKeys = ["work_version_id", "author_id"],
    foreignKeys = [
        ForeignKey(
            entity = WorkVersionEntity::class,
            parentColumns = ["work_version_id"],
            childColumns = ["work_version_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AuthorEntity::class,
            parentColumns = ["author_id"],
            childColumns = ["author_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["author_id"], name = "idx_wa_author_id")
    ]
)
data class WorkAuthorEntity(
    @ColumnInfo(name = "work_version_id")
    val workVersionId: String,

    @ColumnInfo(name = "author_id")
    val authorId: String,

    @ColumnInfo(name = "author_role")
    val authorRole: String = "PRIMARY", // PRIMARY, CO_AUTHOR, COMMENTATOR

    @ColumnInfo(name = "author_order")
    val authorOrder: Int = 1,

    @ColumnInfo(name = "determination_status")
    val determinationStatus: String = "CONFIRMED", // CONFIRMED, TRADITIONAL, DISPUTED

    @ColumnInfo(name = "evidence")
    val evidence: String? = null,

    @ColumnInfo(name = "review_status")
    val reviewStatus: String = "PENDING", // PENDING, VERIFIED

    @ColumnInfo(name = "review_note")
    val reviewNote: String? = null
)
