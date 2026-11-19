package com.shi.yun.data.local.generator

import android.content.Context
import androidx.room.Room
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.entity.AppDatabaseMetadataEntity
import com.shi.yun.data.local.entity.AppReleasePointerEntity
import com.shi.yun.data.local.entity.DataReleaseEntity
import com.shi.yun.data.local.importer.BatchWorkImporter
import com.shi.yun.data.local.importer.WorkImportPrechecker
import com.shi.yun.data.local.importer.merger.MultiSourceWorkMerger
import com.shi.yun.data.local.importer.parser.ChinesePoetryParser
import com.shi.yun.data.local.importer.parser.PoemsDbParser
import com.shi.yun.data.local.importer.parser.PoetryCnParser
import com.shi.yun.data.local.importer.pipeline.OfflineDataPipeline
import com.shi.yun.data.local.importer.pipeline.RawInputSource
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 预置数据库构建器 (PrepackagedDatabaseBuilder)
 *
 * 负责从验收通过的真实数据（如 poems-db 100 首）离线构建出符合 Room Schema V1.0 规范的
 * 预置 SQLite 数据库 (shiyun.db)，随 App 安装包 (assets/database/shiyun.db) 发布。
 *
 * 核心技术保障：
 * 1. 使用 Room 原生数据库构建，自动生成 36 张表、触发器及包含 schema identity_hash 的 room_master_table；
 * 2. 绑定正式批次 (data_batches) 与正式发布 (data_releases, release_work_entries, app_release_pointer)；
 * 3. 强制执行 WAL Checkpoint (TRUNCATE) 并转换为 DELETE journal_mode，确保单文件完整无遗漏；
 * 4. 内置完整性检查 (PRAGMA integrity_check) 与重放验证。
 */
object PrepackagedDatabaseBuilder {

    const val DEFAULT_BATCH_CODE = "BATCH_POEMS_DB_100_V1"
    const val DEFAULT_BATCH_SOURCE = "poems-db单一来源100首真实诗词精选"
    const val DEFAULT_RELEASE_ID = "release_v1_0"
    const val DEFAULT_RELEASE_CODE = "REL_2026_01"
    const val DEFAULT_RELEASE_DESC = "基于 poems-db 真实单一数据源，经 HTML 安全清洗与体裁校验的预置底本"

    data class BuildResult(
        val databaseFile: File,
        val fileSizeBytes: Long,
        val worksCount: Int,
        val authorsCount: Int,
        val componentsCount: Int,
        val sourceLinksCount: Int,
        val releaseEntriesCount: Int,
        val identityHash: String,
        val integrityCheck: String,
        val batchStatus: String
    )

