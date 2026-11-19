package com.shi.yun.data.util

import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.MatchConfidence
import com.shi.yun.data.model.Work
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkDeduplicatorTest {

    private fun createWork(id: String, title: String, author: String, content: String): Work {
        return Work(
            id = id,
            title = title,
            author = author,
            dynasty = "唐",
            genre = Genre.POEM,
            content = content,
            excerpt = content.lines().firstOrNull() ?: ""
        )
    }

    @Test
    fun testConfirmedMatchIdenticalTitleAuthorAndContent() {
        val work1 = createWork("tang_001", "静夜思", "李白", "床前明月光，疑是地上霜。举头望明月，低头思故乡。")
        val work2 = createWork("shici_001", "静夜思", "李白", "床前明月光，疑是地上霜。\n举头望明月，低头思故乡。")

        val result = WorkDeduplicator.match(work1, work2)
        assertEquals("tang_001", result.sourceWorkId)
        assertEquals("shici_001", result.candidateWorkId)
        assertEquals(MatchConfidence.CONFIRMED_MATCH, result.confidence)
        assertTrue(result.fingerprintA.isNotEmpty())
        assertEquals(result.fingerprintA, result.fingerprintB)
    }

    @Test
    fun testSuspectedMatchWithSlightTextVariants() {
        val work1 = createWork("tang_002", "感遇", "张九龄", "兰叶春葳蕤，桂华秋皎洁。欣欣此生意，自尔为佳节。")
        val work2 = createWork("shici_002", "感遇", "张九龄", "兰叶春萎蕤，桂华秋皎洁。欣欣此生意，自尔为嘉节。")

        val result = WorkDeduplicator.match(work1, work2)
        assertEquals("tang_002", result.sourceWorkId)
        assertEquals("shici_002", result.candidateWorkId)
        assertEquals(MatchConfidence.SUSPECTED_MATCH, result.confidence)
        assertTrue(result.reason.contains("版本异文"))
    }

    @Test
    fun testVariantMatchAliasTitleWithIdenticalContent() {
        // 早发白帝城 vs 下江陵
        val work1 = createWork("tang_003", "早发白帝城", "李白", "朝辞白帝彩云间，千里江陵一日还。两岸猿声啼不住，轻舟已过万重山。")
        val work2 = createWork("palemoky_003", "下江陵", "李白", "朝辞白帝彩云间，千里江陵一日还。两岸猿声啼不住，轻舟已过万重山。")

        val result = WorkDeduplicator.match(work1, work2)
        assertEquals("tang_003", result.sourceWorkId)
        assertEquals("palemoky_003", result.candidateWorkId)
        assertEquals(MatchConfidence.VARIANT_MATCH, result.confidence)
        assertTrue(result.reason.contains("别题"))
    }

    @Test
    fun testNotMatchSameCipaiDifferentAuthorAndContent() {
        // 水调歌头 苏轼 vs 水调歌头 辛弃疾 -> 必须是明确 NOT_MATCH，绝不能归入 UNCERTAIN
        val suShi = createWork("song_001", "水调歌头", "苏轼", "明月几时有？把酒问青天。不知天上宫阙，今夕是何年。")
        val xinQiji = createWork("song_002", "水调歌头", "辛弃疾", "带湖吾甚爱，千丈翠奁开。先生杖屦无害，秋水下平台。")

        val result = WorkDeduplicator.match(suShi, xinQiji)
        assertEquals("song_001", result.sourceWorkId)
        assertEquals("song_002", result.candidateWorkId)
        assertEquals(MatchConfidence.NOT_MATCH, result.confidence)
        assertTrue(result.reason.contains("作者明确不同"))
    }

    @Test
    fun testUncertainGenericTitleMissingAuthorAndShortContent() {
        val work1 = createWork("w_anon1", "无题", "", "春风知我意。")
        val work2 = createWork("w_anon2", "无题", "", "微云淡河汉。")

        val result = WorkDeduplicator.match(work1, work2)
        assertEquals(MatchConfidence.UNCERTAIN, result.confidence)
        assertTrue(result.reason.contains("证据不足") || result.reason.contains("无法判定"))
    }

    @Test
    fun testLongTextProtectionDoesNotCrashAndMatchesIdenticalLongPoem() {
        val line = "天生我材必有用，千金散尽还复来。\n"
        val longContent = line.repeat(120) // 超过 1500 字符
        val work1 = createWork("long_001", "将进酒长篇", "李白", longContent)
        val work2 = createWork("long_002", "将进酒长篇", "李白", longContent)

        val result = WorkDeduplicator.match(work1, work2)
        // 验证：超长篇幅文本仅凭采样即使指纹高度一致，也绝不直接判定为 CONFIRMED_MATCH，必须保守判定为 SUSPECTED_MATCH，防止强行合并
        assertEquals(MatchConfidence.SUSPECTED_MATCH, result.confidence)
        assertTrue(result.reason.contains("长篇文本采样"))
    }
}
