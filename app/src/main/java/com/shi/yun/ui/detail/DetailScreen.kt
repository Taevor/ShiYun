package com.shi.yun.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Velocity
import com.shi.yun.data.model.AnnotationItem
import com.shi.yun.data.model.AuxiliaryContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.shi.yun.common.log.AppLogger
import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.LayoutType
import com.shi.yun.data.model.ReadingPreferences
import com.shi.yun.data.model.Work
import com.shi.yun.data.model.formatAuthorDynastyGenre
import com.shi.yun.data.repository.CustomFontRepository
import com.shi.yun.ui.animation.LocalContainerContentAlpha
import com.shi.yun.ui.favorite.FavoriteEmptyQuoteManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shi.yun.ui.theme.LocalAppFontFamily
import com.shi.yun.ui.theme.LocalAppTheme

/**
 * 诗词阅读排版参数配置（由 ReadingPreferences 动态生成并驱动 UI 渲染）
 *
 * @property bodyFontSize 正文字号
 * @property bodyLineHeight 正文行高
 * @property letterSpacing 文字字间距
 * @property fontFamily 字体类型（系统默认/系统衬线/系统等宽）
 * @property fontWeight 字体字重
 * @property horizontalPadding 左右阅读版心边距
 * @property lineSpacing 诗句句内/句间行距
 * @property coupletSpacing 绝句/律诗分联间距
 * @property stanzaSpacing 宋词分阕留白
 * @property paragraphSpacing 古文/长篇自然段落间距
 * @property firstLineIndentSpaces 古文段落首行缩进空格数
 * @property backgroundColor 阅读背景色
 * @property textColor 正文文字色
 */
data class PoemLayoutConfig(
    val bodyFontSize: TextUnit = 22.sp,
    val bodyLineHeight: TextUnit = 37.sp,
    val letterSpacing: TextUnit = 0.5.sp,
    val fontFamily: FontFamily? = null,
    val fontWeight: FontWeight = FontWeight.Medium,
    val horizontalPadding: Dp = 24.dp,
    val lineSpacing: Dp = 8.dp,
    val coupletSpacing: Dp = 16.dp,
    val stanzaSpacing: Dp = 18.dp,
    val paragraphSpacing: Dp = 20.dp,
    val firstLineIndentSpaces: Int = 2,
    val backgroundColor: Color? = null,
    val textColor: Color? = null,
) {
    companion object {
        val Default = PoemLayoutConfig()
    }
}

/**
 * 详情页正文加载状态
 */
enum class DetailContentStatus {
    LOADING,    // 正在加载中
    SUCCESS,    // 加载成功并已呈现正文
    NOT_FOUND,  // 作品不存在或已被废弃
    ERROR;      // 加载异常失败
}

/**
 * 诗词作品详情页
 *
 * 阶段五 B 全面升级：
 * 1. 词牌、词题与标题主副层级分明，避免重复；
 * 2. 小序题记独立雅致区块展示，空值不占位；
 * 3. 遵照格律验证行数后进行绝句、律诗分联分组；其他退回安全自然分行；
 * 4. 古文散文支持自然段首行缩进（TextIndent）与段落留白；
 * 5. 顶栏右侧新增「阅读版式」设置按钮，调出半屏设置面板，正文即时预览；
 * 6. 选本上一首/下一首平滑切篇与首尾禁用控制。
 */
