package com.shi.yun.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.shi.yun.ui.theme.AppThemeId
import com.shi.yun.ui.theme.LocalAppTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shi.yun.common.log.AppLogger
import com.shi.yun.data.model.Anthology
import com.shi.yun.data.model.AnthologyWorkItem
import com.shi.yun.data.model.Work
import com.shi.yun.data.model.formatAuthorDynasty
import com.shi.yun.data.model.formatAuthorDynastyGenre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.shi.yun.data.model.toWork
import com.shi.yun.data.repository.WorkRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * 分类维度枚举（顶部一级分类）
 */
enum class CategoryDimension(val label: String) {
    DYNASTY("朝代"),
    GENRE("体裁"),
    ANTHOLOGY("文集")
}

/**
 * 中华历史朝代正统时间先后顺序索引表（从早到晚）
 * 覆盖先秦至近现代所有常见称谓与别名
 */
private val DYNASTY_CHRONOLOGICAL_ORDER = listOf(
    // 1. 先秦时代（远古、三皇五帝、夏、商、西周、东周、春秋、战国）
    "先秦", "夏代", "夏朝", "商代", "商朝", "周代", "周朝", "西周", "东周", "春秋", "战国",
    // 2. 秦汉时代（秦朝、西汉、东汉、汉代、两汉）
    "秦代", "秦朝", "秦", "两汉", "汉代", "汉朝", "汉", "西汉", "前汉", "东汉", "后汉",
    // 3. 三国两晋南北朝（三国、魏晋、晋代、西晋、东晋、南北朝、南朝、北朝）
    "三国", "魏晋", "魏晋南北朝", "晋代", "晋朝", "晋", "西晋", "东晋", "南北朝", "南朝", "北朝",
    // 4. 隋唐五代（隋代、隋朝、唐代、唐朝、五代、五代十国）
    "隋代", "隋朝", "隋", "唐代", "唐朝", "唐", "五代", "五代十国", "五代十国时期",
    // 5. 宋辽金（宋代、宋朝、北宋、南宋、辽代、辽朝、金朝、金代）
    "宋代", "宋朝", "宋", "北宋", "南宋", "辽代", "辽朝", "金朝", "金代", "金",
    // 6. 元明清（元代、元朝、明代、明朝、清代、清朝）
    "元代", "元朝", "元", "明代", "明朝", "明", "清代", "清朝", "清",
    // 7. 近现代（近代、中华民国、现代、当代）
    "近代", "民国", "现代", "当代"
)

/**
 * 计算朝代的历史年代序号，用于自早至晚的历史正序排序
 */
internal fun getDynastyChronologicalRank(dynastyName: String): Int {
    val clean = dynastyName.trim()
    val directIndex = DYNASTY_CHRONOLOGICAL_ORDER.indexOf(clean)
    if (directIndex >= 0) return directIndex

    // 模糊匹配前缀或包含（例如“先秦”匹配“先秦时期”，“清”匹配“清代”）
    val matchedIndex = DYNASTY_CHRONOLOGICAL_ORDER.indexOfFirst {
        clean.startsWith(it) || it.startsWith(clean)
    }
    return if (matchedIndex >= 0) matchedIndex else 999
}

/**
 * 体裁配置数据类
 */
data class GenreOption(val code: String, val label: String)

/**
 * 分类列表 UI 状态定义
 */
sealed interface CategoryUiState {
    data object Loading : CategoryUiState
    data class Success(val works: List<Work>) : CategoryUiState
    data class AnthologySuccess(val items: List<AnthologyWorkItem>, val anthology: Anthology?) : CategoryUiState
    data object Empty : CategoryUiState
    data class Error(val message: String) : CategoryUiState
}

/**
 * 分类页面主组件（受控组件，状态由外层统一提升管理，支持朝代/体裁/选本横向滑动切换）
 */
