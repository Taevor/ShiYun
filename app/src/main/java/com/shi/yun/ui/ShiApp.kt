package com.shi.yun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shi.yun.R
import com.shi.yun.data.model.Work
import com.shi.yun.data.model.toWork
import com.shi.yun.data.repository.CustomFontRepository
import com.shi.yun.data.repository.LocalCustomFontRepository
import com.shi.yun.data.repository.LocalReadingPreferencesRepository
import com.shi.yun.data.repository.LocalThemePreferencesRepository
import com.shi.yun.data.repository.ReadingPreferencesRepository
import com.shi.yun.data.repository.ThemePreferencesRepository
import com.shi.yun.data.repository.WorkRepository
import com.shi.yun.ui.category.CategoryDimension
import com.shi.yun.ui.category.CategoryScreen
import com.shi.yun.ui.detail.DetailScreen
import com.shi.yun.ui.favorite.FavoriteScreen
import com.shi.yun.ui.home.HomeScreen
import com.shi.yun.ui.home.generateRecommendations
import com.shi.yun.common.log.AppLogger
import com.shi.yun.data.model.AuxiliaryContent
import com.shi.yun.ui.detail.DetailContentStatus
import com.shi.yun.ui.search.SearchScreen
import com.shi.yun.ui.theme.AppThemeId
import com.shi.yun.ui.theme.LocalAppTheme
import com.shi.yun.ui.theme.ThemeBackgroundStyle
import com.shi.yun.ui.theme.ThemeSelectionBottomSheet
import com.shi.yun.ui.theme.TextureTestBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 底部导航入口枚举（支持【首页】、【分类】、【搜索】与【收藏】四个一级入口）
 */
enum class AppTab(val label: String) {
    HOME("首页"),
    CATEGORY("分类"),
    SEARCH("搜索"),
    FAVORITE("收藏")
}

/**
 * 应用根组件与主导航控制器
 *
 * 核心架构设计：
 * 1. 状态提升（State Hoisting）：
 *    - 首页 PagerState 与推荐序列由顶层持久维护；
 *    - 分类页 LazyListState、维度（朝代 vs 体裁）、各维度独立标签及请求版本号由顶层持久维护；
 *    - 搜索页 searchKeyword（rememberSaveable）与 searchListState 由顶层持久维护；
 *    - 全局阅读偏好 ReadingPreferences 与全局主题外观 ThemePreferences 由顶层持久化仓储管理并实时响应；
 * 2. 全局主题背景图片体系：
 *    - 在顶层唯一定位并渲染当前主题专属 WebP 背景图（ContentScale.Crop），全应用所有页面坐标恒定一致，切 Tab 零跳动零重复解码；
 * 3. 交互安全隔离：
 *    - 采用标准条件分支（when）组合，未展示的页面不进入布局树，绝对阻断滑动与点击干扰；
 * 4. 详情页全屏覆盖与底栏自适应：
 *    - 打开详情页时覆盖全屏并隐藏底栏；返回后状态完全恢复；
 * 5. 纯原生绘制：底栏图标采用 Canvas 绘制，零外部图标库依赖。
 */