@Composable
fun DetailScreen(
    work: Work,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onPreviousWork: (() -> Unit)? = null,
    onNextWork: (() -> Unit)? = null,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    preferences: ReadingPreferences = ReadingPreferences.Default,
    onPreferencesChange: ((ReadingPreferences) -> Unit)? = null,
    onResetPreferences: (() -> Unit)? = null,
    customFontRepository: CustomFontRepository? = null,
    auxiliaryContent: AuxiliaryContent? = null,
    isAuxiliaryLoading: Boolean = false,
    contentStatus: DetailContentStatus = if (work.content.isNotBlank()) DetailContentStatus.SUCCESS else DetailContentStatus.LOADING,
    onRetryLoad: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showAuxiliaryPanel by rememberSaveable { mutableStateOf(false) }
    var selectedAuxiliaryTab by rememberSaveable { mutableStateOf(AuxiliaryTab.ANNOTATION) }

    // 当作品 ID 变更时（如通过小组件热唤醒换篇或选本切篇），重置正文滚动条与面板状态
    LaunchedEffect(work.id) {
        scrollState.scrollTo(0)
        showAuxiliaryPanel = false
        showSettingsSheet = false
    }

    // 当辅助内容面板展开时，系统返回键优先收起辅助面板
    BackHandler(enabled = showAuxiliaryPanel) {
        AppLogger.interaction("DetailScreen", "SystemBack", "CloseAuxiliaryPanel", "SUCCESS", "系统返回键收起辅助面板")
        showAuxiliaryPanel = false
    }

    // 双击收藏轻量反馈状态
    var feedbackTrigger by remember { mutableIntStateOf(0) }
    var feedbackIsFavorite by remember { mutableStateOf(false) }
    val feedbackAlpha = remember { Animatable(0f) }
    val feedbackScale = remember { Animatable(0.7f) }

    LaunchedEffect(feedbackTrigger) {
        if (feedbackTrigger > 0) {
            feedbackAlpha.snapTo(0f)
            feedbackScale.snapTo(0.7f)
            launch {
                feedbackScale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
                )
            }
            feedbackAlpha.animateTo(1f, animationSpec = tween(150))
            delay(500)
            feedbackAlpha.animateTo(0f, animationSpec = tween(250))
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val activeFont = LocalAppFontFamily.current

    // 将全局阅读偏好动态解析为当前排版契约配置
    val layoutConfig = remember(preferences, surfaceColor, onSurfaceColor, activeFont) {
        preferences.toPoemLayoutConfig(
            themeSurface = surfaceColor,
            themeOnSurface = onSurfaceColor,
            activeFontFamily = activeFont,
        )
    }

    // ========== 状态变动响应式监听（仅在值实质变化时记录日志） ==========
    LaunchedEffect(isFavorite) {
        AppLogger.stateChange("DetailScreen", "FavoriteStatus", if (isFavorite) "已收藏" else "未收藏", "作品ID=${work.id}, 标题=${work.title}")
    }

    LaunchedEffect(showAuxiliaryPanel) {
        AppLogger.stateChange("DetailScreen", "AuxiliaryPanelVisible", showAuxiliaryPanel, "当前标签=${selectedAuxiliaryTab.displayName}")
    }

    LaunchedEffect(selectedAuxiliaryTab) {
        if (showAuxiliaryPanel) {
            AppLogger.stateChange("DetailScreen", "SelectedAuxiliaryTab", selectedAuxiliaryTab.displayName, "作品ID=${work.id}")
        }
    }

    // 监听正文垂直滑动状态
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            AppLogger.gesture(null, "DetailScreen", "PoemScrollView", "SCROLL_START", "开始垂直滑动, 当前位移=${scrollState.value}/${scrollState.maxValue}")
        } else {
            AppLogger.gesture(null, "DetailScreen", "PoemScrollView", "SCROLL_END", "停止垂直滑动, 最终位移=${scrollState.value}/${scrollState.maxValue}")
        }
    }

    val contentAlpha = LocalContainerContentAlpha.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 1. 底层：主阅读界面（包含顶部返回/设置/收藏栏、正文独立滚动卡片、底部辅助入口与选本控制栏）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                },
        ) {
            // 顶部操作栏（左侧极简返回键，右侧排版设置与收藏红心按钮，不再放置辅助内容入口）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        AppLogger.interaction("DetailScreen", "TopBackButton", "Click", "SUCCESS", "返回上一级页面")
                        onBack()
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    BackArrowIcon(tint = MaterialTheme.colorScheme.onSurface)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 阅读版式调节按钮
                    IconButton(
                        onClick = {
                            AppLogger.interaction("DetailScreen", "TopSettingsButton", "Click", "SUCCESS", "呼出阅读版式设置弹窗")
                            showSettingsSheet = true
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        SettingsSlidersIcon(tint = MaterialTheme.colorScheme.onSurface)
                    }

                    // 收藏红心按钮（全区域 48dp 响应，并与正文双击共用同一收藏状态及视觉反馈动效）
                    val heartTint = LocalAppTheme.current.customTokens.heartFavoriteTint
                    IconButton(
                        onClick = {
                            val next = !isFavorite
                            AppLogger.interaction(
                                page = "DetailScreen",
                                widget = "TopFavoriteButton",
                                event = "Click",
                                result = "SUCCESS",
                                detail = "触发收藏切换: ID=${work.id}, 变动前=${if (isFavorite) "已收藏" else "未收藏"} -> 变动后=${if (next) "已收藏" else "未收藏"}"
                            )
                            onToggleFavorite()
                            feedbackIsFavorite = next
                            feedbackTrigger++
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = FavoriteEmptyQuoteManager.FIRST_TIME_FAVORITE_HINT
                            },
                    ) {
                        FavoriteHeartIcon(
                            isFavorite = isFavorite,
                            tint = if (isFavorite) heartTint else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            val density = LocalDensity.current
            val swipeThresholdPx = with(density) { 50.dp.toPx() }
            var horizontalDragAccumulator by remember { mutableFloatStateOf(0f) }
            var pageTurnDirection by remember { mutableStateOf<com.shi.yun.ui.home.SwipeDirection>(com.shi.yun.ui.home.SwipeDirection.NONE) }
            var isPageTurning by remember { mutableStateOf(false) }

            // 监听切篇完成，重置翻页锁定状态
            LaunchedEffect(work.id) {
                delay(330L)
                isPageTurning = false
            }

            val canSwipe = (hasPrevious || hasNext) && (onPreviousWork != null || onNextWork != null)
            val swipeModifier = if (canSwipe) {
                Modifier.pointerInput(hasPrevious, hasNext) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            horizontalDragAccumulator = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            horizontalDragAccumulator += dragAmount
                            if (abs(horizontalDragAccumulator) > swipeThresholdPx) {
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (!isPageTurning) {
                                if (horizontalDragAccumulator < -swipeThresholdPx && hasNext) {
                                    pageTurnDirection = com.shi.yun.ui.home.SwipeDirection.NEXT
                                    isPageTurning = true
                                    AppLogger.interaction("DetailScreen", "PoemArea", "SwipeNext", "SUCCESS", "水平向左滑仿真翻页切换下一首")
                                    onNextWork?.invoke()
                                } else if (horizontalDragAccumulator > swipeThresholdPx && hasPrevious) {
                                    pageTurnDirection = com.shi.yun.ui.home.SwipeDirection.PREVIOUS
                                    isPageTurning = true
                                    AppLogger.interaction("DetailScreen", "PoemArea", "SwipePrev", "SUCCESS", "水平向右滑仿真翻页切换上一首")
                                    onPreviousWork?.invoke()
                                }
                            }
                            horizontalDragAccumulator = 0f
                        },
                        onDragCancel = {
                            horizontalDragAccumulator = 0f
                        }
                    )
                }
            } else Modifier

            // 详情内容可滚动区域（支持双击收藏 + 仿真翻页水平滑动切篇：左滑下一篇自然翻出，右滑上一篇自然翻出）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(swipeModifier)
                    .pointerInput(work.id, isFavorite) {
                        detectTapGestures(
                            onDoubleTap = {
                                val next = !isFavorite
                                onToggleFavorite()
                                feedbackIsFavorite = next
                                feedbackTrigger++
                            },
                        )
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                // 仿真纸张书卷翻页切换动效
                AnimatedContent(
                    targetState = work,
                    transitionSpec = {
                        val isNext = pageTurnDirection == com.shi.yun.ui.home.SwipeDirection.NEXT
                        if (pageTurnDirection != com.shi.yun.ui.home.SwipeDirection.NONE) {
                            (slideInHorizontally(
                                initialOffsetX = { fullWidth -> if (isNext) fullWidth else -fullWidth },
                                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(260))).togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> if (isNext) -fullWidth / 3 else fullWidth / 3 },
                                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(220)) + scaleOut(
                                    targetScale = 0.94f,
                                    transformOrigin = if (isNext) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                                )
                            )
                        } else {
                            fadeIn(animationSpec = tween(180)).togetherWith(fadeOut(animationSpec = tween(180)))
                        }
                    },
                    label = "PoemPageTurn",
                    modifier = Modifier.fillMaxSize(),
                ) { currentWork ->
                    val pageScrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .verticalScroll(pageScrollState)
                            .padding(horizontal = layoutConfig.horizontalPadding, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 1. 标题与作者信息层级
                        PoemTitleSection(work = currentWork)

                        // 2. 小序 / 题记区块（若存在且不为空白，且过滤脏数据 "null"）
                        val preface = currentWork.preface?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
                        if (preface != null) {
                            Spacer(modifier = Modifier.height(20.dp))
                            PoemPrefaceSection(preface = preface)
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // 3. 完整正文排版（根据体裁与排版契约动态渲染，受全屏统一透明度驱动，绝不修改原始数据）
                        PoemContentLayout(
                            work = currentWork,
                            config = layoutConfig,
                            contentStatus = contentStatus,
                            onRetryLoad = onRetryLoad,
                        )

                        // 底部留白，保障视觉呼吸感
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                // 浮层遮罩拦截层：当辅助面板展开时，正文区域覆盖遮罩
                // 点击正文区域收起辅助面板，同时阻断点击事件下透，防止误触发双击收藏或滑动
                // 仅覆盖正文区域，不覆盖底部栏，避免拦截底部三个入口或在展开动画期间误触关闭
                if (showAuxiliaryPanel) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        AppLogger.interaction("DetailScreen", "ScrimLayer", "Click", "SUCCESS", "点击正文遮罩区域收起辅助面板")
                                        AppLogger.gesture(null, "DetailScreen", "ScrimLayer", "SINGLE_TAP", "消费点击事件并收起辅助面板")
                                        showAuxiliaryPanel = false
                                    }
                                )
                            }
                    )
                }
            }

            // 底部操作栏：辅助内容三大入口 [注释] [译文] [赏析]，居中纯净
            DetailBottomBar(
                onToggleAuxiliary = { tab ->
                    val isClosing = showAuxiliaryPanel && selectedAuxiliaryTab == tab
                    AppLogger.interaction(
                        page = "DetailScreen",
                        widget = "DetailBottomBar",
                        event = "ToggleAuxiliaryTab",
                        result = "SUCCESS",
                        detail = "点击入口 [${tab.displayName}], 操作=${if (isClosing) "收起面板" else "展开/切换面板"}"
                    )
                    if (isClosing) {
                        showAuxiliaryPanel = false
                    } else {
                        selectedAuxiliaryTab = tab
                        showAuxiliaryPanel = true
                    }
                },
                currentTab = if (showAuxiliaryPanel) selectedAuxiliaryTab else null,
            )
        }

        // 2. 独立固定高度底部浮层面板（Overlay！绝对悬浮在正文下方，不挤压正文、不改变正文布局与滚动位置）
        androidx.compose.animation.AnimatedVisibility(
            visible = showAuxiliaryPanel,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 240),
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 200),
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AuxiliaryContentPanel(
                auxiliary = auxiliaryContent,
                selectedTab = selectedAuxiliaryTab,
                onTabSelected = { selectedAuxiliaryTab = it },
                isLoading = isAuxiliaryLoading,
            )
        }

        // 3. 收藏轻量浮层反馈（居中展示心形与提示，温润宣纸卡片质感，自动柔和淡出）
        if (feedbackAlpha.value > 0f) {
            val appTheme = LocalAppTheme.current
            val isDark = appTheme.themeId.isDark
            val cardBg = appTheme.customTokens.cardBackground.copy(alpha = if (isDark) 0.96f else 0.95f)
            val borderColor = appTheme.customTokens.cardBorder
            val textColor = MaterialTheme.colorScheme.onSurface

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = feedbackAlpha.value
                        scaleX = feedbackScale.value
                        scaleY = feedbackScale.value
                    }
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = Color.Black.copy(alpha = 0.08f),
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .border(
                        width = 0.6.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FavoriteHeartIcon(
                        isFavorite = feedbackIsFavorite,
                        tint = if (feedbackIsFavorite) appTheme.customTokens.heartFavoriteTint else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = if (feedbackIsFavorite) "已收藏" else "已取消收藏",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                    )
                }
            }
        }
    }

    // 呼出个性化阅读版式调节半屏弹窗
    if (showSettingsSheet && onPreferencesChange != null) {
        ReadingSettingsSheet(
            preferences = preferences,
            onPreferencesChange = onPreferencesChange,
            onResetToDefault = { onResetPreferences?.invoke() },
            onDismissRequest = { showSettingsSheet = false },
            customFontRepository = customFontRepository,
        )
    }
}