@Composable
fun CategoryScreen(
    repository: WorkRepository,
    works: List<Work>,
    dynastyListState: LazyListState = rememberLazyListState(),
    genreListState: LazyListState = rememberLazyListState(),
    anthologyListState: LazyListState = rememberLazyListState(),
    selectedDimension: CategoryDimension = CategoryDimension.DYNASTY,
    selectedDynasty: String = "全部",
    selectedGenre: String = "ALL",
    selectedAnthologyId: String = "",
    requestVersion: Long = 0L,
    onDimensionChange: (CategoryDimension) -> Unit = {},
    onDynastyChange: (String) -> Unit = {},
    onGenreChange: (String) -> Unit = {},
    onAnthologyChange: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onWorkClick: (Work, List<Work>?, Rect?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    isWorkDetailOpen: Boolean = false,
) {
    var anthologiesList by remember { mutableStateOf<List<Anthology>>(emptyList()) }
    var dynastyUiState by remember { mutableStateOf<CategoryUiState>(CategoryUiState.Loading) }
    var genreUiState by remember { mutableStateOf<CategoryUiState>(CategoryUiState.Loading) }
    var anthologyUiState by remember { mutableStateOf<CategoryUiState>(CategoryUiState.Loading) }

    // 重新开放“文集”分类入口，恢复朝代、体裁、文集三大完整维度
    val visibleDimensions = remember {
        listOf(CategoryDimension.DYNASTY, CategoryDimension.GENRE, CategoryDimension.ANTHOLOGY)
    }
    val coroutineScope = rememberCoroutineScope()
    val initialPageIndex = remember(selectedDimension) {
        visibleDimensions.indexOf(selectedDimension).coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPageIndex) { visibleDimensions.size }

    // 体裁二级分类选中状态
    var selectedSubGenre by rememberSaveable { mutableStateOf("全部") }

    // 文集内按返回键返回文集目录列表，当作品详情页打开时禁用此拦截，保障详情返回优先退出详情页
    BackHandler(enabled = !isWorkDetailOpen && selectedDimension == CategoryDimension.ANTHOLOGY && selectedAnthologyId.isNotBlank()) {
        onAnthologyChange("")
    }

    // 初始异步加载选本定义列表，供文集目录展示
    LaunchedEffect(Unit) {
        try {
            anthologiesList = repository.getAnthologies()
        } catch (e: Exception) {
            AppLogger.e("CategoryScreen", "加载选本列表定义失败: ${e.message}", e)
        }
    }

    // 动态提取已有作品中存在的全部朝代并去重，严格按中华历史年代从早到晚排序
    val dynastyOptions = remember(works) {
        val list = mutableListOf("全部")
        val uniqueDynasties = works
            .map { it.dynasty }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(
                compareBy(
                    { getDynastyChronologicalRank(it) },
                    { it }
                )
            )
        list.addAll(uniqueDynasties)
        list
    }

    // 体裁选项配置
    val genreOptions = remember {
        listOf(
            GenreOption("ALL", "全部"),
            GenreOption("poem", "诗"),
            GenreOption("ci", "词"),
            GenreOption("qu", "曲"),
            GenreOption("wen", "文"),
        )
    }

    // 1. 朝代作品加载：优先利用外层已装载的轻量作品集合在内存中极速过滤，消除重复 DB 查询
    LaunchedEffect(requestVersion, selectedDynasty, works) {
        if (works.isNotEmpty()) {
            val results = withContext(Dispatchers.Default) {
                if (selectedDynasty == "全部") {
                    works
                } else {
                    works.filter { work ->
                        val d = work.dynasty.trim()
                        d == selectedDynasty || d == "${selectedDynasty}代" || d == "${selectedDynasty}朝" || selectedDynasty.startsWith(d)
                    }
                }
            }
            dynastyUiState = if (results.isEmpty()) CategoryUiState.Empty else CategoryUiState.Success(results)
        } else {
            dynastyUiState = CategoryUiState.Loading
            try {
                val results = if (selectedDynasty == "全部") {
                    repository.getWorkList().map { it.toWork() }
                } else {
                    repository.getWorksByDynasty(selectedDynasty)
                }
                dynastyUiState = if (results.isEmpty()) CategoryUiState.Empty else CategoryUiState.Success(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CategoryScreen", "加载朝代分类失败: ${e.message}", e)
                dynastyUiState = CategoryUiState.Error(e.message ?: "加载朝代作品失败")
            }
        }
    }

    // 2. 体裁作品加载：结合一级体裁与二级细分在内存中极速过滤
    LaunchedEffect(requestVersion, selectedGenre, selectedSubGenre, works) {
        if (works.isNotEmpty()) {
            val results = withContext(Dispatchers.Default) {
                val baseList = if (selectedGenre == "ALL" || selectedGenre == "全部") {
                    works
                } else {
                    works.filter { work ->
                        work.genre.code.equals(selectedGenre, ignoreCase = true)
                    }
                }
                if (selectedSubGenre == "全部" || selectedGenre == "ALL") {
                    baseList
                } else {
                    baseList.filter { matchSubGenre(it, selectedGenre, selectedSubGenre) }
                }
            }
            genreUiState = if (results.isEmpty()) CategoryUiState.Empty else CategoryUiState.Success(results)
        } else {
            genreUiState = CategoryUiState.Loading
            try {
                val baseList = if (selectedGenre == "ALL" || selectedGenre == "全部") {
                    repository.getWorkList().map { it.toWork() }
                } else {
                    repository.getWorksByGenre(selectedGenre)
                }
                val results = if (selectedSubGenre == "全部" || selectedGenre == "ALL") {
                    baseList
                } else {
                    baseList.filter { matchSubGenre(it, selectedGenre, selectedSubGenre) }
                }
                genreUiState = if (results.isEmpty()) CategoryUiState.Empty else CategoryUiState.Success(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CategoryScreen", "加载体裁分类失败: ${e.message}", e)
                genreUiState = CategoryUiState.Error(e.message ?: "加载体裁作品失败")
            }
        }
    }

    // 3. 选本作品独立加载
    LaunchedEffect(requestVersion, selectedAnthologyId) {
        if (selectedAnthologyId.isBlank()) {
            anthologyUiState = CategoryUiState.Empty
            return@LaunchedEffect
        }
        anthologyUiState = CategoryUiState.Loading
        try {
            val currentAnthology = repository.getAnthologyById(selectedAnthologyId)
                ?: anthologiesList.firstOrNull { it.id == selectedAnthologyId }
            val anthologyItems = repository.getWorksByAnthology(selectedAnthologyId)
            anthologyUiState = if (anthologyItems.isEmpty()) {
                CategoryUiState.Empty
            } else {
                CategoryUiState.AnthologySuccess(anthologyItems, currentAnthology)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("CategoryScreen", "加载选本作品失败: ${e.message}", e)
            anthologyUiState = CategoryUiState.Error(e.message ?: "加载选本作品失败")
        }
    }

    // 页面滑动与顶部维度状态双向同步
    LaunchedEffect(pagerState.currentPage) {
        val newDim = visibleDimensions.getOrNull(pagerState.currentPage) ?: CategoryDimension.DYNASTY
        if (selectedDimension != newDim) {
            onDimensionChange(newDim)
        }
    }

    LaunchedEffect(selectedDimension) {
        val targetIndex = visibleDimensions.indexOf(selectedDimension)
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // 1. 一级分类维度切换（朝代 / 体裁 / 文集）固定置顶
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) },
        ) {
            visibleDimensions.forEachIndexed { index, dimension ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        AppLogger.interaction("CategoryScreen", "DimensionTab", "Click", "SUCCESS", "切换分类维度: ${dimension.label}")
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                        onDimensionChange(dimension)
                    },
                    text = {
                        Text(
                            text = dimension.label,
                            fontSize = 15.sp,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        // 2. 横向滑动分页区域（朝代 / 体裁 / 文集，每个页面独立筛选条件与滚动位置）
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when (visibleDimensions[page]) {
                CategoryDimension.DYNASTY -> {
                    DynastyPage(
                        dynastyOptions = dynastyOptions,
                        selectedDynasty = selectedDynasty,
                        onDynastyChange = onDynastyChange,
                        uiState = dynastyUiState,
                        listState = dynastyListState,
                        onRetry = onRetry,
                        onWorkClick = onWorkClick,
                    )
                }
                CategoryDimension.GENRE -> {
                    GenrePage(
                        genreOptions = genreOptions,
                        selectedGenre = selectedGenre,
                        onGenreChange = { newGenre ->
                            onGenreChange(newGenre)
                            selectedSubGenre = "全部"
                        },
                        selectedSubGenre = selectedSubGenre,
                        onSubGenreChange = { selectedSubGenre = it },
                        uiState = genreUiState,
                        listState = genreListState,
                        onRetry = onRetry,
                        onWorkClick = onWorkClick,
                    )
                }
                CategoryDimension.ANTHOLOGY -> {
                    AnthologyPage(
                        anthologiesList = anthologiesList,
                        selectedAnthologyId = selectedAnthologyId,
                        onAnthologyChange = onAnthologyChange,
                        uiState = anthologyUiState,
                        listState = anthologyListState,
                        onRetry = onRetry,
                        onWorkClick = onWorkClick,
                    )
                }
            }
        }
    }
}

/**
 * 兼容单 listState 的重载函数
 */
@Composable
fun CategoryScreen(
    repository: WorkRepository,
    works: List<Work>,
    listState: LazyListState,
    selectedDimension: CategoryDimension,
    selectedDynasty: String,
    selectedGenre: String,
    selectedAnthologyId: String,
    requestVersion: Long,
    onDimensionChange: (CategoryDimension) -> Unit,
    onDynastyChange: (String) -> Unit,
    onGenreChange: (String) -> Unit,
    onAnthologyChange: (String) -> Unit,
    onRetry: () -> Unit,
    onWorkClick: (Work, List<Work>?, Rect?) -> Unit,
    modifier: Modifier = Modifier,
    isWorkDetailOpen: Boolean = false,
) = CategoryScreen(
    repository = repository,
    works = works,
    isWorkDetailOpen = isWorkDetailOpen,
    dynastyListState = listState,
    genreListState = listState,
    anthologyListState = listState,
    selectedDimension = selectedDimension,
    selectedDynasty = selectedDynasty,
    selectedGenre = selectedGenre,
    selectedAnthologyId = selectedAnthologyId,
    requestVersion = requestVersion,
    onDimensionChange = onDimensionChange,
    onDynastyChange = onDynastyChange,
    onGenreChange = onGenreChange,
    onAnthologyChange = onAnthologyChange,
    onRetry = onRetry,
    onWorkClick = onWorkClick,
    modifier = modifier,
)

@Composable
private fun RenderDynastyChip(
    dynasty: String,
    selectedDynasty: String,
    width: Dp,
    onDynastyChange: (String) -> Unit,
) {
    val isSelected = selectedDynasty == dynasty
    val displayLabel = when {
        dynasty == "全部" -> "全部"
        dynasty in listOf("先秦", "两汉", "魏晋", "南北朝", "三国", "春秋", "战国", "近代", "现代", "当代", "近现代") -> dynasty
        dynasty.endsWith("代") || dynasty.endsWith("朝") -> dynasty
        else -> "${dynasty}代"
    }
    CategoryTabChip(
        label = displayLabel,
        isSelected = isSelected,
        width = width,
        height = 32.dp,
        fontSize = if (displayLabel.length >= 3) 11.sp else 12.5.sp,
        onClick = {
            AppLogger.interaction("CategoryScreen", "DynastyChip", "Click", "SUCCESS", "选择朝代: $dynasty")
            onDynastyChange(dynasty)
        },
    )
}

@Composable
private fun DynastyPage(
    dynastyOptions: List<String>,
    selectedDynasty: String,
    onDynastyChange: (String) -> Unit,
    uiState: CategoryUiState,
    listState: LazyListState,
    onRetry: () -> Unit,
    onWorkClick: (Work, List<Work>?, Rect?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 自适应三行布局（5+5+5）：彻底取消横向滚动，每行 5 个居中等宽标签，杜绝与页面滑动冲突
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val totalWidth = maxWidth
            val horizontalSpacing = 6.dp
            val verticalSpacing = 6.dp
            val columns = 5
            val itemWidth = (totalWidth - horizontalSpacing * (columns - 1)) / columns

            val row1Items = dynastyOptions.take(5)
            val row2Items = dynastyOptions.drop(5).take(5)
            val row3Items = dynastyOptions.drop(10).take(5)

            Column(
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 第一行 (全部, 先秦, 两汉, 魏晋, 南北朝)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                ) {
                    row1Items.forEach { dynasty ->
                        RenderDynastyChip(dynasty, selectedDynasty, itemWidth, onDynastyChange)
                    }
                }
                // 第二行 (隋代, 唐代, 五代, 宋代, 金朝)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                ) {
                    row2Items.forEach { dynasty ->
                        RenderDynastyChip(dynasty, selectedDynasty, itemWidth, onDynastyChange)
                    }
                }
                // 第三行 (元代, 明代, 清代, 近现代, 当代)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                ) {
                    row3Items.forEach { dynasty ->
                        RenderDynastyChip(dynasty, selectedDynasty, itemWidth, onDynastyChange)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState) {
                is CategoryUiState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                is CategoryUiState.Empty -> Text(text = "暂无符合条件的作品", fontSize = 15.sp, color = MaterialTheme.colorScheme.outline)
                is CategoryUiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(text = uiState.message, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onRetry) { Text(text = "重试") }
                    }
                }
                is CategoryUiState.Success -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = uiState.works, key = { it.id }) { work ->
                            var itemBounds by remember { mutableStateOf<Rect?>(null) }
                            CategoryWorkItem(
                                work = work,
                                onClick = {
                                    AppLogger.interaction("CategoryScreen", "DynastyWorkItem", "Click", "SUCCESS", "点击作品: ${work.title} (${work.id})")
                                    onWorkClick(work, uiState.works, itemBounds)
                                },
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    itemBounds = coordinates.boundsInRoot()
                                },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
                is CategoryUiState.AnthologySuccess -> Unit
            }
        }
    }
}

