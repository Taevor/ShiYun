package com.shi.yun.data.local.importer.parser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.importer.WorkImportPrechecker
import com.shi.yun.data.local.importer.merger.MultiSourceWorkMerger
import com.shi.yun.data.local.importer.merger.SourceWorkCandidate
import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.PrecheckDisposition
import com.shi.yun.data.local.importer.model.TitleSourceType
import com.shi.yun.data.local.importer.pipeline.OfflineDataPipeline
import com.shi.yun.data.local.importer.pipeline.RawInputSource
import com.shi.yun.data.local.importer.pipeline.RawRecordArchive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 阶段 4.2B 四项定点修复专项验证套件 (Phase42BFixTest)
 *
 * 针对核验报告指出的四大核心问题进行严密定点验证：
 * 1. 宋词作品身份与标题处理（防误合并、词牌解耦、保守正文聚类）；
 * 2. 古诗文网来源凭证严格校验（防子串冒充、合法路径与域名核验）；
 * 3. 原始文本保真度（空行与空格保留、rawContent 区分）；
 * 4. 完整原始数据归档与四项独立追溯指标核算。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase42BFixTest {

    private lateinit var db: ShiYunDatabase
    private lateinit var prechecker: WorkImportPrechecker
    private lateinit var pipeline: OfflineDataPipeline
    private lateinit var merger: MultiSourceWorkMerger
    private lateinit var chinesePoetryParser: ChinesePoetryParser
    private lateinit var poemsDbParser: PoemsDbParser

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShiYunDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()
        prechecker = WorkImportPrechecker(db.workDao(), db.categoryDao())
        merger = MultiSourceWorkMerger()
        chinesePoetryParser = ChinesePoetryParser()
        poemsDbParser = PoemsDbParser()
        pipeline = OfflineDataPipeline(
            merger = merger,
            prechecker = prechecker,
            chinesePoetryParser = chinesePoetryParser,
            poemsDbParser = poemsDbParser
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // 一、宋词作品身份识别与防误合并验证
    // =========================================================================

    @Test
    fun testSameAuthor_sameCipai_differentContent_areNotMerged() {
        // 苏轼的两首不同《水调歌头》
        // 作品 1：《水调歌头·明月几时有》
        val song1 = """
        [
          {
            "author": "苏轼",
            "rhythmic": "水调歌头",
            "paragraphs": ["明月几时有，把酒问青天。", "不知天上宫阙，今夕是何年。"],
            "id": "sushi-shuidiao-001"
          },
          {
            "author": "苏轼",
            "rhythmic": "水调歌头",
            "paragraphs": ["落日绣帘卷，猩血染罗巾。", "沉吟双泪落，回首望归津。"],
            "id": "sushi-shuidiao-002"
          }
        ]
        """.trimIndent()

        val input = RawInputSource(rawContent = song1, sourceCode = "CHINESE_POETRY", rawFilePath = "宋词/ci.song.test.json")
        val result = pipeline.processRawSources(listOf(input))

        // 关键断言：同作者、同词牌、不同正文，绝不能合并为同一首作品！
        assertEquals(2, result.mergedWorks.size)
        assertEquals(2, result.mergeAudits.size)

        val work1 = result.mergedWorks[0]
        val work2 = result.mergedWorks[1]
        assertNotEquals(work1.content, work2.content)
        // 词牌均记录为水调歌头
        assertEquals("水调歌头", work1.cipai)
        assertEquals("水调歌头", work2.cipai)
    }

    @Test
    fun testSameAuthor_sameCipai_identicalContent_crossSources_canMerge() {
        // 跨来源候选：同一首《水调歌头·明月几时有》
        // 来源 1：chinese-poetry (无独立 title，仅有 rhythmic，生成首句默认词题)
        val cpJson = """
        [
          {
            "author": "苏轼",
            "rhythmic": "水调歌头",
            "paragraphs": ["明月几时有，把酒问青天。", "不知天上宫阙，今夕是何年。"],
            "id": "cp-shuidiao-001"
          }
        ]
        """.trimIndent()

        // 来源 2：poems-db (有独立 title: "水调歌头·明月几时有"，词牌词题拆分后词题为 "明月几时有")
        val pdbJson = """
        [
          {
            "_id": "pdb-shuidiao-001",
            "title": "水调歌头·明月几时有",
            "author": "苏轼",
            "dynasty": "宋代",
            "content": "明月几时有？把酒问青天。\n不知天上宫阙，今夕是何年。",
            "translation": "明月从何时出现？我端酒问青天。"
          }
        ]
        """.trimIndent()

        val inputs = listOf(
            RawInputSource(rawContent = cpJson, sourceCode = "CHINESE_POETRY", rawFilePath = "宋词/ci.song.0.json"),
            RawInputSource(rawContent = pdbJson, sourceCode = "POEMS_DB", rawFilePath = "fixtures/poems_db.json")
        )
        val result = pipeline.processRawSources(inputs)

        // 关键断言：同作者、相同词牌、相同正文，跨来源精准识别为同一作品并合并
        assertEquals(1, result.mergedWorks.size)
        val merged = result.mergedWorks.first()

        // 标题拆分出独立词题 "明月几时有"，词牌独立为 "水调歌头"，词题类型为 SOURCE_TITLE
        assertEquals("明月几时有", merged.title)
        assertEquals("水调歌头", merged.cipai)
        assertEquals(TitleSourceType.SOURCE_TITLE, merged.titleSourceType)
        assertEquals("苏轼", merged.author?.name)
        assertNotNull(merged.translation) // 译文来自 poems-db
    }

    @Test
    fun testTitlelessSongCi_doesNotFabricateTitle_markedNeedsConfirmationInPrecheck() {
        // 正文损坏或无法提取首句（例如纯标点或无有效字符）的宋词
        val songJson = """
        [
          {
            "author": "和岘",
            "rhythmic": "导引",
            "paragraphs": ["，，，！！！"],
            "id": "hexian-daoyin-001"
          }
        ]
        """.trimIndent()

        val parsed = chinesePoetryParser.parseJson(songJson)
        assertEquals(1, parsed.size)
        val candidate = parsed.first()

        // 关键断言：无法可靠提取首句时 title 必须保持为 null，类型为 UNKNOWN
        assertNull("无法可靠提取首句时 title 必须保持为 null", candidate.title)
        assertEquals("导引", candidate.cipai)
        assertEquals(TitleSourceType.UNKNOWN, candidate.titleSourceType)

        val input = RawInputSource(rawContent = songJson, sourceCode = "CHINESE_POETRY", rawFilePath = "宋词/ci.song.0.json")
        val result = pipeline.processRawSources(listOf(input))

        // 预检断言：不编造假标题，保留待确认 (NEEDS_CONFIRMATION) 状态
        val summary = result.precheckSummary
        assertNotNull(summary)
        assertEquals(1, summary!!.totalCount)
        assertEquals(1, summary.needsConfirmationCount)
        assertEquals(PrecheckDisposition.NEEDS_CONFIRMATION, summary.items[0].disposition)
        assertTrue(summary.items[0].reason.contains("作品无独立标题且无法可靠提取正文首句（仅含词牌 [导引]）"))
    }

    // =========================================================================
    // 二、古诗文网来源凭证严格校验验证
    // =========================================================================

    @Test
    fun testGushiwenSourceVerifier_officialUrlsPass_allOthersRejected() {
        // 1. 官方确认域名下的有效作品链接 -> 通过
        assertTrue(GushiwenSourceVerifier.isValidGushiwenWorkUrl("https://www.gushiwen.cn/shiwenv_4567.aspx"))
        assertTrue(GushiwenSourceVerifier.isValidGushiwenWorkUrl("http://gushiwen.org/shiwenv_8901.aspx"))
        assertTrue(GushiwenSourceVerifier.isValidGushiwenWorkUrl("https://so.gushiwen.cn/shiwenv_1122.aspx"))
        assertTrue(GushiwenSourceVerifier.isValidGushiwenWorkUrl("https://www.gushiwen.cn/guwen/book_1.aspx"))

        // 2. 非官方域名中包含 gushiwen 字符串 -> 拒绝
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl("https://myblog.com/gushiwen/shiwenv_123.aspx"))
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl("https://evil-gushiwen.cn/shiwenv_123.aspx"))
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl("https://gushiwen.cn.fake.com/shiwenv_123.aspx"))

        // 3. 普通博客参数中包含 gushiwen -> 拒绝
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl("https://blog.csdn.net/article?ref=gushiwen"))
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl("https://zhihu.com/question/123?utm_source=gushiwen"))

        // 4. 无效 URL 或非法协议 -> 拒绝
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl("not-a-valid-url::gushiwen"))
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl("ftp://www.gushiwen.cn/shiwenv_1.aspx"))

        // 5. 空 URL 或 null -> 拒绝
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl(""))
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl("   "))
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl(null))

        // 6. 其他不能确认的来源链接（非作品链接或无关网站） -> 拒绝
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl("https://github.com/chinese-poetry/chinese-poetry"))
        assertFalse(GushiwenSourceVerifier.isValidGushiwenWorkUrl("https://www.gushiwen.cn/about"))
    }

    @Test
    fun testPoemsDbParser_rejectsFakeGushiwenLink_noConfirmedFlag() {
        val fakeLinkRecord = """
        {
          "_id": "fake_link_001",
          "title": "测试诗作",
          "author": "李白",
          "content": "床前明月光，疑是地上霜。",
          "translation": "白话翻译内容",
          "sourceLink": "https://myblog.com/article?ref=gushiwen_copy"
        }
        """.trimIndent()

        val parsed = poemsDbParser.parseJson(fakeLinkRecord)
        assertEquals(1, parsed.size)
        val candidate = parsed.first()

        assertFalse("包含 gushiwen 子串的非官方链接绝不能标记为 isGushiwenConfirmed", candidate.isGushiwenConfirmed)
    }

    @Test
    fun testPoemsDbParser_acceptsOfficialGushiwenLink_confirmedFlagTrue() {
        val realLinkRecord = """
        {
          "_id": "real_link_001",
          "title": "静夜思",
          "author": "李白",
          "content": "床前明月光，疑是地上霜。",
          "translation": "明亮的月光洒在床前，好像地上铺了一层白霜。",
          "sourceLink": "https://www.gushiwen.cn/shiwenv_1234.aspx"
        }
        """.trimIndent()

        val parsed = poemsDbParser.parseJson(realLinkRecord)
        assertEquals(1, parsed.size)
        val candidate = parsed.first()

        assertTrue("官方有效作品链接必须标记为 isGushiwenConfirmed = true", candidate.isGushiwenConfirmed)
    }

    // =========================================================================
    // 三、原始正文保留与空格空行验证
    // =========================================================================

    @Test
    fun testRawContentPreservedWithEmptyLinesAndSpaces() {
        val textWithSpaces = "  \n  床前明月光，  \n\n  疑是地上霜。  \n  "
        val recordJson = """
        {
          "_id": "space_test_001",
          "title": "静夜思",
          "author": "李白",
          "content": "  \n  床前明月光，  \n\n  疑是地上霜。  \n  "
        }
        """.trimIndent()

        val parsed = poemsDbParser.parseJson(recordJson)
        assertEquals(1, parsed.size)
        val candidate = parsed.first()

        // 原始正文必须完整保留原样（包括换行与空格）
        assertEquals(textWithSpaces, candidate.rawContent)
        // 候选清洗正文允许适当 trim 以便排版展示
        assertNotNull(candidate.content)
        assertFalse(candidate.content!!.startsWith("  \n"))
    }

    // =========================================================================
    // 四、完整原始数据追溯与四项独立指标核算验证
    // =========================================================================

    @Test
    fun testPipelineStats_strictlySeparatesFourTraceabilityMetrics() {
        val tangJson = """
        [
          {
            "author": "李白",
            "title": "静夜思",
            "paragraphs": ["床前明月光，疑是地上霜。"],
            "id": "tang-libai-001"
          }
        ]
        """.trimIndent()

        val input = RawInputSource(
            rawContent = tangJson,
            sourceCode = "CHINESE_POETRY",
            rawFilePath = "全唐诗/poet.tang.0.json",
            dynastyHint = "唐",
            sourceVersion = "git-commit-abc1234"
        )

        val result = pipeline.processRawSources(listOf(input))
        val stats = result.stats

        // 严格检验四项独立指标绝不混淆
        assertEquals(100.0, stats.parsedSuccessRate, 0.001)       // 解析成功率
        assertEquals(100.0, stats.rawFileArchiveRate, 0.001)      // 原始文件归档率
        assertEquals(100.0, stats.rawRecordLocatableRate, 0.001)  // 原始记录定位率
        assertEquals(100.0, stats.sourceIndexPresenceRate, 0.001) // 来源索引非空率

        // 归档检索验证：能准确定位完整原始 JSON
        val archive = result.archive
        assertTrue(archive.hasRecord("CHINESE_POETRY", "tang-libai-001"))
        val record = archive.findRawRecord("CHINESE_POETRY", "tang-libai-001")
        assertNotNull(record)
        assertTrue(record!!.rawJsonPayload.contains("tang-libai-001"))
        assertTrue(record.rawJsonPayload.contains("李白"))
    }
}