/**
 * 底部操作栏：辅助内容三大入口 [注释] [译文] [赏析]，严格居中，直接响应，即触即开
 */
@Composable
private fun DetailBottomBar(
    onToggleAuxiliary: (AuxiliaryTab) -> Unit,
    currentTab: AuxiliaryTab?,
    modifier: Modifier = Modifier,
) {
    val activeFont = LocalAppFontFamily.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuxiliaryTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                Surface(
                    onClick = { onToggleAuxiliary(tab) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                    },
                    border = BorderStroke(
                        0.6.dp,
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                        },
                    ),
                    modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 36.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = tab.shortName,
                            fontSize = 14.sp,
                            fontFamily = activeFont,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 诗词标题与元信息分层排版
 */
@Composable
private fun PoemTitleSection(work: Work) {
    val (primaryTitle, subtitle) = remember(work) {
        resolveTitleAndSubtitle(work)
    }
    val activeFont = LocalAppFontFamily.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 主标题 / 词牌
        Text(
            text = primaryTitle,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = activeFont),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        // 词题 / 副标题（避免与主标题重复）
        if (!subtitle.isNullOrBlank() && subtitle != primaryTitle) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = activeFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 作者、朝代与体裁
        Text(
            text = work.formatAuthorDynastyGenre(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = activeFont),
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 解析并拆分主标题与副标题，优先使用结构化 cipai 与 subtitle，并兼容含 · 的复合标题
 */
private fun resolveTitleAndSubtitle(work: Work): Pair<String, String?> {
    val rawCipai = work.cipai?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    val rawSubtitle = work.subtitle?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    if (!rawCipai.isNullOrEmpty()) {
        val sub = if (!rawSubtitle.isNullOrEmpty()) {
            rawSubtitle
        } else if (work.title.contains("·")) {
            val parts = work.title.split("·")
            if (parts.size >= 2 && parts[0].trim() == rawCipai) {
                parts.subList(1, parts.size).joinToString("·").trim()
            } else null
        } else null
        return Pair(rawCipai, sub)
    }

    if (work.title.contains("·")) {
        val parts = work.title.split("·")
        if (parts.size >= 2 && parts[0].isNotBlank()) {
            val main = parts[0].trim()
            val sub = parts.subList(1, parts.size).joinToString("·").trim()
            return Pair(main, sub.ifEmpty { null })
        }
    }

    return Pair(work.title, rawSubtitle.takeIf { !it.isNullOrBlank() })
}

/**
 * 小序 / 题记展示区块
 */
@Composable
private fun PoemPrefaceSection(
    preface: String,
    modifier: Modifier = Modifier,
) {
    val activeFont = LocalAppFontFamily.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column {
            Text(
                text = "【小序】",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = activeFont),
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = preface,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = activeFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 诗词正文精细化排版组件
 *
 * 遵循严格原则：
 * 1. 绝不修改或截断原始正文文字与标点；
 * 2. 古文散文支持自然段落首行缩进（TextIndent）与段间距；
 * 3. 绝句验证 4 行后分两联；
 * 4. 律诗验证 8 行后分四联（首联、颔联、颈联、尾联）；
 * 5. 宋词若有明确 \n\n 则按分阕留白，否则安全保留原本行序与行距，绝不猜测切阕；
 * 6. 全面接入 PoemLayoutConfig 参数，支持字号、行距、字距、字重与边距。
 */
@Composable
private fun PoemContentLayout(
    work: Work,
    config: PoemLayoutConfig = PoemLayoutConfig.Default,
    contentStatus: DetailContentStatus = DetailContentStatus.SUCCESS,
    onRetryLoad: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val activeFont = config.fontFamily ?: LocalAppFontFamily.current

    when (contentStatus) {
        DetailContentStatus.LOADING -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "正文载入中…",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = activeFont,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                        letterSpacing = 0.5.sp,
                    ),
                )
            }
            return
        }
        DetailContentStatus.NOT_FOUND -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "未找到该作品正文",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = activeFont,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                        letterSpacing = 0.5.sp,
                    ),
                )
            }
            return
        }
        DetailContentStatus.ERROR -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "正文载入失败",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = activeFont,
                        color = MaterialTheme.colorScheme.error,
                        letterSpacing = 0.5.sp,
                    ),
                )
                if (onRetryLoad != null) {
                    Button(
                        onClick = onRetryLoad,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "重新载入",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = activeFont),
                        )
                    }
                }
            }
            return
        }
        DetailContentStatus.SUCCESS -> {
            val rawContent = work.content
            if (rawContent.isBlank()) {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无正文内容",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = activeFont,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                            letterSpacing = 0.5.sp,
                        ),
                    )
                }
                return
            }
        }
    }

    val rawContent = work.content
    val isProse = work.genre == Genre.WEN || work.layout == LayoutType.PROSE

    // 1. 古文散文排版分支：自然段首行缩进两字符，左右两端对齐，末行自然左对齐，段落间应用 paragraphSpacing
    if (isProse) {
        val paragraphs = if (rawContent.contains("\n\n")) {
            rawContent.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            rawContent.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        }

        val baseStyle = MaterialTheme.typography.bodyLarge
        val textStyle = baseStyle.copy(
            fontSize = config.bodyFontSize,
            lineHeight = config.bodyLineHeight,
            letterSpacing = config.letterSpacing,
            fontFamily = config.fontFamily ?: baseStyle.fontFamily,
            fontWeight = config.fontWeight,
            textIndent = if (config.firstLineIndentSpaces > 0) {
                TextIndent(firstLine = config.bodyFontSize * config.firstLineIndentSpaces)
            } else TextIndent.None,
        )
        val textColor = config.textColor ?: MaterialTheme.colorScheme.onSurface

        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(config.paragraphSpacing),
        ) {
            paragraphs.forEach { rawParagraph ->
                val cleanParagraph = rawParagraph.trim()
                    .removePrefix("　　")
                    .removePrefix("  ")
                Text(
                    text = cleanParagraph,
                    style = textStyle,
                    color = textColor,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }

    // 2. 诗词类排版分支：以完整句子为基本换行单位，对仗两句优先同行，行宽受限时整句换行
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val maxWidthPx = with(density) { maxWidth.toPx() }

        // 宋词排版分支：若原始文本有明确 \n\n，则按阕应用 stanzaSpacing；否则按原始换行应用 lineSpacing
        if (work.genre == Genre.CI) {
            if (rawContent.contains("\n\n")) {
                val stanzas = rawContent.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(config.stanzaSpacing),
                ) {
                    stanzas.forEach { stanza ->
                        val lines = stanza.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        PoemLineGroup(lines = lines, config = config, textMeasurer = textMeasurer, maxWidthPx = maxWidthPx)
                    }
                }
                return@BoxWithConstraints
            }

            val lines = rawContent.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(config.lineSpacing),
            ) {
                PoemLineGroup(lines = lines, config = config, textMeasurer = textMeasurer, maxWidthPx = maxWidthPx)
            }
            return@BoxWithConstraints
        }

        val lines = rawContent.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val layout = work.layout

        // 绝句：必须严格验证行数为 4 句才分两联
        if ((layout == LayoutType.FIVE_JUEJU || layout == LayoutType.SEVEN_JUEJU) && lines.size == 4) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(config.coupletSpacing),
            ) {
                PoemLineGroup(lines = lines.subList(0, 2), config = config, textMeasurer = textMeasurer, maxWidthPx = maxWidthPx)
                PoemLineGroup(lines = lines.subList(2, 4), config = config, textMeasurer = textMeasurer, maxWidthPx = maxWidthPx)
            }
            return@BoxWithConstraints
        }

        // 律诗：必须严格验证行数为 8 句才分四联
        if ((layout == LayoutType.FIVE_LVSHI || layout == LayoutType.SEVEN_LVSHI) && lines.size == 8) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(config.coupletSpacing),
            ) {
                PoemLineGroup(lines = lines.subList(0, 2), config = config, textMeasurer = textMeasurer, maxWidthPx = maxWidthPx) // 首联
                PoemLineGroup(lines = lines.subList(2, 4), config = config, textMeasurer = textMeasurer, maxWidthPx = maxWidthPx) // 颔联
                PoemLineGroup(lines = lines.subList(4, 6), config = config, textMeasurer = textMeasurer, maxWidthPx = maxWidthPx) // 颈联
                PoemLineGroup(lines = lines.subList(6, 8), config = config, textMeasurer = textMeasurer, maxWidthPx = maxWidthPx) // 尾联
            }
            return@BoxWithConstraints
        }

        // 长篇古体诗或行数非标准的诗作：
        if (rawContent.contains("\n\n")) {
            val sections = rawContent.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(config.paragraphSpacing),
            ) {
                sections.forEach { section ->
                    val sectionLines = section.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    PoemLineGroup(lines = sectionLines, config = config, textMeasurer = textMeasurer, maxWidthPx = maxWidthPx)
                }
            }
            return@BoxWithConstraints
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(config.lineSpacing),
        ) {
            PoemLineGroup(lines = lines, config = config, textMeasurer = textMeasurer, maxWidthPx = maxWidthPx)
        }
    }
}