@Composable
private fun GenrePage(
    genreOptions: List<GenreOption>,
    selectedGenre: String,
    onGenreChange: (String) -> Unit,
    selectedSubGenre: String,
    onSubGenreChange: (String) -> Unit,
    uiState: CategoryUiState,
    listState: LazyListState,
    onRetry: () -> Unit,
    onWorkClick: (Work, List<Work>?, Rect?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 一级体裁选择：全部、诗、词、曲、文（等宽自适应布局，彻底取消横向滚动）
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            val totalWidth = maxWidth
            val spacing = 8.dp
            val count = genreOptions.size
            val itemWidth = (totalWidth - spacing * (count - 1)) / count

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                genreOptions.forEach { option ->
                    val isSelected = selectedGenre == option.code
                    CategoryTabChip(
                        label = option.label,
                        isSelected = isSelected,
                        width = itemWidth,
                        height = 34.dp,
                        fontSize = 13.sp,
                        onClick = {
                            AppLogger.interaction("CategoryScreen", "GenrePrimaryChip", "Click", "SUCCESS", "选择一级体裁: ${option.label}")
                            onGenreChange(option.code)
                            onSubGenreChange("全部")
                        },
                    )
                }
            }
        }

        // 2. 二级分类自适应选择区（当选中 诗、词、曲、文 时展示对应二级分类）
        val subOptions = remember(selectedGenre) {
            when (selectedGenre) {
                "poem" -> listOf("全部", "五言绝句", "七言绝句", "五言律诗", "七言律诗", "五言古诗", "七言古诗", "乐府", "其他")
                "ci" -> listOf("全部", "水调歌头", "念奴娇", "浣溪沙", "鹧鸪天", "临江仙", "虞美人", "蝶恋花", "其他词牌")
                "qu" -> listOf("全部", "小令", "散曲", "套数", "其他")
                "wen" -> listOf("全部", "赋", "序", "记", "论", "书信", "表奏", "其他古文")
                else -> emptyList()
            }
        }

        if (subOptions.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                val totalWidth = maxWidth
                val hSpacing = 6.dp
                val vSpacing = 5.dp
                val columns = maxOf(1, (subOptions.size + 1) / 2)
                val itemWidth = (totalWidth - hSpacing * (columns - 1)) / columns

                val row1 = subOptions.take(columns)
                val row2 = subOptions.drop(columns)

                Column(
                    verticalArrangement = Arrangement.spacedBy(vSpacing),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(hSpacing),
                    ) {
                        row1.forEach { sub ->
                            CategoryTabChip(
                                label = sub,
                                isSelected = selectedSubGenre == sub,
                                width = itemWidth,
                                height = 30.dp,
                                fontSize = if (sub.length >= 4) 10.sp else 11.5.sp,
                                onClick = {
                                    AppLogger.interaction("CategoryScreen", "GenreSubChip", "Click", "SUCCESS", "选择二级体裁: $sub")
                                    onSubGenreChange(sub)
                                },
                            )
                        }
                    }
                    if (row2.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(hSpacing),
                        ) {
                            row2.forEach { sub ->
                                CategoryTabChip(
                                    label = sub,
                                    isSelected = selectedSubGenre == sub,
                                    width = itemWidth,
                                    height = 30.dp,
                                    fontSize = if (sub.length >= 4) 10.sp else 11.5.sp,
                                    onClick = {
                                        AppLogger.interaction("CategoryScreen", "GenreSubChip", "Click", "SUCCESS", "选择二级体裁: $sub")
                                        onSubGenreChange(sub)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState) {
                is CategoryUiState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                is CategoryUiState.Empty -> Text(text = "暂无符合条件的作品", fontSize = 15.sp, color = MaterialTheme.colorScheme.outline)
                is CategoryUiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(text = uiState.message, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onRetry) { Text(text = "重试") }
                    }
                }
                is CategoryUiState.Success -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = uiState.works, key = { it.id }) { work ->
                            var itemBounds by remember { mutableStateOf<Rect?>(null) }
                            CategoryWorkItem(
                                work = work,
                                onClick = {
                                    AppLogger.interaction("CategoryScreen", "GenreWorkItem", "Click", "SUCCESS", "点击作品: ${work.title} (${work.id})")
                                    onWorkClick(work, uiState.works, itemBounds)
                                },
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    itemBounds = coordinates.boundsInRoot()
                                },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
                is CategoryUiState.AnthologySuccess -> Unit
            }
        }
    }
}

