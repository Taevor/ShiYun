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
import com.shi.yun.data.model.Genre
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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
 * 步骤 2：poems-db 单一来源 100 首真实作品试导入与全链路核验套件
 *
 * 核心验证矩阵：
 * 1. 唯一数据源：100% 抽取自 D:\ShiYunDataSources\poems-db，未混入任何其他数据源或伪造数据；
 * 2. 覆盖配额：唐诗 30 + 宋诗 20 + 宋词 25 + 先秦汉魏 10 + 元曲 5 + 明清其他 10 = 100 首；
 * 3. 关键名篇与特性覆盖：
 *    - 唐诗名篇：白居易《长恨歌》、李白《将进酒》《静夜思》、高适《别董大》、元稹《行宫》；
 *    - 宋词名篇：苏轼《水调歌头》、李清照《声声慢》、辛弃疾《摸鱼儿》、赵长卿《蝶恋花》；
 *    - 先秦汉魏：屈原《离骚》、曹操《短歌行》、《诗经·关雎》；
 *    - 元曲：马致远《天净沙·秋思》；
 *    - 特殊字/构字标记保留：赵长卿《蝶恋花》(含<黑宛>)、吴孺子《望西山》(含<舟双>)、张佳胤《三石篇》(含<石含>)；
 * 4. 离线流水线处理（PoemsDbParser + ClassicalTextHtmlCleaner + Prechecker + BatchWorkImporter）；
 * 5. 独立物理隔离测试库（trial_import_poemsdb_100.db），正式库、旧600首及用户数据绝对安全；
 * 6. 物理关闭并重开数据库，经 RoomWorkRepository 进行多维读取核验；
 * 7. 二次重复导入幂等性与防重核验（importedCount = 0, skippedCount = 100）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PoemsDbPureOneHundredTrialImportTest {

    private lateinit var context: Context
    private lateinit var trialDbFile: File
    private lateinit var productionDbFile: File

    private lateinit var trialDb: ShiYunDatabase
    private lateinit var importer: BatchWorkImporter
    private lateinit var prechecker: WorkImportPrechecker
    private lateinit var pipeline: OfflineDataPipeline
    private val poemsDbParser = PoemsDbParser()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()

        // 独立隔离测试数据库
        trialDbFile = context.getDatabasePath("trial_import_poemsdb_100.db")
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
            chinesePoetryParser = ChinesePoetryParser()
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
        val resource = javaClass.classLoader?.getResource("fixtures/poems_db_pure_100_records.json")
        requireNotNull(resource) { "找不到测试资源: fixtures/poems_db_pure_100_records.json" }
        val text = File(resource.toURI()).readText(Charsets.UTF_8)
        return JSONArray(text)
    }

    @Test
    fun testPurePoemsDbHundredWorks_fullPipeline_isolation_persistence_and_readBack() = runBlocking {
        val rawArray = readFixtureRawRecords()
        assertEquals("必须包含恰好100条纯正poems-db真实原始记录", 100, rawArray.length())

        // 1. 组装流水线输入 RawInputSource 列表
        val inputs = mutableListOf<RawInputSource>()
        for (i in 0 until rawArray.length()) {
            val recordObj = rawArray.getJSONObject(i)
            val sourceFile = recordObj.getString("sourceFile")
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

        // 2. 执行离线流水线
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
        assertEquals("100条独立作品聚类后生成100篇独立作品", 100, mergedWorks.size)

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
        assertEquals("100首作品预检必须100%直接就绪", 100, precheckSummary.readyCount)

        // 4. 执行物理入库
        println("=== 执行隔离试验数据库批量写入 ===")
        val batchResult = importer.importBatch(
            batchCode = "BATCH_TRIAL_POEMSDB_100_001",
            batchSource = "poems-db单一来源100首真实诗词试导入",
            items = mergedWorks,
            releaseId = null // 绝不改变正式发布指针！
        )

        println("importedCount: ${batchResult.importedCount}")
        println("skippedCount: ${batchResult.skippedCount}")
        println("needsConfirmationCount: ${batchResult.needsConfirmationCount}")
        println("failedCount: ${batchResult.failedCount}")
        println("batchStatus: ${batchResult.batchStatus}")

        assertEquals(100, batchResult.totalInputCount)
        assertEquals(100, batchResult.importedCount)
        assertEquals(0, batchResult.skippedCount)
        assertEquals(0, batchResult.needsConfirmationCount)
        assertEquals(0, batchResult.failedCount)
        assertEquals("IMPORTED", batchResult.batchStatus)

        // 5. 严格验证数据库与用户资产隔离
        println("=== 验证数据库与用户资产隔离 ===")
        assertFalse("正式数据库文件 shiyun.db 严禁被连接或写入", productionDbFile.exists())
        val pointer = trialDb.releaseDao().getReleasePointer()
        assertNull("试验批次绝不修改正式发布指针", pointer)
        val favoriteCount = trialDb.favoriteDao().getFavoriteCount()
        assertEquals("用户收藏数必须保持为0", 0, favoriteCount)

        // 6. 关闭连接并重新打开验证持久化
        println("=== 关闭数据库连接并检查物理文件 ===")
        trialDb.close()

        assertTrue("试验数据库物理文件必须真实持久化在磁盘上", trialDbFile.exists())
        val fileSize = trialDbFile.length()
        println("试验数据库文件真实大小: $fileSize 字节")
        assertTrue("试验数据库文件大小必须大于 50KB", fileSize > 50_000)

        println("=== 重新打开试验数据库并经由 RoomWorkRepository 读取 ===")
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
            assertEquals("重新打开数据库后作品数量必须严格保持 100", 100, allWorks.size)

            val allAuthors = reopenDb.categoryDao().getAllAuthors()
            println("实际入库作者总数: ${allAuthors.size}")
            assertTrue("作者档案应被有效复用，作者数在 30 到 80 之间", allAuthors.size in 30..80)

            // 特别验证项 C：绝无“佚名”或带有“anon”字样的虚假作者实体
            val invalidAuthors = allAuthors.filter {
                it.standardName == "佚名" || it.standardName == "未知" || it.authorId.contains("anon")
            }
            assertTrue("绝不允许创建名为'佚名'的假作者实体", invalidAuthors.isEmpty())

            // 特别验证项 A：正文未截断、无误删、长篇完整度深度抽检
            // A1. 白居易《长恨歌》
            val changhengeList = reopenDb.workDao().getWorksByTitle("长恨歌")
            assertFalse("《长恨歌》必须存在", changhengeList.isEmpty())
            val changhenge = repo.getWorkById(changhengeList.first().workId)
            assertNotNull(changhenge)
            assertEquals("长恨歌", changhenge?.title)
            assertEquals("白居易", changhenge?.author)
            assertEquals("唐代", changhenge?.dynasty)
            assertTrue("《长恨歌》正文首句完整", changhenge?.content?.contains("汉皇重色思倾国，御宇多年求不得。") == true)
            assertTrue("《长恨歌》正文中段完整", changhenge?.content?.contains("渔阳鼙鼓动地来，惊破霓裳羽衣曲。") == true)
            assertTrue("《长恨歌》正文尾句完整", changhenge?.content?.contains("在天愿作比翼鸟，在地愿为连理枝。") == true)
            assertTrue("《长恨歌》全文结尾完整", changhenge?.content?.contains("天长地久有时尽，此恨绵绵无绝期。") == true)
            assertTrue("长篇诗作字数应充分完整（800字以上）", (changhenge?.content?.length ?: 0) >= 800)

            // A2. 屈原《离骚》
            val lisaoList = reopenDb.workDao().getWorksByTitle("离骚")
            assertFalse("《离骚》必须存在", lisaoList.isEmpty())
            val lisao = repo.getWorkById(lisaoList.first().workId)
            assertNotNull(lisao)
            assertEquals("屈原", lisao?.author)
            assertEquals("先秦", lisao?.dynasty)
            assertTrue("《离骚》首句完整", lisao?.content?.contains("帝高阳之苗裔兮，朕皇考曰伯庸。") == true)
            assertTrue("《离骚》名句完整", lisao?.content?.contains("长太息以掩涕兮，哀民生之多艰。") == true)
            assertTrue("《离骚》名篇字数充分完整（2000字以上）", (lisao?.content?.length ?: 0) >= 2000)

            // A3. 曹操《短歌行》
            val duangeList = reopenDb.workDao().getWorksByTitle("短歌行")
            assertFalse("《短歌行》必须存在", duangeList.isEmpty())
            val duange = repo.getWorkById(duangeList.first().workId)
            assertNotNull(duange)
            assertEquals("曹操", duange?.author)
            assertTrue("《短歌行》首句完整", duange?.content?.contains("对酒当歌，人生几何！") == true || duange?.content?.contains("对酒当歌，人生几何") == true)
            assertTrue("《短歌行》尾句完整", duange?.content?.contains("周公吐哺，天下归心。") == true || duange?.content?.contains("周公吐哺，天下归心") == true)

            // A4. 李白《将进酒》
            val jiangjinjiuList = reopenDb.workDao().getWorksByTitle("将进酒")
            assertFalse("《将进酒》必须存在", jiangjinjiuList.isEmpty())
            val jiangjinjiu = repo.getWorkById(jiangjinjiuList.first().workId)
            assertNotNull(jiangjinjiu)
            assertEquals("李白", jiangjinjiu?.author)
            assertTrue("《将进酒》名句完整", jiangjinjiu?.content?.contains("黄河之水天上来") == true)
            assertTrue("《将进酒》名句完整", jiangjinjiu?.content?.contains("天生我材必有用") == true)
            assertTrue("《将进酒》名句完整", jiangjinjiu?.content?.contains("与尔同销万古愁") == true)

            // A5. 高适《别董大二首·其一》
            val bieldongdaList = allWorks.filter { it.standardTitle.contains("别董大") }
            assertFalse("《别董大》必须存在", bieldongdaList.isEmpty())
            val bieldongda = repo.getWorkById(bieldongdaList.first().workId)
            assertNotNull(bieldongda)
            assertEquals("高适", bieldongda?.author)
            assertTrue(bieldongda?.content?.contains("千里黄云白日曛") == true)
            assertTrue(bieldongda?.content?.contains("莫愁前路无知己，天下谁人不识君") == true)

            // A6. 李白《静夜思》
            val jingyesiList = reopenDb.workDao().getWorksByTitle("静夜思")
            assertFalse("《静夜思》必须存在", jingyesiList.isEmpty())
            val jingyesi = repo.getWorkById(jingyesiList.first().workId)
            assertNotNull(jingyesi)
            assertEquals("李白", jingyesi?.author)
            assertTrue(jingyesi?.content?.contains("床前明月光") == true)
            assertTrue(jingyesi?.content?.contains("低头思故乡") == true)

            // A7. 元稹《行宫》
            val xinggongList = reopenDb.workDao().getWorksByTitle("行宫")
            assertFalse("《行宫》必须存在", xinggongList.isEmpty())
            val xinggong = repo.getWorkById(xinggongList.first().workId)
            assertNotNull(xinggong)
            assertEquals("元稹", xinggong?.author)
            assertTrue(xinggong?.content?.contains("寥落古行宫") == true)
            assertTrue(xinggong?.content?.contains("白头宫女在") == true)

            // A8. 元曲：马致远《天净沙·秋思》
            val qiusiList = allWorks.filter { it.standardTitle.contains("秋思") || it.standardTitle.contains("天净沙") }
            assertFalse("马致远《天净沙·秋思》必须存在", qiusiList.isEmpty())
            val qiusi = repo.getWorkById(qiusiList.first().workId)
            assertNotNull(qiusi)
            assertEquals("马致远", qiusi?.author)
            assertTrue(qiusi?.content?.contains("枯藤老树昏鸦") == true)
            assertTrue(qiusi?.content?.contains("古道西风瘦马") == true)
            assertTrue(qiusi?.content?.contains("断肠人在天涯") == true)

            // 特别验证项 B：构字标记与特殊字 100% 原始保留，未被 HTML 清洗器误删
            // B1. 赵长卿《蝶恋花（和任路分荷花）》保留 <黑宛>
            val dielianhuaList = allWorks.filter { it.standardTitle.contains("和任路分荷花") || it.standardTitle.contains("蝶恋花") }
            assertFalse("《蝶恋花》必须存在", dielianhuaList.isEmpty())
            val dielianhua = repo.getWorkById(dielianhuaList.first().workId)
            assertNotNull(dielianhua)
            assertEquals("赵长卿", dielianhua?.author)
            assertTrue("构字标记<黑宛>必须100%完整保留在正文中", dielianhua?.content?.contains("<黑宛>") == true)

            // B2. 吴孺子《望西山》保留 <舟双>
            val wangxishanList = reopenDb.workDao().getWorksByTitle("望西山")
            assertFalse("《望西山》必须存在", wangxishanList.isEmpty())
            val wangxishan = repo.getWorkById(wangxishanList.first().workId)
            assertNotNull(wangxishan)
            assertEquals("吴孺子", wangxishan?.author)
            assertTrue("构字标记<舟双>必须100%完整保留在正文中", wangxishan?.content?.contains("<舟双>") == true)

            // B3. 张佳胤《三石篇》保留 <石含>
            val sanshipianList = reopenDb.workDao().getWorksByTitle("三石篇")
            assertFalse("《三石篇》必须存在", sanshipianList.isEmpty())
            val sanshipian = repo.getWorkById(sanshipianList.first().workId)
            assertNotNull(sanshipian)
            assertEquals("张佳胤", sanshipian?.author)
            assertTrue("构字标记<石含>必须100%完整保留在正文中", sanshipian?.content?.contains("<石含>") == true)

            // 特别验证项 C（续）：无名氏/未知作者作品规范
            val guanjuList = reopenDb.workDao().getWorksByTitle("关雎")
            assertFalse("《关雎》必须存在", guanjuList.isEmpty())
            val guanju = repo.getWorkById(guanjuList.first().workId)
            assertNotNull(guanju)
            assertEquals("关雎", guanju?.title)
            assertEquals("先秦", guanju?.dynasty)
            assertEquals("未知作者作品在领域模型中 author 必须为空字符串", "", guanju?.author)
            assertTrue(guanju?.content?.contains("关关雎鸠，在河之洲。") == true || guanju?.content?.contains("关关雎鸠，在河之洲") == true)

            // 特别验证项 D：宋词名篇词牌与标题解耦
            // D1. 苏轼《水调歌头·明月几时有》
            val shuidiaoList = allWorks.filter { it.standardTitle.contains("水调歌头") || it.standardTitle.contains("明月几时有") }
            assertFalse("苏轼《水调歌头》必须存在", shuidiaoList.isEmpty())
            val shuidiao = repo.getWorkById(shuidiaoList.first().workId)
            assertNotNull(shuidiao)
            assertEquals("苏轼", shuidiao?.author)
            assertEquals("水调歌头", shuidiao?.cipai)
            assertTrue(shuidiao?.content?.contains("明月几时有？把酒问青天") == true || shuidiao?.content?.contains("明月几时有") == true)
            assertTrue(shuidiao?.content?.contains("但愿人长久，千里共婵娟") == true)

            // D2. 李清照《声声慢·寻寻觅觅》
            val shengshengmanList = allWorks.filter { it.standardTitle.contains("声声慢") || it.standardTitle.contains("寻寻觅觅") }
            assertFalse("李清照《声声慢》必须存在", shengshengmanList.isEmpty())
            val shengshengman = repo.getWorkById(shengshengmanList.first().workId)
            assertNotNull(shengshengman)
            assertEquals("李清照", shengshengman?.author)
            assertEquals("声声慢", shengshengman?.cipai)
            assertTrue(shengshengman?.content?.contains("寻寻觅觅，冷冷清清，凄凄惨惨戚戚") == true)
            assertTrue(shengshengman?.content?.contains("这次第，怎一个愁字了得") == true)

            // D3. 辛弃疾《摸鱼儿·更能消几番风雨》
            val moyuerList = allWorks.filter { it.standardTitle.contains("摸鱼儿") || it.standardTitle.contains("更能消几番风雨") }
            assertFalse("辛弃疾《摸鱼儿》必须存在", moyuerList.isEmpty())
            val moyuer = repo.getWorkById(moyuerList.first().workId)
            assertNotNull(moyuer)
            assertEquals("辛弃疾", moyuer?.author)
            assertEquals("摸鱼儿", moyuer?.cipai)
            assertTrue(moyuer?.content?.contains("更能消、几番风雨") == true || moyuer?.content?.contains("更能消几番风雨") == true)

            // 特别验证项 E：来源追溯链接完整存在
            val changhengeLinks = reopenDb.workDao().getSourceLinksByWorkId(changhengeList.first().workId)
            assertFalse("长恨歌来源链接必须存在", changhengeLinks.isEmpty())
            assertEquals("POEMS_DB", changhengeLinks.first().sourceCode)
            assertEquals("5c22086597880d3b825c97b9", changhengeLinks.first().externalItemId)

            // ==================== 特别验证项 G：定点修复体裁分类深度核验 (100% 准确率) ====================
            val allRepoWorks = allWorks.mapNotNull { repo.getWorkById(it.workId) }
            assertEquals(100, allRepoWorks.size)

            val poemCount = allRepoWorks.count { it.genre == Genre.POEM }
            val ciCount = allRepoWorks.count { it.genre == Genre.CI }
            val quCount = allRepoWorks.count { it.genre == Genre.QU }
            val wenCount = allRepoWorks.count { it.genre == Genre.WEN }

            println("=== 100 首真实作品试导入入库体裁分布 ===")
            println("POEM (诗): $poemCount")
            println("CI (词): $ciCount")
            println("QU (曲): $quCount")
            println("WEN (文): $wenCount")

            assertEquals("诗(POEM)作品总数应为 71 首", 71, poemCount)
            assertEquals("宋词(CI)作品总数应为 23 首", 23, ciCount)
            assertEquals("元曲(QU)作品总数应为 4 首", 4, quCount)
            assertEquals("文言散文(WEN)作品总数应为 2 首", 2, wenCount)

            // G1. 散文 (2 首)
            val zhenzhouList = allWorks.filter { it.standardTitle == "真州东园记" }
            assertFalse("《真州东园记》必须存在", zhenzhouList.isEmpty())
            val zhenzhou = repo.getWorkById(zhenzhouList.first().workId)
            assertEquals(Genre.WEN, zhenzhou?.genre)
            assertEquals("真州东园记", zhenzhou?.title)
            assertEquals("欧阳修", zhenzhou?.author)

            val xuedouList = allWorks.filter { it.standardTitle == "雪窦游志" }
            assertFalse("《雪窦游志》必须存在", xuedouList.isEmpty())
            val xuedou = repo.getWorkById(xuedouList.first().workId)
            assertEquals(Genre.WEN, xuedou?.genre)
            assertEquals("雪窦游志", xuedou?.title)
            assertEquals("邓牧", xuedou?.author)

            // G2. 元曲 (4 首)
            // 马致远《天净沙·秋思》
            assertEquals(Genre.QU, qiusi?.genre)
            assertEquals("天净沙", qiusi?.cipai)
            assertEquals("秋思", qiusi?.title)

            // 冯子振《鹦鹉曲·都门感旧》
            val yingwuList = allWorks.filter { it.standardTitle.contains("都门感旧") }
            assertFalse("冯子振《鹦鹉曲·都门感旧》必须存在", yingwuList.isEmpty())
            val yingwu = repo.getWorkById(yingwuList.first().workId)
            assertEquals(Genre.QU, yingwu?.genre)
            assertEquals("鹦鹉曲", yingwu?.cipai)
            assertEquals("都门感旧", yingwu?.title)
            assertEquals("冯子振", yingwu?.author)

            // 顾德润《醉高歌带摊破喜春来·旅中》 (带过曲)
            val zuigaogeList = allWorks.filter { it.standardTitle.contains("旅中") }
            assertFalse("顾德润《醉高歌带摊破喜春来·旅中》必须存在", zuigaogeList.isEmpty())
            val zuigaoge = repo.getWorkById(zuigaogeList.first().workId)
            assertEquals(Genre.QU, zuigaoge?.genre)
            assertEquals("醉高歌带摊破喜春来", zuigaoge?.cipai)
            assertEquals("旅中", zuigaoge?.title)
            assertEquals("顾德润", zuigaoge?.author)

            // 贯云石《清江引·立春》
            val qingjiangList = allWorks.filter { it.standardTitle.contains("立春") }
            assertFalse("贯云石《清江引·立春》必须存在", qingjiangList.isEmpty())
            val qingjiang = repo.getWorkById(qingjiangList.first().workId)
            assertEquals(Genre.QU, qingjiang?.genre)
            assertEquals("清江引", qingjiang?.cipai)
            assertEquals("立春", qingjiang?.title)
            assertEquals("贯云石", qingjiang?.author)

            // G3. 宋词括号格式与 9 个新增词牌 (10 首)
            // 赵长卿《蝶恋花（和任路分荷花）》 (括号格式)
            assertEquals(Genre.CI, dielianhua?.genre)
            assertEquals("蝶恋花", dielianhua?.cipai)
            assertEquals("和任路分荷花", dielianhua?.title)

            // 9个补充词牌逐一验证
            val tianrenjiao = allRepoWorks.firstOrNull { it.cipai == "殢人娇" }
            assertNotNull("殢人娇词作必须识别", tianrenjiao)
            assertEquals(Genre.CI, tianrenjiao?.genre)
            assertEquals("后亭梅花开有感", tianrenjiao?.title)

            val taoyuanyiguren = allRepoWorks.firstOrNull { it.cipai == "桃源忆故人" }
            assertNotNull("桃源忆故人词作必须识别", taoyuanyiguren)
            assertEquals(Genre.CI, taoyuanyiguren?.genre)
            assertEquals("暮春", taoyuanyiguren?.title)

            val yueshangguazhou = allRepoWorks.firstOrNull { it.cipai == "月上瓜洲" }
            assertNotNull("月上瓜洲词作必须识别", yueshangguazhou)
            assertEquals(Genre.CI, yueshangguazhou?.genre)

            val fengxianbei = allRepoWorks.firstOrNull { it.cipai == "凤衔杯" }
            assertNotNull("凤衔杯词作必须识别", fengxianbei)
            assertEquals(Genre.CI, fengxianbei?.genre)

            val ruizhegu = allRepoWorks.firstOrNull { it.cipai == "瑞鹧鸪" }
            assertNotNull("瑞鹧鸪词作必须识别", ruizhegu)
            assertEquals(Genre.CI, ruizhegu?.genre)

            val lvluoqun = allRepoWorks.firstOrNull { it.cipai == "绿罗裙" }
            assertNotNull("绿罗裙词作必须识别", lvluoqun)
            assertEquals(Genre.CI, lvluoqun?.genre)

            val yefeique = allRepoWorks.firstOrNull { it.cipai == "夜飞鹊" }
            assertNotNull("夜飞鹊词作必须识别", yefeique)
            assertEquals(Genre.CI, yefeique?.genre)

            val leijiangyue = allRepoWorks.firstOrNull { it.cipai == "酹江月" }
            assertNotNull("酹江月词作必须识别", leijiangyue)
            assertEquals(Genre.CI, leijiangyue?.genre)

            val yuyouchunshui = allRepoWorks.firstOrNull { it.cipai == "鱼游春水" }
            assertNotNull("鱼游春水词作必须识别", yuyouchunshui)
            assertEquals(Genre.CI, yuyouchunshui?.genre)

            // G4. 律诗排除验证 (2 首组诗)
            val wangtingguiList = allWorks.filter { it.standardTitle.contains("送胡邦衡之新州贬所") }
            assertFalse("王庭珪组诗必须存在", wangtingguiList.isEmpty())
            val wangtinggui = repo.getWorkById(wangtingguiList.first().workId)
            assertEquals("包含·其一的律诗坚决识别为诗(POEM)", Genre.POEM, wangtinggui?.genre)
            assertNull("律诗不得产生词牌", wangtinggui?.cipai)

            val zhengsixiaoList = allWorks.filter { it.standardTitle.contains("德佑二年岁旦") }
            assertFalse("郑思肖组诗必须存在", zhengsixiaoList.isEmpty())
            val zhengsixiao = repo.getWorkById(zhengsixiaoList.first().workId)
            assertEquals("包含·其二的律诗坚决识别为诗(POEM)", Genre.POEM, zhengsixiao?.genre)
            assertNull("律诗不得产生词牌", zhengsixiao?.cipai)

            // 特别验证项 F：次轮重复导入幂等性与防重核验
            println("=== 执行次轮重复导入防重测试 ===")
            val round2Importer = BatchWorkImporter(reopenDb)
            val round2Result = round2Importer.importBatch(
                batchCode = "BATCH_TRIAL_POEMSDB_100_002",
                batchSource = "poems-db单一来源100首真实诗词次轮重复测试",
                items = mergedWorks,
                releaseId = null
            )

            println("round2 totalInput: ${round2Result.totalInputCount}")
            println("round2 imported: ${round2Result.importedCount}")
            println("round2 skipped: ${round2Result.skippedCount}")
            println("round2 failed: ${round2Result.failedCount}")

            assertEquals(100, round2Result.totalInputCount)
            assertEquals(0, round2Result.importedCount)
            assertEquals(100, round2Result.skippedCount)
            assertEquals(0, round2Result.needsConfirmationCount)
            assertEquals(0, round2Result.failedCount)

            // 数据库作品总数严格恒定为 100
            val postCheckWorks = reopenDb.workDao().getAllActiveWorks()
            assertEquals("次轮重复导入后库中作品数必须严格恒定为 100", 100, postCheckWorks.size)

        } finally {
            reopenDb.close()
        }
    }
}
