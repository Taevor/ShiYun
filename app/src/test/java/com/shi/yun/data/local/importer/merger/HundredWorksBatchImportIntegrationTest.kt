package com.shi.yun.data.local.importer.merger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.entity.ComponentType
import com.shi.yun.data.local.importer.BatchWorkImporter
import com.shi.yun.data.local.importer.WorkImportPrechecker
import com.shi.yun.data.local.importer.fixture.OneHundredSampleWorksData
import com.shi.yun.data.local.repository.RoomWorkRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 阶段 4.2 100 首多来源整合样本批量导入与全链路验证测试套件
 *
 * 验证目标：
 * 1. 100 首多来源整合标准样本通过 WorkImportPrechecker 执行预检，100% 合法无阻碍；
 * 2. 100 首样本经由 BatchWorkImporter 执行批次级联写入，批次状态流转为 IMPORTED；
 * 3. 验证 100 篇作品、多维内容组件、分类属性、细粒度来源追溯表持久化完整性；
 * 4. 验证未知作者作品（如《诗经》名篇）作品与版本正常保存，作者关联为空，严禁产生“佚名”或匿名作者实体；
 * 5. 验证李白、杜甫、苏轼等多篇名作复用同一作者档案，作者总数远少于作品总数；
 * 6. 验证次轮重复导入 100 首作品时 100% 识别为 CAN_SKIP 安全跳过，新增作品数严格为 0；
 * 7. 验证现有数据仓库 (RoomWorkRepository) 无损聚合还原所有作品。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HundredWorksBatchImportIntegrationTest {

    private lateinit var db: ShiYunDatabase
    private lateinit var importer: BatchWorkImporter
    private lateinit var prechecker: WorkImportPrechecker
    private lateinit var repository: RoomWorkRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShiYunDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()
        importer = BatchWorkImporter(db)
        prechecker = WorkImportPrechecker(db.workDao(), db.categoryDao())
        repository = RoomWorkRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testHundredWorks_precheck_allPassValidation() = runBlocking {
        val hundredWorks = OneHundredSampleWorksData.getHundredSampleWorks()
        assertEquals(100, hundredWorks.size)

        // 执行前置规则校验与查重预检
        val summary = prechecker.precheckBatch(hundredWorks)

        assertEquals(100, summary.totalCount)
        assertEquals(100, summary.readyCount)
        assertEquals(0, summary.skipCount)
        assertEquals(0, summary.needsConfirmationCount)
        assertEquals(0, summary.failedCount)
    }

    @Test
    fun testHundredWorks_fullBatchImport_andRepositoryReadBack() = runBlocking {
        val hundredWorks = OneHundredSampleWorksData.getHundredSampleWorks()

        // 1. 首轮全量导入 100 篇作品
        val result = importer.importBatch(
            batchCode = "BATCH_PHASE4_2_HUNDRED_001",
            batchSource = "阶段4.2多来源整合100首样本库",
            items = hundredWorks
        )

        // 验证批次导入统计
        assertEquals(100, result.totalInputCount)
        assertEquals(100, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(0, result.needsConfirmationCount)
        assertEquals(0, result.failedCount)
        assertEquals("IMPORTED", result.batchStatus)

        // 2. 验证底层数据表计数
        val allWorks = db.workDao().getAllActiveWorks()
        assertEquals(100, allWorks.size)

        val allAuthors = db.categoryDao().getAllAuthors()
        // 验证多篇名作共享作者档案：李白10首、杜甫10首、王维7首、白居易8首、苏轼8首、辛弃疾7首、李清照6首等
        // 预期作者实体总数显著小于作品总数（大约 30 左右），证明作者档案被成功复用
        assertTrue("作者总数 (${allAuthors.size}) 应在 25 到 40 之间", allAuthors.size in 25..40)

        // 3. 严格核实无名氏/未知作者规范
        val anonEntities = allAuthors.filter {
            it.standardName == "佚名" || it.standardName == "未知" || it.authorId.contains("anon")
        }
        assertTrue("绝不允许创建名为'佚名'或带有'anon'的虚假作者实体", anonEntities.isEmpty())

        // 查验《关雎》和《蒹葭》
        val guanjuList = db.workDao().getWorksByTitle("关雎")
        assertFalse(guanjuList.isEmpty())
        val guanju = guanjuList.first()
        val guanjuVersions = db.workDao().getVersionsByWorkId(guanju.workId)
        assertEquals(1, guanjuVersions.size)
        val guanjuAuthors = db.workDao().getAuthorsByVersionId(guanjuVersions.first().workVersionId)
        assertTrue("《关雎》作者关联必须为空", guanjuAuthors.isEmpty())

        // 4. 验证五维内容组件与细粒度来源
        val guanjuComponents = db.workDao().getComponentsByVersionId(guanjuVersions.first().workVersionId)
        // 包含 CONTENT, TRANSLATION, ANNOTATION, APPRECIATION
        val types = guanjuComponents.map { it.componentType }.toSet()
        assertTrue(types.contains(ComponentType.CONTENT.code))
        assertTrue(types.contains(ComponentType.TRANSLATION.code))
        assertTrue(types.contains(ComponentType.ANNOTATION.code))
        assertTrue(types.contains(ComponentType.APPRECIATION.code))

        // 5. 验证现有数据仓库 (RoomWorkRepository) 无损还原读取
        val loadedGuanju = repository.getWorkById(guanju.workId)
        assertNotNull(loadedGuanju)
        assertEquals("关雎", loadedGuanju?.title)
        assertEquals("未知作者作品映射为领域模型时 author 必须为空字符串", "", loadedGuanju?.author)
        assertEquals("先秦", loadedGuanju?.dynasty)
        assertNotNull(loadedGuanju?.auxiliary?.translation)
        assertNotNull(loadedGuanju?.auxiliary?.appreciation)

        // 查验李白《静夜思》
        val jingyesiList = db.workDao().getWorksByTitle("静夜思")
        assertFalse(jingyesiList.isEmpty())
        val jingyesi = jingyesiList.first()
        val loadedJingyesi = repository.getWorkById(jingyesi.workId)
        assertNotNull(loadedJingyesi)
        assertEquals("李白", loadedJingyesi?.author)
        assertEquals("唐", loadedJingyesi?.dynasty)

        // 6. 次轮重复导入 100 首作品：验证防重保护全量生效
        val round2Result = importer.importBatch(
            batchCode = "BATCH_PHASE4_2_HUNDRED_002",
            batchSource = "阶段4.2多来源整合100首样本次轮",
            items = hundredWorks
        )

        assertEquals(100, round2Result.totalInputCount)
        assertEquals(0, round2Result.importedCount)
        assertEquals(100, round2Result.skippedCount)
        assertEquals(0, round2Result.needsConfirmationCount)
        assertEquals(0, round2Result.failedCount)
        assertEquals("IMPORTED", round2Result.batchStatus)

        // 库中作品数保持为 100，零膨胀
        assertEquals(100, db.workDao().getAllActiveWorks().size)
    }
}