private val SOLAR_TERMS = listOf(
    "立春", "雨水", "惊蛰", "春分", "清明", "谷雨",
    "立夏", "小满", "芒种", "夏至", "小暑", "大暑",
    "立秋", "处暑", "白露", "秋分", "寒露", "霜降",
    "立冬", "小雪", "大雪", "冬至", "小寒", "大寒"
)

private val SEASONS = listOf("春", "夏", "秋", "冬")

private val FESTIVALS = listOf("除夕", "春节", "元宵", "寒食", "清明", "端午", "七夕", "中秋", "重阳")

private val FORMAL_KEYWORDS = listOf(
    "三百首", "诗经", "十九首", "乐府", "楚辞", "观止", "古诗词", "文言文", "千家诗",
    "白香词谱", "三十六计", "经", "启蒙", "词话", "对韵", "四时幽赏录", "史记", "世说新语",
    "红楼梦", "西游记", "三国演义", "水浒传", "论语", "随园食单", "中庸", "晏子春秋",
    "幽梦影", "孟子", "左传", "月令七十二候集解", "西厢记", "菜根谭", "给孩子的诗",
    "庄子", "鬼谷子", "孙子兵法"
)

private val TEXTBOOK_KEYWORDS = listOf(
    "册", "人教", "苏教", "北师", "沪教", "语文版", "长春", "鄂教", "鲁教", "河大", "五四", "北京", "粤教", "鲁人", "华师"
)

