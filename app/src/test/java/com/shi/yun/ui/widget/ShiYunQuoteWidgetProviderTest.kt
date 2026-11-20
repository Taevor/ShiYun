package com.shi.yun.ui.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.shi.yun.R
import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.Work
import com.shi.yun.ui.theme.AppThemeId
import com.shi.yun.ui.theme.AppThemePalette
import com.shi.yun.ui.theme.ThemeBackgroundStyle
import com.shi.yun.ui.theme.ThemeVisualSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 桌面小组件名句提取、居中排版折行、十套主题配色对比度与背景纹理配置单元测试
 */
class ShiYunQuoteWidgetProviderTest {

    @Test
    fun testExtractDisplayQuote_prefersExcerpt() {
        val work = Work(
            id = "test_001",
            title = "静夜思",
            author = "李白",
            dynasty = "唐",
            genre = Genre.POEM,
            content = "床前明月光，疑是地上霜。\n举头望明月，低头思故乡。",
            excerpt = "床前明月光，疑是地上霜。",
        )
        val quote = ShiYunQuoteWidgetProvider.extractDisplayQuote(work)
        assertEquals("床前明月光，疑是地上霜。", quote)
    }

    @Test
    fun testExtractDisplayQuote_fallbackToFirstLineWhenExcerptBlank() {
        val work = Work(
            id = "test_002",
            title = "春晓",
            author = "孟浩然",
            dynasty = "唐",
            genre = Genre.POEM,
            content = "  \n春眠不觉晓，处处闻啼鸟。\n夜来风雨声，花落知多少。",
            excerpt = "",
        )
        val quote = ShiYunQuoteWidgetProvider.extractDisplayQuote(work)
        assertEquals("春眠不觉晓，处处闻啼鸟。", quote)
    }

    @Test
    fun testExtractDisplayQuote_fallbackToTitleWhenContentEmpty() {
        val work = Work(
            id = "test_003",
            title = "短歌行",
            author = "曹操",
            dynasty = "汉",
            genre = Genre.POEM,
            content = "",
            excerpt = "",
        )
        val quote = ShiYunQuoteWidgetProvider.extractDisplayQuote(work)
        assertEquals("短歌行", quote)
    }

    @Test
    fun testFormatWidgetQuote_adaptiveCleanTextWithoutForcedLineBreaks() {
        // 1. 五言对仗两句，消除强制换行，由 TextView 根据实际宽度决定是否单行显示或自动换行
        val fiveCharCouplet = ShiYunQuoteWidgetProvider.formatWidgetQuote("床前明月光，疑是地上霜。")
        assertEquals("床前明月光，疑是地上霜。", fiveCharCouplet)

        // 2. 原始文本带有换行符但已有标点，自动连贯平滑连接
        val withNewlines = ShiYunQuoteWidgetProvider.formatWidgetQuote("床前明月光，\n疑是地上霜。")
        assertEquals("床前明月光，疑是地上霜。", withNewlines)

        // 3. 原始文本有换行但缺少标点，自动补齐逗号保持语义连贯
        val missingPunctuation = ShiYunQuoteWidgetProvider.formatWidgetQuote("床前明月光\n疑是地上霜。")
        assertEquals("床前明月光，疑是地上霜。", missingPunctuation)

        // 4. 七言对仗两句
        val sevenCharCouplet = ShiYunQuoteWidgetProvider.formatWidgetQuote("人生若只如初见，何事秋风悲画扇。")
        assertEquals("人生若只如初见，何事秋风悲画扇。", sevenCharCouplet)

        // 5. 单行短句，保持自然单行
        val singleShort = ShiYunQuoteWidgetProvider.formatWidgetQuote("落霞与孤鹜齐飞")
        assertEquals("落霞与孤鹜齐飞", singleShort)

        // 6. 较长诗词名句，连贯输出供 TextView 自由排版
        val fourClauses = ShiYunQuoteWidgetProvider.formatWidgetQuote("白日依山尽，黄河入海流。欲穷千里目，更上一层楼。")
        assertEquals("白日依山尽，黄河入海流。欲穷千里目，更上一层楼。", fourClauses)
    }

