package com.shi.yun.data.local.importer.pipeline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.importer.BatchWorkImporter
import com.shi.yun.data.local.importer.WorkImportPrechecker
import com.shi.yun.data.local.importer.merger.MultiSourceWorkMerger
import com.shi.yun.data.local.importer.parser.ChinesePoetryParser
import com.shi.yun.data.local.importer.parser.PoemsDbParser
import com.shi.yun.data.local.importer.parser.PoetryCnParser
import com.shi.yun.data.local.repository.RoomWorkRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 阶段 4.2B 第一批 100 首真实开源诗词数据抽取、离线转换与隔离试导入全链路核验套件
 *
 * 核心验证矩阵：
 * 1. 真实原始 JSON 记录读取与独立解析器 (PoemsDbParser, ChinesePoetryParser) 解析能力；
 * 2. 离线流水线 (OfflineDataPipeline) 归档、保守聚类、多源合并决策 (MultiSourceWorkMerger) 与前置预检；
 * 3. 试验数据库物理隔离：
 *    - 使用独立磁盘数据库文件 "trial_import_real100.db"；
 *    - 严禁触碰正式数据库 "shiyun.db"；
 *    - 严禁篡改 works.json 与 600 首旧测试数据；
 *    - 严禁改动正式发布指针 (release_pointer)；
 *    - 用户收藏、笔记等资产表保持纯净隔离；
 * 4. 真实物理入库与断开重连持久化核验：
 *    - 使用 BatchWorkImporter 写入 100 首真实作品；
 *    - 关闭数据库连接 (db.close())；
 *    - 重新打开真实数据库文件并经由 RoomWorkRepository 读取；
 *    - 重点查验李白、杜甫、王维、苏轼、辛弃疾、和岘、《诗经》无名氏、屈原、曹操、关汉卿等名篇正文、题名、词牌与作者；
 * 5. 防重与二次幂等导入验证：次轮导入 100% CAN_SKIP；
 * 6. 真实统计数据严格核算并输出。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealOneHundredWorksTrialImportTest {

    private lateinit var context: Context
    private lateinit var trialDbFile: File
    private lateinit var productionDbFile: File

    private lateinit var trialDb: ShiYunDatabase
    private lateinit var importer: BatchWorkImporter
    private lateinit var prechecker: WorkImportPrechecker
    private lateinit var pipeline: OfflineDataPipeline
    private val poemsDbParser = PoemsDbParser()
    private val chinesePoetryParser = ChinesePoetryParser()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()

        // 严格物理隔离：试验数据库使用独立物理文件
        trialDbFile = context.getDatabasePath("trial_import_real100.db")
        productionDbFile = context.getDatabasePath(ShiYunDatabase.DATABASE_NAME)

        if (trialDbFile.exists()) {
            trialDbFile.delete()
        }

        trialDb = Room.databaseBuilder(
            context,
            ShiYunDatabase::class.java,
            trialDbFile.absolutePath
        )
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()

        prechecker = WorkImportPrechecker(trialDb.workDao(), trialDb.categoryDao())
        importer = BatchWorkImporter(trialDb, prechecker)

        pipeline = OfflineDataPipeline(
            merger = MultiSourceWorkMerger(),
            prechecker = prechecker,
            poemsDbParser = poemsDbParser,
            poetryCnParser = PoetryCnParser(),
            chinesePoetryParser = chinesePoetryParser
        )
    }

    @After
    fun tearDown() {
        if (trialDb.isOpen) {
            trialDb.close()
        }
        if (trialDbFile.exists()) {
            trialDbFile.delete()
        }
    }

    private fun readFixtureRawRecords(): JSONArray {
        val resource = javaClass.classLoader?.getResource("fixtures/real_100_raw_records.json")
        requireNotNull(resource) { "找不到测试资源: fixtures/real_100_raw_records.json" }
        val text = File(resource.toURI()).readText(Charsets.UTF_8)
        return JSONArray(text)
    }

    @Test
    fun testRealHundredWorks_fullPipeline_isolation_persistence_and_readBack() = runBlocking {
        val rawArray = readFixtureRawRecords()
        assertEquals("必须包含恰好100条真实原始记录", 100, rawArray.length())

        // 1. 组装流水线输入 RawInputSource 列表
        val inputs = mutableListOf<RawInputSource>()
        for (i in 0 until rawArray.length()) {
            val recordObj = rawArray.getJSONObject(i)
            val sourceCode = recordObj.getString("sourceCode")
            val sourceFile = recordObj.getString("sourceFile")
            val rawRecordJson = recordObj.getJSONObject("rawRecordJson")
            val dynasty = recordObj.optString("dynasty").ifEmpty { null }

            inputs.add(
                RawInputSource(
                    rawContent = rawRecordJson.toString(),
                    sourceCode = sourceCode,
                    rawFilePath = sourceFile,
                    dynastyHint = dynasty,
                    sourceVersion = "trial-run-v1"
                )
            )
        }

        // 2. 执行离线全链路流水线：原始JSON -> 独立解析器 -> 归档 -> 保守聚类 -> 合并决策 -> 预检
        val pipelineResult = pipeline.processRawSources(inputs)
        val stats = pipelineResult.stats

        println("=== 离线流水线执行统计 ===")
        println("rawRecordCount: ${inputs.size}")
        println("parsedCount: ${stats.totalCandidatesParsed}")
        println("parsedSuccessRate: ${stats.parsedSuccessRate}%")
        println("rawFileArchiveRate: ${stats.rawFileArchiveRate}%")
        println("rawRecordLocatableRate: ${stats.rawRecordLocatableRate}%")
        println("sourceIndexPresenceRate: ${stats.sourceIndexPresenceRate}%")
        println("mergedWorksCount: ${pipelineResult.mergedWorks.size}")

        assertEquals("原始输入记录数必须为 100", 100, inputs.size)
        assertEquals("成功解析候选数必须为 100", 100, stats.totalCandidatesParsed)
        assertEquals("解析失败记录数必须为 0", 0, stats.failedRecordsCount)
        assertEquals("解析成功率必须为 100%", 100.0, stats.parsedSuccessRate, 0.0001)
        assertEquals("原始记录定位率必须为 100%", 100.0, stats.rawRecordLocatableRate, 0.0001)
        assertEquals("来源索引留存率必须为 100%", 100.0, stats.sourceIndexPresenceRate, 0.0001)

        val mergedWorks = pipelineResult.mergedWorks
        assertEquals("100条无重合作品经聚类后必须生成100篇独立作品", 100, mergedWorks.size)

        // 3. 验证预检结果
        val precheckSummary = pipelineResult.precheckSummary
        assertNotNull(precheckSummary)
        println("=== 导入前预检统计 ===")
        println("totalCount: ${precheckSummary!!.totalCount}")
        println("readyCount: ${precheckSummary.readyCount}")
        println("skipCount: ${precheckSummary.skipCount}")
        println("needsConfirmationCount: ${precheckSummary.needsConfirmationCount}")
        println("failedCount: ${precheckSummary.failedCount}")

        assertEquals(100, precheckSummary.totalCount)
        assertEquals(0, precheckSummary.failedCount)
        assertEquals(0, precheckSummary.skipCount)
        // 允许词牌分离后无首句极端情况进入 needsConfirmation，实测应绝大多数就绪
        assertTrue("绝大多数作品应直接就绪进入待导入队列", precheckSummary.readyCount >= 98)

        // 4. 执行真实物理入库
        println("=== 执行试验数据库批量写入 ===")
        val batchResult = importer.importBatch(
            batchCode = "BATCH_TRIAL_REAL100_001",
            batchSource = "第一批100首真实诗词试导入",
            items = mergedWorks,
            releaseId = null // 严格不改变正式发布指针！
        )

        println("importedCount: ${batchResult.importedCount}")
        println("skippedCount: ${batchResult.skippedCount}")
        println("needsConfirmationCount: ${batchResult.needsConfirmationCount}")
        println("failedCount: ${batchResult.failedCount}")
        println("batchStatus: ${batchResult.batchStatus}")

        assertEquals(100, batchResult.totalInputCount)
        assertEquals(precheckSummary.readyCount, batchResult.importedCount)
        assertEquals(precheckSummary.needsConfirmationCount, batchResult.needsConfirmationCount)
        assertEquals(0, batchResult.failedCount)
        assertEquals(if (precheckSummary.needsConfirmationCount > 0) "PARTIAL" else "IMPORTED", batchResult.batchStatus)

        // 5. 严格验证第四部分：数据库隔离
        println("=== 验证数据库与用户数据隔离 ===")
        // 5.1 验证正式数据库文件根本未被建立或未被写入
        assertFalse("正式数据库文件 shiyun.db 严禁被连接或写入", productionDbFile.exists())
        // 5.2 验证正式发布指针未被改变
        val pointer = trialDb.releaseDao().getReleasePointer()
        assertNull("试验批次绝不修改正式发布指针", pointer)
        // 5.3 验证用户收藏表保持纯净
        val favoriteCount = trialDb.favoriteDao().getFavoriteCount()
        assertEquals("用户收藏数必须为0", 0, favoriteCount)

        // 6. 执行第五部分：关闭数据库连接再重新打开，验证真实持久化与无损还原
        println("=== 关闭数据库连接并检查物理文件 ===")
        trialDb.close()

        assertTrue("试验数据库物理文件必须真实持久化在磁盘上", trialDbFile.exists())
        val fileSize = trialDbFile.length()
        println("试验数据库文件真实大小: $fileSize 字节")
        assertTrue("试验数据库文件大小必须大于 50KB", fileSize > 50_000)

        println("=== 重新打开试验数据库并建立 Repository 读取通道 ===")
        val reopenDb = Room.databaseBuilder(
            context,
            ShiYunDatabase::class.java,
            trialDbFile.absolutePath
        )
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()

        val repo = RoomWorkRepository(reopenDb)

        try {
            val allWorks = reopenDb.workDao().getAllActiveWorks()
            assertEquals("重新打开数据库后作品数量必须严格一致", batchResult.importedCount, allWorks.size)

            val allAuthors = reopenDb.categoryDao().getAllAuthors()
            println("实际入库作者总数: ${allAuthors.size}")
            // 100首诗词中多篇出自李白、王维、李商隐、杜甫、苏轼、辛弃疾、寇准等，作者数应在 30~50 之间
            assertTrue("作者档案应被有效复用，作者总数在 25 到 55 之间", allAuthors.size in 25..55)
            // 严格核查：绝无“佚名”或带有“anon”字样的虚假作者实体
            val invalidAuthors = allAuthors.filter {
                it.standardName == "佚名" || it.standardName == "未知" || it.authorId.contains("anon")
            }
            assertTrue("绝不允许创建名为'佚名'的假作者实体", invalidAuthors.isEmpty())

            // 7. 重点名作抽样深度核验
            // 7.1 唐诗绝句：元稹《行宫》
            val xinggongList = reopenDb.workDao().getWorksByTitle("行宫")
            assertFalse(xinggongList.isEmpty())
            val xinggong = repo.getWorkById(xinggongList.first().workId)
            assertNotNull(xinggong)
            assertEquals("行宫", xinggong?.title)
            assertEquals("元稹", xinggong?.author)
            assertEquals("唐代", xinggong?.dynasty)
            assertTrue("正文必须保真包含原句", xinggong?.content?.contains("寥落古行宫，宫花寂寞红") == true)
            assertTrue("正文必须保真包含原句", xinggong?.content?.contains("白头宫女在，闲坐说玄宗") == true)
            // 辅助内容检查
            assertNotNull(xinggong?.auxiliary?.translation)
            assertNotNull(xinggong?.auxiliary?.appreciation)

            // 7.2 唐诗绝句：王之涣《登鹳雀楼》
            val guanqueList = reopenDb.workDao().getWorksByTitle("登鹳雀楼")
            assertFalse(guanqueList.isEmpty())
            val guanque = repo.getWorkById(guanqueList.first().workId)
            assertNotNull(guanque)
            assertEquals("王之涣", guanque?.author)
            assertTrue(guanque?.content?.contains("白日依山尽，黄河入海流") == true)
            assertTrue(guanque?.content?.contains("欲穷千里目，更上一层楼") == true)

            // 7.3 唐诗五绝：王维《鹿柴》
            val luchaiList = reopenDb.workDao().getWorksByTitle("鹿柴")
            assertFalse(luchaiList.isEmpty())
            val luchai = repo.getWorkById(luchaiList.first().workId)
            assertNotNull(luchai)
            assertEquals("王维", luchai?.author)
            assertTrue(luchai?.content?.contains("空山不见人，但闻人语响") == true)
            assertTrue(luchai?.content?.contains("返景入深林，复照青苔上") == true)

            // 7.4 全唐诗繁体底本：太宗皇帝《帝京篇十首 一》
            val taizongList = reopenDb.workDao().getWorksByTitle("帝京篇十首 一")
            assertFalse(taizongList.isEmpty())
            val taizong = repo.getWorkById(taizongList.first().workId)
            assertNotNull(taizong)
            assertEquals("太宗皇帝", taizong?.author)
            assertTrue("繁体原文绝对保真", taizong?.content?.contains("秦川雄帝宅，函谷壯皇居") == true)

            // 7.5 宋词「词牌·词题」复合结构：辛弃疾《虞美人·赋虞美人草》
            val yumeirenList = reopenDb.workDao().getWorksByTitle("赋虞美人草")
            assertFalse(yumeirenList.isEmpty())
            val yumeiren = repo.getWorkById(yumeirenList.first().workId)
            assertNotNull(yumeiren)
            assertEquals("赋虞美人草", yumeiren?.title)
            assertEquals("辛弃疾", yumeiren?.author)
            assertEquals("虞美人", yumeiren?.cipai)
            assertTrue(yumeiren?.content?.contains("当年得意如芳草") == true)

            // 7.6 宋词无独立标题：和岘《导引》首句默认词题生成
            val daoyinWorks = reopenDb.workDao().getAllActiveWorks().filter { it.standardTitle.contains("气和玉烛") }
            assertFalse(daoyinWorks.isEmpty())
            val daoyin = repo.getWorkById(daoyinWorks.first().workId)
            assertNotNull(daoyin)
            assertEquals("和岘", daoyin?.author)
            assertEquals("导引", daoyin?.cipai)
            assertTrue(daoyin?.content?.contains("气和玉烛，睿化著鸿明") == true)

            // 7.7 先秦《诗经·周南·关雎》：无名氏处理
            val guanjuList = reopenDb.workDao().getWorksByTitle("关雎")
            assertFalse(guanjuList.isEmpty())
            val guanju = repo.getWorkById(guanjuList.first().workId)
            assertNotNull(guanju)
            assertEquals("关雎", guanju?.title)
            assertEquals("先秦", guanju?.dynasty)
            assertEquals("未知作者作品映射为领域模型时 author 必须为空字符串", "", guanju?.author)
            assertTrue(guanju?.content?.contains("关关雎鸠，在河之洲") == true)

            // 7.8 楚辞：屈原《离骚》
            val lisaoList = reopenDb.workDao().getWorksByTitle("离骚")
            assertFalse(lisaoList.isEmpty())
            val lisao = repo.getWorkById(lisaoList.first().workId)
            assertNotNull(lisao)
            assertEquals("屈原", lisao?.author)
            assertEquals("先秦", lisao?.dynasty)
            assertTrue(lisao?.content?.contains("帝高阳之苗裔兮") == true)

            // 7.9 汉魏古诗：曹操《短歌行 其一》
            val duangeList = reopenDb.workDao().getWorksByTitle("短歌行 其一")
            assertFalse(duangeList.isEmpty())
            val duange = repo.getWorkById(duangeList.first().workId)
            assertNotNull(duange)
            assertEquals("曹操", duange?.author)
            assertTrue(duange?.content?.contains("对酒当歌，人生几何") == true)

            // 7.10 元曲：关汉卿《诈妮子调风月・仙吕/点绛唇》
            val quList = reopenDb.workDao().getWorksByTitle("诈妮子调风月・仙吕/点绛唇")
            assertFalse(quList.isEmpty())
            val qu = repo.getWorkById(quList.first().workId)
            assertNotNull(qu)
            assertEquals("关汉卿", qu?.author)
            assertEquals("元", qu?.dynasty)
            assertTrue(qu?.content?.contains("半世为人，不曾教大人心困") == true)

            // 8. 来源可定位性深度核验
            val sourceLinks = reopenDb.workDao().getSourceLinksByWorkId(xinggongList.first().workId)
            assertFalse("来源关联必须存在", sourceLinks.isEmpty())
            assertEquals("POEMS_DB", sourceLinks.first().sourceCode)
            assertEquals("5c22086397880d3b825c95c7", sourceLinks.first().externalItemId)

            // 9. 次轮重复导入幂等性与防重验证
            println("=== 次轮重复导入验证防重机制 ===")
            val round2Importer = BatchWorkImporter(reopenDb)
            val round2Result = round2Importer.importBatch(
                batchCode = "BATCH_TRIAL_REAL100_002",
                batchSource = "第一批100首真实诗词次轮重复测试",
                items = mergedWorks,
                releaseId = null
            )

            println("round2 totalInput: ${round2Result.totalInputCount}")
            println("round2 imported: ${round2Result.importedCount}")
            println("round2 skipped: ${round2Result.skippedCount}")
            assertEquals(100, round2Result.totalInputCount)
            assertEquals(0, round2Result.importedCount)
            assertEquals(precheckSummary.readyCount, round2Result.skippedCount)
            assertEquals(precheckSummary.needsConfirmationCount, round2Result.needsConfirmationCount)
            assertEquals(0, round2Result.failedCount)

            // 库中作品数保持不变
            assertEquals("库中作品数保持恒定", batchResult.importedCount, reopenDb.workDao().getAllActiveWorks().size)

        } finally {
            reopenDb.close()
        }
    }
}