private fun isFormalBookTitle(title: String): Boolean {
    if (TEXTBOOK_KEYWORDS.any { title.contains(it) }) return true
    return FORMAL_KEYWORDS.any { title.contains(it) }
}

internal fun sortAnthologiesForCatalog(list: List<Anthology>): List<Anthology> {
    val group1 = mutableListOf<Anthology>()
    val group2 = mutableListOf<Anthology>()
    val group3 = mutableListOf<Anthology>()

    for (anth in list) {
        val title = anth.name
        if (title in FESTIVALS || title in SEASONS || title in SOLAR_TERMS) {
            group2.add(anth)
        } else if (isFormalBookTitle(title)) {
            group1.add(anth)
        } else {
            group3.add(anth)
        }
    }

    // Group 2 内部排序：
    // 1. 节日 (按农历时序)
    // 2. 四季 (春 -> 夏 -> 秋 -> 冬)
    // 3. 24 节气 (按天文公历历法真实时序: 立春 -> 雨水 ... -> 大寒)
    group2.sortWith(compareBy { anth ->
        val title = anth.name
        val num = anth.id.removePrefix("book_ht_").toIntOrNull() ?: 999999
        when {
            title in FESTIVALS && num < 40 -> 0 * 1000 + FESTIVALS.indexOf(title)
            title in SEASONS -> 1 * 1000 + SEASONS.indexOf(title)
            title in SOLAR_TERMS -> 2 * 1000 + SOLAR_TERMS.indexOf(title)
            title in FESTIVALS -> 0 * 1000 + FESTIVALS.indexOf(title)
            else -> 3 * 1000 + num
        }
    })

    return group1 + group2 + group3
}

