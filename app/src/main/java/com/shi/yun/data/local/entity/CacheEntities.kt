package com.shi.yun.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 在线获取作品缓存表 (cached_remote_works)
 *
 * 独立保存由网络接口按需获取的未审核远程作品，与正式库物理解耦。
 */
@Entity(
    tableName = "cached_remote_works",
    indices = [
        Index(value = ["source_code"], name = "idx_crw_src"),
        Index(value = ["last_accessed_epoch_ms"], name = "idx_crw_lru"),
        Index(value = ["expire_at_epoch_ms"], name = "idx_crw_exp")
    ]
)
data class CachedRemoteWorkEntity(
    @PrimaryKey
    @ColumnInfo(name = "remote_work_id")
    val remoteWorkId: String,

    @ColumnInfo(name = "source_code")
    val sourceCode: String, // SHI_CI_CN, PALEMOKY

    @ColumnInfo(name = "external_id")
    val externalId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "author_name")
    val authorName: String,

    @ColumnInfo(name = "raw_payload")
    val rawPayload: String, // 原始完整 JSON 报文

    @ColumnInfo(name = "fetched_at_epoch_ms")
    val fetchedAtEpochMs: Long,

    @ColumnInfo(name = "last_accessed_epoch_ms")
    val lastAccessedEpochMs: Long,

    @ColumnInfo(name = "expire_at_epoch_ms")
    val expireAtEpochMs: Long
)
