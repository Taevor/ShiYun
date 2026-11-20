package com.shi.yun.ui.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 诗云自研古典竖排排版计算引擎单元测试
 *
 * 验证核心指标：
 * 1. 汉字从上到下排列 (Y 坐标单调递增)；
 * 2. 多列文字从右向左排列 (第 0 列在最右侧，第 1 列在其左侧)；
 * 3. 五言、七言、双句排版；
 * 4. 正文右上起首，落款左下锚定向上排列，中宫留白，不整体居中；
 * 5. 标点过滤机制，呈现古典纯净汉字列。
 */
class VerticalLayoutEngineTest {

    @Test
    fun testFiveCharacterPoem_columnsRightToLeftAndTopToBottom() {
        val input = VerticalLayoutInput(
            clauses = listOf("床前明月光", "疑是地上霜"),
            attribution = "《静夜思》李白",
            availableWidth = 800f,
            availableHeight = 1000f,
            baseFontSizePx = 40f,
            attributionFontSizePx = 24f,
            columnSpacingPx = 40f,
            lineSpacingPx = 10f,
            paddingTopPx = 50f,
            paddingRightPx = 50f,
            paddingLeftPx = 50f,
            paddingBottomPx = 50f,
        )

        val result = VerticalLayoutEngine.layout(input)

        // 包含出句、对句与左下角落款共 3 列
        assertEquals(3, result.columns.size)

        // 验证正文靠右上起首：第 0 列中心 X 坐标应在最右侧 (800 - 50 - 20 = 730)
        val col0X = result.columns[0].glyphs.first().x
        assertEquals(730f, col0X, 0.01f)

        // 验证第 1 列在第 0 列左侧 (730 - 40 - 40 = 650)
        val col1X = result.columns[1].glyphs.first().x
        assertEquals(650f, col1X, 0.01f)
        assertTrue("第0列应在最右侧: col0X($col0X) > col1X($col1X)", col0X > col1X)

        // 验证落款列在左下角附近：X 坐标应在最左侧 (50 + 12 = 62)
        val attrX = result.columns[2].glyphs.first().x
        assertEquals(62f, attrX, 0.01f)
        assertTrue("正文列必须远在落款列右侧: col1X($col1X) > attrX($attrX)", col1X > attrX)

        // 验证正文字符自上而下排列 (Y 递增)
        val col0Glyphs = result.columns[0].glyphs
        assertEquals(5, col0Glyphs.size)
        for (i in 0 until col0Glyphs.size - 1) {
            val currentY = col0Glyphs[i].y
            val nextY = col0Glyphs[i + 1].y
            assertTrue("字符应从上到下排列: Y[$i]($currentY) < Y[${i + 1}]($nextY)", currentY < nextY)
        }

        // 验证字符内容与顺序
        assertEquals("床前明月光", col0Glyphs.map { it.char }.joinToString(""))
        assertEquals("疑是地上霜", result.columns[1].glyphs.map { it.char }.joinToString(""))
    }

    @Test
    fun testSevenCharacterPoem_properFormatting() {
        val input = VerticalLayoutInput(
            clauses = listOf("人生若只如初见", "何事秋风悲画扇"),
            attribution = "《木兰词》纳兰性德",
            availableWidth = 800f,
            availableHeight = 1200f,
            baseFontSizePx = 40f,
        )

        val result = VerticalLayoutEngine.layout(input)
        assertEquals(3, result.columns.size)

        val firstCol = result.columns[0].glyphs
        assertEquals(7, firstCol.size)
        assertEquals("人生若只如初见", firstCol.map { it.char }.joinToString(""))

        val secondCol = result.columns[1].glyphs
        assertEquals(7, secondCol.size)
        assertEquals("何事秋风悲画扇", secondCol.map { it.char }.joinToString(""))
    }

