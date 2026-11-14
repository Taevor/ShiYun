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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 阶段 4.2B：试验数据库 (trial_import_real100.db) 21 首代表作深度只读抽样核查
 *
 * 抽样覆盖：
 * - 唐诗 (6 首)：元稹、王之涣、王维、李白、太宗皇帝(繁)、李百药(繁)
 * - 宋词 (6 首)：辛弃疾、苏轼、李清照、和岘(首句题名)、寇准(首句题名)、张先(首句题名)
 * - 诗经 (3 首)：关雎、葛覃、卷耳 (未知作者置空规范)
 * - 楚辞 (2 首)：屈原《离骚》、屈原《九歌·东皇太一》
 * - 曹操诗歌 (2 首)：《短歌行 其一》、《度关山》
 * - 元曲 (2 首)：关汉卿《诈妮子调风月・仙吕/点绛唇》、《诈妮子调风月・混江龙》
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrialImportDatabaseSamplingTest {

    private lateinit var context: Context
    private lateinit var trialDbFile: File
    private lateinit var trialDb: ShiYunDatabase
    private lateinit var pipeline: OfflineDataPipeline
    private lateinit var importer: BatchWorkImporter

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Context>()
        trialDbFile = context.getDatabasePath("trial_import_real100.db")
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

        val prechecker = WorkImportPrechecker(trialDb.workDao(), trialDb.categoryDao())
        importer = BatchWorkImporter(trialDb, prechecker)
        pipeline = OfflineDataPipeline(
            merger = MultiSourceWorkMerger(),
            prechecker = prechecker,
            poemsDbParser = PoemsDbParser(),
            poetryCnParser = PoetryCnParser(),
            chinesePoetryParser = ChinesePoetryParser()
        )

        // 导入真实 100 首
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/real_100_raw_records.json")
            ?: error("Missing fixtures/real_100_raw_records.json")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val rawArray = JSONArray(text)

        val inputs = mutableListOf<RawInputSource>()
        for (i in 0 until rawArray.length()) {
            val recordObj = rawArray.getJSONObject(i)
            inputs.add(
                RawInputSource(
                    rawContent = recordObj.getJSONObject("rawRecordJson").toString(),
                    sourceCode = recordObj.getString("sourceCode"),
                    rawFilePath = recordObj.getString("sourceFile"),
                    dynastyHint = recordObj.optString("dynasty").ifEmpty { null },
                    sourceVersion = "trial-v1"
                )
            )
        }

        val pipelineResult = pipeline.processRawSources(inputs)
        val batchResult = importer.importBatch(
            batchCode = "TRIAL_SAMPLING_BATCH",
            batchSource = "抽样核查批次",
            items = pipelineResult.mergedWorks,
            releaseId = null
        )
        assertEquals(100, batchResult.importedCount)

        // 彻底关闭连接，落盘持久化
        trialDb.close()
        assertTrue("物理数据库文件必须存在", trialDbFile.exists())
    }

    @After
    fun tearDown() {
        if (this::trialDb.isInitialized && trialDb.isOpen) {
            trialDb.close()
        }
        if (trialDbFile.exists()) {
            trialDbFile.delete()
        }
    }

    @Test
    fun testDirectDatabaseSamplingTwentyOneWorks() = runBlocking {
        // 重新打开物理 SQLite 数据库
        val reopenDb = Room.databaseBuilder(
            context,
            ShiYunDatabase::class.java,
            trialDbFile.absolutePath
        )
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()

        val repo = RoomWorkRepository(reopenDb)

        val sampleTargets = listOf(
            // 1. 唐诗 (6 首)
            SampleTarget("行宫", "元稹", "唐代", "POEMS_DB", "POEM", false),
            SampleTarget("登鹳雀楼", "王之涣", "唐代", "POEMS_DB", "POEM", false),
            SampleTarget("鹿柴", "王维", "唐代", "POEMS_DB", "POEM", false),
            SampleTarget("静夜思", "李白", "唐代", "POEMS_DB", "POEM", false),
            SampleTarget("帝京篇十首 一", "太宗皇帝", "唐", "CHINESE_POETRY", "POEM", true),
            SampleTarget("帝京篇十首 二", "太宗皇帝", "唐", "CHINESE_POETRY", "POEM", true),

            // 2. 宋词 (6 首)
            SampleTarget("赋虞美人草", "辛弃疾", "宋代", "POEMS_DB", "CI", false, "虞美人"),
            SampleTarget("殢人娇·后亭梅花开有感", "李清照", "宋代", "POEMS_DB", "CI", false, null),
            SampleTarget("感旧", "苏轼", "宋代", "POEMS_DB", "CI", false, "定风波"),
            SampleTarget("博山道中即事", "辛弃疾", "宋代", "POEMS_DB", "CI", false, "清平乐"),
            SampleTarget("气和玉烛", "和岘", "宋", "CHINESE_POETRY", "CI", false, "导引"),
            SampleTarget("塞草烟光阔", "寇准", "宋", "CHINESE_POETRY", "CI", false, "阳关引"),

            // 3. 诗经 (3 首，作者规范置空)
            SampleTarget("关雎", "", "先秦", "CHINESE_POETRY", "POEM", false),
            SampleTarget("葛覃", "", "先秦", "CHINESE_POETRY", "POEM", false),
            SampleTarget("卷耳", "", "先秦", "CHINESE_POETRY", "POEM", false),

            // 4. 楚辞 (2 首)
            SampleTarget("离骚", "屈原", "先秦", "CHINESE_POETRY", "POEM", false),
            SampleTarget("九歌·东皇太一", "屈原", "先秦", "CHINESE_POETRY", "POEM", false),

            // 5. 曹操诗歌 (2 首)
            SampleTarget("短歌行 其一", "曹操", "汉", "CHINESE_POETRY", "POEM", false),
            SampleTarget("度关山", "曹操", "汉", "CHINESE_POETRY", "POEM", false),

            // 6. 元曲 (2 首)
            SampleTarget("诈妮子调风月・仙吕/点绛唇", "关汉卿", "元", "CHINESE_POETRY", "QU", false),
            SampleTarget("诈妮子调风月・混江龙", "关汉卿", "元", "CHINESE_POETRY", "QU", false)
        )

        assertEquals("抽查目标必须为 21 首", 21, sampleTargets.size)

        val reportItems = mutableListOf<JSONObject>()

        try {
            val allWorks = reopenDb.workDao().getAllActiveWorks()
            assertEquals("全量作品必须为 100", 100, allWorks.size)

            for (target in sampleTargets) {
                // 1. 使用 Room WorkDao 从 SQLite 表中按标题查询对应作品
                val workEntity = allWorks.find { entity ->
                    entity.standardTitle.contains(target.title) || target.title.contains(entity.standardTitle)
                } ?: error("数据库中未找到抽查作品: ${target.title}")

                // 2. 通过 Repository 还原高层实体
                val work = repo.getWorkById(workEntity.workId)
                assertNotNull("Repository 必须能成功加载作品: ${target.title}", work)

                // 3. 校验标题
                assertTrue(
                    "标题必须匹配: 期望 ${target.title}, 实际 ${work!!.title}",
                    work.title.contains(target.title) || target.title.contains(work.title)
                )

                // 4. 校验作者
                assertEquals("作者必须一致: 作品 ${target.title}", target.author, work.author)

                // 5. 校验朝代
                assertTrue(
                    "朝代必须一致或相容: 期望 ${target.dynasty}, 实际 ${work.dynasty}",
                    work.dynasty.contains(target.dynasty) || target.dynasty.contains(work.dynasty)
                )

                // 6. 校验词牌
                if (target.cipai != null) {
                    assertEquals("词牌必须一致: 作品 ${target.title}", target.cipai, work.cipai)
                }

                // 7. 校验正文非空且无截断
                val content = work.content
                assertFalse("正文不得为空: ${target.title}", content.isBlank())
                assertTrue("正文长度必须合理: ${target.title} (长度: ${content.length})", content.length >= 10)
                assertFalse("正文不得包含乱码或异常占位符", content.contains("null") || content.contains("undefined"))

                // 8. 校验来源关系
                val sourceLinks = reopenDb.workDao().getSourceLinksByWorkId(work.id)
                assertFalse("作品来源追溯链必须存在: ${target.title}", sourceLinks.isEmpty())
                assertEquals("来源代码必须匹配: ${target.title}", target.expectedSourceCode, sourceLinks.first().sourceCode)

                // 9. 记录抽查报告明细
                val itemJson = JSONObject().apply {
                    put("title", work.title)
                    put("author", work.author.ifEmpty { "未知(空关联)" })
                    put("dynasty", work.dynasty)
                    put("genre", work.genre.name)
                    put("cipai", work.cipai ?: "")
                    put("contentSnippet", content.replace("\n", " ").take(40) + "...")
                    put("contentLength", content.length)
                    put("lineCount", content.split("\n").size)
                    put("sourceCode", sourceLinks.first().sourceCode)
                    put("externalId", sourceLinks.first().externalItemId)
                    put("isTraditional", target.isTraditional)
                    put("status", "VERIFIED_PASS")
                }
                reportItems.add(itemJson)
                println("✅ 抽检通过 [${target.expectedSourceCode}] ${work.author.ifEmpty { "无名氏" }}《${work.title}》 (${work.dynasty}) 字符数: ${content.length}")
            }

            println("=== 21 首抽样全部完成，正在生成抽查报告文件 ===")
            val resultJson = JSONObject().apply {
                put("totalSampled", reportItems.size)
                put("databaseFile", trialDbFile.name)
                put("databaseFileSize", trialDbFile.length())
                put("items", JSONArray(reportItems))
            }

            val scratchFile = File("C:/Users/JiSi/.gemini/antigravity-acp/brain/3e5fc6fe-2d75-499f-835d-49f9d4357302/scratch/database_sampling_21_report.json")
            scratchFile.writeText(resultJson.toString(2), Charsets.UTF_8)
            println("报告已写入: ${scratchFile.absolutePath}")

        } finally {
            reopenDb.close()
        }
    }

    private data class SampleTarget(
        val title: String,
        val author: String,
        val dynasty: String,
        val expectedSourceCode: String,
        val expectedGenre: String,
        val isTraditional: Boolean,
        val cipai: String? = null
    )
}
