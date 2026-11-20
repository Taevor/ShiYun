package com.shi.yun.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shi.yun.common.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * 纹理分辨率测试档位
 */
enum class TextureResolution(val displayName: String, val fileName: String, val approxRamKb: Int) {
    SIZE_256("256×256", "test_texture_256.jpg", 256),
    SIZE_512("512×512", "test_texture_512.jpg", 1024),
    ORIGINAL("原图 1912", "test_texture_orig.jpg", 14620),
}

/**
 * 混合模式档位
 */
enum class TextureBlendModeType(val displayName: String, val isAgslShader: Boolean) {
    LINEAR_LIGHT("线性光 (Linear Light AGSL)", true),
    OVERLAY("叠加 (Overlay)", false),
    SOFT_LIGHT("柔光 (Soft Light)", false),
    HARD_LIGHT("强光 (Hard Light)", false),
    MULTIPLY("正片叠底 (Multiply)", false),
}

/**
 * 六套测试纯色渐变主题参数定义（独立于正式主题库）
 */
enum class TestGradientTheme(
    val themeName: String,
    val shortName: String,
    val topColor: Color,
    val bottomColor: Color,
    val isDark: Boolean,
) {
    XUAN_PAPER("宣纸古籍", "宣", Color(0xFFF6E9CF), Color(0xFFE8D0A5), false),
    BAMBOO_GREEN("竹青雅韵", "竹", Color(0xFFE5F0DF), Color(0xFFB8D2B5), false),
    INDIGO_MOUNTAIN("黛青山水", "黛", Color(0xFFE5EEF0), Color(0xFFB4CDD0), false),
    CINNABAR_RED("朱砂雅集", "朱", Color(0xFFF7E5DD), Color(0xFFE8B8A8), false),
    AMBER_AUTUMN("琥珀秋笺", "琥", Color(0xFFF6E6C2), Color(0xFFE5BD78), false),
    NIGHT_INK("墨夜清辉", "夜", Color(0xFF182536), Color(0xFF34475B), true),
}

/**
 * 纹理位图全局单例缓存（仅位于 debug 模式，杜绝每帧重复解码）
 */
object TextureBitmapCache {
    private val cache = ConcurrentHashMap<TextureResolution, Bitmap>()

    suspend fun getBitmap(context: Context, res: TextureResolution): Bitmap = withContext(Dispatchers.IO) {
        if (res == TextureResolution.ORIGINAL) {
            // 直接复用全局单例缓存加载最新修复版原图 bg_paper_texture.webp
            return@withContext PaperTextureCache.getTextureBitmap(context, com.shi.yun.R.drawable.bg_paper_texture)
        }
        cache.getOrPut(res) {
            val startMs = System.currentTimeMillis()
            var inputStream: InputStream? = null
            try {
                inputStream = context.assets.open("texture_test/${res.fileName}")
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeStream(inputStream, null, opts)
                    ?: throw IllegalStateException("解码纹理失败: ${res.fileName}")
                val duration = System.currentTimeMillis() - startMs
                AppLogger.i("TextureTest", "纹理位图解码成功: ${res.displayName}, 尺寸=${bmp.width}x${bmp.height}, 耗时=${duration}ms")
                bmp
            } finally {
                inputStream?.close()
            }
        }
    }

    fun clear() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
    }
}

/**
 * AGSL 线性光着色器源码 (Photoshop 100% 数学等价实现)
 *
 * Photoshop 数学公式:
 * B: 底色渐变颜色, S: 混合贴图纹理颜色 (中性灰为 0.5 / 128)
 * Result = B + alpha * (2.0 * S - 1.0)
 */
private const val LINEAR_LIGHT_AGSL = """
uniform shader baseShader;
uniform shader textureShader;
uniform float alpha;

half4 main(float2 coord) {
    half4 base = baseShader.eval(coord);
    half4 tex = textureShader.eval(coord);
    // Photoshop Linear Light 数学原语
    half3 blended = base.rgb + alpha * (2.0 * tex.rgb - 1.0);
    blended = clamp(blended, 0.0, 1.0);
    return half4(blended, base.a);
}
"""

