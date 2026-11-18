package com.shi.yun.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import com.shi.yun.data.repository.ThemePreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 阶段 A & B：全局主题系统数据模型、调色板完整性、仓储契约与文字可读性对比度单元测试
 */
class ThemeTest {

    @Test
    fun testTenThemesClassificationAndUniqueness() {
        // 1. 验证总共有且仅有 10 套预设主题
        assertEquals("全局主题数量必须恰好为 10 套", 10, AppThemeId.entries.size)

        // 2. 验证经典主题与四时主题分组精确性
        val classicThemes = AppThemeId.getByCategory(ThemeCategory.CLASSIC)
        val seasonalThemes = AppThemeId.getByCategory(ThemeCategory.SEASONAL)
        assertEquals("经典主题必须包含 6 套", 6, classicThemes.size)
        assertEquals("四时主题必须包含 4 套", 4, seasonalThemes.size)

        // 3. 验证每套主题 ID 与显示名称全局唯一
        val ids = AppThemeId.entries.map { it.id }
        assertEquals("所有主题 ID 必须全局唯一", ids.size, ids.distinct().size)

        val names = AppThemeId.entries.map { it.displayName }
        assertEquals("所有主题显示名称必须全局唯一", names.size, names.distinct().size)

        // 4. 验证默认主题为宣纸古籍
        assertEquals(AppThemeId.XUAN_PAPER, AppThemeId.Default)

        // 5. 验证暗色模式标记仅对墨夜清辉生效
        for (theme in AppThemeId.entries) {
            if (theme == AppThemeId.NIGHT_INK) {
                assertTrue("${theme.displayName} 必须是暗色主题", theme.isDark)
            } else {
                assertFalse("${theme.displayName} 必须是浅色/日间主题", theme.isDark)
            }
        }
    }

    @Test
    fun testThemeFromIdFallbackAndParsing() {
        assertEquals(AppThemeId.XUAN_PAPER, AppThemeId.fromId("xuan_paper"))
        assertEquals(AppThemeId.BAMBOO_GREEN, AppThemeId.fromId("bamboo_green"))
        assertEquals(AppThemeId.INDIGO_MOUNTAIN, AppThemeId.fromId("INDIGO_MOUNTAIN")) // 大小写不敏感
        assertEquals(AppThemeId.CINNABAR_RED, AppThemeId.fromId("  cinnabar_red  ")) // 自动去空
        assertEquals(AppThemeId.AMBER_AUTUMN, AppThemeId.fromId("amber_autumn"))
        assertEquals(AppThemeId.NIGHT_INK, AppThemeId.fromId("night_ink"))
        assertEquals(AppThemeId.SPRING_WILLOW, AppThemeId.fromId("spring_willow"))
        assertEquals(AppThemeId.SUMMER_LOTUS, AppThemeId.fromId("summer_lotus"))
        assertEquals(AppThemeId.AUTUMN_MAPLE, AppThemeId.fromId("autumn_maple"))
        assertEquals(AppThemeId.WINTER_SNOW, AppThemeId.fromId("winter_snow"))

        // 容错降级回默认宣纸古籍
        assertEquals(AppThemeId.Default, AppThemeId.fromId(null))
        assertEquals(AppThemeId.Default, AppThemeId.fromId(""))
        assertEquals(AppThemeId.Default, AppThemeId.fromId("non_existent_theme_id"))
    }