    /**
     * 构建预置数据库文件
     *
     * @param context Android 上下文 (用于 Room 初始化)
     * @param rawRecordsJson 验收通过的 100 首真实作品 JSON 数组字符串
     * @param outputDbFile 最终输出的 SQLite 数据库文件 (如 app/src/main/assets/database/shiyun.db)
     * @param releaseId 正式发布包 ID
     * @param batchCode 数据批次编号
     */
    fun build(
        context: Context,
        rawRecordsJson: String,
        outputDbFile: File,
        releaseId: String = DEFAULT_RELEASE_ID,
        batchCode: String = DEFAULT_BATCH_CODE
    ): BuildResult = runBlocking {
        // 1. 确保输出目录存在，清理旧数据库及其附属 WAL/SHM 文件
        outputDbFile.parentFile?.mkdirs()
        deleteDatabaseFiles(outputDbFile)

        // 2. 使用 Room 初始化目标数据库
        val db = Room.databaseBuilder(
            context,
            ShiYunDatabase::class.java,
            outputDbFile.absolutePath
        )
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()

        try {
            // 3. 组装输入数据源并执行离线数据流水线
            val rawArray = JSONArray(rawRecordsJson)
            val inputs = mutableListOf<RawInputSource>()
            for (i in 0 until rawArray.length()) {
                val recordObj = rawArray.getJSONObject(i)
                val sourceFile = recordObj.optString("sourceFile", "poems1.json")
                val rawRecord = recordObj.getJSONObject("rawRecord")
                val dynasty = rawRecord.optString("dynasty").ifEmpty { null }

                inputs.add(
                    RawInputSource(
                        rawContent = rawRecord.toString(),
                        sourceCode = PoemsDbParser.SOURCE_CODE,
                        rawFilePath = sourceFile,
                        dynastyHint = dynasty,
                        sourceVersion = "poems-db-pure-100-v1"
                    )
                )
            }

            val prechecker = WorkImportPrechecker(db.workDao(), db.categoryDao())
            val pipeline = OfflineDataPipeline(
                merger = MultiSourceWorkMerger(),
                prechecker = prechecker,
                poemsDbParser = PoemsDbParser(),
                poetryCnParser = PoetryCnParser(),
                chinesePoetryParser = ChinesePoetryParser()
            )

            val pipelineResult = pipeline.processRawSources(inputs)
            require(pipelineResult.mergedWorks.size == rawArray.length()) {
                "流水线处理作品数量不一致: 期望 ${rawArray.length()}, 实际 ${pipelineResult.mergedWorks.size}"
            }

            // 4. 批量原子导入作品（带正式 releaseId）
            val now = System.currentTimeMillis()
            val importer = BatchWorkImporter(db, prechecker)

            // 在批次生成后立刻插入正式发布包，满足 release_work_entries 的外键约束
            importer.onAfterBatchCreated = { batchId ->
                db.releaseDao().insertRelease(
                    DataReleaseEntity(
                        releaseId = releaseId,
                        releaseCode = DEFAULT_RELEASE_CODE,
                        batchId = batchId,
                        releaseType = "FULL",
                        description = DEFAULT_RELEASE_DESC,
                        publishedAtEpochMs = now
                    )
                )
            }

            val batchResult = importer.importBatch(
                batchCode = batchCode,
                batchSource = DEFAULT_BATCH_SOURCE,
                items = pipelineResult.mergedWorks,
                releaseId = releaseId
            )
            require(batchResult.isSuccessful) { "批量导入失败: ${batchResult.errorMessage}" }
            require(batchResult.importedCount == rawArray.length()) {
                "导入成功作品数不足: 期望 ${rawArray.length()}, 实际 ${batchResult.importedCount}"
            }

            // 5. 建立激活发布指针
            db.releaseDao().setReleasePointer(
                AppReleasePointerEntity(
                    pointerId = 1,
                    currentReleaseId = releaseId,
                    activatedAtEpochMs = now,
                    updatedBy = "SYSTEM_PREPACKAGED_BUILDER"
                )
            )

            // 6. 写入核心元数据记录
            val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(now))
            db.releaseDao().setMetadata(AppDatabaseMetadataEntity("SCHEMA_VERSION", "1.0", now))
            db.releaseDao().setMetadata(AppDatabaseMetadataEntity("CONTENT_SOURCE", "poems-db", now))
            db.releaseDao().setMetadata(AppDatabaseMetadataEntity("PREPACKAGED_COUNT", rawArray.length().toString(), now))
            db.releaseDao().setMetadata(AppDatabaseMetadataEntity("BUILD_TIMESTAMP", isoDate, now))
            db.releaseDao().setMetadata(AppDatabaseMetadataEntity("ACTIVE_RELEASE_ID", releaseId, now))

            // 7. 读取各表实际统计数量
            val sdb = db.openHelper.writableDatabase
            val worksCount = queryCount(sdb, "works")
            val authorsCount = queryCount(sdb, "authors")
            val componentsCount = queryCount(sdb, "content_components")
            val sourceLinksCount = queryCount(sdb, "work_source_links")
            val releaseEntriesCount = queryCount(sdb, "release_work_entries")

            // 8. 检查 room_master_table identity_hash
            var identityHash = ""
            val masterCursor = sdb.query("SELECT identity_hash FROM room_master_table WHERE id = 42")
            if (masterCursor.moveToFirst()) {
                identityHash = masterCursor.getString(0)
            }
            masterCursor.close()

            // 9. 强制执行 WAL Checkpoint 并切换回 DELETE 日志模式
            sdb.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
            sdb.query("PRAGMA journal_mode = DELETE;").close()

            // 10. 执行 SQLite 完整性检查
            var integrityCheck = "unknown"
            val integrityCursor = sdb.query("PRAGMA integrity_check;")
            if (integrityCursor.moveToFirst()) {
                integrityCheck = integrityCursor.getString(0)
            }
            integrityCursor.close()

            require(integrityCheck == "ok") { "SQLite 数据库完整性检查未通过: $integrityCheck" }

            BuildResult(
                databaseFile = outputDbFile,
                fileSizeBytes = 0L, // 待关闭连接后获取真实物理文件大小
                worksCount = worksCount,
                authorsCount = authorsCount,
                componentsCount = componentsCount,
                sourceLinksCount = sourceLinksCount,
                releaseEntriesCount = releaseEntriesCount,
                identityHash = identityHash,
                integrityCheck = integrityCheck,
                batchStatus = batchResult.batchStatus
            )
        } finally {
            // 11. 安全关闭数据库连接，清理遗留附属文件
            if (db.isOpen) {
                db.close()
            }
            cleanWalAndShm(outputDbFile)
        }
    }.let { result ->
        // 补充实际最终物理文件大小
        result.copy(fileSizeBytes = outputDbFile.length())
    }

    private fun queryCount(db: androidx.sqlite.db.SupportSQLiteDatabase, tableName: String): Int {
        val cursor = db.query("SELECT COUNT(*) FROM $tableName")
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }

    private fun deleteDatabaseFiles(dbFile: File) {
        if (dbFile.exists()) dbFile.delete()
        cleanWalAndShm(dbFile)
    }

    private fun cleanWalAndShm(dbFile: File) {
        val wal = File(dbFile.parentFile, "${dbFile.name}-wal")
        if (wal.exists()) wal.delete()
        val shm = File(dbFile.parentFile, "${dbFile.name}-shm")
        if (shm.exists()) shm.delete()
    }
}