/**
 * 纸张纹理混合模式专项测试界面 (TextureTestScreen)
 *
 * 重新设计的布局特点：
 * 1. 上方：实时纹理全屏预览区，100% 满屏渲染背景与纹理，绝不被常驻面板遮挡；
 * 2. 下方：紧凑浮动控制栏（默认仅约 60dp），支持一键展开/收起完整调参抽屉；
 * 3. 一键纯净观察：点击空白处或“全屏”按钮可临时隐藏全部控制组件，观察完整背景；
 * 4. 独立参数持久化：支持保存当前参数、恢复上次保存、恢复默认，不影响正式系统。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextureTestScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 当前生效的测试参数状态（优先从本地 Debug SharedPreferences 装载上次保存的值）
    val initialConfig = remember { TextureTestPreferences.loadConfig(context) }
    var selectedTheme by remember { mutableStateOf(initialConfig.theme) }
    var isTextureEnabled by remember { mutableStateOf(initialConfig.isTextureEnabled) }
    var selectedResolution by remember { mutableStateOf(initialConfig.resolution) }
    var selectedBlendMode by remember { mutableStateOf(initialConfig.blendMode) }
    var textureAlpha by remember { mutableFloatStateOf(initialConfig.alpha) }
    var showPoemCard by remember { mutableStateOf(initialConfig.showPoemCard) }

    // 面板交互状态
    var isPanelExpanded by remember { mutableStateOf(false) } // 默认紧凑收起状态
    var isControlsVisible by remember { mutableStateOf(true) } // 是否展示浮层控制栏（支持全屏纯净模式）

    // 纹理位图状态
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingBitmap by remember { mutableStateOf(false) }

    // 性能监控状态
    var lastDrawDurationMs by remember { mutableLongStateOf(0L) }
    var frameCount by remember { mutableIntStateOf(0) }

    // 注册广播监听，方便自动化测试脚本精准调整主题与透明度
    androidx.compose.runtime.DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                intent?.getStringExtra("theme")?.let { themeStr ->
                    TestGradientTheme.entries.find { it.name.equals(themeStr, ignoreCase = true) || it.themeName == themeStr }?.let {
                        selectedTheme = it
                    }
                }
                if (intent?.hasExtra("alpha") == true) {
                    val a = intent.getFloatExtra("alpha", textureAlpha)
                    textureAlpha = a
                    isTextureEnabled = a > 0f
                }
                if (intent?.hasExtra("isTextureEnabled") == true) {
                    isTextureEnabled = intent.getBooleanExtra("isTextureEnabled", true)
                }
            }
        }
        val filter = android.content.IntentFilter("com.shi.yun.action.SET_TEXTURE_TEST_CONFIG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    // 异步装载当前所选分辨率的纹理位图
    LaunchedEffect(selectedResolution) {
        isLoadingBitmap = true
        try {
            val bmp = TextureBitmapCache.getBitmap(context, selectedResolution)
            currentBitmap = bmp
        } catch (e: Exception) {
            AppLogger.e("TextureTest", "加载纹理异常: ${e.message}", e)
        } finally {
            isLoadingBitmap = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable {
                // 点击背景空白处：如果处于展开状态则收起，如果隐藏了控制栏则重新唤出
                if (!isControlsVisible) {
                    isControlsVisible = true
                } else if (isPanelExpanded) {
                    isPanelExpanded = false
                }
            },
    ) {
        // ========== 1. 上方/全屏：实时纹理背景绘制区 (占据 100% 完整屏幕比例) ==========
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val drawStartNs = System.nanoTime()
            val canvasWidth = size.width
            val canvasHeight = size.height

            if (!isTextureEnabled || textureAlpha <= 0f || currentBitmap == null) {
                // 基准对照组：纯渐变背景绘制 (0% 透明度真正不绘制纹理)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(selectedTheme.topColor, selectedTheme.bottomColor),
                    ),
                    size = size,
                )
            } else {
                val bmp = currentBitmap!!
                val isLinearLightAgsl = selectedBlendMode == TextureBlendModeType.LINEAR_LIGHT &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

                if (isLinearLightAgsl) {
                    // 方案 A: AGSL 硬件着色器单次 Pass 完成渐变与线性光混合 (Android 13+)
                    val runtimeShader = RuntimeShader(LINEAR_LIGHT_AGSL)
                    val baseGradient = LinearGradient(
                        0f, 0f, 0f, canvasHeight,
                        selectedTheme.topColor.toArgb(), selectedTheme.bottomColor.toArgb(),
                        Shader.TileMode.CLAMP
                    )
                    val textureShader = BitmapShader(
                        bmp,
                        Shader.TileMode.REPEAT,
                        Shader.TileMode.REPEAT
                    )
                    runtimeShader.setInputShader("baseShader", baseGradient)
                    runtimeShader.setInputShader("textureShader", textureShader)
                    runtimeShader.setFloatUniform("alpha", textureAlpha)

                    val agslPaint = Paint().apply {
                        shader = runtimeShader
                    }
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, canvasWidth, canvasHeight, agslPaint)
                } else {
                    // 方案 B: 原生 BlendMode 叠加两趟 Pass (Overlay, SoftLight, Multiply, HardLight)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(selectedTheme.topColor, selectedTheme.bottomColor),
                        ),
                        size = size,
                    )
                    val textureShader = BitmapShader(
                        bmp,
                        Shader.TileMode.REPEAT,
                        Shader.TileMode.REPEAT
                    )
                    val blendPaint = Paint().apply {
                        shader = textureShader
                        alpha = (textureAlpha * 255f).roundToInt().coerceIn(0, 255)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            this.blendMode = when (selectedBlendMode) {
                                TextureBlendModeType.OVERLAY -> android.graphics.BlendMode.OVERLAY
                                TextureBlendModeType.SOFT_LIGHT -> android.graphics.BlendMode.SOFT_LIGHT
                                TextureBlendModeType.HARD_LIGHT -> android.graphics.BlendMode.HARD_LIGHT
                                TextureBlendModeType.MULTIPLY -> android.graphics.BlendMode.MULTIPLY
                                else -> android.graphics.BlendMode.SRC_OVER
                            }
                        }
                    }
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, canvasWidth, canvasHeight, blendPaint)
                }
            }

            val drawDurationNs = System.nanoTime() - drawStartNs
            lastDrawDurationMs = drawDurationNs / 1_000_000L
            frameCount++
        }

        // ========== 2. 真实正文阅读卡片（可切换隐藏/显示以查看纯底纹或文字可读性） ==========
        if (showPoemCard) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedTheme.isDark) Color(0x33182536) else Color(0x26FFFFFF),
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(width = 0.8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "春思",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTheme.isDark) Color(0xFFF2EFE8) else Color(0xFF1A1815),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "〔唐代〕李白  ·  诗",
                            fontSize = 13.sp,
                            color = if (selectedTheme.isDark) Color(0xFF969B95) else Color(0xFF62584E),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "燕草如碧丝，秦桑低绿枝。",
                            fontSize = 17.sp,
                            lineHeight = 28.sp,
                            color = if (selectedTheme.isDark) Color(0xFFDCD7CE) else Color(0xFF2D2824),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "当君怀归日，是妾断肠时。",
                            fontSize = 17.sp,
                            lineHeight = 28.sp,
                            color = if (selectedTheme.isDark) Color(0xFFDCD7CE) else Color(0xFF2D2824),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "春风不相识，何事入罗帏。",
                            fontSize = 17.sp,
                            lineHeight = 28.sp,
                            color = if (selectedTheme.isDark) Color(0xFFDCD7CE) else Color(0xFF2D2824),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // ========== 3. 顶部轻量浮动条 ==========
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 返回按钮与状态 Chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedTheme.isDark) Color(0x66000000) else Color(0x66FFFFFF))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Text(
                            text = "←",
                            fontSize = 18.sp,
                            color = if (selectedTheme.isDark) Color.White else Color(0xFF1A1815),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${selectedTheme.themeName} · ${if (isTextureEnabled && textureAlpha > 0f) "纹理开" else "纯色"} · ${(textureAlpha * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selectedTheme.isDark) Color.White else Color(0xFF1A1815),
                    )
                }

                // 右侧操作项：诗卡切换与纯净全屏
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 诗词卡片开关
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selectedTheme.isDark) Color(0x66000000) else Color(0x66FFFFFF))
                            .clickable { showPoemCard = !showPoemCard }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (showPoemCard) "📖 诗卡" else "🖼 纯底",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedTheme.isDark) Color.White else Color(0xFF1A1815),
                        )
                    }

                    // 纯净全屏观察开关（隐藏控制栏）
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selectedTheme.isDark) Color(0x66000000) else Color(0x66FFFFFF))
                            .clickable { isControlsVisible = false }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "👁 全屏",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedTheme.isDark) Color.White else Color(0xFF1A1815),
                        )
                    }
                }
            }
        }

        // ========== 4. 下方：参数控制区 (紧凑悬浮式底栏 + 展开抽屉) ==========
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .clickable(enabled = false) {}, // 阻止点击穿透到背景
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    // 拖拽把手与收起/展开按钮
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPanelExpanded = !isPanelExpanded }
                            .padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        )
                    }

                    // ---------- 常驻紧凑控制行 (高度仅约 54dp，收起状态下仍保留核心入口) ----------
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 1. 六套主题色快速圆点切换
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TestGradientTheme.entries.forEach { theme ->
                                val isSelected = selectedTheme == theme
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 0.5.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                            shape = CircleShape,
                                        )
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(theme.topColor, theme.bottomColor)
                                            )
                                        )
                                        .clickable { selectedTheme = theme },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = theme.shortName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (theme.isDark) Color.White else Color(0xFF222222),
                                    )
                                }
                            }
                        }

                        // 2. 纹理透明度快速循环与纹理开关
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 透明度循环切换 Chip (0% -> 5% -> 10% -> 15% -> 20%)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                    .clickable {
                                        val steps = listOf(0.0f, 0.05f, 0.10f, 0.15f, 0.20f)
                                        val curIdx = steps.indexOfFirst { (it * 100).roundToInt() == (textureAlpha * 100).roundToInt() }
                                        val nextAlpha = if (curIdx >= 0 && curIdx < steps.size - 1) steps[curIdx + 1] else steps[0]
                                        textureAlpha = nextAlpha
                                        isTextureEnabled = nextAlpha > 0f
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "α ${(textureAlpha * 100).roundToInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            // 纹理开关
                            Switch(
                                checked = isTextureEnabled,
                                onCheckedChange = { isTextureEnabled = it },
                                modifier = Modifier.size(width = 36.dp, height = 24.dp),
                            )

                            // 展开/收起切换按钮
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { isPanelExpanded = !isPanelExpanded }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (isPanelExpanded) "收起 ▼" else "更多 ▲",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // ---------- 展开后的高级调节抽屉 (最大占屏约 40%，绝不遮挡全部界面) ----------
                    AnimatedVisibility(
                        visible = isPanelExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )

                            // 1. 纹理图片尺寸选择
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "纹理尺寸",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextureResolution.entries.forEach { res ->
                                        val isSelected = selectedResolution == res
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                )
                                                .border(
                                                    width = if (isSelected) 1.2.dp else 0.5.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(6.dp),
                                                )
                                                .clickable { selectedResolution = res }
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = res.displayName,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. 混合模式选择
                            Text(
                                text = "混合模式 (重点: 线性光 vs 对照组)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                TextureBlendModeType.entries.forEach { mode ->
                                    val isSelected = selectedBlendMode == mode
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                            .border(
                                                width = if (isSelected) 1.2.dp else 0.5.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(6.dp),
                                            )
                                            .clickable { selectedBlendMode = mode }
                                            .padding(horizontal = 9.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            text = mode.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }

                            // 3. 透明度精确滑动与预设
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "纹理透明度: ${(textureAlpha * 100).roundToInt()}%",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(0.0f, 0.05f, 0.10f, 0.15f, 0.20f).forEach { opVal ->
                                        val isCur = (textureAlpha * 100).roundToInt() == (opVal * 100).roundToInt()
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable {
                                                    textureAlpha = opVal
                                                    isTextureEnabled = opVal > 0f
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = "${(opVal * 100).roundToInt()}%",
                                                fontSize = 10.sp,
                                                color = if (isCur) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            Slider(
                                value = textureAlpha,
                                onValueChange = { textureAlpha = it },
                                valueRange = 0.05f..0.40f,
                                steps = 7,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // 4. 【核心新功能】：开发测试参数持久化保存与恢复
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // 按钮 1: 保存当前参数
                                Box(
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable {
                                            val currentConfig = TextureTestConfig(
                                                theme = selectedTheme,
                                                resolution = selectedResolution,
                                                blendMode = selectedBlendMode,
                                                alpha = textureAlpha,
                                                isTextureEnabled = isTextureEnabled,
                                                showPoemCard = showPoemCard,
                                            )
                                            TextureTestPreferences.saveConfig(context, currentConfig)
                                            AppLogger.interaction("TextureTest", "SaveParams", "Click", "SUCCESS", "已保存开发测试参数: $currentConfig")
                                            Toast.makeText(context, "已保存当前测试参数！再次打开自动恢复", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "💾 保存当前参数",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }

                                // 按钮 2: 恢复上次保存
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable {
                                            if (TextureTestPreferences.hasSavedConfig(context)) {
                                                val saved = TextureTestPreferences.loadConfig(context)
                                                selectedTheme = saved.theme
                                                selectedResolution = saved.resolution
                                                selectedBlendMode = saved.blendMode
                                                textureAlpha = saved.alpha
                                                isTextureEnabled = saved.isTextureEnabled
                                                showPoemCard = saved.showPoemCard
                                                Toast.makeText(context, "已恢复上次保存参数！", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "暂无上次保存记录", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "↺ 恢复上次保存",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }

                                // 按钮 3: 恢复默认
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            val def = TextureTestPreferences.getDefaultConfig()
                                            selectedTheme = def.theme
                                            selectedResolution = def.resolution
                                            selectedBlendMode = def.blendMode
                                            textureAlpha = def.alpha
                                            isTextureEnabled = def.isTextureEnabled
                                            showPoemCard = def.showPoemCard
                                            Toast.makeText(context, "已重置为默认测试参数", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "⚙ 恢复默认",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // 5. 渲染监视摘要
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(8.dp),
                            ) {
                                Text(
                                    text = "渲染耗时 ≈ ${lastDrawDurationMs}ms | 显存=${selectedResolution.approxRamKb}KB | 模式=${if (selectedBlendMode.isAgslShader) "AGSL GPU着色器" else "原生离屏Blend"}",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
