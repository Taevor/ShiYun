package com.shi.yun.data.local.importer.parser

import com.shi.yun.data.local.importer.merger.MultiSourceWorkMerger
import com.shi.yun.data.local.importer.model.TitleSourceType
import com.shi.yun.data.local.importer.pipeline.OfflineDataPipeline
import com.shi.yun.data.local.importer.pipeline.RawInputSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 真实原始数据文件读取与解析器集成测试 (RealRawDataParserTest)
 *
 * 专门验证阶段 4.2B 真实解析链路与无损追溯：
 * 1. 从 test/resources/fixtures/ 读取 4 个真实样本文件；
 * 2. 验证三个独立解析器 (PoemsDbParser, PoetryCnParser, ChinesePoetryParser) 的真实解析能力；
 * 3. 验证端到端流水线 (OfflineDataPipeline)：真实文件 -> 对应解析器 -> SourceWorkCandidate -> MultiSourceWorkMerger -> WorkImportItem；
 * 4. 统计并验证解析成功率、追溯信息留存率与各项预检指标。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealRawDataParserTest {

    private lateinit var poemsDbParser: PoemsDbParser
    private lateinit var poetryCnParser: PoetryCnParser
    private lateinit var chinesePoetryParser: ChinesePoetryParser
    private lateinit var pipeline: OfflineDataPipeline

    @Before
    fun setUp() {
        poemsDbParser = PoemsDbParser()
        poetryCnParser = PoetryCnParser()
        chinesePoetryParser = ChinesePoetryParser()
        pipeline = OfflineDataPipeline(
            merger = MultiSourceWorkMerger(),
            poemsDbParser = poemsDbParser,
            poetryCnParser = poetryCnParser,
            chinesePoetryParser = chinesePoetryParser
        )
    }

    private fun readFixtureText(fileName: String): String {
        val resource = javaClass.classLoader?.getResource("fixtures/$fileName")
        requireNotNull(resource) { "找不到测试 fixture 资源: fixtures/$fileName" }
        return File(resource.toURI()).readText(Charsets.UTF_8)
    }

    @Test
    fun testPoemsDbParser_realSamples_parsesCorrectly() {
        val rawJson = readFixtureText("poems_db_real_samples.json")
        val candidates = poemsDbParser.parseJson(
            jsonStr = rawJson,
            rawFilePath = "fixtures/poems_db_real_samples.json",
            sourceVersion = "mongo-export-v1"
        )

        assertEquals(5, candidates.size)

        // 验证第一条：李白《渡荆门送别》
        val libai = candidates[0]
        assertEquals("渡荆门送别", libai.title)
        assertEquals("李白", libai.author?.name)
        assertEquals("唐代", libai.dynasty)
        assertEquals("五言律诗", libai.genre)
        assertEquals(PoemsDbParser.SOURCE_NAME, libai.sourceName)
        assertEquals(PoemsDbParser.SOURCE_CODE, libai.sourceCode)
        assertEquals("fixtures/poems_db_real_samples.json", libai.rawFilePath)
        assertTrue(libai.content!!.contains("渡远荆门外，来从楚国游"))
        assertNotNull(libai.translation)
        assertNotNull(libai.annotations)
        assertNotNull(libai.appreciation)
        assertEquals(4, libai.tags.size)
        // 原始 JSON 留存完整性验证
        assertNotNull(libai.rawPayload)
        assertTrue(libai.rawPayload!!.contains("5c22086497880d3b825c968f"))
        // 来源出处严格校验：官方有效链接判定通过
        assertTrue(libai.isGushiwenConfirmed)

        // 验证第二条：苏轼《水调歌头·明月几时有》（词牌与词题拆分）
        val sushi = candidates[1]
        assertEquals("明月几时有", sushi.title)
        assertEquals("水调歌头·明月几时有", sushi.rawTitle)
        assertEquals("水调歌头", sushi.cipai)
        assertEquals(TitleSourceType.SOURCE_TITLE, sushi.titleSourceType)
        assertEquals("苏轼", sushi.author?.name)
        assertEquals("宋代", sushi.dynasty)
        assertEquals("CI", sushi.genre)
        assertTrue(sushi.isGushiwenConfirmed)
    }

    @Test
    fun testPoetryCnParser_realSamples_parsesCorrectly() {
        val rawJson = readFixtureText("poetry_cn_real_samples.json")
        val candidates = poetryCnParser.parseJson(
            jsonStr = rawJson,
            rawFilePath = "fixtures/poetry_cn_real_samples.json",
            sourceVersion = "git-v1.0"
        )

        assertEquals(3, candidates.size)

        // 验证第一条：杜甫《望岳》
        val dufu = candidates[0]
        assertEquals("望岳", dufu.title)
        assertEquals("杜甫", dufu.author?.name)
        assertEquals("唐", dufu.dynasty)
        assertEquals("五言古诗", dufu.genre)
        assertEquals(PoetryCnParser.SOURCE_NAME, dufu.sourceName)
        assertEquals(PoetryCnParser.SOURCE_CODE, dufu.sourceCode)
        assertTrue(dufu.content!!.contains("岱宗夫如何？齐鲁青未了"))
        assertNotNull(dufu.translation)
        assertNotNull(dufu.annotations)
        assertNotNull(dufu.appreciation)
        assertNotNull(dufu.rawPayload)
    }

    @Test
    fun testChinesePoetryParser_tangPoetry_parsesCorrectly() {
        val rawJson = readFixtureText("chinese_poetry_tang_real_samples.json")
        val candidates = chinesePoetryParser.parseJson(
            jsonStr = rawJson,
            rawFilePath = "全唐诗/poet.tang.0.json",
            dynastyHint = "唐",
            sourceVersion = "git-v2.0"
        )

        assertEquals(4, candidates.size)

        // 第一条：太宗皇帝《帝京篇十首 一》
        val taizong = candidates[0]
        assertEquals("帝京篇十首 一", taizong.title)
        assertEquals("太宗皇帝", taizong.author?.name)
        assertEquals("唐", taizong.dynasty)
        assertEquals("SHI", taizong.genre)
        assertEquals("CHINESE_POETRY", taizong.sourceCode)
        assertTrue(taizong.content!!.contains("秦川雄帝宅，函谷壯皇居"))
        // chinese-poetry 纯正文字库无辅助内容，验证字段严格置空
        assertEquals(null, taizong.translation)
        assertEquals(null, taizong.annotations)
        assertEquals(null, taizong.appreciation)
        assertEquals(null, taizong.background)
        assertNotNull(taizong.rawPayload)
    }

    @Test
    fun testChinesePoetryParser_songCi_parsesRhythmicAsCipaiAndGeneratesFirstLineTitle() {
        val rawJson = readFixtureText("chinese_poetry_song_ci_real_samples.json")
        val candidates = chinesePoetryParser.parseJson(
            jsonStr = rawJson,
            rawFilePath = "宋词/ci.song.0.json",
            dynastyHint = "宋",
            sourceVersion = "git-v2.0"
        )

        assertEquals(3, candidates.size)

        // 苏轼《水调歌头》
        val shuidiao = candidates.find { it.cipai == "水调歌头" }
        assertNotNull(shuidiao)
        // 关键断言：无独立 title 时生成正文首句作为默认展示词题，类型为 GENERATED_FIRST_LINE
        assertEquals("明月几时有", shuidiao!!.title)
        assertEquals(TitleSourceType.GENERATED_FIRST_LINE, shuidiao.titleSourceType)
        assertEquals("苏轼", shuidiao.author?.name)
        assertEquals("宋", shuidiao.dynasty)
        assertEquals("CI", shuidiao.genre)
        assertEquals("水调歌头", shuidiao.cipai)
        assertTrue(shuidiao.content!!.contains("明月几时有，把酒问青天"))
        // 原始未清洗正文存在性验证
        assertNotNull(shuidiao.rawContent)
        assertEquals(shuidiao.rawContent, shuidiao.content)
    }

    @Test
    fun testOfflineDataPipeline_completeEndToEndFlow_fromRawFilesToPrechecker() {
        // 构建多来源混合输入：包含来自 poems-db、Poetry_CN 和 chinese-poetry 的真实原始数据
        val raw1 = readFixtureText("poems_db_real_samples.json")
        val raw2 = readFixtureText("poetry_cn_real_samples.json")
        val raw3 = readFixtureText("chinese_poetry_tang_real_samples.json")
        val raw4 = readFixtureText("chinese_poetry_song_ci_real_samples.json")

        val rawInputs = listOf(
            RawInputSource(rawContent = raw1, sourceCode = "POEMS_DB", rawFilePath = "fixtures/poems_db_real_samples.json"),
            RawInputSource(rawContent = raw2, sourceCode = "POETRY_CN", rawFilePath = "fixtures/poetry_cn_real_samples.json"),
            RawInputSource(rawContent = raw3, sourceCode = "CHINESE_POETRY", rawFilePath = "全唐诗/poet.tang.0.json", dynastyHint = "唐"),
            RawInputSource(rawContent = raw4, sourceCode = "CHINESE_POETRY", rawFilePath = "宋词/ci.song.0.json", dynastyHint = "宋")
        )

        // 执行完整流水线：原始字符串 -> 对应解析器 -> Candidate -> Merger -> WorkImportItem -> Prechecker
        val result = pipeline.processRawSources(rawInputs)

        // 1. 验证解析统计
        val stats = result.stats
        assertEquals(3, stats.filesReadPerSource.size) // POEMS_DB, POETRY_CN, CHINESE_POETRY
        assertEquals(15, stats.totalCandidatesParsed) // 5 + 3 + 4 + 3 = 15 条原始记录
        assertEquals(0, stats.failedRecordsCount) // 0 失败

        // 2. 验证多来源合并后作品聚合情况
        // 15 个候选记录通过保守正文特征聚类去重合并为 13 部独立作品
        assertEquals(13, result.mergedWorks.size)
        assertEquals(13, result.mergeAudits.size)

        // 验证苏轼《水调歌头·明月几时有》跨源对齐：成功将 poems-db 来源题名与 chinese-poetry 正文合并，词题与词牌分离
        val suShiMerged = result.mergedWorks.find { it.author?.name == "苏轼" && it.cipai == "水调歌头" }
        assertNotNull(suShiMerged)
        assertEquals("明月几时有", suShiMerged!!.title)
        assertEquals("水调歌头", suShiMerged.cipai)
        assertEquals(TitleSourceType.SOURCE_TITLE, suShiMerged.titleSourceType)

        // 3. 验证四项追溯指标严格区分
        assertEquals(100.0, stats.parsedSuccessRate, 0.001)
        assertEquals(100.0, stats.rawFileArchiveRate, 0.001)
        assertEquals(100.0, stats.rawRecordLocatableRate, 0.001)
        assertEquals(100.0, stats.sourceIndexPresenceRate, 0.001)

        // 4. 验证 RawRecordArchive 原始归档与检索能力
        assertEquals(4, result.archive.totalArchivedFiles())
        val poemsDbRecord = result.archive.findRawRecord("POEMS_DB", "5c22086497880d3b825c968f")
        assertNotNull("必须能在归档中随时按来源与ID检索到完整原始JSON", poemsDbRecord)
        assertTrue(poemsDbRecord!!.rawJsonPayload.contains("渡荆门送别"))

        // 5. 验证作品总数
        assertEquals(13, result.mergedWorks.size)
    }
}
