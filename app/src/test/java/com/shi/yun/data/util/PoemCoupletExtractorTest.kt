package com.shi.yun.data.util

import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.Work
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 首页诗词双句提取工具单元测试
 */
class PoemCoupletExtractorTest {

    @Test
    fun testExtractCouplet_fiveCharacterPoem() {
        val work = Work(
            id = "tang_001",
            title = "静夜思",
            author = "〔唐〕李白",
            dynasty = "唐",
            genre = Genre.POEM,
            content = "床前明月光，疑是地上霜。\n举头望明月，低头思故乡。",
            excerpt = "床前明月光，疑是地上霜。",
        )

        val couplet = PoemCoupletExtractor.extractCouplet(work)
        assertNotNull(couplet)
        assertEquals("tang_001", couplet!!.workId)
        assertEquals("床前明月光", couplet.line1)
        assertEquals("疑是地上霜", couplet.line2)
        assertEquals("静夜思", couplet.title)
        assertEquals("李白", couplet.author)
        assertEquals("床前明月光，疑是地上霜。", couplet.formattedWithPunctuation)
    }

    @Test
    fun testExtractCouplet_sevenCharacterPoem() {
        val work = Work(
            id = "tang_002",
            title = "黄鹤楼送孟浩然之广陵",
            author = "李白",
            dynasty = "唐",
            genre = Genre.POEM,
            content = "故人西辞黄鹤楼，烟花三月下扬州。\n孤帆远影碧空尽，唯见长江天际流。",
            excerpt = "",
        )

        val couplet = PoemCoupletExtractor.extractCouplet(work)
        assertNotNull(couplet)
        assertEquals("故人西辞黄鹤楼", couplet!!.line1)
        assertEquals("烟花三月下扬州", couplet.line2)
        assertEquals("黄鹤楼送孟浩然之广陵", couplet.title)
        assertEquals("李白", couplet.author)
    }

    @Test
    fun testExtractCouplet_filtersOutProseAndEssays() {
        // 散文/文章 (Genre.WEN) 必须被彻底过滤
        val proseWork = Work(
            id = "wen_001",
            title = "出师表",
            author = "诸葛亮",
            dynasty = "三国",
            genre = Genre.WEN,
            content = "先帝创业未半而中道崩殂，今天下三分，益州疲弊，此诚危急存亡之秋也。",
            excerpt = "先帝创业未半而中道崩殂",
        )

        assertNull("散文体裁严禁提取推荐双句", PoemCoupletExtractor.extractCouplet(proseWork))

        // 词曲体裁 (Genre.CI) 暂不混入诗类推荐
        val ciWork = Work(
            id = "ci_001",
            title = "水调歌头",
            author = "苏轼",
            dynasty = "宋",
            genre = Genre.CI,
            content = "明月几时有？把酒问青天。",
            excerpt = "明月几时有？把酒问青天。",
        )
        assertNull("词曲体裁暂不混入诗类推荐", PoemCoupletExtractor.extractCouplet(ciWork))
    }

    @Test
    fun testExtractCouplet_skipsIrregularOrAsymmetricClauses() {
        val irregularWork = Work(
            id = "test_irregular",
            title = "测试非整饬诗",
            author = "无名氏",
            dynasty = "现代",
            genre = Genre.POEM,
            content = "风吹。\n草低见牛羊茫茫一片大草原。",
            excerpt = "",
        )

        assertNull("非整饬长短句应被跳过", PoemCoupletExtractor.extractCouplet(irregularWork))
    }

    @Test
    fun testExtractCouplet_cleansTitleAndAuthor() {
        val work = Work(
            id = "test_clean",
            title = "《登鹳雀楼》",
            author = "·唐·王之涣",
            dynasty = "唐",
            genre = Genre.POEM,
            content = "白日依山尽，黄河入海流。",
            excerpt = "",
        )

        val couplet = PoemCoupletExtractor.extractCouplet(work)
        assertNotNull(couplet)
        assertEquals("登鹳雀楼", couplet!!.title)
        assertEquals("王之涣", couplet.author)
    }
}
