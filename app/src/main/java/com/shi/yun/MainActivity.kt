package com.shi.yun

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shi.yun.common.log.AppLogger
import com.shi.yun.data.model.ReadingFontFamily
import com.shi.yun.data.repository.CustomFontRepository
import com.shi.yun.data.repository.LocalCustomFontRepository
import com.shi.yun.data.repository.LocalReadingPreferencesRepository
import com.shi.yun.data.repository.LocalThemePreferencesRepository
import com.shi.yun.data.repository.ReadingPreferencesRepository
import com.shi.yun.data.repository.ThemePreferencesRepository
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.repository.RoomWorkRepository
import com.shi.yun.data.repository.WorkRepository
import com.shi.yun.ui.ShiApp
import com.shi.yun.ui.theme.诗云Theme
import kotlinx.coroutines.channels.Channel

/**
 * 诗云应用主入口 Activity
 *
 * 遵循极简职责划分：
 * 1. 负责窗口与 Edge-to-Edge 全屏沉浸式配置；
 * 2. 唯一定义并持有本地数据仓库、主题仓储、排版偏好仓储与自定义字体仓储单例；
 * 3. 在顶层响应式监听全局主题与用户字体，驱动 [诗云Theme] 与 [ShiApp]，切换后即时刷新，无需重启应用；
 * 4. 监听桌面小组件与系统深度链接 Intent，实现作品详情页秒级直达（采用非阻塞 CONFLATED Channel）。
 */
class MainActivity : ComponentActivity() {

    // 采用 Channel.CONFLATED 确保高频连续点击时最新请求必定送达且不发生状态竞态撤销
    private val navigationChannel = Channel<String>(Channel.CONFLATED)

    private val database: ShiYunDatabase by lazy {
        ShiYunDatabase.getInstance(applicationContext)
    }

    private val workRepository: WorkRepository by lazy {
        RoomWorkRepository(database)
    }

    private val themeRepository: ThemePreferencesRepository by lazy {
        LocalThemePreferencesRepository(applicationContext)
    }

    private val readingPreferencesRepository: ReadingPreferencesRepository by lazy {
        LocalReadingPreferencesRepository(applicationContext)
    }

    private val customFontRepository: CustomFontRepository by lazy {
        LocalCustomFontRepository(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化应用日志系统
        AppLogger.init(applicationContext)
        AppLogger.i("MainActivity", "应用主 Activity 启动 (onCreate)")

        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            val currentThemeId by themeRepository.currentThemeIdFlow.collectAsStateWithLifecycle()
            val readingPrefs by readingPreferencesRepository.preferencesFlow.collectAsStateWithLifecycle()

            val activeFontFamily = remember(readingPrefs.fontType, readingPrefs.customFontId) {
                val customId = readingPrefs.customFontId
                if (!customId.isNullOrBlank()) {
                    customFontRepository.getFontFamily(customId) ?: when (readingPrefs.fontType) {
                        ReadingFontFamily.SYSTEM_SERIF -> FontFamily.Serif
                        ReadingFontFamily.SYSTEM_MONOSPACE -> FontFamily.Monospace
                        ReadingFontFamily.SYSTEM_DEFAULT -> FontFamily.Default
                    }
                } else {
                    when (readingPrefs.fontType) {
                        ReadingFontFamily.SYSTEM_SERIF -> FontFamily.Serif
                        ReadingFontFamily.SYSTEM_MONOSPACE -> FontFamily.Monospace
                        ReadingFontFamily.SYSTEM_DEFAULT -> FontFamily.Default
                    }
                }
            }

            诗云Theme(
                themeId = currentThemeId,
                fontFamily = activeFontFamily,
            ) {
                ShiApp(
                    repository = workRepository,
                    preferencesRepository = readingPreferencesRepository,
                    themeRepository = themeRepository,
                    customFontRepository = customFontRepository,
                    navigationChannel = navigationChannel,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppLogger.i("MainActivity", "Activity onNewIntent (收到新 Intent 唤醒)")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        var workId = intent?.getStringExtra("work_id")
            ?: intent?.getStringExtra("EXTRA_TARGET_WORK_ID")
            ?: extractWorkIdFromUri(intent?.data)

        // 桌面小组件点击直达兜底：若 intent 带有来自小组件的特征标记或 ACTION_VIEW 但未携带 work_id，
        // 则主动重新读取当前桌面小组件展示的最新作品 ID，确保每次点击都能正确定位
        if (workId.isNullOrBlank() && (intent?.action == Intent.ACTION_VIEW || intent?.getBooleanExtra("FROM_WIDGET", false) == true)) {
            workId = com.shi.yun.ui.widget.ShiYunQuoteWidgetProvider.getLatestWorkId(applicationContext)
        }

        if (!workId.isNullOrBlank()) {
            AppLogger.i("MainActivity", "捕获桌面小组件/深度链接直达目标作品 ID: $workId")
            navigationChannel.trySend(workId)
        }
    }

    private fun extractWorkIdFromUri(uri: Uri?): String? {
        if (uri == null) return null
        // 匹配 shiyun://work/{work_id}
        if (uri.scheme == "shiyun" && uri.host == "work") {
            return uri.lastPathSegment
        }
        return null
    }

    override fun onStart() {
        super.onStart()
        AppLogger.i("MainActivity", "Activity onStart (进入前台可见状态)")
    }

    override fun onResume() {
        super.onResume()
        AppLogger.i("MainActivity", "Activity onResume (获得用户交互焦点)")
    }

    override fun onPause() {
        super.onPause()
        AppLogger.i("MainActivity", "Activity onPause (失去用户交互焦点)")
    }

    override fun onStop() {
        super.onStop()
        AppLogger.i("MainActivity", "Activity onStop (进入后台不可见状态)")
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i("MainActivity", "Activity onDestroy (Activity 销毁)")
        AppLogger.flush(1000L)
    }
}
