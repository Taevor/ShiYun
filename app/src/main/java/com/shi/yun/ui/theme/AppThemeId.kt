package com.shi.yun.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题分类：经典主题 vs 四时主题
 */
enum class ThemeCategory(val displayName: String) {
    CLASSIC("经典"),
    SEASONAL("四时"),
}

/**
 * 诗云全局 10 套预设主题标识
 *
 * 经典主题 (6套)：
 * 1. 宣纸古籍 - 典雅米黄，温润护眼（默认）
 * 2. 竹青雅韵 - 清风竹影，雅致静心
 * 3. 黛青山水 - 远岫烟峦，沉静深邃
 * 4. 朱砂雅集 - 金石文会，古朴温润
 * 5. 琥珀秋笺 - 金菊暖阳，古册书香
 * 6. 墨夜清辉 - 幽夜松烟，沉浸夜读（暗色）
 *
 * 四时主题 (4套)：
 * 7. 桃夭春晓 - 桃溪春晓，落英映水
 * 8. 荷风清韵 - 映日荷花，微风出岫
 * 9. 枫笺染霜 - 霜叶晚林，秋色连波
 * 10. 寒江雪隐 - 孤舟钓雪，素练苍茫
 */
enum class AppThemeId(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val category: ThemeCategory,
    val isDark: Boolean = false,
    val primaryPreviewColor: Color,
    val surfacePreviewColor: Color,
    val accentPreviewColor: Color,
) {
    // ========== 经典主题 (6套) ==========
    XUAN_PAPER(
        id = "xuan_paper",
        displayName = "宣纸古籍",
        subtitle = "典雅米黄 · 温润护眼",
        category = ThemeCategory.CLASSIC,
        isDark = false,
        primaryPreviewColor = Color(0xFF493A2B),
        surfacePreviewColor = Color(0xFFF6E9CF),
        accentPreviewColor = Color(0xFFB89B68),
    ),
    BAMBOO_GREEN(
        id = "bamboo_green",
        displayName = "竹青雅韵",
        subtitle = "清风竹影 · 雅致静心",
        category = ThemeCategory.CLASSIC,
        isDark = false,
        primaryPreviewColor = Color(0xFF304331),
        surfacePreviewColor = Color(0xFFE5F0DF),
        accentPreviewColor = Color(0xFF829A79),
    ),
    INDIGO_MOUNTAIN(
        id = "indigo_mountain",
        displayName = "黛青山水",
        subtitle = "远岫烟峦 · 沉静深邃",
        category = ThemeCategory.CLASSIC,
        isDark = false,
        primaryPreviewColor = Color(0xFF293F47),
        surfacePreviewColor = Color(0xFFE5EEF0),
        accentPreviewColor = Color(0xFF6F8F99),
    ),
    CINNABAR_RED(
        id = "cinnabar_red",
        displayName = "朱砂雅集",
        subtitle = "金石文会 · 古朴温润",
        category = ThemeCategory.CLASSIC,
        isDark = false,
        primaryPreviewColor = Color(0xFF4E2B2A),
        surfacePreviewColor = Color(0xFFF7E5DD),
        accentPreviewColor = Color(0xFFA85F53),
    ),
    AMBER_AUTUMN(
        id = "amber_autumn",
        displayName = "琥珀秋笺",
        subtitle = "金菊暖阳 · 古册书香",
        category = ThemeCategory.CLASSIC,
        isDark = false,
        primaryPreviewColor = Color(0xFF49371D),
        surfacePreviewColor = Color(0xFFF6E6C2),
        accentPreviewColor = Color(0xFFA9823F),
    ),
    NIGHT_INK(
        id = "night_ink",
        displayName = "墨夜清辉",
        subtitle = "幽夜松烟 · 沉浸夜读",
        category = ThemeCategory.CLASSIC,
        isDark = true,
        primaryPreviewColor = Color(0xFFE9D8A8),
        surfacePreviewColor = Color(0xFF34475B),
        accentPreviewColor = Color(0xFF526A80),
    ),

    // ========== 四时主题 (4套) ==========
    SPRING_WILLOW(
        id = "spring_willow",
        displayName = "桃夭春晓",
        subtitle = "桃溪春晓 · 落英映水",
        category = ThemeCategory.SEASONAL,
        isDark = false,
        primaryPreviewColor = Color(0xFF7B384A),
        surfacePreviewColor = Color(0xFFF5E8EA),
        accentPreviewColor = Color(0xFFC95B75),
    ),
    SUMMER_LOTUS(
        id = "summer_lotus",
        displayName = "荷风清韵",
        subtitle = "映日荷花 · 微风出岫",
        category = ThemeCategory.SEASONAL,
        isDark = false,
        primaryPreviewColor = Color(0xFF235E4E),
        surfacePreviewColor = Color(0xFFDFEFEA),
        accentPreviewColor = Color(0xFFC24F68),
    ),
    AUTUMN_MAPLE(
        id = "autumn_maple",
        displayName = "枫笺染霜",
        subtitle = "霜叶晚林 · 秋色连波",
        category = ThemeCategory.SEASONAL,
        isDark = false,
        primaryPreviewColor = Color(0xFF944630),
        surfacePreviewColor = Color(0xFFF7F2EC),
        accentPreviewColor = Color(0xFFB87B2E),
    ),
    WINTER_SNOW(
        id = "winter_snow",
        displayName = "寒江雪隐",
        subtitle = "孤舟钓雪 · 素练苍茫",
        category = ThemeCategory.SEASONAL,
        isDark = false,
        primaryPreviewColor = Color(0xFF375069),
        surfacePreviewColor = Color(0xFFEFF3F6),
        accentPreviewColor = Color(0xFF9A4232),
    );

    companion object {
        val Default = XUAN_PAPER

        fun fromId(rawId: String?): AppThemeId {
            if (rawId.isNullOrBlank()) return Default
            val trimmed = rawId.trim()
            return entries.firstOrNull {
                it.id.equals(trimmed, ignoreCase = true) || it.name.equals(trimmed, ignoreCase = true)
            } ?: Default
        }

        fun getByCategory(category: ThemeCategory): List<AppThemeId> {
            return entries.filter { it.category == category }
        }
    }
}
