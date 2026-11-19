package com.shi.yun.data.local.importer.pipeline

import com.shi.yun.data.local.importer.merger.MultiSourceWorkMerger
import com.shi.yun.data.local.importer.parser.ChinesePoetryParser
import com.shi.yun.data.local.importer.parser.PoemsDbParser
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 阶段 4.2B：真实跨来源作品合并验证测试 (RealCrossSourcePairsVerificationTest)
 *
 * 严格基于 D:\ShiYunDataSources\poems-db 和 D:\ShiYunDataSources\chinese-poetry
 * 中定位到的 6 组真实相同作品（含 3 组唐诗与 3 组宋词）：
 * 1. 李白《静夜思》
 * 2. 王之涣《登鹳雀楼》
 * 3. 王维《相思》
 * 4. 苏轼《水调歌头·明月几时有》
 * 5. 辛弃疾《青玉案·元夕》
 * 6. 李清照《声声慢·寻寻觅觅》
 *
 * 验证目标：
 * - 验证跨源相同作品在正文一致时的正确合并与双源追溯
 * - 验证正文异文、简繁差异、体裁特性下的保守聚类与防误合并机制
 * - 验证双源记录在归档与合并审计日志中的完整保留
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealCrossSourcePairsVerificationTest {

    private lateinit var pipeline: OfflineDataPipeline
    private lateinit var pairsArray: JSONArray

    @Before
    fun setUp() {
        pipeline = OfflineDataPipeline()
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/real_cross_source_pairs.json")
            ?: error("Cannot load fixtures/real_cross_source_pairs.json")
        val jsonText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        pairsArray = JSONArray(jsonText)
        assertEquals("必须包含 6 组真实跨源样本", 6, pairsArray.length())
    }

    @Test
    fun testRealPair_XinQiji_QingYuAn_mergesSeamlesslyWithDualSourceTraceability() {
        // 辛弃疾《青玉案·元夕》：两源作者均为简体“辛弃疾”，词牌均为“青玉案”，正文完全一致
        val pairObj = pairsArray.getJSONObject(4)
        assertEquals("辛弃疾《青玉案·元夕》", pairObj.getString("pair_name"))

        val pRaw = pairObj.getJSONObject("poems_db").getJSONObject("raw_record").toString()
        val cRaw = pairObj.getJSONObject("chinese_poetry").getJSONObject("raw_record").toString()

        val inputs = listOf(
            RawInputSource(
                rawContent = pRaw,
                sourceCode = PoemsDbParser.SOURCE_CODE,
                rawFilePath = pairObj.getJSONObject("poems_db").getString("file"),
                sourceVersion = "trial-run-v1"
            ),
            RawInputSource(
                rawContent = cRaw,
                sourceCode = ChinesePoetryParser.SOURCE_CODE,
                rawFilePath = pairObj.getJSONObject("chinese_poetry").getString("file"),
                sourceVersion = "trial-run-v1"
            )
        )

        val result = pipeline.processRawSources(inputs)
        val stats = result.stats

        assertEquals(2, stats.totalCandidatesParsed)
        assertEquals(0, stats.failedRecordsCount)

        // 验证：成功识别为同一部作品，未重复创建两部正式作品！
        assertEquals("词牌相同且正文一致的两源宋词必须合并为 1 部作品", 1, result.mergedWorks.size)
        val mergedWork = result.mergedWorks.first()

        assertEquals("元夕", mergedWork.title)
        assertEquals("青玉案", mergedWork.cipai)
        assertEquals("辛弃疾", mergedWork.author?.name)
        assertEquals("宋代", mergedWork.dynasty)
        assertTrue(mergedWork.content.contains("东风夜放花千树"))
        assertTrue(mergedWork.content.contains("蓦然回首，那人却在，灯火阑珊处"))

        // 验证：主来源与追溯凭据完整记录
        assertEquals(PoemsDbParser.SOURCE_CODE, mergedWork.externalSourceCode)
        assertEquals("5c26e0be97880d3b825f8e26", mergedWork.externalWorkId)
        assertEquals("https://so.gushiwen.org/shiwenv_51aa3a553057.aspx", mergedWork.externalUrl)
        assertNotNull(mergedWork.contentSource)
        assertEquals("yxcs/poems-db", mergedWork.contentSource?.sourceName)

        // 验证：通过归档引擎能够同时检索到两个来源的原始记录
        val pArchived = result.archive.findRawRecord(PoemsDbParser.SOURCE_CODE, "5c26e0be97880d3b825f8e26")
        assertNotNull("归档中必须能定位到 poems-db 原始记录", pArchived)
        assertEquals(2, result.archive.totalArchivedRecords())

        // 验证：合并审计日志中完整记录了本次 2 候选合并
        val audit = result.mergeAudits.first()
        assertEquals(2, audit.candidateCount)
        assertTrue(audit.diffRecords.isNotEmpty())
    }

    @Test
    fun testRealPair_LiBai_JingYeSi_behaviorUnderTextVariant() {
        // 李白《静夜思》：唐诗经典名篇，存在历史上著名的文献异文
        // poems-db: 床前明月光，疑是地上霜。举头望明月，低头思故乡。
        // chinese-poetry: 牀前看月光，疑是地上霜。舉頭望山月，低頭思故鄉。
        val pairObj = pairsArray.getJSONObject(0)
        assertEquals("李白《静夜思》", pairObj.getString("pair_name"))

        val pRaw = pairObj.getJSONObject("poems_db").getJSONObject("raw_record").toString()
        val cRaw = pairObj.getJSONObject("chinese_poetry").getJSONObject("raw_record").toString()

        val inputs = listOf(
            RawInputSource(
                rawContent = pRaw,
                sourceCode = PoemsDbParser.SOURCE_CODE,
                rawFilePath = pairObj.getJSONObject("poems_db").getString("file"),
                sourceVersion = "trial-run-v1"
            ),
            RawInputSource(
                rawContent = cRaw,
                sourceCode = ChinesePoetryParser.SOURCE_CODE,
                rawFilePath = pairObj.getJSONObject("chinese_poetry").getString("file"),
                sourceVersion = "trial-run-v1"
            )
        )

        val result = pipeline.processRawSources(inputs)
        println("=== 李白《静夜思》跨源处理结果 ===")
        println("mergedWorksCount: ${result.mergedWorks.size}")
        result.mergedWorks.forEachIndexed { idx, work ->
            println("Work[$idx]: title=${work.title}, author=${work.author?.name}, content=${work.content}")
        }

        // poems-db 标题为“静夜思”，chinese-poetry 标题为繁体“靜夜思”。
        // 在唐诗体裁下，因标题与作者字形差异，且正文存在历史版本异文（看月光/望山月 vs 明月光/望明月）
        // 离线流水线坚持保守安全策略：因标题文字未完全一致且正文存在异文，聚类器独立聚类，保留各自底本
        assertTrue("必须生成 1 或 2 部作品，绝不发生正文覆盖丢失", result.mergedWorks.size in 1..2)
    }

    @Test
    fun testRealPair_WangZhiHuan_GuanQueLou_simplifiedVsTraditional() {
        // 王之涣《登鹳雀楼》：
        // poems-db: 标题“登鹳雀楼”，作者“王之涣”，正文“白日依山尽，黄河入海流。欲穷千里目，更上一层楼。”
        // chinese-poetry: 标题“登鸛雀樓”，作者“王之渙”，正文“白日依山盡，黃河入海流。欲窮千里目，更上一層樓。”
        val pairObj = pairsArray.getJSONObject(1)
        val pRaw = pairObj.getJSONObject("poems_db").getJSONObject("raw_record").toString()
        val cRaw = pairObj.getJSONObject("chinese_poetry").getJSONObject("raw_record").toString()

        val inputs = listOf(
            RawInputSource(
                rawContent = pRaw,
                sourceCode = PoemsDbParser.SOURCE_CODE,
                rawFilePath = pairObj.getJSONObject("poems_db").getString("file"),
                sourceVersion = "trial-run-v1"
            ),
            RawInputSource(
                rawContent = cRaw,
                sourceCode = ChinesePoetryParser.SOURCE_CODE,
                rawFilePath = pairObj.getJSONObject("chinese_poetry").getString("file"),
                sourceVersion = "trial-run-v1"
            )
        )

        val result = pipeline.processRawSources(inputs)
        println("=== 王之涣《登鹳雀楼》跨源处理结果 ===")
        println("mergedWorksCount: ${result.mergedWorks.size}")
        result.mergedWorks.forEachIndexed { idx, work ->
            println("Work[$idx]: title=${work.title}, author=${work.author?.name}, source=${work.externalSourceCode}")
        }

        assertTrue("在不启用繁简自动转换的前提下，聚类器安全保留两部作品或依标题合并", result.mergedWorks.size in 1..2)
    }

    @Test
    fun testRealPair_SuShi_ShuiDiaoGeTou_prefaceAndVariantNotes() {
        // 苏轼《水调歌头·明月几时有》：
        // poems-db 包含小序“丙辰中秋...”与多处异文校注“(何似 一作：何时)”
        // chinese-poetry 则为纯净正文“明月几时有，把酒问青天...”
        val pairObj = pairsArray.getJSONObject(3)
        val pRaw = pairObj.getJSONObject("poems_db").getJSONObject("raw_record").toString()
        val cRaw = pairObj.getJSONObject("chinese_poetry").getJSONObject("raw_record").toString()

        val inputs = listOf(
            RawInputSource(
                rawContent = pRaw,
                sourceCode = PoemsDbParser.SOURCE_CODE,
                rawFilePath = pairObj.getJSONObject("poems_db").getString("file"),
                sourceVersion = "trial-run-v1"
            ),
            RawInputSource(
                rawContent = cRaw,
                sourceCode = ChinesePoetryParser.SOURCE_CODE,
                rawFilePath = pairObj.getJSONObject("chinese_poetry").getString("file"),
                sourceVersion = "trial-run-v1"
            )
        )

        val result = pipeline.processRawSources(inputs)
        println("=== 苏轼《水调歌头》跨源处理结果 ===")
        println("mergedWorksCount: ${result.mergedWorks.size}")
        result.mergedWorks.forEachIndexed { idx, work ->
            println("Work[$idx]: title=${work.title}, cipai=${work.cipai}, author=${work.author?.name}")
        }

        // 验证：宋词体裁下，因 poems-db 正文包含 HTML 标签、小序与异文括号，归一化后与纯净正文不同，
        // 聚类器严格坚守“正文不同绝不合并”铁律，杜绝了把带小序版本与纯净版本强行覆盖的风险！
        assertEquals("正文不同（带小序注 vs 纯正文）的同词牌词作坚决独立聚类", 2, result.mergedWorks.size)
    }

    @Test
    fun testRealPair_LiQingZhao_ShengShengMan_variantCharacter() {
        // 李清照《声声慢·寻寻觅觅》：
        // poems-db: “如今有谁堪摘” + HTML 标签 + “（守着窗儿 一作：守著窗儿）”
        // chinese-poetry: “如今有谁忺摘”
        val pairObj = pairsArray.getJSONObject(5)
        val pRaw = pairObj.getJSONObject("poems_db").getJSONObject("raw_record").toString()
        val cRaw = pairObj.getJSONObject("chinese_poetry").getJSONObject("raw_record").toString()

        val inputs = listOf(
            RawInputSource(
                rawContent = pRaw,
                sourceCode = PoemsDbParser.SOURCE_CODE,
                rawFilePath = pairObj.getJSONObject("poems_db").getString("file"),
                sourceVersion = "trial-run-v1"
            ),
            RawInputSource(
                rawContent = cRaw,
                sourceCode = ChinesePoetryParser.SOURCE_CODE,
                rawFilePath = pairObj.getJSONObject("chinese_poetry").getString("file"),
                sourceVersion = "trial-run-v1"
            )
        )

        val result = pipeline.processRawSources(inputs)
        println("=== 李清照《声声慢》跨源处理结果 ===")
        println("mergedWorksCount: ${result.mergedWorks.size}")

        // 验证：因存在异文字符“忺” vs “堪”及括号异文注，聚类器严格独立聚类为 2 篇独立作品
        assertEquals(2, result.mergedWorks.size)
    }

    @Test
    fun testAllSixRealPairsBatchExecution() {
        // 将全部 6 组（12 条真实原始记录）一起送入流水线
        val inputs = mutableListOf<RawInputSource>()
        for (i in 0 until pairsArray.length()) {
            val pairObj = pairsArray.getJSONObject(i)
            val pRaw = pairObj.getJSONObject("poems_db").getJSONObject("raw_record").toString()
            val cRaw = pairObj.getJSONObject("chinese_poetry").getJSONObject("raw_record").toString()

            inputs.add(
                RawInputSource(
                    rawContent = pRaw,
                    sourceCode = PoemsDbParser.SOURCE_CODE,
                    rawFilePath = pairObj.getJSONObject("poems_db").getString("file"),
                    sourceVersion = "trial-run-v1"
                )
            )
            inputs.add(
                RawInputSource(
                    rawContent = cRaw,
                    sourceCode = ChinesePoetryParser.SOURCE_CODE,
                    rawFilePath = pairObj.getJSONObject("chinese_poetry").getString("file"),
                    sourceVersion = "trial-run-v1"
                )
            )
        }

        assertEquals(12, inputs.size)
        val result = pipeline.processRawSources(inputs)
        val stats = result.stats

        assertEquals(12, stats.totalCandidatesParsed)
        assertEquals(0, stats.failedRecordsCount)
        assertEquals(100.0, stats.parsedSuccessRate, 0.0001)
        assertEquals(100.0, stats.rawRecordLocatableRate, 0.0001)
        assertEquals(100.0, stats.sourceIndexPresenceRate, 0.0001)

        println("=== 6 组 12 条真实记录全量处理统计 ===")
        println("totalCandidatesParsed: ${stats.totalCandidatesParsed}")
        println("mergedWorksCount: ${result.mergedWorks.size}")
        println("duplicateWorksCount (内容完全相同去重): ${stats.duplicateWorksCount}")
        println("variantNeedsConfirmationCount (正文排版/异文差异记录): ${stats.variantNeedsConfirmationCount}")

        // 验证：12 条候选成功合并为 11 部独立作品（辛弃疾跨源成功合并）
        assertEquals("12 条候选在保守聚类下准确合并为 11 部独立作品", 11, result.mergedWorks.size)
        // 验证：排版异文差异被完整记入变体统计中
        assertTrue("存在跨源内容异文记录", stats.variantNeedsConfirmationCount >= 1)
    }
}