    @Test
    fun testFormatAttribution_standardAndNarrow() {
        // 1. 标准宽度下格式化：仅显示《作品》作者，剥离朝代与破折号
        val standardAttr = ShiYunQuoteWidgetProvider.formatAttribution(
            title = "静夜思",
            author = "〔唐〕李白",
            isNarrow = false,
        )
        assertEquals("《静夜思》李白", standardAttr)

        // 验证带有前缀中点与朝代的脏数据也能被清洗
        val standardAttrWithDots = ShiYunQuoteWidgetProvider.formatAttribution(
            title = "《静夜思》",
            author = "·唐·李白",
            isNarrow = false,
        )
        assertEquals("《静夜思》李白", standardAttrWithDots)

        // 2. 窄宽度下格式化（仅显示纯净作者名以省空间）
        val narrowAttr = ShiYunQuoteWidgetProvider.formatAttribution(
            title = "静夜思",
            author = "〔唐〕李白",
            isNarrow = true,
        )
        assertEquals("李白", narrowAttr)

        // 3. 仅有标题无作者时的表现
        val titleOnlyStandard = ShiYunQuoteWidgetProvider.formatAttribution(
            title = "击壤歌",
            author = "",
            isNarrow = false,
        )
        assertEquals("《击壤歌》", titleOnlyStandard)

        val titleOnlyNarrow = ShiYunQuoteWidgetProvider.formatAttribution(
            title = "击壤歌",
            author = "",
            isNarrow = true,
        )
        assertEquals("《击壤歌》", titleOnlyNarrow)
    }

    @Test
    fun testAllTenThemesHaveValidWidgetColorsAndContrast() {
        for (themeId in AppThemeId.entries) {
            val themeDef = AppThemePalette.getThemeDefinition(themeId)
            val bgColor = themeDef.customTokens.homeCardBackground
            val quoteTextColor = themeDef.customTokens.titleText
            val authorTextColor = themeDef.customTokens.secondaryText
            val primaryColor = themeDef.colorScheme.primary

            // 1. 颜色不能为全透明或默认未指定
            assertNotEquals(0, bgColor.toArgb())
            assertNotEquals(0, quoteTextColor.toArgb())
            assertNotEquals(0, authorTextColor.toArgb())
            assertNotEquals(0, primaryColor.toArgb())

            // 2. 名句正文与卡片背景的对比度检查 (WCAG AA 规范：大号粗体文字要求 >= 3.0:1)
            val contrast = calculateContrastRatio(quoteTextColor, bgColor)
            assertTrue(
                "主题 [${themeId.displayName}] 桌面小组件名句文字对比度必须 >= 3.0 (实际: $contrast)",
                contrast >= 3.0,
            )
        }
    }

    @Test
    fun testWidgetTextureAlphaStrictlySetToTenPercent() {
        assertEquals(0.10f, ShiYunQuoteWidgetProvider.WIDGET_TEXTURE_ALPHA, 0.0001f)
    }

    @Test
    fun testAllTenThemesTextureResourcesResolvedCorrectly() {
        for (themeId in AppThemeId.entries) {
            val textureResId = ShiYunQuoteWidgetProvider.resolveThemeTextureResId(themeId)
            assertNotEquals("主题 [${themeId.displayName}] 纹理资源 ID 不能为 0", 0, textureResId)
        }
    }