@Composable
fun ShiApp(
    repository: WorkRepository,
    preferencesRepository: ReadingPreferencesRepository? = null,
    themeRepository: ThemePreferencesRepository? = null,
    customFontRepository: CustomFontRepository? = null,
    targetWorkId: String? = null,
    targetWorkEventKey: Long = 0L,
    onTargetWorkIdConsumed: (() -> Unit)? = null,
    navigationChannel: Channel<String>? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefsRepo = remember(preferencesRepository, context) {
        preferencesRepository ?: LocalReadingPreferencesRepository(context.applicationContext)
    }
    val themeRepo = remember(themeRepository, context) {
        themeRepository ?: LocalThemePreferencesRepository(context.applicationContext)
    }
    val fontRepo = remember(customFontRepository, context) {
        customFontRepository ?: LocalCustomFontRepository(context.applicationContext)
    }

    var works by remember { mutableStateOf<List<Work>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedWork by remember { mutableStateOf<Work?>(null) }
    val scope = rememberCoroutineScope()

    fun loadData() {
        errorMessage = null
        AppLogger.i("ShiApp", "发起轻量作品列表单次投影异步装载")
        val startTime = System.currentTimeMillis()
        scope.launch {
            try {
                val list = repository.getWorkList()
                works = list.map { it.toWork() }
                val duration = System.currentTimeMillis() - startTime
                AppLogger.i("ShiApp", "轻量作品数据装载成功: 总数=${list.size}首, 耗时=${duration}ms")
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                AppLogger.e("ShiApp", "轻量作品数据装载失败: 耗时=${duration}ms, 原因=${e.message}", e)
                errorMessage = e.message ?: "读取本地诗词数据失败"
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val appTheme = LocalAppTheme.current

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // 全局主题背景体系：根据当前主题配置的 backgroundStyle 决定渲染纯色纸张纹理或独立季节插画
        when (val bgStyle = appTheme.customTokens.backgroundStyle) {
            is ThemeBackgroundStyle.SolidWithTexture -> {
                // 1. 六套经典主题：纯色底叠加 Linear Light 线性光纸张纹理 (Alpha=0.25f)
                com.shi.yun.ui.theme.SolidTextureBackground(
                    solidColor = bgStyle.solidColor,
                    textureResId = bgStyle.textureResId,
                    textureAlpha = bgStyle.textureAlpha,
                )
            }

            is ThemeBackgroundStyle.Gradient -> {
                // 兼容旧版渐变模式
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(bgStyle.topColor, bgStyle.bottomColor),
                            ),
                        ),
                )
            }

            is ThemeBackgroundStyle.ImagePattern -> {
                // 2. 四套季节主题：先绘制保底底色，再等比居中裁剪绘制各自专属的独立四季图片，并施加轻微季节柔光遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgStyle.fallbackBaseColor),
                ) {
                    Image(
                        painter = painterResource(id = bgStyle.drawableResId),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = bgStyle.alpha,
                    )
                    // 柔和遮罩层：降低背景存在感，不压暗图片，不覆盖任何前景交互组件
                    if (bgStyle.scrimAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(bgStyle.scrimColor.copy(alpha = bgStyle.scrimAlpha)),
                        )
                    }
                }
            }
        }

        // 色彩氛围微调层
        if (appTheme.customTokens.overlayBlendColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appTheme.customTokens.overlayBlendColor),
            )
        }

        // 核心渲染区：解除冷启动全屏菊花门禁，直接渲染主框架界面（底栏、顶栏、背景先行）
        if (errorMessage != null && works.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "数据加载失败",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = errorMessage.orEmpty(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
                Button(onClick = { loadData() }) {
                    Text(text = "重试")
                }
            }
        } else {
            MainContent(
                repository = repository,
                works = works,
                selectedWork = selectedWork,
                preferencesRepository = prefsRepo,
                themeRepository = themeRepo,
                customFontRepository = fontRepo,
                targetWorkId = targetWorkId,
                targetWorkEventKey = targetWorkEventKey,
                onTargetWorkIdConsumed = onTargetWorkIdConsumed,
                navigationChannel = navigationChannel,
                onSelectWork = { work ->
                    AppLogger.i("Detail", "进入作品详情: ID=${work.id}, 标题=${work.title}, 朝代=${work.dynasty}, 作者=${work.author}")
                    selectedWork = work
                },
                onDismissDetail = {
                    AppLogger.i("Detail", "离开作品详情")
                    selectedWork = null
                },
            )
        }
    }
}

/**
 * 主内容区域组件
 */
