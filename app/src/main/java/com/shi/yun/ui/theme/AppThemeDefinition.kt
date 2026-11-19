package com.shi.yun.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shi.yun.R

/**
 * 诗云全局统一主题视觉规范 (Single Source of Truth)
 *
 * 提炼自四季主题实际验证的黄金视觉规则，确保全应用所有界面、所有主题
 * 共享完全一致的质感、透明度、边框细度、圆角尺寸与零阴影规范。
 * 所有主题共用一套组件视觉规则，主题之间只提供各自的基础颜色和背景资源。
 */
object ThemeVisualSpecs {
    // 1. 卡片与导航栏透明度规范 (Alpha)
    const val CARD_BG_ALPHA_LIGHT = 0.42f
    const val CARD_BG_ALPHA_DARK = 0.60f

    const val NAV_BG_ALPHA_LIGHT = 0.75f
    const val NAV_BG_ALPHA_DARK = 0.85f

    const val CARD_BORDER_ALPHA_LIGHT = 0.25f
    const val CARD_BORDER_ALPHA_DARK = 0.40f

    const val NAV_DIVIDER_ALPHA_LIGHT = 0.12f
    const val NAV_DIVIDER_ALPHA_DARK = 0.20f

    // 2. 经典纸张纹理规范 (Linear Light 线性光混合，最终强度 15%)
    const val TEXTURE_ALPHA_CLASSIC = 0.15f

    // 3. 边框线宽与分割线 (Dp)
    val BORDER_WIDTH_CARD: Dp = 0.5.dp
    val BORDER_WIDTH_SELECTED_TAG: Dp = 1.0.dp
    val DIVIDER_HEIGHT_NAV: Dp = 0.5.dp

    // 4. 组件圆角规范 (Dp)
    val CORNER_RADIUS_HOME_CARD: Dp = 16.dp
    val CORNER_RADIUS_LIST_CARD: Dp = 12.dp
    val CORNER_RADIUS_TAG: Dp = 8.dp
    val CORNER_RADIUS_ANTHOLOGY_CARD: Dp = 8.dp

    // 5. 零阴影与零 Elevation 规范 (保障纸张通透平整，杜绝 M3 调色与深色断层)
    val ELEVATION_ZERO: Dp = 0.dp
}

/**
 * 诗云全局主题背景渲染策略
 */
sealed interface ThemeBackgroundStyle {
    /**
     * 六套经典纯色背景模式（纯色底叠加纸张纹理，Linear Light 线性光混合模式，Alpha=0.15f）
     * @param solidColor 纯色背景基色
     * @param textureResId 纹理图片资源 ID（默认修复版原图 R.drawable.bg_paper_texture）
     * @param textureAlpha 纹理透明度，统一规范为 0.15f
     */
    data class SolidWithTexture(
        val solidColor: Color,
        @param:DrawableRes val textureResId: Int = R.drawable.bg_paper_texture,
        val textureAlpha: Float = ThemeVisualSpecs.TEXTURE_ALPHA_CLASSIC,
    ) : ThemeBackgroundStyle

    /**
     * 经典纯色东方渐变模式（旧版兼容定义）
     * @param topColor 顶部起始渐变色
     * @param bottomColor 底部终止渐变色
     */
    data class Gradient(
        val topColor: Color,
        val bottomColor: Color,
    ) : ThemeBackgroundStyle

    /**
     * 季节水墨插画模式（独立背景图，支持底色保底、可调透明度与柔和护眼遮罩）
     * @param drawableResId 独立 Drawable 资源 ID
     * @param fallbackBaseColor 背景图解码加载完成前的安全基底色
     * @param alpha 图片渲染透明度，默认 0.85f
     * @param scrimColor 季节轻微柔光遮罩基色（使用季节浅色调，杜绝因深底色压暗背景）
     * @param scrimAlpha 遮罩透明度，默认 0.45f
     */
    data class ImagePattern(
        @param:DrawableRes val drawableResId: Int,
        val fallbackBaseColor: Color,
        val alpha: Float = 0.85f,
        val scrimColor: Color = Color.White,
        val scrimAlpha: Float = 0.45f,
    ) : ThemeBackgroundStyle
}