@Composable
private fun AnthologyPage(
    anthologiesList: List<Anthology>,
    selectedAnthologyId: String,
    onAnthologyChange: (String) -> Unit,
    uiState: CategoryUiState,
    listState: LazyListState,
    onRetry: () -> Unit,
    onWorkClick: (Work, List<Work>?, Rect?) -> Unit,
) {
    // 目录网格独立滚动状态与按文集 ID 隔离的详情列表状态字典，彻底避免滑动位置联动错位
    val catalogGridState = rememberLazyGridState()
    val anthologyDetailStates = remember { mutableMapOf<String, LazyListState>() }

    val sortedAnthologies = remember(anthologiesList) {
        sortAnthologiesForCatalog(anthologiesList)
    }

    if (selectedAnthologyId.isBlank()) {
        // ========== 一级：387 部经典文集三列平铺网格目录 ==========
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = catalogGridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = sortedAnthologies,
                key = { it.id },
            ) { anthology ->
                AnthologyGridCard(
                    anthology = anthology,
                    onClick = {
                        AppLogger.interaction("CategoryScreen", "AnthologyCard", "Click", "SUCCESS", "打开文集: ${anthology.name} (${anthology.id})")
                        onAnthologyChange(anthology.id)
                    },
                )
            }
        }
    } else {
        // ========== 二级：具体文集详情与作品条目列表 ==========
        val currentAnthologyListState = anthologyDetailStates.getOrPut(selectedAnthologyId) { LazyListState() }

        Column(modifier = Modifier.fillMaxSize()) {
            // 返回全部文集导航条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAnthologyChange("") }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "← 全部文集",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (uiState is CategoryUiState.AnthologySuccess) {
                    Text(
                        text = uiState.anthology?.name ?: "",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when (uiState) {
                    is CategoryUiState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    is CategoryUiState.Empty -> Text(text = "暂无收录条目", fontSize = 15.sp, color = MaterialTheme.colorScheme.outline)
                    is CategoryUiState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(text = uiState.message, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                            Button(onClick = onRetry) { Text(text = "重试") }
                        }
                    }
                    is CategoryUiState.AnthologySuccess -> {
                        val anthologyWorks = remember(uiState.items) {
                            uiState.items.map { it.work }
                        }
                        LazyColumn(
                            state = currentAnthologyListState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            uiState.anthology?.let { anth ->
                                item(key = "anthology_header") {
                                    val appTheme = com.shi.yun.ui.theme.LocalAppTheme.current
                                    val isDark = appTheme.themeId.isDark
                                    val itemCardBg = appTheme.customTokens.resolvedCardBackground(isDark)
                                    val itemBorder = appTheme.customTokens.resolvedCardBorder(isDark)

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .clip(RoundedCornerShape(com.shi.yun.ui.theme.ThemeVisualSpecs.CORNER_RADIUS_LIST_CARD))
                                            .background(itemCardBg)
                                            .border(com.shi.yun.ui.theme.ThemeVisualSpecs.BORDER_WIDTH_CARD, itemBorder, RoundedCornerShape(com.shi.yun.ui.theme.ThemeVisualSpecs.CORNER_RADIUS_LIST_CARD))
                                            .padding(horizontal = 18.dp, vertical = 12.dp),
                                    ) {
                                        Text(
                                            text = "${anth.name} · ${uiState.items.size} 篇",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        if (anth.compiler.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "编选：${anth.compiler}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                        val displayDesc = anth.description.ifBlank { anth.editionNote }
                                        if (displayDesc.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = displayDesc,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 18.sp,
                                            )
                                        }
                                    }
                                }
                            }

                            items(
                                items = uiState.items,
                                key = { it.item.id },
                            ) { item ->
                                var itemBounds by remember { mutableStateOf<Rect?>(null) }
                                AnthologyWorkRowItem(
                                    item = item,
                                    onClick = { onWorkClick(item.work, anthologyWorks, itemBounds) },
                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                        itemBounds = coordinates.boundsInRoot()
                                    },
                                )
                            }

                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                    is CategoryUiState.Success -> Unit
                }
            }
        }
    }
}

/**
 * 文集目录网格卡片（每行三个，统一尺寸，标题居中置顶，收录篇数置底）
 */
