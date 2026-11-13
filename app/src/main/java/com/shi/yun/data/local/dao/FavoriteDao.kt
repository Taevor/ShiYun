package com.shi.yun.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shi.yun.data.local.entity.UserFavoriteEntity
import com.shi.yun.data.local.entity.WorkEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户收藏数据访问对象 (FavoriteDao)
 */
@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: UserFavoriteEntity)

    @Query("DELETE FROM user_favorites WHERE work_id = :workId")
    suspend fun deleteFavoriteByWorkId(workId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM user_favorites WHERE work_id = :workId)")
    suspend fun isFavorite(workId: String): Boolean

    @Query("SELECT work_id FROM user_favorites ORDER BY created_at_epoch_ms DESC")
    fun getAllFavoriteWorkIdsFlow(): Flow<List<String>>

    @Query("SELECT work_id FROM user_favorites ORDER BY created_at_epoch_ms DESC")
    suspend fun getAllFavoriteWorkIds(): List<String>

    @Query("""
        SELECT w.* FROM works w
        INNER JOIN user_favorites f ON w.work_id = f.work_id
        ORDER BY f.created_at_epoch_ms DESC
    """)
    suspend fun getFavoriteWorks(): List<WorkEntity>

    @Query("SELECT COUNT(*) FROM user_favorites")
    suspend fun getFavoriteCount(): Int
}