    @Test
    fun testAllThemesColorSchemeCompleteness() {
        for (theme in AppThemeId.entries) {
            val def = AppThemePalette.getThemeDefinition(theme)
            assertNotNull("主题定义体不能为空: ${theme.id}", def)
            assertEquals("主题标识必须一致", theme, def.themeId)

            val cs = def.colorScheme
            assertTrue("${theme.id} primary 必须已指定", cs.primary.isSpecified)
            assertTrue("${theme.id} onPrimary 必须已指定", cs.onPrimary.isSpecified)
            assertTrue("${theme.id} primaryContainer 必须已指定", cs.primaryContainer.isSpecified)
            assertTrue("${theme.id} secondary 必须已指定", cs.secondary.isSpecified)
            assertTrue("${theme.id} tertiary 必须已指定", cs.tertiary.isSpecified)
            assertTrue("${theme.id} surface 必须已指定", cs.surface.isSpecified)
            assertTrue("${theme.id} onSurface 必须已指定", cs.onSurface.isSpecified)
            assertTrue("${theme.id} background 必须已指定", cs.background.isSpecified)
            assertTrue("${theme.id} onBackground 必须已指定", cs.onBackground.isSpecified)
            assertTrue("${theme.id} surfaceVariant 必须已指定", cs.surfaceVariant.isSpecified)
            assertTrue("${theme.id} onSurfaceVariant 必须已指定", cs.onSurfaceVariant.isSpecified)
            assertTrue("${theme.id} outline 必须已指定", cs.outline.isSpecified)
            assertTrue("${theme.id} outlineVariant 必须已指定", cs.outlineVariant.isSpecified)

            // 扩展属性有效性校验
            val tokens = def.customTokens
            assertTrue("${theme.id} homeCardBackground 必须已指定", tokens.homeCardBackground.isSpecified)
            assertTrue("${theme.id} categoryTagBackground 必须已指定", tokens.categoryTagBackground.isSpecified)
            assertTrue("${theme.id} bottomNavBackground 必须已指定", tokens.bottomNavBackground.isSpecified)
            assertTrue("${theme.id} bottomNavIconColor 必须已指定", tokens.bottomNavIconColor.isSpecified)
            assertTrue("${theme.id} cardBackground 必须已指定", tokens.cardBackground.isSpecified)
            assertTrue("${theme.id} cardBorder 必须已指定", tokens.cardBorder.isSpecified)
            assertTrue("${theme.id} statusBarProtectionColor 必须已指定", tokens.statusBarProtectionColor.isSpecified)
            assertTrue("${theme.id} heartFavoriteTint 必须已指定", tokens.heartFavoriteTint.isSpecified)
            assertTrue("${theme.id} titleText 必须已指定", tokens.titleText.isSpecified)
            assertTrue("${theme.id} bodyText 必须已指定", tokens.bodyText.isSpecified)
            assertTrue("${theme.id} secondaryText 必须已指定", tokens.secondaryText.isSpecified)

            // 背景样式架构断言：6套经典主题为纯色纸张纹理背景，4套季节主题为独立图片模式
            when (theme.category) {
                ThemeCategory.CLASSIC -> {
                    assertTrue(
                        "经典主题【${theme.displayName}】必须使用 ThemeBackgroundStyle.SolidWithTexture",
                        tokens.backgroundStyle is ThemeBackgroundStyle.SolidWithTexture,
                    )
                    val solid = tokens.backgroundStyle as ThemeBackgroundStyle.SolidWithTexture
                    assertTrue("${theme.displayName} solidColor 必须已指定", solid.solidColor.isSpecified)
                    assertEquals("经典主题纹理透明度统一规范为 0.15f", ThemeVisualSpecs.TEXTURE_ALPHA_CLASSIC, solid.textureAlpha)
                    assertEquals("经典主题兼容属性 backgroundResId 必须为 0", 0, tokens.backgroundResId)
                }
                ThemeCategory.SEASONAL -> {
                    assertTrue(
                        "季节主题【${theme.displayName}】必须使用 ThemeBackgroundStyle.ImagePattern",
                        tokens.backgroundStyle is ThemeBackgroundStyle.ImagePattern,
                    )
                    val pattern = tokens.backgroundStyle as ThemeBackgroundStyle.ImagePattern
                    assertTrue("${theme.displayName} pattern drawableResId 必须有效指向图片资源", pattern.drawableResId != 0)
                    assertTrue("${theme.displayName} pattern fallbackBaseColor 必须已指定", pattern.fallbackBaseColor.isSpecified)
                    assertEquals("季节主题兼容属性 backgroundResId 必须与 pattern.drawableResId 一致", pattern.drawableResId, tokens.backgroundResId)
                }
            }
        }

        // 默认宣纸古籍主题必须保持无额外图层染色 (Color.Transparent)，确保验收效果 100% 不变形
        val defaultDef = AppThemePalette.getThemeDefinition(AppThemeId.Default)
        assertEquals("宣纸古籍默认背景混色必须为完全透明", Color.Transparent, defaultDef.customTokens.overlayBlendColor)
    }