@Composable
private fun AnthologyGridCard(
    anthology: Anthology,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appTheme = com.shi.yun.ui.theme.LocalAppTheme.current
    val isDark = appTheme.themeId.isDark
    val itemCardBg = appTheme.customTokens.resolvedCardBackground(isDark)
    val itemBorder = appTheme.customTokens.resolvedCardBorder(isDark)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        onClick = onClick,
        shape = RoundedCornerShape(com.shi.yun.ui.theme.ThemeVisualSpecs.CORNER_RADIUS_ANTHOLOGY_CARD),
        color = itemCardBg,
        border = BorderStroke(com.shi.yun.ui.theme.ThemeVisualSpecs.BORDER_WIDTH_CARD, itemBorder),
        tonalElevation = com.shi.yun.ui.theme.ThemeVisualSpecs.ELEVATION_ZERO,
        shadowElevation = com.shi.yun.ui.theme.ThemeVisualSpecs.ELEVATION_ZERO,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = anthology.name,
                fontSize = if (anthology.name.length > 5) 12.sp else 13.sp,
                lineHeight = if (anthology.name.length > 5) 14.5.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(1.5.dp))
            val countText = if (anthology.targetCount > 0) "${anthology.targetCount}篇" else "精选"
            Text(
                text = countText,
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * 统一定义的分类胶囊按钮（等宽、居中、自适应、无手势冲突）
 */
@Composable
private fun CategoryTabChip(
    label: String,
    isSelected: Boolean,
    width: Dp,
    height: Dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    onClick: () -> Unit,
) {
    val appTheme = LocalAppTheme.current
    val isDark = appTheme.themeId.isDark
    val tagBg = appTheme.customTokens.categoryTagBackground

    // 选中状态：使用分类页面标签专属强调色 (tagBg)
    // 未选中状态：统一使用卡片底色 (cardBackground)，浅色0.42f、暗色0.60f，与诗词列表卡片底色与质感完全同源
    val containerColor = if (isSelected) {
        tagBg
    } else {
        appTheme.customTokens.resolvedCardBackground(isDark)
    }
    val contentColor = if (isSelected) {
        appTheme.customTokens.categoryTagTextColor
    } else {
        appTheme.customTokens.titleText.copy(alpha = 0.85f)
    }
    // 边框统一：未选中态使用与列表卡片完全一致的轻柔边框 (0.5dp, 浅色0.25f, 暗色0.40f)
    val borderStroke = if (isSelected) {
        BorderStroke(com.shi.yun.ui.theme.ThemeVisualSpecs.BORDER_WIDTH_SELECTED_TAG, tagBg)
    } else {
        BorderStroke(com.shi.yun.ui.theme.ThemeVisualSpecs.BORDER_WIDTH_CARD, appTheme.customTokens.resolvedCardBorder(isDark))
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .height(height),
        shape = RoundedCornerShape(com.shi.yun.ui.theme.ThemeVisualSpecs.CORNER_RADIUS_TAG),
        color = containerColor,
        border = borderStroke,
        tonalElevation = com.shi.yun.ui.theme.ThemeVisualSpecs.ELEVATION_ZERO,
        shadowElevation = com.shi.yun.ui.theme.ThemeVisualSpecs.ELEVATION_ZERO,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = fontSize,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * 严格按照古典体裁结构与元数据判定二级体裁
 */
private fun matchSubGenre(work: Work, genre: String, subGenre: String): Boolean {
    if (subGenre == "全部" || subGenre.isBlank()) return true
    val title = work.title
    val excerpt = work.excerpt
    return when (genre) {
        "poem" -> {
            when (subGenre) {
                "乐府" -> title.contains("乐府") || title.endsWith("歌") || title.endsWith("行") || title.endsWith("吟") || title.endsWith("引") || title.contains("歌·") || title.contains("行·")
                "五言绝句" -> {
                    val m = getPoemLineMeter(excerpt)
                    title.contains("五绝") || (title.contains("绝句") && (m == 5 || m == 0)) || ((title.endsWith("绝") || title.contains("绝·") || title.matches(Regex(".*[二三四五六七八九十]绝.*"))) && m == 5)
                }
                "七言绝句" -> {
                    val m = getPoemLineMeter(excerpt)
                    title.contains("七绝") || (title.contains("绝句") && (m == 7 || m == 0)) || ((title.endsWith("绝") || title.contains("绝·") || title.matches(Regex(".*[二三四五六七八九十]绝.*"))) && m == 7)
                }
                "五言律诗" -> {
                    val m = getPoemLineMeter(excerpt)
                    title.contains("五律") || (title.contains("律诗") && (m == 5 || m == 0)) || ((title.endsWith("律") || title.contains("律·") || title.matches(Regex(".*[二三四五六七八九十]律.*"))) && m == 5)
                }
                "七言律诗" -> {
                    val m = getPoemLineMeter(excerpt)
                    title.contains("七律") || (title.contains("律诗") && (m == 7 || m == 0)) || ((title.endsWith("律") || title.contains("律·") || title.matches(Regex(".*[二三四五六七八九十]律.*"))) && m == 7)
                }
                "五言古诗" -> (title.contains("五言") || title.contains("古诗") || title.contains("古风")) && (getPoemLineMeter(excerpt) == 5 || getPoemLineMeter(excerpt) == 0)
                "七言古诗" -> (title.contains("七言") || title.contains("古诗") || title.contains("古风")) && (getPoemLineMeter(excerpt) == 7 || getPoemLineMeter(excerpt) == 0)
                "其他" -> {
                    val known = listOf("乐府", "五言绝句", "七言绝句", "五言律诗", "七言律诗", "五言古诗", "七言古诗")
                    known.none { matchSubGenre(work, genre, it) }
                }
                else -> title.contains(subGenre)
            }
        }
        "ci" -> {
            when (subGenre) {
                "水调歌头" -> title.contains("水调歌头")
                "念奴娇" -> title.contains("念奴娇")
                "浣溪沙" -> title.contains("浣溪沙")
                "鹧鸪天" -> title.contains("鹧鸪天")
                "临江仙" -> title.contains("临江仙")
                "虞美人" -> title.contains("虞美人")
                "蝶恋花" -> title.contains("蝶恋花")
                "其他词牌" -> {
                    val majorCipais = listOf("水调歌头", "念奴娇", "浣溪沙", "鹧鸪天", "临江仙", "虞美人", "蝶恋花")
                    majorCipais.none { title.contains(it) }
                }
                else -> title.contains(subGenre)
            }
        }
        "qu" -> {
            when (subGenre) {
                "小令" -> title.contains("小令") || (!title.contains("套数") && (title.contains("天净沙") || title.contains("喜春来") || title.contains("清江引") || title.contains("山坡羊") || title.contains("折桂令") || title.contains("醉高歌") || title.contains("红绣鞋")))
                "套数" -> title.contains("套数") || title.contains("一枝花") || title.contains("煞")
                "散曲" -> title.contains("散曲") || !title.contains("套数")
                "其他" -> true
                else -> title.contains(subGenre)
            }
        }
        "wen" -> {
            when (subGenre) {
                "赋" -> title.endsWith("赋") || title.contains("赋·") || title.contains("赋（") || title.contains("赋 ") || title.contains("赋二首")
                "序" -> title.endsWith("序") || title.contains("序·") || title.contains("序（") || title.contains("序 ") || title.contains("序二首")
                "记" -> title.endsWith("记") || title.contains("记·") || title.contains("记（") || title.contains("记 ")
                "论" -> title.endsWith("论") || title.contains("论·") || title.contains("论（") || title.contains("论 ")
                "书信" -> title.endsWith("书") || title.contains("书·") || title.contains("书（") || title.endsWith("启") || title.endsWith("信") || title.endsWith("函")
                "表奏" -> title.endsWith("表") || title.contains("表·") || title.contains("表（") || title.endsWith("疏") || title.endsWith("策")
                "其他古文" -> {
                    val proseTypes = listOf("赋", "序", "记", "论", "书", "表", "疏", "策", "启", "信", "函")
                    proseTypes.none { type ->
                        title.endsWith(type) || title.contains("$type·") || title.contains("$type（") || title.contains("$type ")
                    }
                }
                else -> title.contains(subGenre)
            }
        }
        else -> true
    }
}

private fun getPoemLineMeter(excerpt: String): Int {
    if (excerpt.isBlank()) return 0
    val firstClause = excerpt.split(Regex("[，。？！；\\s]+")).firstOrNull { it.isNotBlank() } ?: return 0
    val chineseChars = firstClause.count { it in '\u4e00'..'\u9fa5' }
    return if (chineseChars in 5..7) chineseChars else 0
}

/**
 * 选本专属列表项：包含原书篇目序号（如 001）、分卷体裁、标题、作者与名句摘录
 */
@Composable
private fun AnthologyWorkRowItem(
    item: AnthologyWorkItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appTheme = com.shi.yun.ui.theme.LocalAppTheme.current
    val isDark = appTheme.themeId.isDark
    val itemCardBg = appTheme.customTokens.resolvedCardBackground(isDark)
    val itemBorder = appTheme.customTokens.resolvedCardBorder(isDark)

    val work = item.work
    val orderStr = String.format(java.util.Locale.CHINA, "%03d", item.item.orderIndex)
    val authorText = work.formatAuthorDynasty().ifEmpty { work.author }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(com.shi.yun.ui.theme.ThemeVisualSpecs.CORNER_RADIUS_LIST_CARD))
            .background(itemCardBg)
            .border(com.shi.yun.ui.theme.ThemeVisualSpecs.BORDER_WIDTH_CARD, itemBorder, RoundedCornerShape(com.shi.yun.ui.theme.ThemeVisualSpecs.CORNER_RADIUS_LIST_CARD))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = orderStr,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )

                Text(
                    text = work.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            Text(
                text = authorText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        if (work.excerpt.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = work.excerpt,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
            )
        }
    }
}

/**
 * 列表项组件：纯净雅致的文字排版
 */
@Composable
private fun CategoryWorkItem(
    work: Work,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appTheme = com.shi.yun.ui.theme.LocalAppTheme.current
    val isDark = appTheme.themeId.isDark
    val itemCardBg = appTheme.customTokens.resolvedCardBackground(isDark)
    val itemBorder = appTheme.customTokens.resolvedCardBorder(isDark)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(com.shi.yun.ui.theme.ThemeVisualSpecs.CORNER_RADIUS_LIST_CARD))
            .background(itemCardBg)
            .border(com.shi.yun.ui.theme.ThemeVisualSpecs.BORDER_WIDTH_CARD, itemBorder, RoundedCornerShape(com.shi.yun.ui.theme.ThemeVisualSpecs.CORNER_RADIUS_LIST_CARD))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = work.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = work.formatAuthorDynastyGenre(),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = work.excerpt,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 20.sp,
        )
    }
}
