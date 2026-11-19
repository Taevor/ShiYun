package com.shi.yun.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 数据导入批次表 (data_batches)
 */
@Entity(
    tableName = "data_batches",
    indices = [
        Index(value = ["batch_code"], unique = true, name = "idx_batches_code")
    ]
)
data class DataBatchEntity(
    @PrimaryKey
    @ColumnInfo(name = "batch_id")
    val batchId: String,

    @ColumnInfo(name = "batch_code")
    val batchCode: String,

    @ColumnInfo(name = "batch_source")
    val batchSource: String,

    @ColumnInfo(name = "item_count")
    val itemCount: Int,

    @ColumnInfo(name = "status")
    val status: String = "IMPORTING", // IMPORTING, IMPORTED, PARTIAL, FAILED, VERIFIED

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long
)

/**
 * 正式发布包表 (data_releases)
 */
@Entity(
    tableName = "data_releases",
    foreignKeys = [
        ForeignKey(
            entity = DataBatchEntity::class,
            parentColumns = ["batch_id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["release_code"], unique = true, name = "idx_releases_code"),
        Index(value = ["batch_id"], name = "idx_releases_batch")
    ]
)
data class DataReleaseEntity(
    @PrimaryKey
    @ColumnInfo(name = "release_id")
    val releaseId: String,

    @ColumnInfo(name = "release_code")
    val releaseCode: String,

    @ColumnInfo(name = "batch_id")
    val batchId: String,

    @ColumnInfo(name = "release_type")
    val releaseType: String = "FULL", // FULL, INCREMENTAL

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "published_at_epoch_ms")
    val publishedAtEpochMs: Long
)

/**
 * 发布作品版本映射表 (release_work_entries)
 */
@Entity(
    tableName = "release_work_entries",
    primaryKeys = ["release_id", "work_id"],
    foreignKeys = [
        ForeignKey(
            entity = DataReleaseEntity::class,
            parentColumns = ["release_id"],
            childColumns = ["release_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["work_id"],
            childColumns = ["work_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WorkVersionEntity::class,
            parentColumns = ["work_version_id"],
            childColumns = ["work_version_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["work_id"], name = "idx_rwe_wid"),
        Index(value = ["work_version_id"], name = "idx_rwe_wvid")
    ]
)
data class ReleaseWorkEntryEntity(
    @ColumnInfo(name = "release_id")
    val releaseId: String,

    @ColumnInfo(name = "work_id")
    val workId: String,

    @ColumnInfo(name = "work_version_id")
    val workVersionId: String
)

/**
 * 典籍图书表 (anthology_books)
 */
@Entity(
    tableName = "anthology_books",
    indices = [
        Index(value = ["book_code"], unique = true, name = "idx_ab_code")
    ]
)
data class AnthologyBookEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val bookId: String,

    @ColumnInfo(name = "standard_title")
    val standardTitle: String,

    @ColumnInfo(name = "book_code")
    val bookCode: String,

    @ColumnInfo(name = "compiler")
    val compiler: String? = null,

    @ColumnInfo(name = "total_volumes")
    val totalVolumes: Int = 1,

    @ColumnInfo(name = "description")
    val description: String? = null
)

/**
 * 典籍版本表 (anthology_book_versions)
 */
@Entity(
    tableName = "anthology_book_versions",
    foreignKeys = [
        ForeignKey(
            entity = AnthologyBookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DataReleaseEntity::class,
            parentColumns = ["release_id"],
            childColumns = ["release_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["book_id", "version_code"], unique = true, name = "idx_abv_book_version"),
        Index(value = ["release_id"], name = "idx_abv_release_id")
    ]
)
data class AnthologyBookVersionEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_version_id")
    val bookVersionId: String,

    @ColumnInfo(name = "book_id")
    val bookId: String,

    @ColumnInfo(name = "version_code")
    val versionCode: Int,

    @ColumnInfo(name = "edition_note")
    val editionNote: String? = null,

    @ColumnInfo(name = "release_id")
    val releaseId: String
)

/**
 * 选集层级目录节点表 (anthology_nodes)
 */
@Entity(
    tableName = "anthology_nodes",
    foreignKeys = [
        ForeignKey(
            entity = AnthologyBookVersionEntity::class,
            parentColumns = ["book_version_id"],
            childColumns = ["book_version_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AnthologyNodeEntity::class,
            parentColumns = ["node_id"],
            childColumns = ["parent_node_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_version_id"], name = "idx_an_bvid"),
        Index(value = ["parent_node_id"], name = "idx_an_parent")
    ]
)
data class AnthologyNodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "node_id")
    val nodeId: String,

    @ColumnInfo(name = "book_version_id")
    val bookVersionId: String,

    @ColumnInfo(name = "parent_node_id")
    val parentNodeId: String? = null,

    @ColumnInfo(name = "node_title")
    val nodeTitle: String,

    @ColumnInfo(name = "node_type")
    val nodeType: String = "SECTION", // VOLUME, SECTION, CHAPTER

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * 选集目录条目挂载表 (anthology_items)
 */
@Entity(
    tableName = "anthology_items",
    foreignKeys = [
        ForeignKey(
            entity = AnthologyNodeEntity::class,
            parentColumns = ["node_id"],
            childColumns = ["node_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["work_id"],
            childColumns = ["work_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WorkVersionEntity::class,
            parentColumns = ["work_version_id"],
            childColumns = ["work_version_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["node_id", "work_id"], unique = true, name = "idx_ai_node_work"),
        Index(value = ["work_id"], name = "idx_ai_work"),
        Index(value = ["work_version_id"], name = "idx_ai_wvid")
    ]
)
data class AnthologyItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: String,

    @ColumnInfo(name = "node_id")
    val nodeId: String,

    @ColumnInfo(name = "work_id")
    val workId: String,

    @ColumnInfo(name = "work_version_id")
    val workVersionId: String,

    @ColumnInfo(name = "item_order")
    val itemOrder: Int = 0
)

/**
 * 当前发布指针单行表 (app_release_pointer)
 *
 * pointer_id 恒等于 1，通过真实外键引用 data_releases.release_id。
 * （注：pointer_id = 1 的 CHECK 约束在步骤 3 ShiYunDatabase 回调中强保证）
 */
@Entity(
    tableName = "app_release_pointer",
    foreignKeys = [
        ForeignKey(
            entity = DataReleaseEntity::class,
            parentColumns = ["release_id"],
            childColumns = ["current_release_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["current_release_id"], name = "idx_arp_release_id")
    ]
)
data class AppReleasePointerEntity(
    @PrimaryKey
    @ColumnInfo(name = "pointer_id")
    val pointerId: Int = 1,

    @ColumnInfo(name = "current_release_id")
    val currentReleaseId: String? = null,

    @ColumnInfo(name = "activated_at_epoch_ms")
    val activatedAtEpochMs: Long,

    @ColumnInfo(name = "updated_by")
    val updatedBy: String = "SYSTEM_INIT" // SYSTEM_INIT, RELEASE_SWITCH, ROLLBACK
)

/**
 * 通用数据库键值元数据表 (app_database_metadata)
 */
@Entity(tableName = "app_database_metadata")
data class AppDatabaseMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "metadata_key")
    val metadataKey: String,

    @ColumnInfo(name = "metadata_value")
    val metadataValue: String,

    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)
