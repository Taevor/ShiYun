package com.shi.yun.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.shi.yun.R

/**
 * 诗云 10 套预设全局主题调色板与定义注册表
 *
 * 包含 6 套经典纯色纸张纹理主题与 4 套四时主题：
 * 1. 六套经典主题：纯色背景叠加 Linear Light 线性光纸张纹理 (Alpha=0.25f)；
 * 2. 首页卡片、分类标签、底部导航拥有独立解耦的专属配色；
 * 3. 严格遵循中国传统色色彩规范与 WCAG AA 对比度；
 * 4. 四季主题保持原有水墨插画、遮罩与颜色配置完全不变。
 */
object AppThemePalette {

    // ========== 1. 宣纸古籍 (默认经典) ==========
    private val XuanPaperColorScheme = lightColorScheme(
        primary = Color(0xFF493A2B),
        onPrimary = Color(0xFFE9DEC8),
        primaryContainer = Color(0xFFE9DEC8),
        onPrimaryContainer = Color(0xFF493A2B),
        secondary = Color(0xFFB89B68),
        onSecondary = Color(0xFF493A2B),
        secondaryContainer = Color(0xFFE9DEC8),
        onSecondaryContainer = Color(0xFF493A2B),
        tertiary = Color(0xFFB83A2A),
        onTertiary = Color.White,
        background = Color(0xFFF6E9CF),
        onBackground = Color(0xFF493A2B),
        surface = Color(0xFFF6E9CF),
        onSurface = Color(0xFF493A2B),
        surfaceVariant = Color(0xFFE9DEC8),
        onSurfaceVariant = Color(0xFF493A2B),
        outline = Color(0xFF6B5843),
        outlineVariant = Color(0x26493A2B),
    )
    private val XuanPaperTokens = AppThemeCustomTokens(
        homeCardBackground = Color(0xFFE9DEC8),
        categoryTagBackground = Color(0xFFB89B68),
        categoryTagTextColor = Color(0xFF493A2B),
        bottomNavBackground = Color(0xFFC5AD82),
        bottomNavIconColor = XuanPaperColorScheme.primary,
        cardBackground = Color(0xFFE9DEC8),
        cardBorder = Color(0x26493A2B),
        overlayBlendColor = Color.Transparent,
        statusBarProtectionColor = Color(0xFFF6E9CF),
        heartFavoriteTint = Color(0xFFB83A2A),
        titleText = Color(0xFF493A2B),
        bodyText = Color(0xFF493A2B),
        secondaryText = Color(0xFF6B5843),
        backgroundStyle = ThemeBackgroundStyle.SolidWithTexture(
            solidColor = Color(0xFFF6E9CF),
            textureResId = R.drawable.bg_paper_texture,
            textureAlpha = ThemeVisualSpecs.TEXTURE_ALPHA_CLASSIC,
        ),
    )

    // ========== 2. 竹青雅韵 (经典) ==========
    private val BambooGreenColorScheme = lightColorScheme(
        primary = Color(0xFF304331),
        onPrimary = Color(0xFFD5E0D0),
        primaryContainer = Color(0xFFD5E0D0),
        onPrimaryContainer = Color(0xFF304331),
        secondary = Color(0xFF829A79),
        onSecondary = Color(0xFF304331),
        secondaryContainer = Color(0xFFD5E0D0),
        onSecondaryContainer = Color(0xFF304331),
        tertiary = Color(0xFFA0562D),
        onTertiary = Color.White,
        background = Color(0xFFE5F0DF),
        onBackground = Color(0xFF304331),
        surface = Color(0xFFE5F0DF),
        onSurface = Color(0xFF304331),
        surfaceVariant = Color(0xFFD5E0D0),
        onSurfaceVariant = Color(0xFF304331),
        outline = Color(0xFF556A5E),
        outlineVariant = Color(0x26304331),
    )
    private val BambooGreenTokens = AppThemeCustomTokens(
        homeCardBackground = Color(0xFFD5E0D0),
        categoryTagBackground = Color(0xFF829A79),
        categoryTagTextColor = Color(0xFF304331),
        bottomNavBackground = Color(0xFF8FA58A),
        bottomNavIconColor = BambooGreenColorScheme.primary,
        cardBackground = Color(0xFFD5E0D0),
        cardBorder = Color(0x26304331),
        overlayBlendColor = Color.Transparent,
        statusBarProtectionColor = Color(0xFFE5F0DF),
        heartFavoriteTint = Color(0xFFB83A2A),
        titleText = Color(0xFF304331),
        bodyText = Color(0xFF304331),
        secondaryText = Color(0xFF556A5E),
        backgroundStyle = ThemeBackgroundStyle.SolidWithTexture(
            solidColor = Color(0xFFE5F0DF),
            textureResId = R.drawable.bg_paper_texture,
            textureAlpha = ThemeVisualSpecs.TEXTURE_ALPHA_CLASSIC,
        ),
    )

