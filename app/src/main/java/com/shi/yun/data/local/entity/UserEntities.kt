package com.shi.yun.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户收藏表 (user_favorites)
 *
 * 强外键关联 works.work_id，删除策略为 RESTRICT，绝不因作品版本升级或批次替换被级联删除。
 */
@Entity(
    tableName = "user_favorites",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["work_id"],
            childColumns = ["work_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["work_id"], unique = true, name = "idx_fav_wid"),
        Index(value = ["created_at_epoch_ms"], name = "idx_fav_time")
    ]
)
data class UserFavoriteEntity(
    @PrimaryKey
    @ColumnInfo(name = "favorite_id")
    val favoriteId: String,

    @ColumnInfo(name = "work_id")
    val workId: String,

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long
)

/**
 * 用户自建选集表 (user_anthologies)
 */
@Entity(tableName = "user_anthologies")
data class UserAnthologyEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_anthology_id")
    val userAnthologyId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "cover_theme_id")
    val coverThemeId: String? = null,

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,

    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)

/**
 * 用户自建选集作品条目表 (user_anthology_items)
 */
@Entity(
    tableName = "user_anthology_items",
    foreignKeys = [
        ForeignKey(
            entity = UserAnthologyEntity::class,
            parentColumns = ["user_anthology_id"],
            childColumns = ["user_anthology_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["work_id"],
            childColumns = ["work_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["user_anthology_id", "work_id"], unique = true, name = "idx_uai_uaid_wid"),
        Index(value = ["work_id"], name = "idx_uai_wid")
    ]
)
data class UserAnthologyItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: String,

    @ColumnInfo(name = "user_anthology_id")
    val userAnthologyId: String,

    @ColumnInfo(name = "work_id")
    val workId: String,

    @ColumnInfo(name = "item_order")
    val itemOrder: Int = 0,

    @ColumnInfo(name = "added_at_epoch_ms")
    val addedAtEpochMs: Long
)

/**
 * 用户研读笔记与批注表 (user_notes)
 */
@Entity(
    tableName = "user_notes",
    foreignKeys = [
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
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["work_id"], name = "idx_un_wid"),
        Index(value = ["work_version_id"], name = "idx_un_wvid"),
        Index(value = ["created_at_epoch_ms"], name = "idx_un_time")
    ]
)
data class UserNoteEntity(
    @PrimaryKey
    @ColumnInfo(name = "note_id")
    val noteId: String,

    @ColumnInfo(name = "work_id")
    val workId: String,

    @ColumnInfo(name = "work_version_id")
    val workVersionId: String? = null,

    @ColumnInfo(name = "anchor_quote")
    val anchorQuote: String? = null,

    @ColumnInfo(name = "anchor_start_index")
    val anchorStartIndex: Int? = null,

    @ColumnInfo(name = "anchor_end_index")
    val anchorEndIndex: Int? = null,

    @ColumnInfo(name = "note_content")
    val noteContent: String,

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,

    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)

/**
 * 作品身份归并审计表 (identity_merges)
 *
 * 记录旧作品 ID 归并至目标主作品 ID 的审计历史。
 * old_work_id 声明唯一索引（防重复归并），防自环约束在步骤 3 ShiYunDatabase 回调中强保证。
 */
@Entity(
    tableName = "identity_merges",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["work_id"],
            childColumns = ["old_work_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["work_id"],
            childColumns = ["target_work_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["old_work_id"], unique = true, name = "idx_im_old"),
        Index(value = ["target_work_id"], name = "idx_im_target")
    ]
)
data class IdentityMergeEntity(
    @PrimaryKey
    @ColumnInfo(name = "merge_id")
    val mergeId: String,

    @ColumnInfo(name = "old_work_id")
    val oldWorkId: String,

    @ColumnInfo(name = "target_work_id")
    val targetWorkId: String,

    @ColumnInfo(name = "reason")
    val reason: String,

    @ColumnInfo(name = "operator")
    val operator: String = "SYSTEM_CLEANUP",

    @ColumnInfo(name = "merged_at_epoch_ms")
    val mergedAtEpochMs: Long
)
