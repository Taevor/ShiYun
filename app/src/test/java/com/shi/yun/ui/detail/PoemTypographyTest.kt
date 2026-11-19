package com.shi.yun.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 诗词排版断行与整句换行单元测试
 */
class PoemTypographyTest {

    @Test
    fun testSplitIntoClauses() {
        val sample1 = "床前明月光，疑是地上霜。"
        val clauses1 = PoemTypography.splitIntoClauses(sample1)
        assertEquals(listOf("床前明月光，", "疑是地上霜。"), clauses1)

        val sample2 = "汉皇重色思倾国，御宇多年求不得。"
        val clauses2 = PoemTypography.splitIntoClauses(sample2)
        assertEquals(listOf("汉皇重色思倾国，", "御宇多年求不得。"), clauses2)

        val sample3 = "君不见，黄河之水天上来，奔流到海不复回。"
        val clauses3 = PoemTypography.splitIntoClauses(sample3)
        assertEquals(listOf("君不见，", "黄河之水天上来，", "奔流到海不复回。"), clauses3)

        val sample4 = "白日依山尽"
        val clauses4 = PoemTypography.splitIntoClauses(sample4)
        assertEquals(listOf("白日依山尽"), clauses4)
    }

    @Test
    fun testSplitSentenceIntoLines() {
        // 五言对仗联 (12字) 自动分为两行整句
        val sample1 = "床前明月光，疑是地上霜。"
        val lines1 = PoemTypography.splitSentenceIntoLines(sample1, thresholdChars = 11)
        assertEquals(listOf("床前明月光，", "疑是地上霜。"), lines1)

        // 七言对仗联 (16字) 自动分为两行整句
        val sample2 = "汉皇重色思倾国，御宇多年求不得。"
        val lines2 = PoemTypography.splitSentenceIntoLines(sample2, thresholdChars = 11)
        assertEquals(listOf("汉皇重色思倾国，", "御宇多年求不得。"), lines2)

        // 短句不超过门限保持单行
        val sample3 = "清明时节雨纷纷"
        val lines3 = PoemTypography.splitSentenceIntoLines(sample3, thresholdChars = 11)
        assertEquals(listOf("清明时节雨纷纷"), lines3)
    }

    @Test
    fun testAuxiliaryTabShortNames() {
        assertEquals("注释", AuxiliaryTab.ANNOTATION.displayName)
        assertEquals("注", AuxiliaryTab.ANNOTATION.shortName)

        assertEquals("译文", AuxiliaryTab.TRANSLATION.displayName)
        assertEquals("译", AuxiliaryTab.TRANSLATION.shortName)

        assertEquals("赏析", AuxiliaryTab.APPRECIATION.displayName)
        assertEquals("赏", AuxiliaryTab.APPRECIATION.shortName)
    }
}