/**
 * 单个诗联或自然段落内的诗句渲染（整句换行，对仗两句优先同行，绝不随意拆断单句）
 */
@Composable
private fun PoemLineGroup(
    lines: List<String>,
    config: PoemLayoutConfig = PoemLayoutConfig.Default,
    textMeasurer: TextMeasurer,
    maxWidthPx: Float,
    modifier: Modifier = Modifier,
) {
    val baseStyle = MaterialTheme.typography.bodyLarge
    val textStyle = baseStyle.copy(
        fontSize = config.bodyFontSize,
        lineHeight = config.bodyLineHeight,
        letterSpacing = config.letterSpacing,
        fontFamily = config.fontFamily ?: baseStyle.fontFamily,
        fontWeight = config.fontWeight,
    )
    val textColor = config.textColor ?: MaterialTheme.colorScheme.onSurface

    val formattedLines = remember(lines, textStyle, maxWidthPx) {
        val packed = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val clauses = PoemTypography.splitIntoClauses(line)

            if (clauses.size > 1) {
                // 单行中已经包含多个分句（如：白日依山尽，黄河入海流。）
                val linePacked = PoemTypography.packClausesIntoLines(clauses, textMeasurer, textStyle, maxWidthPx)
                packed.addAll(linePacked)
                i++
            } else if (i + 1 < lines.size && lines[i].length <= 10 && lines[i + 1].length <= 10) {
                // 两个独立行构成的一联（如 line[i] = "白日依山尽，", line[i+1] = "黄河入海流。"）
                // 优先作为对仗排版组：同一行能容纳则排在同一行，否则各占一行
                val pairClauses = clauses + PoemTypography.splitIntoClauses(lines[i + 1])
                val pairPacked = PoemTypography.packClausesIntoLines(pairClauses, textMeasurer, textStyle, maxWidthPx)
                packed.addAll(pairPacked)
                i += 2
            } else {
                val linePacked = PoemTypography.packClausesIntoLines(clauses, textMeasurer, textStyle, maxWidthPx)
                packed.addAll(linePacked)
                i++
            }
        }
        packed
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(config.lineSpacing),
    ) {
        formattedLines.forEach { line ->
            Text(
                text = line,
                style = textStyle,
                color = textColor,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1,
            )
        }
    }
}