/**
 * 诗云古典扩展主题属性（超越 M3 默认字段的古籍定制视觉属性）
 *
 * @property homeCardBackground 首页作品卡片专属底色（默认与 cardBackground 保持统一）
 * @property categoryTagBackground 分类页面标签专属底色
 * @property categoryTagTextColor 分类页面标签选中文字颜色（默认纯白，部分高明度标签可定制）
 * @property bottomNavBackground 底部导航区域专属底色（默认与 cardBackground 保持统一）
 * @property bottomNavIconColor 底部导航图标专属独立颜色
 * @property cardBackground 通用诗词卡片、反馈浮层底色
 * @property cardBorder 卡片细微墨线边框色
 * @property overlayBlendColor 首页插画的主题微调混色（纯色纹理模式下为完全透明）
 * @property statusBarProtectionColor 状态栏顶部保护底色（与主背景色严格一致）
 * @property heartFavoriteTint 收藏红心高亮强调色（各主题可典雅微调）
 * @property titleText 诗词主标题、大字摘录核心标题色（主要文字）
 * @property bodyText 诗词正文、小序内容、释义说明主文字色
 * @property secondaryText 作者、朝代、体裁、注脚辅助文字色
 * @property backgroundStyle 全局主题背景渲染策略（纯色纹理或四季图片）
 */
data class AppThemeCustomTokens(
    val homeCardBackground: Color,
    val categoryTagBackground: Color,
    val bottomNavBackground: Color,
    val bottomNavIconColor: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val overlayBlendColor: Color = Color.Transparent,
    val statusBarProtectionColor: Color,
    val heartFavoriteTint: Color = Color(0xFFB83A2A),
    val titleText: Color,
    val bodyText: Color,
    val secondaryText: Color,
    val backgroundStyle: ThemeBackgroundStyle,
    val categoryTagTextColor: Color = Color.White,
) {
    /**
     * 针对旧有代码的向后兼容计算属性：若为图片模式则返回其资源 ID，纯色或渐变模式返回 0
     */
    val backgroundResId: Int
        get() = (backgroundStyle as? ThemeBackgroundStyle.ImagePattern)?.drawableResId ?: 0

    /**
     * 全局统一计算属性：解析当前主题在对应明暗模式下的卡片生效底色（0.42f / 0.60f）
     */
    fun resolvedCardBackground(isDark: Boolean): Color =
        cardBackground.copy(
            alpha = if (isDark) ThemeVisualSpecs.CARD_BG_ALPHA_DARK else ThemeVisualSpecs.CARD_BG_ALPHA_LIGHT
        )

    /**
     * 全局统一计算属性：解析当前主题在对应明暗模式下的卡片生效边框色（0.25f / 0.40f）
     */
    fun resolvedCardBorder(isDark: Boolean): Color =
        cardBorder.copy(
            alpha = if (isDark) ThemeVisualSpecs.CARD_BORDER_ALPHA_DARK else ThemeVisualSpecs.CARD_BORDER_ALPHA_LIGHT
        )

    /**
     * 全局统一计算属性：解析当前主题在对应明暗模式下的底部导航栏生效底色（0.75f / 0.85f）
     */
    fun resolvedNavBackground(isDark: Boolean): Color =
        cardBackground.copy(
            alpha = if (isDark) ThemeVisualSpecs.NAV_BG_ALPHA_DARK else ThemeVisualSpecs.NAV_BG_ALPHA_LIGHT
        )

    /**
     * 全局统一计算属性：解析当前主题在对应明暗模式下的底部导航栏分割线颜色（0.12f / 0.20f）
     */
    fun resolvedNavDividerColor(isDark: Boolean): Color =
        cardBorder.copy(
            alpha = if (isDark) ThemeVisualSpecs.NAV_DIVIDER_ALPHA_DARK else ThemeVisualSpecs.NAV_DIVIDER_ALPHA_LIGHT
        )
}

/**
 * 单套全局主题的完整定义体
 */
data class AppThemeDefinition(
    val themeId: AppThemeId,
    val colorScheme: ColorScheme,
    val customTokens: AppThemeCustomTokens,
) {
    /**
     * 底部导航栏生效图标颜色：
     * - 经典主题（ThemeCategory.CLASSIC）：统一自动继承当前实际生效的 colorScheme.primary；
     * - 四时主题（ThemeCategory.SEASONAL）：保持各四季主题专属独立的 customTokens.bottomNavIconColor。
     */
    val resolvedNavIconColor: Color
        get() = if (themeId.category == ThemeCategory.CLASSIC) {
            colorScheme.primary
        } else {
            customTokens.bottomNavIconColor
        }
}

/**
 * 全局主题 CompositionLocal，供顶层和特殊视觉组件（如首页山水滤镜、反馈卡片）获取扩展属性
 */
val LocalAppTheme = compositionLocalOf<AppThemeDefinition> {
    AppThemePalette.getThemeDefinition(AppThemeId.Default)
}