@Composable
private fun MainContent(
    repository: WorkRepository,
    works: List<Work>,
    selectedWork: Work?,
    preferencesRepository: ReadingPreferencesRepository,
    themeRepository: ThemePreferencesRepository,
    customFontRepository: CustomFontRepository,
    targetWorkId: String? = null,
    targetWorkEventKey: Long = 0L,
    onTargetWorkIdConsumed: (() -> Unit)? = null,
    navigationChannel: Channel<String>? = null,
    onSelectWork: (Work) -> Unit,
    onDismissDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readingPreferences by preferencesRepository.preferencesFlow.collectAsState()
    val currentThemeId by themeRepository.currentThemeIdFlow.collectAsState()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showTextureTestScreen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 顶层状态：当前选中的主功能 Tab
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }

    // 在分类页或搜索页按下系统返回键时回到首页
    BackHandler(enabled = selectedWork == null && currentTab != AppTab.HOME) {
        currentTab = AppTab.HOME
    }

    // ========== 首页状态提升（持久留存） ==========
    val recommendationList = remember { mutableStateListOf<Work>() }
    var isHomeLoading by remember { mutableStateOf(true) }
    var homeCurrentIndex by rememberSaveable { mutableIntStateOf(0) }
    var isHomeHintShown by remember { mutableStateOf(preferencesRepository.isHomeGestureHintShown()) }

    // 首次冷启动：优先极速装载首页精选经典推荐集（毫秒级响应，无需等待全量作品扫描）
    LaunchedEffect(Unit) {
        val startMs = System.currentTimeMillis()
        try {
            val initialRecs = repository.getHomeRecommendations(40)
            if (initialRecs.isNotEmpty()) {
                recommendationList.addAll(initialRecs)
                val duration = System.currentTimeMillis() - startMs
                AppLogger.i("ShiApp", "首页精选推荐集装载完成: count=${initialRecs.size}, 耗时=${duration}ms")
            }
        } catch (e: Exception) {
            AppLogger.w("ShiApp", "首页精选推荐集装载异常: ${e.message}")
        } finally {
            isHomeLoading = false
        }
    }

    // 滑动接近末尾时异步预取更多精选推荐
    LaunchedEffect(homeCurrentIndex) {
        if (recommendationList.size - homeCurrentIndex <= 8 && recommendationList.isNotEmpty()) {
            try {
                val more = repository.getHomeRecommendations(20)
                if (more.isNotEmpty()) {
                    recommendationList.addAll(more)
                }
            } catch (e: Exception) {
                AppLogger.w("ShiApp", "预取更多推荐异常: ${e.message}")
            }
        }
    }

    // ========== 分类页状态提升（持久留存，维度解耦，三维独立滚动状态） ==========
    val categoryDynastyListState = rememberLazyListState()
    val categoryGenreListState = rememberLazyListState()
    val categoryAnthologyListState = rememberLazyListState()
    var selectedDimension by rememberSaveable { mutableStateOf(CategoryDimension.DYNASTY) }
    var selectedDynasty by rememberSaveable { mutableStateOf("全部") }
    var selectedGenre by rememberSaveable { mutableStateOf("ALL") }
    var selectedAnthologyId by rememberSaveable { mutableStateOf("") }
    var requestVersion by remember { mutableLongStateOf(0L) }

    fun onDimensionChange(newDimension: CategoryDimension) {
        if (selectedDimension != newDimension) {
            selectedDimension = newDimension
            requestVersion++
        }
    }

    fun onDynastyChange(newDynasty: String) {
        if (selectedDynasty != newDynasty) {
            selectedDynasty = newDynasty
            requestVersion++
        }
    }

    fun onGenreChange(newGenre: String) {
        if (selectedGenre != newGenre) {
            selectedGenre = newGenre
            requestVersion++
        }
    }

    fun onAnthologyChange(newAnthologyId: String) {
        if (selectedAnthologyId != newAnthologyId) {
            selectedAnthologyId = newAnthologyId
            requestVersion++
        }
    }

    fun onRetryCategory() {
        requestVersion++
    }

    // ========== 选本阅读切篇上下文状态 ==========
    var anthologyContextWorks by remember { mutableStateOf<List<Work>?>(null) }
    var anthologyCurrentIndex by remember { mutableIntStateOf(-1) }

    // 容器转场来源矩形坐标快照与交互锁
    var transitionSourceRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var isTransitioning by remember { mutableStateOf(false) }

    // 详情页分阶段加载与辅助内容后台预加载状态
    var currentTargetWorkId by remember { mutableStateOf<String?>(null) }
    var detailLoadJob by remember { mutableStateOf<Job?>(null) }
    var auxiliaryLoadJob by remember { mutableStateOf<Job?>(null) }
    var auxiliaryWorkId by remember { mutableStateOf<String?>(null) }
    var currentAuxiliaryContent by remember { mutableStateOf<AuxiliaryContent?>(null) }
    var isAuxiliaryLoading by remember { mutableStateOf(false) }
    var detailContentStatus by remember { mutableStateOf(DetailContentStatus.LOADING) }

    // 核心作品详情与辅助内容加载执行器（统一控制协程取消、目标 ID 绑定与生命周期）
    fun loadWorkDetail(targetWork: Work) {
        detailLoadJob?.cancel()
        auxiliaryLoadJob?.cancel()
        currentTargetWorkId = targetWork.id
        auxiliaryWorkId = null
        currentAuxiliaryContent = null
        isAuxiliaryLoading = true
        detailContentStatus = if (targetWork.content.isNotBlank()) DetailContentStatus.SUCCESS else DetailContentStatus.LOADING

        // 2. 第二阶段：后台协程异步单篇按需加载完整作品（包含完整正文、排版）
        detailLoadJob = scope.launch {
            try {
                val fullWork = repository.getWorkById(targetWork.id)
                if (currentTargetWorkId == targetWork.id) {
                    if (fullWork != null) {
                        detailContentStatus = DetailContentStatus.SUCCESS
                        onSelectWork(fullWork)
                    } else {
                        detailContentStatus = DetailContentStatus.NOT_FOUND
                    }
                }
            } catch (e: CancellationException) {
                // 协程正常取消时必须重新抛出，严禁当作普通加载错误捕获
                throw e
            } catch (e: Exception) {
                AppLogger.w("ShiApp", "按需加载作品完整详情异常: ${e.message}")
                if (currentTargetWorkId == targetWork.id) {
                    detailContentStatus = DetailContentStatus.ERROR
                }
            }
        }

        // 3. 第三阶段：后台协程异步预加载当前作品的辅助内容（注释、译文、赏析）
        auxiliaryLoadJob = scope.launch {
            try {
                val aux = repository.getAuxiliaryContent(targetWork.id)
                if (currentTargetWorkId == targetWork.id) {
                    auxiliaryWorkId = targetWork.id
                    currentAuxiliaryContent = aux
                    isAuxiliaryLoading = false
                }
            } catch (e: CancellationException) {
                // 协程正常取消时必须重新抛出，严禁当作普通加载错误捕获
                throw e
            } catch (e: Exception) {
                AppLogger.w("ShiApp", "预加载作品辅助内容异常: ${e.message}")
                if (currentTargetWorkId == targetWork.id) {
                    isAuxiliaryLoading = false
                }
            }
        }
    }

    // 统一处理作品点击进入详情（记录来源矩形快照，并在进入转场期间防二次重入，三阶段平滑加载）
    fun navigateToDetail(work: Work, contextWorks: List<Work>?, sourceRect: androidx.compose.ui.geometry.Rect?) {
        if (isTransitioning || selectedWork != null) return
        isTransitioning = true
        transitionSourceRect = sourceRect
        anthologyContextWorks = contextWorks
        anthologyCurrentIndex = if (contextWorks != null) contextWorks.indexOfFirst { it.id == work.id } else -1

        // 1. 第一阶段启动：优先使用列表元数据，容器转场毫秒级启动
        onSelectWork(work)
        loadWorkDetail(work)
    }

    // 外部深度链接与桌面小组件专属直达通道（跨越现有界面状态与动画锁，支持热唤醒下快速换篇与重复点击直达）
    fun openWorkDetailDirectly(work: Work) {
        AppLogger.i("Detail", "小组件直达通道进入详情: ID=${work.id}, 标题=${work.title}, 朝代=${work.dynasty}, 作者=${work.author}")
        transitionSourceRect = null
        anthologyContextWorks = null
        anthologyCurrentIndex = -1
        isTransitioning = false

        onSelectWork(work)
        loadWorkDetail(work)
    }

    // 监听 Channel 导航请求（小组件高频连续点击 / 外部深度链接，CONFLATED 确保最新请求必达且绝无状态竞争）
    if (navigationChannel != null) {
        LaunchedEffect(navigationChannel) {
            for (workId in navigationChannel) {
                if (workId.isNotBlank()) {
                    AppLogger.i("ShiApp", "收到 Channel 小组件直达目标作品 ID: $workId")
                    try {
                        val target = works.firstOrNull { it.id == workId } ?: repository.getWorkById(workId)
                        if (target != null) {
                            openWorkDetailDirectly(target)
                        } else {
                            AppLogger.w("ShiApp", "Channel 未能查询到目标作品 ID: $workId")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e("ShiApp", "Channel 小组件直达跳转异常: ${e.message}", e)
                    }
                }
            }
        }
    } else {
        // 兼容非 Channel 传入时的旧版状态机制
        LaunchedEffect(targetWorkId, targetWorkEventKey) {
            if (!targetWorkId.isNullOrBlank()) {
                AppLogger.i("ShiApp", "收到小组件/深度链接直达目标作品 ID: $targetWorkId (EventKey=$targetWorkEventKey)")
                try {
                    val target = works.firstOrNull { it.id == targetWorkId } ?: repository.getWorkById(targetWorkId)
                    if (target != null) {
                        openWorkDetailDirectly(target)
                    } else {
                        AppLogger.w("ShiApp", "未能查询到目标作品 ID: $targetWorkId")
                    }
                } catch (e: Exception) {
                    AppLogger.e("ShiApp", "小组件直达跳转异常: ${e.message}", e)
                } finally {
                    onTargetWorkIdConsumed?.invoke()
                }
            }
        }
    }

    // 选本内切篇：上一首（切篇后清除来源矩形，返回时自动优雅降级为淡出）
    fun onPreviousAnthologyWork() {
        val list = anthologyContextWorks ?: return
        if (anthologyCurrentIndex > 0) {
            val prevIndex = anthologyCurrentIndex - 1
            val prevWork = list[prevIndex]
            anthologyCurrentIndex = prevIndex
            transitionSourceRect = null // 切篇后坐标失效，标记为 null 以便返回时平滑降级
            AppLogger.i("Detail", "选本切篇 [上一首]: 序号=$prevIndex, 作品 ID=${prevWork.id}, 标题=${prevWork.title}")

            onSelectWork(prevWork)
            loadWorkDetail(prevWork)
        }
    }

    // 选本内切篇：下一首（切篇后清除来源矩形，返回时自动优雅降级为淡出）
    fun onNextAnthologyWork() {
        val list = anthologyContextWorks ?: return
        if (anthologyCurrentIndex in 0 until list.size - 1) {
            val nextIndex = anthologyCurrentIndex + 1
            val nextWork = list[nextIndex]
            anthologyCurrentIndex = nextIndex
            transitionSourceRect = null // 切篇后坐标失效，标记为 null 以便返回时平滑降级
            AppLogger.i("Detail", "选本切篇 [下一首]: 序号=$nextIndex, 作品 ID=${nextWork.id}, 标题=${nextWork.title}")

            onSelectWork(nextWork)
            loadWorkDetail(nextWork)
        }
    }

    // ========== 搜索页状态提升（持久留存） ==========
    var searchKeyword by rememberSaveable { mutableStateOf("") }
    val searchListState = rememberLazyListState()

    // ========== 收藏页状态提升与响应式数据监听 ==========
    val favoriteListState = rememberLazyListState()
    val favoriteIds by repository.favoriteIdsFlow.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        // 详情页全屏浮层与底层页面容器转场（接管底层页面透明度，转场时平滑淡出，彻底消除文字重叠）
        com.shi.yun.ui.animation.ContainerTransformBox(
            visible = selectedWork != null,
            sourceRect = transitionSourceRect,
            onDismissRequest = {
                detailLoadJob?.cancel()
                auxiliaryLoadJob?.cancel()
                currentTargetWorkId = null
                auxiliaryWorkId = null
                currentAuxiliaryContent = null
                isAuxiliaryLoading = false
                detailContentStatus = DetailContentStatus.LOADING
                anthologyContextWorks = null
                anthologyCurrentIndex = -1
                transitionSourceRect = null
                isTransitioning = false
                onDismissDetail()
            },
            underlyingContent = {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 页面内容显示区：Tab 之间采用轻量克制的淡入淡出动画过渡，未处于活跃状态的页面不进入渲染树，绝对杜绝触控穿透
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        androidx.compose.animation.AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                            },
                            label = "TabTransition",
                        ) { targetTab ->
                            when (targetTab) {
                                AppTab.HOME -> {
                                    HomeScreen(
                                        recommendationList = recommendationList,
                                        currentIndex = homeCurrentIndex,
                                        onIndexChange = { newIndex -> homeCurrentIndex = newIndex },
                                        isHintShown = isHomeHintShown,
                                        isLoading = isHomeLoading,
                                        onMarkHintShown = {
                                            scope.launch {
                                                preferencesRepository.markHomeGestureHintShown()
                                                isHomeHintShown = true
                                            }
                                        },
                                        onWorkClick = { work, rect ->
                                            navigateToDetail(work, null, rect)
                                        },
                                    )
                                }
                                AppTab.CATEGORY -> {
                                    CategoryScreen(
                                        repository = repository,
                                        works = works,
                                        isWorkDetailOpen = selectedWork != null,
                                        dynastyListState = categoryDynastyListState,
                                        genreListState = categoryGenreListState,
                                        anthologyListState = categoryAnthologyListState,
                                        selectedDimension = selectedDimension,
                                        selectedDynasty = selectedDynasty,
                                        selectedGenre = selectedGenre,
                                        selectedAnthologyId = selectedAnthologyId,
                                        requestVersion = requestVersion,
                                        onDimensionChange = ::onDimensionChange,
                                        onDynastyChange = ::onDynastyChange,
                                        onGenreChange = ::onGenreChange,
                                        onAnthologyChange = ::onAnthologyChange,
                                        onRetry = ::onRetryCategory,
                                        onWorkClick = { work, ctxWorks, rect ->
                                            navigateToDetail(work, ctxWorks, rect)
                                        },
                                    )
                                }
                                AppTab.SEARCH -> {
                                    SearchScreen(
                                        repository = repository,
                                        keyword = searchKeyword,
                                        onKeywordChange = { searchKeyword = it },
                                        listState = searchListState,
                                        onWorkClick = { work, rect ->
                                            navigateToDetail(work, null, rect)
                                        },
                                    )
                                }
                                AppTab.FAVORITE -> {
                                    FavoriteScreen(
                                        repository = repository,
                                        listState = favoriteListState,
                                        onWorkClick = { work, rect ->
                                            navigateToDetail(work, null, rect)
                                        },
                                        onOpenThemeSelection = {
                                            showThemeSheet = true
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 底部导航栏：始终稳定驻留于原有 Column 底部，不随详情页展开而改变高度与挤压内容区
                    ShiBottomNavigationBar(
                        currentTab = currentTab,
                        onTabSelected = { newTab ->
                            if (selectedWork == null && currentTab != newTab) {
                                AppLogger.i("Navigation", "切换底部主导航: ${currentTab.label} -> ${newTab.label}")
                                currentTab = newTab
                            }
                        },
                    )
                }
            },
        ) { requestDismiss ->
            selectedWork?.let { work ->
                val isFav = favoriteIds.contains(work.id)
                val inAnthology = anthologyContextWorks != null
                val hasPrev = inAnthology && anthologyCurrentIndex > 0
                val hasNxt = inAnthology && anthologyContextWorks?.let { anthologyCurrentIndex in 0 until it.size - 1 } == true
                val effectiveAuxiliary = if (auxiliaryWorkId == work.id) currentAuxiliaryContent else null
                val auxiliaryLoading = isAuxiliaryLoading && effectiveAuxiliary == null

                DetailScreen(
                    work = work,
                    isFavorite = isFav,
                    onToggleFavorite = {
                        scope.launch {
                            repository.toggleFavorite(work.id)
                        }
                    },
                    onBack = requestDismiss,
                    onPreviousWork = if (inAnthology) ::onPreviousAnthologyWork else null,
                    onNextWork = if (inAnthology) ::onNextAnthologyWork else null,
                    hasPrevious = hasPrev,
                    hasNext = hasNxt,
                    preferences = readingPreferences,
                    onPreferencesChange = { newPrefs ->
                        scope.launch {
                            preferencesRepository.updatePreferences(newPrefs)
                        }
                    },
                    onResetPreferences = {
                        scope.launch {
                            preferencesRepository.resetToDefault()
                        }
                    },
                    customFontRepository = customFontRepository,
                    auxiliaryContent = effectiveAuxiliary,
                    isAuxiliaryLoading = auxiliaryLoading,
                    contentStatus = detailContentStatus,
                    onRetryLoad = {
                        loadWorkDetail(work)
                    },
                )
            }
        }

        // 全局主题选择半屏浮层：独立于阅读排版设置，即触即换，毫秒级响应
        if (showThemeSheet) {
            ThemeSelectionBottomSheet(
                currentThemeId = currentThemeId,
                onThemeSelected = { selected ->
                    scope.launch {
                        themeRepository.updateThemeId(selected)
                    }
                },
                onDismissRequest = {
                    showThemeSheet = false
                },
                onOpenTextureTest = {
                    showTextureTestScreen = true
                },
            )
        }

        // 独立纸张纹理测试全屏界面 (仅在 Debug 开发阶段通过桥接挂载，Release 彻底剔除)
        if (showTextureTestScreen && TextureTestBridge.isAvailable) {
            TextureTestBridge.RenderTestScreen(
                onDismiss = {
                    showTextureTestScreen = false
                },
            )
        }
    }
}

/**
 * 底部原生紧凑导航栏（仅展示图标无文字，高度紧凑，选中实心/未选中空心）
 */
@Composable
private fun ShiBottomNavigationBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appTheme = LocalAppTheme.current
    val isDark = appTheme.themeId.isDark
    // 全局统一主题视觉规范：底部导航栏底色（浅色0.75f，暗色0.85f）与分割线（0.5dp，浅色0.12f，暗色0.20f）
    val navBg = appTheme.customTokens.resolvedNavBackground(isDark)
    // 0.2.0 主题修复：经典主题自动继承当前实际生效的 colorScheme.primary，四季主题保持独立配置
    val activeIconColor = appTheme.resolvedNavIconColor

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = navBg,
        tonalElevation = com.shi.yun.ui.theme.ThemeVisualSpecs.ELEVATION_ZERO,
        shadowElevation = com.shi.yun.ui.theme.ThemeVisualSpecs.ELEVATION_ZERO,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = com.shi.yun.ui.theme.ThemeVisualSpecs.DIVIDER_HEIGHT_NAV,
                color = appTheme.customTokens.resolvedNavDividerColor(isDark),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(54.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            AppTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                val iconTint = if (isSelected) {
                    activeIconColor
                } else {
                    activeIconColor.copy(alpha = 0.50f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 24.dp),
                            onClick = { onTabSelected(tab) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val iconRes = when (tab) {
                        AppTab.HOME -> if (isSelected) R.drawable.ic_nav_home_selected else R.drawable.ic_nav_home_unselected
                        AppTab.CATEGORY -> if (isSelected) R.drawable.ic_nav_category_selected else R.drawable.ic_nav_category_unselected
                        AppTab.SEARCH -> if (isSelected) R.drawable.ic_nav_search_selected else R.drawable.ic_nav_search_unselected
                        AppTab.FAVORITE -> if (isSelected) R.drawable.ic_nav_favorite_selected else R.drawable.ic_nav_favorite_unselected
                    }
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                        tint = iconTint,
                    )
                }
            }
        }
    }
}
}
