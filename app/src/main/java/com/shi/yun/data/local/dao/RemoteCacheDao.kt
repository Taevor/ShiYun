package com.shi.yun.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shi.yun.data.local.entity.CachedRemoteWorkEntity

/**
 * 在线作品缓存数据访问对象 (RemoteCacheDao)
 */
@Dao
interface RemoteCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: CachedRemoteWorkEntity)

    @Query("SELECT * FROM cached_remote_works WHERE remote_work_id = :remoteWorkId")
    suspend fun getCacheById(remoteWorkId: String): CachedRemoteWorkEntity?

    @Query("SELECT * FROM cached_remote_works WHERE source_code = :sourceCode AND external_id = :externalId")
    suspend fun getCacheBySourceAndExternalId(sourceCode: String, externalId: String): CachedRemoteWorkEntity?

    @Query("UPDATE cached_remote_works SET last_accessed_epoch_ms = :nowMs WHERE remote_work_id = :remoteWorkId")
    suspend fun updateAccessTime(remoteWorkId: String, nowMs: Long)

    @Query("DELETE FROM cached_remote_works WHERE expire_at_epoch_ms < :nowMs")
    suspend fun deleteExpired(nowMs: Long): Int

    @Query("""
        DELETE FROM cached_remote_works
        WHERE remote_work_id NOT IN (
            SELECT remote_work_id FROM cached_remote_works
            ORDER BY last_accessed_epoch_ms DESC
            LIMIT :maxCount
        )
    """)
    suspend fun trimCacheToSize(maxCount: Int): Int

    @Query("SELECT COUNT(*) FROM cached_remote_works")
    suspend fun getCacheCount(): Int

    @Query("DELETE FROM cached_remote_works")
    suspend fun clearAllCache(): Int
}
