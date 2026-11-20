package com.shi.yun.ui.widget

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.shi.yun.MainActivity
import com.shi.yun.R
import com.shi.yun.common.log.AppLogger
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.repository.RoomWorkRepository
import com.shi.yun.data.model.Work
import com.shi.yun.data.util.PoemCoupletExtractor
import com.shi.yun.ui.theme.AppThemeId
import com.shi.yun.ui.theme.AppThemePalette
import com.shi.yun.ui.theme.ThemeBackgroundStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 桌面小组件展示的名句数据载体
 */
data class WidgetQuoteData(
    val workId: String,
    val quote: String,
    val title: String,
    val author: String,
)

/**
 * 小组件主题色彩模型
 */
data class WidgetThemeColors(
    val backgroundColor: Int,
    val borderColor: Int,
    val quoteTextColor: Int,
    val authorTextColor: Int,
    val primaryColor: Int,
)

/**
 * 桌面小组件名句展示与主题跟随提供器
 *
 * 核心架构设计：
 * 1. 采用原生 RemoteViews + AppWidgetProvider 实现，零常驻后台服务，零依赖外部沉重框架；
 * 2. 纯单机离线运行，复用现行经典作品推荐机制提取诗词名句与出处；
 * 3. 背景与圆角完全基于 Android 原生 Shape Drawable（16dp 固定半径），任意拉伸尺寸永不变形；
 * 4. 纹理层与圆角完全解耦，以 0.1f 纯净轻微透明度居中等比叠底，保障文字绝佳可读性；
 * 5. 真正中文名句自适应排版：根据小组件实际可用宽度与高度自动决定单行或多行自然折行，绝不机械强制两行；
 * 6. 深度链接安全直达：点击卡片直达对应作品详情，携带精确 work_id；
 * 7. 尺寸自适应排版：根据桌面网格宽度与高度动态收缩/展开元素、调节字号与排版，严防文字溢出。
 */
class ShiYunQuoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        AppLogger.i("QuoteWidget", "onUpdate 触发: 实例数=${appWidgetIds.size}")
        for (appWidgetId in appWidgetIds) {
            val hasCache = hasCachedQuote(context, appWidgetId)
            if (hasCache) {
                // 已有缓存数据时立即渲染，避免等待数据库冷启动
                updateAppWidget(context, appWidgetManager, appWidgetId)
            } else {
                // 首次添加尚无缓存时，先渲染优雅保底内容，随即异步拉取推荐名句
                updateAppWidget(context, appWidgetManager, appWidgetId)
                fetchAndRefreshWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        AppLogger.d("QuoteWidget", "小组件尺寸变化: appWidgetId=$appWidgetId")
        updateAppWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        AppLogger.i("QuoteWidget", "小组件实例已删除，清理对应缓存: count=${appWidgetIds.size}")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (id in appWidgetIds) {
            editor.remove(keyWorkId(id))
            editor.remove(keyQuote(id))
            editor.remove(keyTitle(id))
            editor.remove(keyAuthor(id))
        }
        editor.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_WIDGET -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                AppLogger.i("QuoteWidget", "收到用户主动点击【换一换】广播: appWidgetId=$appWidgetId")
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            fetchAndSaveNewQuote(context, appWidgetId)
                            withContext(Dispatchers.Main) {
                                updateAppWidget(context, appWidgetManager, appWidgetId)
                            }
                        } catch (e: Exception) {
                            AppLogger.w("QuoteWidget", "换一换刷新异常: ${e.message}")
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            ACTION_THEME_CHANGED -> {
                AppLogger.i("QuoteWidget", "收到全局主题切换广播，即时刷新小组件主题配色与纹理")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, ShiYunQuoteWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                for (id in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
        }
    }

    /**
     * 异步拉取推荐作品并刷新指定小组件
     */
    private fun fetchAndRefreshWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fetchAndSaveNewQuote(context, appWidgetId)
                withContext(Dispatchers.Main) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                AppLogger.w("QuoteWidget", "初始化拉取推荐名句异常: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.shi.yun.action.REFRESH_WIDGET"
        const val ACTION_THEME_CHANGED = "com.shi.yun.action.THEME_CHANGED"
        private const val PREFS_NAME = "shiyun_quote_widget_preferences"

        // 统一小组件专属纹理叠加透明度（十套主题统一严格锁定 0.1f）
        const val WIDGET_TEXTURE_ALPHA = 0.10f

        // 默认保底文学名句（确保冷启动极速无空白）
        private const val DEFAULT_WORK_ID = "tang_000001"
        private const val DEFAULT_QUOTE = "床前明月光，疑是地上霜。"
        private const val DEFAULT_TITLE = "静夜思"
        private const val DEFAULT_AUTHOR = "〔唐〕李白"

        private fun keyWorkId(id: Int) = "work_id_$id"
        private fun keyQuote(id: Int) = "quote_$id"
        private fun keyTitle(id: Int) = "title_$id"
        private fun keyAuthor(id: Int) = "author_$id"

        /**
         * 检查是否存在已缓存的名句数据
         */
        fun hasCachedQuote(context: Context, appWidgetId: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.contains(keyWorkId(appWidgetId)) || prefs.contains("global_work_id")
        }

        /**
         * 读取指定小组件当前展示的名句数据（具有绝对保底兜底机制）
         */
        fun loadQuoteData(context: Context, appWidgetId: Int): WidgetQuoteData {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val workId = prefs.getString(keyWorkId(appWidgetId), null)
                ?: prefs.getString("global_work_id", DEFAULT_WORK_ID)
                ?: DEFAULT_WORK_ID
            val quote = prefs.getString(keyQuote(appWidgetId), null)
                ?: prefs.getString("global_quote", DEFAULT_QUOTE)
                ?: DEFAULT_QUOTE
            val title = prefs.getString(keyTitle(appWidgetId), null)
                ?: prefs.getString("global_title", DEFAULT_TITLE)
                ?: DEFAULT_TITLE
            val author = prefs.getString(keyAuthor(appWidgetId), null)
                ?: prefs.getString("global_author", DEFAULT_AUTHOR)
                ?: DEFAULT_AUTHOR

            return WidgetQuoteData(
                workId = workId,
                quote = quote,
                title = title,
                author = author,
            )
        }

        /**
         * 获取当前小组件最新展示的作品 ID（供外部快速读取）
         */
        fun getLatestWorkId(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString("global_work_id", DEFAULT_WORK_ID) ?: DEFAULT_WORK_ID
        }

        /**
         * 从 Room 推荐集中异步获取一首经典作品并落盘缓存
         */
        suspend fun fetchAndSaveNewQuote(context: Context, appWidgetId: Int) {
            val database = ShiYunDatabase.getInstance(context)
            val repo = RoomWorkRepository(database)
            val recs = repo.getHomeRecommendations(1)
            val work = recs.firstOrNull() ?: return
            saveQuoteData(context, appWidgetId, work)
        }

        /**
         * 将作品格式化为名句数据并写入轻量持久化偏好
         */
        fun saveQuoteData(context: Context, appWidgetId: Int, work: Work) {
            val couplet = PoemCoupletExtractor.extractCouplet(work)
            val quoteText = couplet?.formattedWithPunctuation ?: extractDisplayQuote(work)
            val titleText = couplet?.title ?: work.title.trim().removePrefix("《").removeSuffix("》").trim()
            val authorText = couplet?.author ?: work.author.trim()
                .replace(Regex("^[·•\\[〔【(（].*?[·•\\]〕】)）]"), "")
                .replace(Regex("^[·•\\-—\\s]+"), "")
                .trim()
                .ifBlank { "佚名" }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(keyWorkId(appWidgetId), work.id)
                .putString(keyQuote(appWidgetId), quoteText)
                .putString(keyTitle(appWidgetId), titleText)
                .putString(keyAuthor(appWidgetId), authorText)
                // 同步写入全局保底键，供新实例零延迟共享
                .putString("global_work_id", work.id)
                .putString("global_quote", quoteText)
                .putString("global_title", titleText)
                .putString("global_author", authorText)
                .apply()

            AppLogger.d("QuoteWidget", "名句数据已落盘: ID=${work.id}, 标题=${work.title}")
        }

        /**
         * 从作品中提取适合桌面小组件展示的名句短语
         * 优先采用作品中的精选 excerpt；若为空则提取正文首个非空行
         */
        fun extractDisplayQuote(work: Work): String {
            val excerpt = work.excerpt.trim()
            if (excerpt.isNotBlank()) return excerpt

            val firstLine = work.content.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
            return firstLine ?: work.title
        }

        /**
         * 将名句格式化为适合小组件自然自适应排版的文字
         * 核心规则：
         * 1. 规范化空白字符，消除任何生硬、多余的强制折行符；
         * 2. 若换行处两句间缺少标点，则平滑补齐标点，确保诗意完整；
         * 3. 绝不强制预先截断或固定拆分为两行，完全交由 TextView 根据小组件实际可用宽度与字号自适应排版。
         */
        fun formatWidgetQuote(rawQuote: String): String {
            val clean = rawQuote.replace("\r", "").trim()
            if (clean.isEmpty()) return ""

            val lines = clean.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val punctuationRegex = Regex("[，。！？；：、,!?;:]$")
            val joined = buildString {
                for (i in lines.indices) {
                    val line = lines[i]
                    append(line)
                    if (i < lines.size - 1 && !punctuationRegex.containsMatchIn(line)) {
                        append("，")
                    }
                }
            }
            return joined
        }

        /**
         * 格式化底部作者与作品出处展示文字
         * 规范：极简展示《作品》作者，不显示朝代，删除多余标点与破折号
         * 示例：《静夜思》李白
         */
        fun formatAttribution(title: String, author: String, isNarrow: Boolean): String {
            val cleanTitle = title.trim().removePrefix("《").removeSuffix("》").trim()
            val cleanAuthor = author.trim()
                .replace(Regex("^[·•\\[〔【(（].*?[·•\\]〕】)）]"), "")
                .replace(Regex("^[·•\\-—\\s]+"), "")
                .trim()

            if (isNarrow) {
                // 窄尺寸：简化出处，优先作者或标题
                return when {
                    cleanAuthor.isNotEmpty() -> cleanAuthor
                    cleanTitle.isNotEmpty() -> "《$cleanTitle》"
                    else -> ""
                }
            }

            // 标准或宽尺寸：输出标准文学极简出处格式《作品》作者
            return when {
                cleanTitle.isNotEmpty() && cleanAuthor.isNotEmpty() -> "《$cleanTitle》$cleanAuthor"
                cleanTitle.isNotEmpty() -> "《$cleanTitle》"
                cleanAuthor.isNotEmpty() -> cleanAuthor
                else -> ""
            }
        }

        /**
         * 获取当前应用持久化的主题 ID
         */
        fun getCurrentThemeId(context: Context): AppThemeId {
            val themePrefs = context.getSharedPreferences("shiyun_theme_preferences", Context.MODE_PRIVATE)
            val rawThemeId = themePrefs.getString("key_app_theme_id", null)
            return AppThemeId.fromId(rawThemeId)
        }

        /**
         * 解析指定主题对应的小组件纹理 Drawable 资源 ID
         */
        fun resolveThemeTextureResId(themeId: AppThemeId): Int {
            val themeDef = AppThemePalette.getThemeDefinition(themeId)
            return when (val bg = themeDef.customTokens.backgroundStyle) {
                is ThemeBackgroundStyle.SolidWithTexture -> bg.textureResId
                is ThemeBackgroundStyle.ImagePattern -> bg.drawableResId
                else -> R.drawable.bg_paper_texture
            }
        }

        /**
         * 从主题持久化仓储加载当前活跃主题定义与色彩
         */
        fun resolveCurrentThemeColors(context: Context): WidgetThemeColors {
            val currentThemeId = getCurrentThemeId(context)
            val themeDef = AppThemePalette.getThemeDefinition(currentThemeId)
            val isDark = currentThemeId.isDark

            return WidgetThemeColors(
                backgroundColor = themeDef.customTokens.homeCardBackground.toArgb(),
                borderColor = themeDef.customTokens.resolvedCardBorder(isDark).toArgb(),
                quoteTextColor = themeDef.customTokens.titleText.toArgb(),
                authorTextColor = themeDef.customTokens.secondaryText.toArgb(),
                primaryColor = themeDef.colorScheme.primary.toArgb(),
            )
        }

        /**
         * 核心渲染入口：构建并更新指定小组件的 RemoteViews
         */
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle? = null,
        ) {
            val widgetOptions = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = widgetOptions?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 200
            val minHeight = widgetOptions?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 100

            val quoteData = loadQuoteData(context, appWidgetId)
            val currentThemeId = getCurrentThemeId(context)
            val colors = resolveCurrentThemeColors(context)

            val views = RemoteViews(context.packageName, R.layout.widget_shiyun_quote)

            // 1. 底色层：原生 Shape Drawable 着色（圆角 16dp 由矢量几何实时计算，拉伸永不变形）
            views.setInt(R.id.widget_card_bg, "setColorFilter", colors.backgroundColor)

            // 2. 纹理层：独立纹理叠加，透明度严格设定为 0.10f，居中裁剪与圆角完全解耦
            val textureResId = resolveThemeTextureResId(currentThemeId)
            views.setImageViewResource(R.id.widget_texture_overlay, textureResId)
            views.setInt(R.id.widget_texture_overlay, "setImageAlpha", (255 * WIDGET_TEXTURE_ALPHA).toInt())

            // 3. 边框层：原生 16dp 圆角微边框着色
            views.setInt(R.id.widget_card_border, "setColorFilter", colors.borderColor)

            // 4. 文字色彩应用与右上角刷新图标着色
            views.setTextColor(R.id.widget_quote_text, colors.quoteTextColor)
            views.setTextColor(R.id.widget_author_text, colors.authorTextColor)
            views.setTextColor(R.id.widget_app_brand, colors.primaryColor)
            views.setInt(R.id.widget_btn_refresh, "setColorFilter", colors.primaryColor)

            // 5. 填充名句正文（完全消除强制两行，让 TextView 根据实际可用宽度自适应排版）
            val formattedQuote = formatWidgetQuote(quoteData.quote)
            views.setTextViewText(R.id.widget_quote_text, formattedQuote)

            // 6. 尺寸自适应策略（根据桌面网格实际宽度与高度动态调配）
            val isShortHeight = minHeight < 75
            val isMediumHeight = minHeight in 75..94
            val isNarrowWidth = minWidth < 180

            // 动态设置最大行数：极矮尺寸允许 2 行，标准高度允许 3 行，高大尺寸允许 4 行
            val maxLines = when {
                isShortHeight -> 2
                minHeight >= 110 -> 4
                else -> 3
            }
            views.setInt(R.id.widget_quote_text, "setMaxLines", maxLines)

            if (isShortHeight) {
                // 扁平/极矮空间（如 4x1 网格）：隐藏顶栏品牌与刷新，最大化名句展示区域
                views.setViewVisibility(R.id.widget_header_row, View.GONE)
                views.setTextViewTextSize(R.id.widget_quote_text, TypedValue.COMPLEX_UNIT_SP, 14f)
                views.setTextViewTextSize(R.id.widget_author_text, TypedValue.COMPLEX_UNIT_SP, 10f)
            } else if (isMediumHeight) {
                // 中等高度空间：保留顶栏，微调字号
                views.setViewVisibility(R.id.widget_header_row, View.VISIBLE)
                if (isNarrowWidth) {
                    views.setViewVisibility(R.id.widget_app_brand, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_app_brand, View.VISIBLE)
                    views.setTextViewTextSize(R.id.widget_app_brand, TypedValue.COMPLEX_UNIT_SP, 14f)
                }
                views.setTextViewTextSize(R.id.widget_quote_text, TypedValue.COMPLEX_UNIT_SP, 15f)
                views.setTextViewTextSize(R.id.widget_author_text, TypedValue.COMPLEX_UNIT_SP, 11f)
            } else {
                // 默认/宽敞空间（标准 4x2、全幅大卡片）：完整展示顶栏品牌（字号提升两级至 16sp）与刷新按钮
                views.setViewVisibility(R.id.widget_header_row, View.VISIBLE)
                if (isNarrowWidth) {
                    views.setViewVisibility(R.id.widget_app_brand, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_app_brand, View.VISIBLE)
                    views.setTextViewTextSize(R.id.widget_app_brand, TypedValue.COMPLEX_UNIT_SP, 16f)
                }
                views.setTextViewTextSize(R.id.widget_quote_text, TypedValue.COMPLEX_UNIT_SP, 17f)
                views.setTextViewTextSize(R.id.widget_author_text, TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            // 7. 填充出处文字
            val isNarrowAttribution = isNarrowWidth || isShortHeight
            val attribution = formatAttribution(quoteData.title, quoteData.author, isNarrowAttribution)
            views.setTextViewText(R.id.widget_author_text, attribution)

            // 8. 点击跳转详情页 PendingIntent（包含安全 URI、来源标记与 Android 14/15 官方 BAL 后台启动权限授权）
            val detailIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("shiyun://work/${quoteData.workId}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("work_id", quoteData.workId)
                putExtra("EXTRA_TARGET_WORK_ID", quoteData.workId)
                putExtra("FROM_WIDGET", true)
            }
            // 适配 Android 15 (API 35+)：创作者端显式授权后台 Activity 启动权限
            // 注意：pendingIntentBackgroundActivityStartMode 仅在 PendingIntent.send() 时有效，
            // 若在 PendingIntent.getActivity() 创建时传入会导致系统抛出 IllegalArgumentException
            @Suppress("DEPRECATION")
            val optionsBundle = if (Build.VERSION.SDK_INT >= 35) {
                ActivityOptions.makeBasic().apply {
                    setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }.toBundle()
            } else {
                null
            }
            val detailPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                detailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                optionsBundle,
            )

            // 为主要阅读交互区域及整张卡片绑定跳转，确保卡片任意位置点击均能精确直达作品详情
            views.setOnClickPendingIntent(R.id.widget_root, detailPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_content_container, detailPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_quote_container, detailPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_quote_text, detailPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_footer_row, detailPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_author_text, detailPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_app_brand, detailPendingIntent)

            // 9. 为【换一换】功能绑定广播刷新 PendingIntent
            val refreshIntent = Intent(context, ShiYunQuoteWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

            // 提交至 Launcher
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * 外部（如 App 内切换全局主题）通知桌面小组件即时刷新配色与纹理
         */
        fun notifyThemeChanged(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ShiYunQuoteWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, ShiYunQuoteWidgetProvider::class.java).apply {
                    action = ACTION_THEME_CHANGED
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
