package com.shi.yun.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 诗云全局主题背景渲染策略
 */
sealed interface ThemeBackgroundStyle {
    /**
     * 经典纯色东方渐变模式（纯竖向线性渐变，轻量高效，杜绝图片回退）
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
     * @param scrimAlpha 遮罩透明度，默认 0.30f
     */
    data class ImagePattern(
        @param:DrawableRes val drawableResId: Int,
        val fallbackBaseColor: Color,
        val alpha: Float = 0.85f,
        val scrimColor: Color = Color.White,
        val scrimAlpha: Float = 0.30f,
    ) : ThemeBackgroundStyle
}

/**
 * 诗云古典扩展主题属性（超越 M3 默认字段的古籍定制视觉属性）
 *
 * @property cardBackground 诗词卡片、反馈浮层底色
 * @property cardBorder 卡片细微墨线边框色
 * @property overlayBlendColor 首页山水插画的主题微调混色（专属背景图模式下默认为完全透明）
 * @property statusBarProtectionColor 状态栏顶部宣纸渐变保护底色
 * @property heartFavoriteTint 收藏红心高亮强调色（各主题可典雅微调）
 * @property titleText 诗词主标题、大字摘录核心标题色
 * @property bodyText 诗词正文、小序内容、释义说明主文字色
 * @property secondaryText 作者、朝代、体裁、注脚辅助文字色
 * @property backgroundStyle 全局主题背景渲染策略（渐变或图片）
 */
data class AppThemeCustomTokens(
    val cardBackground: Color,
    val cardBorder: Color,
    val overlayBlendColor: Color = Color.Transparent,
    val statusBarProtectionColor: Color,
    val heartFavoriteTint: Color = Color(0xFFB83A2A),
    val titleText: Color,
    val bodyText: Color,
    val secondaryText: Color,
    val backgroundStyle: ThemeBackgroundStyle,
) {
    /**
     * 针对旧有代码的向后兼容计算属性：若为图片模式则返回其资源 ID，渐变模式返回 0
     */
    val backgroundResId: Int
        get() = (backgroundStyle as? ThemeBackgroundStyle.ImagePattern)?.drawableResId ?: 0
}

/**
 * 单套全局主题的完整定义体
 */
data class AppThemeDefinition(
    val themeId: AppThemeId,
    val colorScheme: ColorScheme,
    val customTokens: AppThemeCustomTokens,
)

/**
 * 全局主题 CompositionLocal，供顶层和特殊视觉组件（如首页山水滤镜、反馈卡片）获取扩展属性
 */
val LocalAppTheme = compositionLocalOf<AppThemeDefinition> {
    AppThemePalette.getThemeDefinition(AppThemeId.Default)
}