    // ========== 3. 黛青山水 (经典) ==========
    private val IndigoMountainColorScheme = lightColorScheme(
        primary = Color(0xFF293F47),
        onPrimary = Color(0xFFD6E1E3),
        primaryContainer = Color(0xFFD6E1E3),
        onPrimaryContainer = Color(0xFF293F47),
        secondary = Color(0xFF6F8F99),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD6E1E3),
        onSecondaryContainer = Color(0xFF293F47),
        tertiary = Color(0xFF9A3E34),
        onTertiary = Color.White,
        background = Color(0xFFE5EEF0),
        onBackground = Color(0xFF293F47),
        surface = Color(0xFFE5EEF0),
        onSurface = Color(0xFF293F47),
        surfaceVariant = Color(0xFFD6E1E3),
        onSurfaceVariant = Color(0xFF293F47),
        outline = Color(0xFF546678),
        outlineVariant = Color(0x26293F47),
    )
    private val IndigoMountainTokens = AppThemeCustomTokens(
        homeCardBackground = Color(0xFFD6E1E3),
        categoryTagBackground = Color(0xFF6F8F99),
        bottomNavBackground = Color(0xFF78949D),
        bottomNavIconColor = IndigoMountainColorScheme.primary,
        cardBackground = Color(0xFFD6E1E3),
        cardBorder = Color(0x26293F47),
        overlayBlendColor = Color.Transparent,
        statusBarProtectionColor = Color(0xFFE5EEF0),
        heartFavoriteTint = Color(0xFF9A3E34),
        titleText = Color(0xFF293F47),
        bodyText = Color(0xFF293F47),
        secondaryText = Color(0xFF546678),
        backgroundStyle = ThemeBackgroundStyle.SolidWithTexture(
            solidColor = Color(0xFFE5EEF0),
            textureResId = R.drawable.bg_paper_texture,
            textureAlpha = ThemeVisualSpecs.TEXTURE_ALPHA_CLASSIC,
        ),
    )

    // ========== 4. 朱砂雅集 (经典) ==========
    private val CinnabarRedColorScheme = lightColorScheme(
        primary = Color(0xFF4E2B2A),
        onPrimary = Color(0xFFEBD5CF),
        primaryContainer = Color(0xFFEBD5CF),
        onPrimaryContainer = Color(0xFF4E2B2A),
        secondary = Color(0xFFA85F53),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEBD5CF),
        onSecondaryContainer = Color(0xFF4E2B2A),
        tertiary = Color(0xFFB83A2A),
        onTertiary = Color.White,
        background = Color(0xFFF7E5DD),
        onBackground = Color(0xFF4E2B2A),
        surface = Color(0xFFF7E5DD),
        onSurface = Color(0xFF4E2B2A),
        surfaceVariant = Color(0xFFEBD5CF),
        onSurfaceVariant = Color(0xFF4E2B2A),
        outline = Color(0xFF6E5550),
        outlineVariant = Color(0x264E2B2A),
    )
    private val CinnabarRedTokens = AppThemeCustomTokens(
        homeCardBackground = Color(0xFFEBD5CF),
        categoryTagBackground = Color(0xFFA85F53),
        bottomNavBackground = Color(0xFFA85F53),
        bottomNavIconColor = CinnabarRedColorScheme.primary,
        cardBackground = Color(0xFFEBD5CF),
        cardBorder = Color(0x264E2B2A),
        overlayBlendColor = Color.Transparent,
        statusBarProtectionColor = Color(0xFFF7E5DD),
        heartFavoriteTint = Color(0xFFB83A2A),
        titleText = Color(0xFF4E2B2A),
        bodyText = Color(0xFF4E2B2A),
        secondaryText = Color(0xFF6E5550),
        backgroundStyle = ThemeBackgroundStyle.SolidWithTexture(
            solidColor = Color(0xFFF7E5DD),
            textureResId = R.drawable.bg_paper_texture,
            textureAlpha = ThemeVisualSpecs.TEXTURE_ALPHA_CLASSIC,
        ),
    )

    // ========== 5. 琥珀秋笺 (经典) ==========
    private val AmberAutumnColorScheme = lightColorScheme(
        primary = Color(0xFF49371D),
        onPrimary = Color(0xFFE9DAB8),
        primaryContainer = Color(0xFFE9DAB8),
        onPrimaryContainer = Color(0xFF49371D),
        secondary = Color(0xFFA9823F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9DAB8),
        onSecondaryContainer = Color(0xFF49371D),
        tertiary = Color(0xFFB24218),
        onTertiary = Color.White,
        background = Color(0xFFF6E6C2),
        onBackground = Color(0xFF49371D),
        surface = Color(0xFFF6E6C2),
        onSurface = Color(0xFF49371D),
        surfaceVariant = Color(0xFFE9DAB8),
        onSurfaceVariant = Color(0xFF49371D),
        outline = Color(0xFF6A5B47),
        outlineVariant = Color(0x2649371D),
    )
    private val AmberAutumnTokens = AppThemeCustomTokens(
        homeCardBackground = Color(0xFFE9DAB8),
        categoryTagBackground = Color(0xFFA9823F),
        bottomNavBackground = Color(0xFFA47F3D),
        bottomNavIconColor = AmberAutumnColorScheme.primary,
        cardBackground = Color(0xFFE9DAB8),
        cardBorder = Color(0x2649371D),
        overlayBlendColor = Color.Transparent,
        statusBarProtectionColor = Color(0xFFF6E6C2),
        heartFavoriteTint = Color(0xFFB24218),
        titleText = Color(0xFF49371D),
        bodyText = Color(0xFF49371D),
        secondaryText = Color(0xFF6A5B47),
        backgroundStyle = ThemeBackgroundStyle.SolidWithTexture(
            solidColor = Color(0xFFF6E6C2),
            textureResId = R.drawable.bg_paper_texture,
            textureAlpha = ThemeVisualSpecs.TEXTURE_ALPHA_CLASSIC,
        ),
    )

    // ========== 6. 墨夜清辉 (经典暗色) ==========
    private val NightInkColorScheme = darkColorScheme(
        primary = Color(0xFFE9D8A8),
        onPrimary = Color(0xFF192B3E),
        primaryContainer = Color(0xFF354A60),
        onPrimaryContainer = Color(0xFFE9D8A8),
        secondary = Color(0xFF526A80),
        onSecondary = Color(0xFFE6E0D2),
        secondaryContainer = Color(0xFF354A60),
        onSecondaryContainer = Color(0xFFE9D8A8),
        tertiary = Color(0xFFD66A5B),
        onTertiary = Color.White,
        background = Color(0xFF34475B),
        onBackground = Color(0xFFE6E0D2),
        surface = Color(0xFF34475B),
        onSurface = Color(0xFFE6E0D2),
        surfaceVariant = Color(0xFF354A60),
        onSurfaceVariant = Color(0xFFE6E0D2),
        outline = Color(0xFFB0B7BA),
        outlineVariant = Color(0x3DE6E0D2),
    )
    private val NightInkTokens = AppThemeCustomTokens(
        homeCardBackground = Color(0xFF354A60),
        categoryTagBackground = Color(0xFF526A80),
        bottomNavBackground = Color(0xFF192B3E),
        bottomNavIconColor = NightInkColorScheme.primary,
        cardBackground = Color(0xFF354A60),
        cardBorder = Color(0x3DE6E0D2),
        overlayBlendColor = Color.Transparent,
        statusBarProtectionColor = Color(0xFF34475B),
        heartFavoriteTint = Color(0xFFD66A5B),
        titleText = Color(0xFFE6E0D2),
        bodyText = Color(0xFFE6E0D2),
        secondaryText = Color(0xFFB0B7BA),
        backgroundStyle = ThemeBackgroundStyle.SolidWithTexture(
            solidColor = Color(0xFF34475B),
            textureResId = R.drawable.bg_paper_texture,
            textureAlpha = ThemeVisualSpecs.TEXTURE_ALPHA_CLASSIC,
        ),
    )

    // ========== 7. 桃夭春晓 (四时春) ==========
    private val SpringWillowColorScheme = lightColorScheme(
        primary = Color(0xFF7B384A),
        onPrimary = Color(0xFFFFF5F6),
        primaryContainer = Color(0xFFF5E8EA),
        onPrimaryContainer = Color(0xFF7B384A),
        secondary = Color(0xFF5E7153),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8EFE5),
        onSecondaryContainer = Color(0xFF394C30),
        tertiary = Color(0xFFC95B75),
        onTertiary = Color.White,
        background = Color(0xFFFAF4F3),
        onBackground = Color(0xFF261418),
        surface = Color(0xFFFAF4F3),
        onSurface = Color(0xFF261418),
        surfaceVariant = Color(0xFFF5E8EA),
        onSurfaceVariant = Color(0xFF381F25),
        outline = Color(0xFF74525B),
        outlineVariant = Color(0x26261418),
    )
    private val SpringWillowTokens = AppThemeCustomTokens(
        homeCardBackground = Color(0xFFF5E8EA),
        categoryTagBackground = Color(0xFFE8CFD4),
        bottomNavBackground = Color(0xFFF0E0E3),
        bottomNavIconColor = Color(0xFF7B384A),
        cardBackground = Color(0xFFF5E8EA),
        cardBorder = Color(0x26261418),
        overlayBlendColor = Color.Transparent,
        statusBarProtectionColor = Color(0xFFFAF4F3),
        heartFavoriteTint = Color(0xFFC95B75),
        titleText = Color(0xFF261418),
        bodyText = Color(0xFF381F25),
        secondaryText = Color(0xFF74525B),
        backgroundStyle = ThemeBackgroundStyle.ImagePattern(
            drawableResId = R.drawable.bg_home_spring,
            fallbackBaseColor = Color(0xFFFAF4F3),
            scrimColor = Color(0xFFEFEAE6),
            scrimAlpha = 0.6f,
        ),
    )

    // ========== 8. 荷风清韵 (四时夏) ==========
    private val SummerLotusColorScheme = lightColorScheme(
        primary = Color(0xFF235E4E),
        onPrimary = Color(0xFFF0F8F5),
        primaryContainer = Color(0xFFDFEFEA),
        onPrimaryContainer = Color(0xFF235E4E),
        secondary = Color(0xFF3D7868),
        onSecondary = Color(0xFFF0F8F5),
        secondaryContainer = Color(0xFFDFEFEA),
        onSecondaryContainer = Color(0xFF235E4E),
        tertiary = Color(0xFFC24F68),
        onTertiary = Color.White,
        background = Color(0xFFEFF6F4),
        onBackground = Color(0xFF11231C),
        surface = Color(0xFFEFF6F4),
        onSurface = Color(0xFF11231C),
        surfaceVariant = Color(0xFFDFEFEA),
        onSurfaceVariant = Color(0xFF1F382E),
        outline = Color(0xFF4B6B5F),
        outlineVariant = Color(0x2611231C),
    )
    private val SummerLotusTokens = AppThemeCustomTokens(
        homeCardBackground = Color(0xFFDFEFEA),
        categoryTagBackground = Color(0xFFC6DFD7),
        bottomNavBackground = Color(0xFFD3E7E1),
        bottomNavIconColor = Color(0xFF235E4E),
        cardBackground = Color(0xFFDFEFEA),
        cardBorder = Color(0x2611231C),
        overlayBlendColor = Color.Transparent,
        statusBarProtectionColor = Color(0xFFEFF6F4),
        heartFavoriteTint = Color(0xFFC24F68),
        titleText = Color(0xFF11231C),
        bodyText = Color(0xFF1F382E),
        secondaryText = Color(0xFF4B6B5F),
        backgroundStyle = ThemeBackgroundStyle.ImagePattern(
            drawableResId = R.drawable.bg_home_lotus,
            fallbackBaseColor = Color(0xFFEFF6F4),
            scrimColor = Color(0xFFE8EDE9),
            scrimAlpha = 0.6f,
        ),
    )

    // ========== 9. 枫笺染霜 (四时秋) ==========
    private val AutumnMapleColorScheme = lightColorScheme(
        primary = Color(0xFF8D3B26),
        onPrimary = Color(0xFFF6EEE6),
        primaryContainer = Color(0xFFEBDFD4),
        onPrimaryContainer = Color(0xFF8D3B26),
        secondary = Color(0xFFB26E22),
        onSecondary = Color(0xFFF6EEE6),
        secondaryContainer = Color(0xFFEBDFD4),
        onSecondaryContainer = Color(0xFFB26E22),
        tertiary = Color(0xFFB83A2A),
        onTertiary = Color.White,
        background = Color(0xFFF6EEE6),
        onBackground = Color(0xFF241611),
        surface = Color(0xFFF6EEE6),
        onSurface = Color(0xFF241611),
        surfaceVariant = Color(0xFFEBDFD4),
        onSurfaceVariant = Color(0xFF392620),
        outline = Color(0xFF705750),
        outlineVariant = Color(0x26241611),
    )
    private val AutumnMapleTokens = AppThemeCustomTokens(
        homeCardBackground = Color(0xFFEBDFD4),
        categoryTagBackground = Color(0xFFDFCEBF),
        bottomNavBackground = Color(0xFFE4D5C7),
        bottomNavIconColor = Color(0xFF8D3B26),
        cardBackground = Color(0xFFEBDFD4),
        cardBorder = Color(0x26241611),
        overlayBlendColor = Color.Transparent,
        statusBarProtectionColor = Color(0xFFF6EEE6),
        heartFavoriteTint = Color(0xFFB83A2A),
        titleText = Color(0xFF241611),
        bodyText = Color(0xFF392620),
        secondaryText = Color(0xFF705750),
        backgroundStyle = ThemeBackgroundStyle.ImagePattern(
            drawableResId = R.drawable.bg_home_maple,
            fallbackBaseColor = Color(0xFFF6EEE6),
            scrimColor = Color(0xFFEFE8E1),
            scrimAlpha = 0.6f,
        ),
    )

    // ========== 10. 寒江雪隐 (四时冬) ==========
    private val WinterSnowColorScheme = lightColorScheme(
        primary = Color(0xFF2D4E6D),
        onPrimary = Color(0xFFE9EFF4),
        primaryContainer = Color(0xFFD9E3EA),
        onPrimaryContainer = Color(0xFF2D4E6D),
        secondary = Color(0xFF436382),
        onSecondary = Color(0xFFE9EFF4),
        secondaryContainer = Color(0xFFD9E3EA),
        onSecondaryContainer = Color(0xFF436382),
        tertiary = Color(0xFF9E4A38),
        onTertiary = Color.White,
        background = Color(0xFFE9EFF4),
        onBackground = Color(0xFF131D26),
        surface = Color(0xFFE9EFF4),
        onSurface = Color(0xFF131D26),
        surfaceVariant = Color(0xFFD9E3EA),
        onSurfaceVariant = Color(0xFF212E3C),
        outline = Color(0xFF536576),
        outlineVariant = Color(0x26131D26),
    )
    private val WinterSnowTokens = AppThemeCustomTokens(
        homeCardBackground = Color(0xFFD9E3EA),
        categoryTagBackground = Color(0xFFC6D5DF),
        bottomNavBackground = Color(0xFFCFDCE6),
        bottomNavIconColor = Color(0xFF2D4E6D),
        cardBackground = Color(0xFFD9E3EA),
        cardBorder = Color(0x26131D26),
        overlayBlendColor = Color.Transparent,
        statusBarProtectionColor = Color(0xFFE9EFF4),
        heartFavoriteTint = Color(0xFF9E4A38),
        titleText = Color(0xFF131D26),
        bodyText = Color(0xFF212E3C),
        secondaryText = Color(0xFF536576),
        backgroundStyle = ThemeBackgroundStyle.ImagePattern(
            drawableResId = R.drawable.bg_home_snow,
            fallbackBaseColor = Color(0xFFE9EFF4),
            scrimColor = Color(0xFFE8ECF0),
            scrimAlpha = 0.6f,
        ),
    )

    /**
     * 根据主题标识获取对应的主题完整定义体
     */
    fun getThemeDefinition(themeId: AppThemeId): AppThemeDefinition {
        return when (themeId) {
            AppThemeId.XUAN_PAPER -> AppThemeDefinition(themeId, XuanPaperColorScheme, XuanPaperTokens)
            AppThemeId.BAMBOO_GREEN -> AppThemeDefinition(themeId, BambooGreenColorScheme, BambooGreenTokens)
            AppThemeId.INDIGO_MOUNTAIN -> AppThemeDefinition(themeId, IndigoMountainColorScheme, IndigoMountainTokens)
            AppThemeId.CINNABAR_RED -> AppThemeDefinition(themeId, CinnabarRedColorScheme, CinnabarRedTokens)
            AppThemeId.AMBER_AUTUMN -> AppThemeDefinition(themeId, AmberAutumnColorScheme, AmberAutumnTokens)
            AppThemeId.NIGHT_INK -> AppThemeDefinition(themeId, NightInkColorScheme, NightInkTokens)
            AppThemeId.SPRING_WILLOW -> AppThemeDefinition(themeId, SpringWillowColorScheme, SpringWillowTokens)
            AppThemeId.SUMMER_LOTUS -> AppThemeDefinition(themeId, SummerLotusColorScheme, SummerLotusTokens)
            AppThemeId.AUTUMN_MAPLE -> AppThemeDefinition(themeId, AutumnMapleColorScheme, AutumnMapleTokens)
            AppThemeId.WINTER_SNOW -> AppThemeDefinition(themeId, WinterSnowColorScheme, WinterSnowTokens)
        }
    }
}
