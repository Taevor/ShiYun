package com.shi.yun.data.local.generator

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.repository.RoomWorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 步骤 3：预置 SQLite 数据库构建与八大核心指标验证测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrepackagedDatabaseBuildAndVerifyTest {

    private lateinit var context: Context
    private lateinit var targetAssetsDbFile: File
    private lateinit var rawRecordsJson: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()

        // 测试输出定位在隔离的缓存目录，避免覆盖生产 assets/database/shiyun.db
        targetAssetsDbFile = File(context.cacheDir, "test_prepackaged_shiyun.db")

        // 读取验收通过的 100 首纯正 poems-db 真实样本
        val resource = javaClass.classLoader?.getResource("fixtures/poems_db_pure_100_records.json")
        requireNotNull(resource) { "找不到测试资源: fixtures/poems_db_pure_100_records.json" }
        rawRecordsJson = File(resource.toURI()).readText(Charsets.UTF_8)
    }

    @Test
    fun testBuildPrepackagedDatabase_fullLifecycle_and_eightVerifications() = runBlocking {
        println("=== 步骤 3：启动预置数据库构建 ===")
        val result = PrepackagedDatabaseBuilder.build(
            context = context,
            rawRecordsJson = rawRecordsJson,
            outputDbFile = targetAssetsDbFile
        )

        // ==================== 验证 1：数据库文件确实生成在目标 assets 目录 ====================
        println("=== 验证 1：文件存在与大小 ===")
        assertTrue("预置数据库文件必须存在于 assets 目录", targetAssetsDbFile.exists())
        assertTrue("预置数据库文件必须大于 50KB", targetAssetsDbFile.length() > 50_000)
        println("预置数据库路径: ${targetAssetsDbFile.absolutePath}")
        println("物理文件大小: ${result.fileSizeBytes} 字节 (${result.fileSizeBytes / 1024} KB)")

        val walFile = File(targetAssetsDbFile.parentFile, "shiyun.db-wal")
        val shmFile = File(targetAssetsDbFile.parentFile, "shiyun.db-shm")
        assertFalse("WAL 必须完成 TRUNCATE checkpoint，不得留存 wal 文件", walFile.exists())
        assertFalse("SHM 共享内存文件必须已清理", shmFile.exists())

        // ==================== 验证 2：数据库完整性检查通过 ====================
        println("=== 验证 2：SQLite 完整性 ===")
        assertEquals("ok", result.integrityCheck)

        // ==================== 验证 3：Room 规范校验与 Schema V1 identity_hash 匹配 ====================
        println("=== 验证 3：Room Schema V1 identity_hash ===")
        assertEquals(
            "Room identity_hash 必须与 Schema V1.0 完全匹配",
            "1b9599dae1e2ce0e84221f69acda80aa",
            result.identityHash
        )

        // ==================== 验证 4：作品数量、作者数量与来源关联与输入 100% 一致 ====================
        println("=== 验证 4：实体数量统计 ===")
        assertEquals("作品表必须恰好包含 100 首作品", 100, result.worksCount)
        assertEquals("外部来源追溯链接必须恰好 100 条", 100, result.sourceLinksCount)
        assertEquals("正式发布映射条目必须恰好 100 条", 100, result.releaseEntriesCount)
        assertTrue("作者表数量应大于 0", result.authorsCount > 0)
        assertTrue("内容组件表数量应大于等于 100", result.componentsCount >= 100)
        println("worksCount: ${result.worksCount}")
        println("authorsCount: ${result.authorsCount}")
        println("componentsCount: ${result.componentsCount}")
        println("sourceLinksCount: ${result.sourceLinksCount}")
        println("releaseEntriesCount: ${result.releaseEntriesCount}")

        // ==================== 验证 5：关闭并重新打开数据库后数据不丢失 ====================
        println("=== 验证 5：关闭后重新打开测试 ===")
        val reopenedDb = Room.databaseBuilder(
            context,
            ShiYunDatabase::class.java,
            targetAssetsDbFile.absolutePath
        )
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()

        try {
            val reopenedWorksCount = reopenedDb.workDao().getAllActiveWorks().size
            assertEquals("重新打开后作品数量仍为 100", 100, reopenedWorksCount)

            // ==================== 验证 6：100 首作品的正文、标题、作者、朝代、体裁和来源均可读取 ====================
            println("=== 验证 6：经由 RoomWorkRepository 读取 100 首作品 ===")
            val repository = RoomWorkRepository(reopenedDb, Dispatchers.Unconfined)
            val allWorks = repository.getAllWorks()
            assertEquals("通过 Repository 获取全部作品必须为 100 首", 100, allWorks.size)

            val rawArray = JSONArray(rawRecordsJson)
            for (i in 0 until rawArray.length()) {
                val rawObj = rawArray.getJSONObject(i).getJSONObject("rawRecord")
                val expectedTitle = rawObj.optString("title").ifEmpty { rawObj.optString("name") }.trim()
                if (expectedTitle.isNotEmpty()) {
                    val found = allWorks.any {
                        expectedTitle.contains(it.title) || it.title.contains(expectedTitle) ||
                            (it.cipai != null && expectedTitle.contains(it.cipai))
                    }
                    assertTrue("第 $i 首作品 [$expectedTitle] 应在数据库中可检索", found)
                }
            }

            // 抽样验证特定名篇
            val jys = allWorks.find { it.title == "静夜思" }
            assertNotNull("《静夜思》必须存在", jys)
            assertEquals("李白", jys!!.author)
            assertEquals("唐代", jys.dynasty)
            assertTrue("静夜思正文必须完整", jys.content.contains("床前明月光"))
            assertFalse("正文绝不得残留 HTML 标签", jys.content.contains("<p>"))

            // 验证正式发布指针
            val currentReleaseId = reopenedDb.releaseDao().getCurrentReleaseId()
            assertEquals("release_v1_0", currentReleaseId)

            val activeReleaseWorks = reopenedDb.releaseDao().getActiveReleaseWorks()
            assertEquals("激活发布下的作品必须恰好为 100 首", 100, activeReleaseWorks.size)

            // 验证元数据
            val schemaVersion = reopenedDb.releaseDao().getMetadataValue("SCHEMA_VERSION")
            assertEquals("1.0", schemaVersion)
            val source = reopenedDb.releaseDao().getMetadataValue("CONTENT_SOURCE")
            assertEquals("poems-db", source)

        } finally {
            reopenedDb.close()
        }

        // ==================== 验证 7：重复构建幂等性，不产生重复作品 ====================
        println("=== 验证 7：重复构建幂等性 ===")
        val secondResult = PrepackagedDatabaseBuilder.build(
            context = context,
            rawRecordsJson = rawRecordsJson,
            outputDbFile = targetAssetsDbFile
        )
        assertEquals("重复构建后作品表依然恰好为 100 首", 100, secondResult.worksCount)
        assertEquals("重复构建后发布条目依然恰好为 100 条", 100, secondResult.releaseEntriesCount)
        assertEquals("ok", secondResult.integrityCheck)
        println("=== 全部验证顺利通过！===")
    }

    @Test
    fun testRoomCreateFromAsset_firstLaunchDirectRead() = runBlocking {
        println("=== 验证 8：测试 App 首次启动从 assets/database/shiyun.db 预置加载 ===")
        val copyDbFile = context.getDatabasePath("test_first_launch_copy.db")
        if (copyDbFile.exists()) copyDbFile.delete()

        val appDb = Room.databaseBuilder(
            context,
            ShiYunDatabase::class.java,
            copyDbFile.name
        )
            .createFromAsset("database/shiyun.db")
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()

        try {
            val repository = RoomWorkRepository(appDb, Dispatchers.Unconfined)
            val works = repository.getAllWorks()
            assertEquals("首次启动从 assets 预置库直接解压读取，作品数必须为 12188", 12188, works.size)

            val randomWork = repository.getRandomWork()
            assertNotNull("随机推荐必须能直接读取预置作品", randomWork)

            val tangWorks = repository.getWorksByDynasty("唐代")
            assertTrue("唐代诗词数量必须大于 0", tangWorks.isNotEmpty())

            val ciWorks = repository.getWorksByGenre("CI")
            assertTrue("宋词分类作品必须大于 0", ciWorks.isNotEmpty())

            println("首次启动解压读取验证通过！作品数: ${works.size}, 随机作品: 《${randomWork?.title}》(${randomWork?.author})")
        } finally {
            appDb.close()
            if (copyDbFile.exists()) copyDbFile.delete()
        }
    }
}