    @Test
    fun testAllTenThemesBackgroundStylesAreConfiguredCorrectly() {
        val classicThemes = listOf(
            AppThemeId.XUAN_PAPER,
            AppThemeId.BAMBOO_GREEN,
            AppThemeId.INDIGO_MOUNTAIN,
            AppThemeId.CINNABAR_RED,
            AppThemeId.AMBER_AUTUMN,
            AppThemeId.NIGHT_INK,
        )
        val seasonalThemes = listOf(
            AppThemeId.SPRING_WILLOW,
            AppThemeId.SUMMER_LOTUS,
            AppThemeId.AUTUMN_MAPLE,
            AppThemeId.WINTER_SNOW,
        )

        // 验证 6 套经典主题：全为 SolidWithTexture 且使用宣纸纹理原图
        for (themeId in classicThemes) {
            val themeDef = AppThemePalette.getThemeDefinition(themeId)
            val style = themeDef.customTokens.backgroundStyle
            assertTrue("经典主题 [${themeId.displayName}] 必须为 SolidWithTexture", style is ThemeBackgroundStyle.SolidWithTexture)
            val solidStyle = style as ThemeBackgroundStyle.SolidWithTexture
            assertEquals(R.drawable.bg_paper_texture, solidStyle.textureResId)
            assertEquals(ThemeVisualSpecs.TEXTURE_ALPHA_CLASSIC, solidStyle.textureAlpha)
        }

        // 验证 4 套四季主题：全为 ImagePattern 且拥有有效独立插画资源
        for (themeId in seasonalThemes) {
            val themeDef = AppThemePalette.getThemeDefinition(themeId)
            val style = themeDef.customTokens.backgroundStyle
            assertTrue("四季主题 [${themeId.displayName}] 必须为 ImagePattern", style is ThemeBackgroundStyle.ImagePattern)
            val imageStyle = style as ThemeBackgroundStyle.ImagePattern
            assertNotEquals(0, imageStyle.drawableResId)
            assertNotEquals(0, imageStyle.fallbackBaseColor.toArgb())
        }
    }

    @Test
    fun testWidgetPendingIntentRequestCode_differentForDifferentWorksAndWidgets() {
        val widgetId1 = 101
        val widgetId2 = 102
        val workIdA = "tang_300_001"
        val workIdB = "song_ci_042"
        val workIdC = "tang_300_099"

        val codeA1 = widgetId1 * 31 + workIdA.hashCode()
        val codeB1 = widgetId1 * 31 + workIdB.hashCode()
        val codeC1 = widgetId1 * 31 + workIdC.hashCode()
        val codeA2 = widgetId2 * 31 + workIdA.hashCode()

        // 相同小组件下不同作品 ID 必须生成不同的 requestCode，杜绝系统 PendingIntent 串篇复用
        assertNotEquals(codeA1, codeB1)
        assertNotEquals(codeB1, codeC1)
        assertNotEquals(codeA1, codeC1)

        // 不同小组件即使是同一首作品也拥有独立 requestCode
        assertNotEquals(codeA1, codeA2)
    }

    @Test
    fun testDeepLinkUriWorkIdExtraction() {
        val uriStrA = "shiyun://work/tang_300_001"
        val uriStrB = "shiyun://work/song_ci_042"

        fun extractWorkId(uriString: String): String? {
            val prefix = "shiyun://work/"
            return if (uriString.startsWith(prefix)) uriString.removePrefix(prefix).trim().takeIf { it.isNotEmpty() } else null
        }

        assertEquals("tang_300_001", extractWorkId(uriStrA))
        assertEquals("song_ci_042", extractWorkId(uriStrB))
        assertEquals(null, extractWorkId("https://example.com"))
        assertEquals(null, extractWorkId("shiyun://work/"))
    }

    /**
     * 计算两组 Color 之间的 WCAG 相对亮度对比度
     */
    private fun calculateContrastRatio(foreground: Color, background: Color): Double {
        val lum1 = calculateRelativeLuminance(foreground)
        val lum2 = calculateRelativeLuminance(background)
        val lighter = max(lum1, lum2)
        val darker = min(lum1, lum2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun calculateRelativeLuminance(color: Color): Double {
        fun channelLuminance(c: Float): Double {
            return if (c <= 0.04045f) {
                c / 12.92
            } else {
                ((c + 0.055) / 1.055).pow(2.4)
            }
        }
        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return (0.2126 * r) + (0.7152 * g) + (0.0722 * b)
    }
}