    @Test
    fun testAttributionAtBottomLeftCorner_anchoredUpward() {
        val input = VerticalLayoutInput(
            clauses = listOf("大漠孤烟直", "长河落日圆"),
            attribution = "《使至塞上》王维",
            availableWidth = 800f,
            availableHeight = 1000f,
            baseFontSizePx = 40f,
            attributionFontSizePx = 24f,
            paddingBottomPx = 50f,
        )

        val result = VerticalLayoutEngine.layout(input)
        val attrCol = result.columns.last()

        assertTrue("最后一列必须为出处落款列", attrCol.isAttribution)
        assertTrue("出处文字字号应小于正文字号", attrCol.glyphs.first().fontSizePx < input.baseFontSizePx)
        assertTrue("出处文字标记 isAttribution 应为 true", attrCol.glyphs.all { it.isAttribution })

        // 验证落款底端锚定在左下角附近：最后一个字符的 Y 坐标应接近 (availableHeight - paddingBottom)
        val lastGlyph = attrCol.glyphs.last()
        assertTrue(
            "落款末字 Y 坐标(${lastGlyph.y})应靠近底端",
            lastGlyph.y > 800f,
        )

        // 验证落款从上至下书写并向上锚定，且已剥离横排书名号《》，作品名与作者之间使用真实几何间距停顿留白（无需填充生硬空格字符）
        assertEquals("使至塞上王维", attrCol.glyphs.map { it.char }.joinToString(""))

        // 验证作品名与作者之间的间距大于落款内部普通字间距
        val titleLastGlyphY = attrCol.glyphs[3].y
        val authorFirstGlyphY = attrCol.glyphs[4].y
        val authorSecondGlyphY = attrCol.glyphs[5].y
        val gapBetweenTitleAndAuthor = authorFirstGlyphY - titleLastGlyphY
        val gapBetweenAuthorChars = authorSecondGlyphY - authorFirstGlyphY
        assertTrue("作品名与作者之间的留白间距应大于普通落款字间距", gapBetweenTitleAndAuthor > gapBetweenAuthorChars)
    }

    @Test
    fun testFilterPunctuation_removesHorizontalPunctuation() {
        val input = VerticalLayoutInput(
            text = "海上升明月，天涯共此时。",
            attribution = "《望月怀远》张九龄",
            availableWidth = 600f,
            availableHeight = 800f,
            filterPunctuation = true,
        )

        val result = VerticalLayoutEngine.layout(input)
        assertEquals(3, result.columns.size)

        // 标点符号已过滤，出句和对句均为 5 个纯汉字，落款中书名号已剥离
        val col0Chars = result.columns[0].glyphs.map { it.char }.joinToString("")
        val col1Chars = result.columns[1].glyphs.map { it.char }.joinToString("")
        val attrChars = result.columns[2].glyphs.map { it.char }.joinToString("")

        assertEquals("海上升明月", col0Chars)
        assertEquals("天涯共此时", col1Chars)
        assertEquals("望月怀远张九龄", attrChars)
    }

    @Test
    fun testStructuredAttributionAndCompactSpacing() {
        val input = VerticalLayoutInput(
            clauses = listOf("白日依山尽", "黄河入海流"),
            attributionTitle = "登鹳雀楼",
            attributionAuthor = "王之涣",
            availableWidth = 800f,
            availableHeight = 1000f,
            baseFontSizePx = 40f,
            attributionFontSizePx = 20f,
            lineSpacingPx = 8f,
            attrLineSpacingPx = 4f,
            attrTitleAuthorGapPx = 16f,
        )

        val result = VerticalLayoutEngine.layout(input)
        assertEquals(3, result.columns.size)

        val attrCol = result.columns[2]
        assertEquals("登鹳雀楼王之涣", attrCol.glyphs.map { it.char }.joinToString(""))

        // 验证正文垂直字距收拢
        val poemY0 = result.columns[0].glyphs[0].y
        val poemY1 = result.columns[0].glyphs[1].y
        val poemCharDistance = poemY1 - poemY0

        // 验证落款垂直字距明显小于正文字距
        val attrY0 = attrCol.glyphs[0].y
        val attrY1 = attrCol.glyphs[1].y
        val attrCharDistance = attrY1 - attrY0
        assertTrue("落款字间距应明显小于正文字间距", attrCharDistance < poemCharDistance)

        // 验证作品名与作者之间的停顿间隔大于落款行内字间距
        val titleLastY = attrCol.glyphs[3].y // '楼'
        val authorFirstY = attrCol.glyphs[4].y // '王'
        val gap = authorFirstY - titleLastY
        assertTrue("作品名与作者之间应有专属停顿留白", gap > attrCharDistance)
    }

    @Test
    fun testEmptyOrBlankInputHandledGracefully() {
        val emptyInput = VerticalLayoutInput(
            text = "",
            availableWidth = 500f,
            availableHeight = 500f,
        )
        val emptyResult = VerticalLayoutEngine.layout(emptyInput)
        assertTrue(emptyResult.columns.isEmpty())

        val zeroWidthInput = VerticalLayoutInput(
            text = "床前明月光",
            availableWidth = 0f,
            availableHeight = 500f,
        )
        val zeroWidthResult = VerticalLayoutEngine.layout(zeroWidthInput)
        assertTrue(zeroWidthResult.columns.isEmpty())
    }
}