/**
 * 极简中式返回箭头图标（原生 Canvas 绘制，零外部依赖）
 */
@Composable
private fun BackArrowIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = Modifier.size(20.dp).then(modifier)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height

        drawLine(
            color = tint,
            start = Offset(w * 0.15f, h * 0.5f),
            end = Offset(w * 0.85f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.15f, h * 0.5f),
            end = Offset(w * 0.45f, h * 0.2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.15f, h * 0.5f),
            end = Offset(w * 0.45f, h * 0.8f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 极简中式排版调节图标（原生 Canvas 绘制，三条滑道与滑钮）
 */
@Composable
private fun SettingsSlidersIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = Modifier.size(20.dp).then(modifier)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        // 顶层滑轨与滑块
        drawLine(
            color = tint,
            start = Offset(w * 0.12f, h * 0.25f),
            end = Offset(w * 0.88f, h * 0.25f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = tint,
            radius = 2.5.dp.toPx(),
            center = Offset(w * 0.35f, h * 0.25f),
        )

        // 中层滑轨与滑块
        drawLine(
            color = tint,
            start = Offset(w * 0.12f, h * 0.5f),
            end = Offset(w * 0.88f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = tint,
            radius = 2.5.dp.toPx(),
            center = Offset(w * 0.68f, h * 0.5f),
        )

        // 底层滑轨与滑块
        drawLine(
            color = tint,
            start = Offset(w * 0.12f, h * 0.75f),
            end = Offset(w * 0.88f, h * 0.75f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = tint,
            radius = 2.5.dp.toPx(),
            center = Offset(w * 0.42f, h * 0.75f),
        )
    }
}

/**
 * 极简心形收藏图标（原生 Canvas 绘制，黄金比例对称典雅心形，零外部依赖）
 */
@Composable
private fun FavoriteHeartIcon(
    isFavorite: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = Modifier.size(22.dp).then(modifier)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.25f)
            // 左上圆弧段：从中央凹陷向左上方优雅隆起
            cubicTo(
                w * 0.44f, h * 0.14f,
                w * 0.30f, h * 0.10f,
                w * 0.20f, h * 0.16f,
            )
            // 左侧肩部向左下过渡段
            cubicTo(
                w * 0.07f, h * 0.24f,
                w * 0.05f, h * 0.44f,
                w * 0.16f, h * 0.58f,
            )
            // 左侧下弧向底部心尖收束
            cubicTo(
                w * 0.25f, h * 0.70f,
                w * 0.42f, h * 0.80f,
                w * 0.50f, h * 0.88f,
            )
            // 右侧从心尖向右上方展开
            cubicTo(
                w * 0.58f, h * 0.80f,
                w * 0.75f, h * 0.70f,
                w * 0.84f, h * 0.58f,
            )
            // 右侧肩部向上隆起
            cubicTo(
                w * 0.95f, h * 0.44f,
                w * 0.93f, h * 0.24f,
                w * 0.80f, h * 0.16f,
            )
            // 右上圆弧向中央凹陷平滑闭合
            cubicTo(
                w * 0.70f, h * 0.10f,
                w * 0.56f, h * 0.14f,
                w * 0.50f, h * 0.25f,
            )
            close()
        }

        if (isFavorite) {
            drawPath(path = path, color = tint)
        } else {
            drawPath(
                path = path,
                color = tint,
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/**
 * 选本翻页极简左箭头
 */
@Composable
private fun SmallArrowLeftIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        drawLine(
            color = tint,
            start = Offset(w * 0.65f, h * 0.2f),
            end = Offset(w * 0.35f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.35f, h * 0.5f),
            end = Offset(w * 0.65f, h * 0.8f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 选本翻页极简右箭头
 */
@Composable
private fun SmallArrowRightIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        drawLine(
            color = tint,
            start = Offset(w * 0.35f, h * 0.2f),
            end = Offset(w * 0.65f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.65f, h * 0.5f),
            end = Offset(w * 0.35f, h * 0.8f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 辅助内容展示标签枚举
 */
enum class AuxiliaryTab(val displayName: String, val shortName: String) {
    ANNOTATION("注释", "注"),
    TRANSLATION("译文", "译"),
    APPRECIATION("赏析", "赏");
}

/**
 * 诗词辅助内容浮层面板 (注释、译文、赏析)
 *
 * 规范约束：
 * 1. 底部固定高度独立浮层，覆盖正文底部区域，不挤压正文，不重新计算正文分页与排版；
 * 2. 顶部始终常驻「注释」「译文」「赏析」三个标签，水平严格居中对齐；
 * 3. 标签切换时平滑更新，内容为空时雅致展示「暂无相关内容」；
 * 4. 内部采用 LazyColumn 独立滚动，彻底解决垂直长内容（如《岳阳楼记》46条注释与长篇赏析）的完整流畅上下滑动；
 * 5. 手势与飞掠阻断下透，到达边界时不带动底层正文滚动。
 */
@Composable
fun AuxiliaryContentPanel(
    auxiliary: AuxiliaryContent?,
    selectedTab: AuxiliaryTab,
    onTabSelected: (AuxiliaryTab) -> Unit,
    isLoading: Boolean = false,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val appTheme = LocalAppTheme.current
    val isDark = appTheme.themeId.isDark
    val panelBg = if (isDark) {
        appTheme.customTokens.cardBackground.copy(alpha = 0.98f)
    } else {
        appTheme.customTokens.cardBackground.copy(alpha = 0.96f)
    }
    val panelBorder = appTheme.customTokens.cardBorder.copy(alpha = if (isDark) 0.50f else 0.35f)
    val activeFont = LocalAppFontFamily.current
    val configuration = LocalConfiguration.current
    val panelHeight = (configuration.screenHeightDp.dp * 0.48f).coerceIn(360.dp, 500.dp)
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) {
        AuxiliaryTab.entries.size
    }

    // 仅在非手势滑动期间（如底部按钮切换时）平滑同步 pager 页面，绝不与用户手指拖拽竞争
    LaunchedEffect(selectedTab) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != selectedTab.ordinal) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
    }

    // pager 横向滑动停驻后，实时同步更新外部 selectedTab
    LaunchedEffect(pagerState.currentPage) {
        val tab = AuxiliaryTab.entries.getOrNull(pagerState.currentPage)
        if (tab != null && tab != selectedTab) {
            onTabSelected(tab)
        }
    }

    Surface(
        modifier = modifier
            .height(panelHeight)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.12f),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.20f else 0.06f),
            ),
        shape = RoundedCornerShape(16.dp),
        color = panelBg,
        border = BorderStroke(0.6.dp, panelBorder),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部控制条：居中固定三标签（注释 · 译文 · 赏析），点击与左右滑动手势双向联动
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AuxiliaryTab.entries.forEach { tab ->
                        val isSelected = tab.ordinal == pagerState.currentPage
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = 28.dp),
                                    onClick = {
                                        AppLogger.interaction("AuxiliaryPanel", "Tab_${tab.name}", "Click", "SUCCESS", "点击选项卡 [${tab.displayName}]")
                                        onTabSelected(tab)
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(tab.ordinal)
                                        }
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = tab.displayName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = activeFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp,
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            // 底部指示条
                            Box(
                                modifier = Modifier
                                    .height(2.5.dp)
                                    .width(22.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(1.5.dp),
                                    ),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = panelBorder.copy(alpha = 0.40f),
                thickness = 0.6.dp,
            )

            // 内部横向滑动与纵向滚动协调容器：HorizontalPager 支持手势左右滑动切换标签；
            // 内部每个页面独立 LazyColumn 支持自然顶部/底部 Stretch Overscroll 回弹
            if (isLoading && auxiliary == null) {
                EmptyAuxiliaryContent(message = "解析辅助内容中…", fontFamily = activeFont)
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { pageIndex ->
                    val pageTab = AuxiliaryTab.entries[pageIndex]
                    val listState = rememberLazyListState()

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (pageTab) {
                            AuxiliaryTab.ANNOTATION -> {
                                val annotations = auxiliary?.annotations
                                if (annotations.isNullOrEmpty()) {
                                    item {
                                        EmptyAuxiliaryContent(message = "暂无相关内容", fontFamily = activeFont)
                                    }
                                } else {
                                    items(
                                        items = annotations,
                                        key = { it.index },
                                    ) { item ->
                                        AnnotationItemRow(
                                            item = item,
                                            fontFamily = activeFont,
                                        )
                                    }
                                }
                            }
                            AuxiliaryTab.TRANSLATION -> {
                                val translation = auxiliary?.translation?.trim()?.takeIf { it.isNotEmpty() }
                                if (translation == null) {
                                    item {
                                        EmptyAuxiliaryContent(message = "暂无相关内容", fontFamily = activeFont)
                                    }
                                } else {
                                    val paragraphs = translation.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                                    items(paragraphs) { paragraph ->
                                        Text(
                                            text = paragraph,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = activeFont,
                                                fontSize = 14.sp,
                                                lineHeight = 24.sp,
                                                letterSpacing = 0.3.sp,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                            AuxiliaryTab.APPRECIATION -> {
                                val appreciation = auxiliary?.appreciation?.trim()?.takeIf { it.isNotEmpty() }
                                if (appreciation == null) {
                                    item {
                                        EmptyAuxiliaryContent(message = "暂无相关内容", fontFamily = activeFont)
                                    }
                                } else {
                                    val paragraphs = appreciation.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                                    items(paragraphs) { paragraph ->
                                        Text(
                                            text = paragraph,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = activeFont,
                                                fontSize = 14.sp,
                                                lineHeight = 24.sp,
                                                letterSpacing = 0.3.sp,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单条注释排版渲染（移除所有来源与底本文字展示）
 */
@Composable
private fun AnnotationItemRow(
    item: AnnotationItem,
    fontFamily: FontFamily?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "${item.index}. ",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "【${item.quote}】",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!item.phonetic.isNullOrBlank()) {
                    Text(
                        text = "(${item.phonetic})",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = fontFamily,
                            fontSize = 13.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.note,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = fontFamily,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 空状态优雅提示
 */
@Composable
private fun EmptyAuxiliaryContent(
    message: String = "暂无相关内容",
    fontFamily: FontFamily? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = fontFamily,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
        )
    }
}

/**
 * 极简中式笺注书卷图标（原生 Canvas 绘制，线装书卷与笺注横线）
 */
@Composable
private fun AuxiliaryBookIcon(
    isActive: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = Modifier.size(20.dp).then(modifier)) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        val left = w * 0.18f
        val top = h * 0.12f
        val right = w * 0.82f
        val bottom = h * 0.88f
        val cornerRadius = 2.dp.toPx()

        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // 激活状态下绘制雅致半透明内衬底色
        if (isActive) {
            drawRoundRect(
                color = tint.copy(alpha = 0.15f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
            )
        }

        // 线装书脊细线
        drawLine(
            color = tint,
            start = Offset(w * 0.35f, top),
            end = Offset(w * 0.35f, bottom),
            strokeWidth = stroke * 0.8f,
            cap = StrokeCap.Round,
        )

        // 笺注文条（右侧两条书页横线）
        drawLine(
            color = tint,
            start = Offset(w * 0.46f, h * 0.36f),
            end = Offset(w * 0.72f, h * 0.36f),
            strokeWidth = stroke * 0.9f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.46f, h * 0.58f),
            end = Offset(w * 0.68f, h * 0.58f),
            strokeWidth = stroke * 0.9f,
            cap = StrokeCap.Round,
        )
    }
}