    @Test
    fun testPoemReadabilityContrastWcagAALevel() {
        for (theme in AppThemeId.entries) {
            val def = AppThemePalette.getThemeDefinition(theme)
            val cs = def.colorScheme
            val tokens = def.customTokens

            // 1. 验证标题文字在卡片底色的对比度 (核心阅读区，WCAG AA 阈值要求 >= 4.5:1，实测均达 6.9:1 ~ 8.8:1)
            val titleCardContrast = calculateContrastRatio(tokens.titleText, tokens.homeCardBackground)
            assertTrue(
                "主题【${theme.displayName}】标题 titleText 在首页卡片底色对比度必须 >= 4.5，实测: $titleCardContrast",
                titleCardContrast >= 4.5,
            )

            // 2. 验证正文字体在卡片底色的对比度 (WCAG AA 阈值要求 >= 4.5:1)
            val bodyCardContrast = calculateContrastRatio(tokens.bodyText, tokens.homeCardBackground)
            assertTrue(
                "主题【${theme.displayName}】正文 bodyText 在首页卡片底色对比度必须达到 WCAG AA (>=4.5)，实测: $bodyCardContrast",
                bodyCardContrast >= 4.5,
            )

            // 3. 验证辅助文字在卡片底色的对比度 (WCAG AA 辅助文字阈值要求 >= 3.0:1)
            val secCardContrast = calculateContrastRatio(tokens.secondaryText, tokens.homeCardBackground)
            assertTrue(
                "主题【${theme.displayName}】辅助文字 secondaryText 在首页卡片底色对比度必须 >= 3.0，实测: $secCardContrast",
                secCardContrast >= 3.0,
            )

            // 4. 验证底部导航图标在导航栏底色上的对比度 (WCAG AA 图形元素阈值要求 >= 3.0:1)
            val navIconContrast = calculateContrastRatio(tokens.bottomNavIconColor, tokens.bottomNavBackground)
            assertTrue(
                "主题【${theme.displayName}】底栏图标 bottomNavIconColor 对比度必须 >= 3.0，实测: $navIconContrast",
                navIconContrast >= 3.0,
            )

            // 5. 验证按钮主文字在主品牌色上的清晰度 (>= 3.0:1)
            val primaryContrast = calculateContrastRatio(cs.onPrimary, cs.primary)
            assertTrue(
                "主题【${theme.displayName}】主按钮文字 onPrimary 对比度必须 >= 3.0，实测: $primaryContrast",
                primaryContrast >= 3.0,
            )
        }
    }

    @Test
    fun testThemePreferencesRepositoryContract() = runBlocking {
        class FakeThemePreferencesRepository(
            initialThemeId: AppThemeId = AppThemeId.Default,
        ) : ThemePreferencesRepository {
            private val _flow = MutableStateFlow(initialThemeId)
            override val currentThemeIdFlow: StateFlow<AppThemeId> = _flow
            override suspend fun updateThemeId(themeId: AppThemeId) {
                _flow.value = themeId
            }
            override fun getCurrentThemeId(): AppThemeId = _flow.value
        }

        val fakeRepo = FakeThemePreferencesRepository()
        assertEquals(AppThemeId.XUAN_PAPER, fakeRepo.getCurrentThemeId())
        assertEquals(AppThemeId.XUAN_PAPER, fakeRepo.currentThemeIdFlow.value)

        fakeRepo.updateThemeId(AppThemeId.BAMBOO_GREEN)
        assertEquals(AppThemeId.BAMBOO_GREEN, fakeRepo.getCurrentThemeId())
        assertEquals(AppThemeId.BAMBOO_GREEN, fakeRepo.currentThemeIdFlow.value)

        fakeRepo.updateThemeId(AppThemeId.NIGHT_INK)
        assertEquals(AppThemeId.NIGHT_INK, fakeRepo.getCurrentThemeId())
        assertEquals(AppThemeId.NIGHT_INK, fakeRepo.currentThemeIdFlow.value)
    }

