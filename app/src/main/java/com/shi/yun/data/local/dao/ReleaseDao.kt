package com.shi.yun.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shi.yun.data.local.entity.AppDatabaseMetadataEntity
import com.shi.yun.data.local.entity.AppReleasePointerEntity
import com.shi.yun.data.local.entity.DataBatchEntity
import com.shi.yun.data.local.entity.DataReleaseEntity
import com.shi.yun.data.local.entity.ReleaseWorkEntryEntity
import com.shi.yun.data.local.entity.WorkEntity

/**
 * 正式批次与发布管理数据访问对象 (ReleaseDao)
 */
@Dao
interface ReleaseDao {

    // ==================== 1. 数据批次 (Data Batch) ====================
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: DataBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBatch(batch: DataBatchEntity)

    @Query("UPDATE data_batches SET status = :status WHERE batch_id = :batchId")
    suspend fun updateBatchStatus(batchId: String, status: String)

    @Query("SELECT * FROM data_batches WHERE batch_id = :batchId")
    suspend fun getBatchById(batchId: String): DataBatchEntity?

    @Query("SELECT * FROM data_batches WHERE batch_code = :batchCode")
    suspend fun getBatchByCode(batchCode: String): DataBatchEntity?

    // ==================== 2. 正式发布包 (Data Release) ====================
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRelease(release: DataReleaseEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReleaseWorkEntries(entries: List<ReleaseWorkEntryEntity>)

    @Query("SELECT * FROM data_releases WHERE release_id = :releaseId")
    suspend fun getReleaseById(releaseId: String): DataReleaseEntity?

    @Query("SELECT * FROM data_releases ORDER BY published_at_epoch_ms DESC")
    suspend fun getAllReleases(): List<DataReleaseEntity>

    // ==================== 3. 当前发布指针 (Release Pointer) ====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setReleasePointer(pointer: AppReleasePointerEntity)

    @Query("SELECT * FROM app_release_pointer WHERE pointer_id = 1")
    suspend fun getReleasePointer(): AppReleasePointerEntity?

    @Query("SELECT current_release_id FROM app_release_pointer WHERE pointer_id = 1")
    suspend fun getCurrentReleaseId(): String?

    // ==================== 4. 激活发布下的作品检索 (Active Release Works) ====================
    @Query("""
        SELECT w.* FROM works w
        INNER JOIN release_work_entries rwe ON w.work_id = rwe.work_id
        INNER JOIN app_release_pointer arp ON rwe.release_id = arp.current_release_id
        WHERE arp.pointer_id = 1
        ORDER BY w.sort_order ASC
    """)
    suspend fun getActiveReleaseWorks(): List<WorkEntity>

    @Query("""
        SELECT rwe.work_version_id FROM release_work_entries rwe
        INNER JOIN app_release_pointer arp ON rwe.release_id = arp.current_release_id
        WHERE arp.pointer_id = 1 AND rwe.work_id = :workId
    """)
    suspend fun getActiveVersionIdForWork(workId: String): String?

    // ==================== 5. 数据库元数据 (Metadata) ====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMetadata(metadata: AppDatabaseMetadataEntity)

    @Query("SELECT metadata_value FROM app_database_metadata WHERE metadata_key = :key")
    suspend fun getMetadataValue(key: String): String?
}
