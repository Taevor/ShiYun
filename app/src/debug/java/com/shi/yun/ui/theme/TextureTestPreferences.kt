package com.shi.yun.ui.theme

import android.content.Context
import android.content.SharedPreferences

/**
 * 纸张纹理测试参数配置数据模型
 */
data class TextureTestConfig(
    val theme: TestGradientTheme = TestGradientTheme.XUAN_PAPER,
    val resolution: TextureResolution = TextureResolution.ORIGINAL,
    val blendMode: TextureBlendModeType = TextureBlendModeType.LINEAR_LIGHT,
    val alpha: Float = 0.10f,
    val isTextureEnabled: Boolean = true,
    val showPoemCard: Boolean = true,
)

/**
 * 仅用于开发调试阶段的轻量测试参数本地持久化仓储 (TextureTestPreferences)
 *
 * 核心隔离特征：
 * 1. 位于 debug 源码目录下，Release 构建完全不包含此类；
 * 2. 独立采用独立的 debug SharedPreferences 文件 ("shiyun_texture_test_debug_prefs")；
 * 3. 绝不向正式 Room 数据库、ThemePreferences 或正式主题配置写入任何字节；
 * 4. 支持【保存当前参数】、【恢复上次保存】与【重置默认】。
 */
object TextureTestPreferences {
    private const val PREFS_NAME = "shiyun_texture_test_debug_prefs"

    private const val KEY_THEME = "test_theme"
    private const val KEY_RESOLUTION = "test_resolution"
    private const val KEY_BLEND_MODE = "test_blend_mode"
    private const val KEY_ALPHA = "test_alpha"
    private const val KEY_ENABLED = "test_enabled"
    private const val KEY_SHOW_POEM_CARD = "test_show_poem_card"
    private const val KEY_HAS_SAVED = "test_has_saved"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存当前开发测试参数
     */
    fun saveConfig(context: Context, config: TextureTestConfig) {
        getPrefs(context).edit().apply {
            putString(KEY_THEME, config.theme.name)
            putString(KEY_RESOLUTION, config.resolution.name)
            putString(KEY_BLEND_MODE, config.blendMode.name)
            putFloat(KEY_ALPHA, config.alpha)
            putBoolean(KEY_ENABLED, config.isTextureEnabled)
            putBoolean(KEY_SHOW_POEM_CARD, config.showPoemCard)
            putBoolean(KEY_HAS_SAVED, true)
            apply()
        }
    }

    /**
     * 检查是否存在上次保存的参数
     */
    fun hasSavedConfig(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAS_SAVED, false)
    }

    /**
     * 读取上次保存的测试参数（若未保存过则返回默认配置）
     */
    fun loadConfig(context: Context): TextureTestConfig {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_HAS_SAVED, false)) {
            return getDefaultConfig()
        }

        val themeName = prefs.getString(KEY_THEME, TestGradientTheme.XUAN_PAPER.name)
        val theme = runCatching { TestGradientTheme.valueOf(themeName ?: "") }.getOrDefault(TestGradientTheme.XUAN_PAPER)

        val resName = prefs.getString(KEY_RESOLUTION, TextureResolution.SIZE_512.name)
        val resolution = runCatching { TextureResolution.valueOf(resName ?: "") }.getOrDefault(TextureResolution.SIZE_512)

        val blendName = prefs.getString(KEY_BLEND_MODE, TextureBlendModeType.LINEAR_LIGHT.name)
        val blendMode = runCatching { TextureBlendModeType.valueOf(blendName ?: "") }.getOrDefault(TextureBlendModeType.LINEAR_LIGHT)

        val alpha = prefs.getFloat(KEY_ALPHA, 0.20f)
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val showPoemCard = prefs.getBoolean(KEY_SHOW_POEM_CARD, true)

        return TextureTestConfig(
            theme = theme,
            resolution = resolution,
            blendMode = blendMode,
            alpha = alpha,
            isTextureEnabled = enabled,
            showPoemCard = showPoemCard,
        )
    }

    /**
     * 默认开发测试基准配置
     */
    fun getDefaultConfig(): TextureTestConfig {
        return TextureTestConfig(
            theme = TestGradientTheme.XUAN_PAPER,
            resolution = TextureResolution.SIZE_512,
            blendMode = TextureBlendModeType.LINEAR_LIGHT,
            alpha = 0.20f,
            isTextureEnabled = true,
            showPoemCard = true,
        )
    }

    /**
     * 清除保存的配置
     */
    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