    @Test
    fun testThemePreviewColorsCompleteness() {
        for (theme in AppThemeId.entries) {
            assertTrue("主预览色彩必须是有效颜色", theme.primaryPreviewColor.isSpecified)
            assertTrue("表面预览色彩必须是有效颜色", theme.surfacePreviewColor.isSpecified)
            assertTrue("强调预览色彩必须是有效颜色", theme.accentPreviewColor.isSpecified)
            assertTrue("副标题不能为空", theme.subtitle.isNotBlank())
        }
    }

    /**
     * W3C WCAG 2.1 相对亮度与对比度标准算法实现
     */
    private fun calculateRelativeLuminance(color: Color): Double {
        fun channelToLinear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = channelToLinear(color.red)
        val g = channelToLinear(color.green)
        val b = channelToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun calculateContrastRatio(c1: Color, c2: Color): Double {
        val l1 = calculateRelativeLuminance(c1)
        val l2 = calculateRelativeLuminance(c2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
    @Test
    fun testGlobalThemeVisualSpecsAndResolution() {
        // 1. 验证全局主题视觉规范常量符合设计基准
        assertEquals(0.42f, ThemeVisualSpecs.CARD_BG_ALPHA_LIGHT, 0.001f)
        assertEquals(0.60f, ThemeVisualSpecs.CARD_BG_ALPHA_DARK, 0.001f)
        assertEquals(0.75f, ThemeVisualSpecs.NAV_BG_ALPHA_LIGHT, 0.001f)
        assertEquals(0.85f, ThemeVisualSpecs.NAV_BG_ALPHA_DARK, 0.001f)
        assertEquals(0.25f, ThemeVisualSpecs.CARD_BORDER_ALPHA_LIGHT, 0.001f)
        assertEquals(0.40f, ThemeVisualSpecs.CARD_BORDER_ALPHA_DARK, 0.001f)
        assertEquals(0.12f, ThemeVisualSpecs.NAV_DIVIDER_ALPHA_LIGHT, 0.001f)
        assertEquals(0.20f, ThemeVisualSpecs.NAV_DIVIDER_ALPHA_DARK, 0.001f)
        assertEquals(0.15f, ThemeVisualSpecs.TEXTURE_ALPHA_CLASSIC, 0.001f)

        // 2. 验证所有 10 套主题自动继承统一的卡片与底栏透明度计算
        for (theme in AppThemeId.entries) {
            val def = AppThemePalette.getThemeDefinition(theme)
            val tokens = def.customTokens
            val isDark = theme.isDark

            val expectedCardAlpha = if (isDark) 0.60f else 0.42f
            val expectedBorderAlpha = if (isDark) 0.40f else 0.25f
            val expectedNavAlpha = if (isDark) 0.85f else 0.75f
            val expectedDividerAlpha = if (isDark) 0.20f else 0.12f

            assertEquals(
                "主题【${theme.displayName}】卡片底色透明度必须为 $expectedCardAlpha",
                expectedCardAlpha,
                tokens.resolvedCardBackground(isDark).alpha,
                0.01f,
            )
            assertEquals(
                "主题【${theme.displayName}】卡片边框透明度必须为 $expectedBorderAlpha",
                expectedBorderAlpha,
                tokens.resolvedCardBorder(isDark).alpha,
                0.01f,
            )
            assertEquals(
                "主题【${theme.displayName}】底部导航栏透明度必须为 $expectedNavAlpha",
                expectedNavAlpha,
                tokens.resolvedNavBackground(isDark).alpha,
                0.01f,
            )
            assertEquals(
                "主题【${theme.displayName}】导航栏分割线透明度必须为 $expectedDividerAlpha",
                expectedDividerAlpha,
                tokens.resolvedNavDividerColor(isDark).alpha,
                0.01f,
            )
        }
    }
}
