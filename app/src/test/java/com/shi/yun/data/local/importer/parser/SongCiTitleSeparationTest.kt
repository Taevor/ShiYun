package com.shi.yun.data.local.importer.parser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.importer.WorkImportPrechecker
import com.shi.yun.data.local.importer.merger.MergeDimension
import com.shi.yun.data.local.importer.merger.MultiSourceWorkMerger
import com.shi.yun.data.local.importer.merger.SourceWorkCandidate
import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.PrecheckDisposition
import com.shi.yun.data.local.importer.model.TitleSourceType
import com.shi.yun.data.local.importer.pipeline.OfflineDataPipeline
import com.shi.yun.data.local.importer.pipeline.RawInputSource
import org.junit.After
import org.junit.Assert.assertEquals
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
 * 阶段 4.2B 宋词「词牌与词题分离及首句默认词题」专项集成与单元测试套件 (SongCiTitleSeparationTest)
 *
 * 覆盖全部 14 项指令要求：
 * 1. chinese-poetry 只有 rhythmic 和正文，无 title，成功生成首句题名 (GENERATED_FIRST_LINE)；
 * 2. chinese-poetry 有独立 title，保留来源题名 (SOURCE_TITLE)；
 * 3. poems-db title 为「水调歌头」，正确识别为词牌，不当作词题；
 * 4. poems-db title 为「水调歌头·明月几时有」，正确拆分词牌和词题；
 * 5. 无法确认词牌的标题不被错误拆分；
 * 6. 小序与正文能够可靠区分时，不能把小序作为默认词题；
 * 7. 小序与正文无法可靠区分时，不进行冒险提取；
 * 8. 来源真实题名优先于首句生成题名；
 * 9. 多来源真实题名冲突能够生成审计差异；
 * 10. 同作者同词牌但正文不同，不能合并；
 * 11. 同作者同词牌且正文相同，能够合并；
 * 12. 两个标题都等于词牌名称、正文不同，不能合并；
 * 13. 首句生成题名不会修改 CONTENT 原文；
 * 14. 预检流转完整性：首句生成题名正常进入后续预检，未提取题名保持 UNKNOWN 进入待确认。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SongCiTitleSeparationTest {

    private lateinit var db: ShiYunDatabase
    private lateinit var prechecker: WorkImportPrechecker
    private lateinit var pipeline: OfflineDataPipeline
    private lateinit var merger: MultiSourceWorkMerger
    private lateinit var chinesePoetryParser: ChinesePoetryParser
    private lateinit var poemsDbParser: PoemsDbParser
    private lateinit var poetryCnParser: PoetryCnParser

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
        poetryCnParser = PoetryCnParser()
        pipeline = OfflineDataPipeline(
            merger = merger,
            prechecker = prechecker,
            chinesePoetryParser = chinesePoetryParser,
            poemsDbParser = poemsDbParser,
            poetryCnParser = poetryCnParser
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1. chinese-poetry 只有 rhythmic 和正文，无 title，成功生成首句题名
    @Test
    fun testChinesePoetry_onlyRhythmicAndParagraphs_generatesFirstLineTitle() {
        val json = """
        [
          {
            "author": "辛弃疾",
            "rhythmic": "青玉案",
            "paragraphs": ["东风夜放花千树，更吹落、星如雨。", "宝马雕车香满路。"],
            "id": "cp-ci-001"
          }
        ]
        """.trimIndent()

        val parsed = chinesePoetryParser.parseJson(json)
        assertEquals(1, parsed.size)
        val candidate = parsed.first()

        assertEquals("青玉案", candidate.cipai)
        assertEquals("东风夜放花千树", candidate.title)
        assertEquals(TitleSourceType.GENERATED_FIRST_LINE, candidate.titleSourceType)
        assertNull(candidate.rawTitle)
    }

    // 2. chinese-poetry 有独立 title，保留来源题名
    @Test
    fun testChinesePoetry_hasIndependentTitle_keepsSourceTitle() {
        val json = """
        [
          {
            "author": "辛弃疾",
            "rhythmic": "青玉案",
            "title": "元夕",
            "paragraphs": ["东风夜放花千树，更吹落、星如雨。"],
            "id": "cp-ci-002"
          }
        ]
        """.trimIndent()

        val parsed = chinesePoetryParser.parseJson(json)
        assertEquals(1, parsed.size)
        val candidate = parsed.first()

        assertEquals("青玉案", candidate.cipai)
        assertEquals("元夕", candidate.title)
        assertEquals("元夕", candidate.rawTitle)
        assertEquals(TitleSourceType.SOURCE_TITLE, candidate.titleSourceType)
    }

    // 3. poems-db title 为「水调歌头」，正确识别为词牌，不当作词题
    @Test
    fun testPoemsDb_titleIsOnlyCipai_recognizesCipaiAndGeneratesFirstLineTitle() {
        val json = """
        {
          "_id": "pdb-ci-001",
          "title": "水调歌头",
          "author": "苏轼",
          "dynasty": "宋代",
          "content": "明月几时有？把酒问青天。\n不知天上宫阙，今夕是何年。",
          "format": "词"
        }
        """.trimIndent()

        val parsed = poemsDbParser.parseJson(json)
        assertEquals(1, parsed.size)
        val candidate = parsed.first()

        assertEquals("水调歌头", candidate.cipai)
        assertEquals("明月几时有", candidate.title)
        assertEquals("水调歌头", candidate.rawTitle)
        assertEquals(TitleSourceType.GENERATED_FIRST_LINE, candidate.titleSourceType)
        assertNotEquals("水调歌头", candidate.title) // 绝不把词牌当词题
    }

    // 4. poems-db title 为「水调歌头·明月几时有」，正确拆分词牌和词题
    @Test
    fun testPoemsDb_titleWithMiddleDot_splitsCipaiAndCiti() {
        val json = """
        {
          "_id": "pdb-ci-002",
          "title": "水调歌头·明月几时有",
          "author": "苏轼",
          "dynasty": "宋代",
          "content": "明月几时有？把酒问青天。",
          "format": "词"
        }
        """.trimIndent()

        val parsed = poemsDbParser.parseJson(json)
        assertEquals(1, parsed.size)
        val candidate = parsed.first()

        assertEquals("水调歌头", candidate.cipai)
        assertEquals("明月几时有", candidate.title)
        assertEquals("水调歌头·明月几时有", candidate.rawTitle)
        assertEquals(TitleSourceType.SOURCE_TITLE, candidate.titleSourceType)
    }

    // 5. 无法确认词牌的标题不被错误拆分
    @Test
    fun testUnknownCipaiTitle_notSplitArbitrarily() {
        val json = """
        {
          "_id": "pdb-poem-001",
          "title": "古意·寄远人",
          "author": "李白",
          "dynasty": "唐代",
          "content": "君为女萝草，妾作菟丝花。",
          "format": "五言古诗"
        }
        """.trimIndent()

        val parsed = poemsDbParser.parseJson(json)
        assertEquals(1, parsed.size)
        val candidate = parsed.first()

        // "古意" 不是词牌，体裁也不是词，不应拆分成 cipai
        assertNull(candidate.cipai)
        assertEquals("古意·寄远人", candidate.title)
        assertEquals(TitleSourceType.SOURCE_TITLE, candidate.titleSourceType)
    }

    // 6. 小序与正文能够可靠区分时，不能把小序作为默认词题
    @Test
    fun testPrefaceExcluded_whenDistinctParagraphOrBracketed() {
        val contentWithPreface = """
        （丙辰中秋，欢饮达旦，大醉，作此篇，兼怀子由。）
        明月几时有？把酒问青天。
        不知天上宫阙，今夕是何年。
        """.trimIndent()

        val extracted = SongCiCipaiHelper.extractFirstLineAsTitle(contentWithPreface)
        assertEquals("明月几时有", extracted) // 成功跳过小序
    }

    // 7. 小序与正文无法可靠区分时，不进行冒险提取
    @Test
    fun testPrefaceCannotBeDistinguished_doesNotFabricate() {
        // 第一句直接混入词序且首句就是词序叙事
        val confusedContent = "作此篇兼怀子由不知天上宫阙今夕是何年"
        val extracted = SongCiCipaiHelper.extractFirstLineAsTitle(confusedContent)
        // 无法识别句读且首句含明显词序词汇 -> 返回 null
        assertNull(extracted)
    }

    // 8. 来源真实题名优先于首句生成题名
    @Test
    fun testSourceTitle_takesPrecedenceOverGeneratedFirstLine() {
        val c1 = SourceWorkCandidate(
            sourceName = "chinese-poetry",
            sourceCode = "CHINESE_POETRY",
            externalWorkId = "cp_001",
            title = "东风夜放花千树", // 首句生成题名
            titleSourceType = TitleSourceType.GENERATED_FIRST_LINE,
            content = "东风夜放花千树，更吹落、星如雨。",
            cipai = "青玉案",
            genre = "CI"
        )

        val c2 = SourceWorkCandidate(
            sourceName = "poems-db",
            sourceCode = "POEMS_DB",
            externalWorkId = "pdb_001",
            title = "元夕", // 来源明确题名
            rawTitle = "青玉案·元夕",
            titleSourceType = TitleSourceType.SOURCE_TITLE,
            content = "东风夜放花千树，更吹落、星如雨。",
            cipai = "青玉案",
            genre = "CI"
        )

        // 即使首句生成的候选排在前面，合并时必须采纳真实来源题名
        val audit = merger.merge(listOf(c1, c2))
        val resolved = audit.resolvedItem

        assertEquals("元夕", resolved.title)
        assertEquals(TitleSourceType.SOURCE_TITLE, resolved.titleSourceType)
        assertEquals("青玉案", resolved.cipai)
    }

    // 9. 多来源真实题名冲突能够生成审计差异
    @Test
    fun testMultipleSourceTitlesConflict_recordsAlternativeDiff() {
        val c1 = SourceWorkCandidate(
            sourceName = "poems-db",
            sourceCode = "POEMS_DB",
            externalWorkId = "pdb_001",
            title = "明月几时有",
            titleSourceType = TitleSourceType.SOURCE_TITLE,
            content = "明月几时有？把酒问青天。",
            cipai = "水调歌头",
            genre = "CI"
        )

        val c2 = SourceWorkCandidate(
            sourceName = "Poetry_CN",
            sourceCode = "POETRY_CN",
            externalWorkId = "pcn_001",
            title = "中秋望月",
            titleSourceType = TitleSourceType.SOURCE_TITLE,
            content = "明月几时有？把酒问青天。",
            cipai = "水调歌头",
            genre = "CI"
        )

        val audit = merger.merge(listOf(c1, c2))
        val titleDiff = audit.diffRecords.find { it.dimension == MergeDimension.TITLE }
        assertNotNull(titleDiff)

        // poems-db 优先级高于 Poetry_CN，采纳 "明月几时有"
        assertEquals("明月几时有", audit.resolvedItem.title)
        // 差异备选中必须包含 "中秋望月"
        assertEquals(1, titleDiff!!.alternativeSources.size)
        assertEquals("POETRY_CN", titleDiff.alternativeSources[0].sourceCode)
        assertTrue(titleDiff.alternativeSources[0].contentSnippet.contains("中秋望月"))
    }

    // 10. 同作者同词牌但正文不同，不能合并
    @Test
    fun testSameAuthorSameCipaiDifferentContent_notMerged() {
        val c1 = SourceWorkCandidate(
            sourceName = "chinese-poetry",
            sourceCode = "CHINESE_POETRY",
            externalWorkId = "cp_sushi_01",
            author = AuthorImportItem("苏轼"),
            title = "明月几时有",
            titleSourceType = TitleSourceType.GENERATED_FIRST_LINE,
            content = "明月几时有，把酒问青天。",
            cipai = "水调歌头",
            genre = "CI"
        )

        val c2 = SourceWorkCandidate(
            sourceName = "chinese-poetry",
            sourceCode = "CHINESE_POETRY",
            externalWorkId = "cp_sushi_02",
            author = AuthorImportItem("苏轼"),
            title = "落日绣帘卷",
            titleSourceType = TitleSourceType.GENERATED_FIRST_LINE,
            content = "落日绣帘卷，猩血染罗巾。",
            cipai = "水调歌头",
            genre = "CI"
        )

        val input1 = RawInputSource(rawContent = """[{"author":"苏轼","rhythmic":"水调歌头","paragraphs":["明月几时有，把酒问青天。"],"id":"1"}]""", sourceCode = "CHINESE_POETRY")
        val input2 = RawInputSource(rawContent = """[{"author":"苏轼","rhythmic":"水调歌头","paragraphs":["落日绣帘卷，猩血染罗巾。"],"id":"2"}]""", sourceCode = "CHINESE_POETRY")

        val result = pipeline.processRawSources(listOf(input1, input2))
        assertEquals("同作者同词牌但正文不同，绝对不能合并", 2, result.mergedWorks.size)
    }

    // 11. 同作者同词牌且正文相同，能够合并
    @Test
    fun testSameAuthorSameCipaiIdenticalContent_canMerge() {
        val input1 = RawInputSource(
            rawContent = """[{"author":"苏轼","rhythmic":"水调歌头","paragraphs":["明月几时有，把酒问青天。"],"id":"1"}]""",
            sourceCode = "CHINESE_POETRY"
        )
        val input2 = RawInputSource(
            rawContent = """[{"_id":"2","title":"水调歌头·明月几时有","author":"苏轼","content":"明月几时有？把酒问青天。","format":"词"}]""",
            sourceCode = "POEMS_DB"
        )

        val result = pipeline.processRawSources(listOf(input1, input2))
        assertEquals(1, result.mergedWorks.size)
        val merged = result.mergedWorks.first()
        assertEquals("明月几时有", merged.title)
        assertEquals("水调歌头", merged.cipai)
    }

    // 12. 两个标题都等于词牌名称、正文不同，不能合并
    @Test
    fun testBothTitlesEqualCipai_differentContent_cannotMerge() {
        // 数据源 A 和 B 均把 title 错误写成了词牌 "水调歌头"，但正文是两首完全不同的词
        val input1 = RawInputSource(
            rawContent = """[{"_id":"bad_01","title":"水调歌头","author":"苏轼","content":"明月几时有？把酒问青天。","format":"词"}]""",
            sourceCode = "POEMS_DB"
        )
        val input2 = RawInputSource(
            rawContent = """[{"_id":"bad_02","title":"水调歌头","author":"苏轼","content":"落日绣帘卷，猩血染罗巾。","format":"词"}]""",
            sourceCode = "POEMS_DB"
        )

        val result = pipeline.processRawSources(listOf(input1, input2))
        // 关键断言：即使原始 title 相同（均为水调歌头），词作正文不同绝不能合并！
        assertEquals(2, result.mergedWorks.size)
        assertEquals("明月几时有", result.mergedWorks[0].title)
        assertEquals("落日绣帘卷", result.mergedWorks[1].title)
    }

    // 13. 首句生成题名不会修改 CONTENT 原文
    @Test
    fun testGeneratedTitle_doesNotAlterOriginalContent() {
        val originalRawContent = "明月几时有，把酒问青天。\n不知天上宫阙，今夕是何年。"
        val json = """
        [
          {
            "author": "苏轼",
            "rhythmic": "水调歌头",
            "paragraphs": ["明月几时有，把酒问青天。", "不知天上宫阙，今夕是何年。"],
            "id": "cp_orig_01"
          }
        ]
        """.trimIndent()

        val parsed = chinesePoetryParser.parseJson(json)
        val candidate = parsed.first()

        assertEquals("明月几时有", candidate.title)
        // CONTENT 原文绝对不能受到首句提取的侵蚀或删减
        assertEquals("明月几时有，把酒问青天。\n不知天上宫阙，今夕是何年。", candidate.content)
        assertEquals("明月几时有，把酒问青天。\n不知天上宫阙，今夕是何年。", candidate.rawContent)
    }

    // 14. 预检流转完整性：首句生成题名正常进入后续预检
    @Test
    fun testGeneratedTitle_passesPrecheck_noNeedsConfirmationDueToMissingTitle() {
        val input = RawInputSource(
            rawContent = """[{"author":"李清照","rhythmic":"如梦令","paragraphs":["常记溪亭日暮，沉醉不知归路。"],"id":"cp_lqz_01"}]""",
            sourceCode = "CHINESE_POETRY"
        )

        val result = pipeline.processRawSources(listOf(input))
        val summary = result.precheckSummary
        assertNotNull(summary)
        assertEquals(1, summary!!.totalCount)
        // 成功提取首句 "常记溪亭日暮" 作为题名，不应被判待确认
        assertEquals(0, summary.needsConfirmationCount)
        assertEquals(1, summary.readyCount)
        assertEquals("常记溪亭日暮", result.mergedWorks.first().title)
        assertEquals(TitleSourceType.GENERATED_FIRST_LINE, result.mergedWorks.first().titleSourceType)
    }

    // 15. 首句行首 ASCII 方括号 [] 题记安全剥离
    @Test
    fun testAsciiSquareBracketExcluded_inFirstLine() {
        val content = "[中秋]明月几时有？把酒问青天。\n不知天上宫阙，今夕是何年。"
        val extracted = SongCiCipaiHelper.extractFirstLineAsTitle(content)
        assertEquals("明月几时有", extracted)
    }

    // 16. 普通诗歌或元曲即使标题命中词牌字典，也绝不被误判为 CI
    @Test
    fun testOrdinaryPoemOrYuanQu_notMisclassifiedAsCi() {
        // A. 李白《长相思》（明确标有五言古诗体裁）
        val libaiJson = """
        {
          "_id": "pdb-libai-cxs",
          "title": "长相思",
          "author": "李白",
          "dynasty": "唐代",
          "content": "长相思，在长安。络纬秋啼金井阑，微霜凄凄簟色寒。",
          "format": "五言古诗",
          "tags": ["唐诗三百首", "乐府"]
        }
        """.trimIndent()

        val parsedLibai = poemsDbParser.parseJson(libaiJson)
        assertEquals(1, parsedLibai.size)
        val libai = parsedLibai.first()
        assertEquals("五言古诗必须优先，绝不能误判为 CI", "五言古诗", libai.genre)
        assertNull("普通诗歌绝不能提取出词牌", libai.cipai)
        assertEquals("长相思", libai.title)
        assertEquals(TitleSourceType.SOURCE_TITLE, libai.titleSourceType)

        // B. 马致远《天净沙·秋思》（明确为元曲）
        val mazhiyuanJson = """
        {
          "_id": "pdb-mazhiyuan-tjs",
          "title": "天净沙·秋思",
          "author": "马致远",
          "dynasty": "元代",
          "content": "枯藤老树昏鸦，小桥流水人家，古道西风瘦马。夕阳西下，断肠人在天涯。",
          "format": "元曲",
          "tags": ["元曲精选", "散曲"]
        }
        """.trimIndent()

        val parsedMa = poemsDbParser.parseJson(mazhiyuanJson)
        assertEquals(1, parsedMa.size)
        val ma = parsedMa.first()
        assertEquals("元曲必须优先，绝不能误判为 CI", "QU", ma.genre)
        assertNull("元曲绝不能被当作宋词词牌提取", ma.cipai)
        assertEquals("天净沙", ma.qupai)
        assertEquals("秋思", ma.title)
        assertEquals(TitleSourceType.SOURCE_TITLE, ma.titleSourceType)

        // C. 信息不足：无 format 且无标签的纯标题 "长相思"，信息不足不猜测为 CI
        val unknownJson = """
        {
          "_id": "pdb-unknown-cxs",
          "title": "长相思",
          "author": "李白",
          "dynasty": "唐代",
          "content": "长相思，在长安。"
        }
        """.trimIndent()

        val parsedUnknown = poemsDbParser.parseJson(unknownJson)
        assertEquals(1, parsedUnknown.size)
        val unknownWork = parsedUnknown.first()
        assertNull("信息不足时保持 null，不盲目猜测为 CI", unknownWork.genre)
        assertNull("未确认为词时，词牌保持 null", unknownWork.cipai)
        assertEquals("长相思", unknownWork.title)
        assertEquals(TitleSourceType.SOURCE_TITLE, unknownWork.titleSourceType)
    }
}
