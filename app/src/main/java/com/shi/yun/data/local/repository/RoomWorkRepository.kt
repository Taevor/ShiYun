package com.shi.yun.data.local.repository

import com.shi.yun.common.log.AppLogger
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.dao.AnthologyDao
import com.shi.yun.data.local.dao.CategoryDao
import com.shi.yun.data.local.dao.FavoriteDao
import com.shi.yun.data.local.dao.ReleaseDao
import com.shi.yun.data.local.dao.WorkDao
import com.shi.yun.data.local.entity.UserFavoriteEntity
import com.shi.yun.data.local.entity.WorkEntity
import com.shi.yun.data.local.entity.WorkVersionEntity
import com.shi.yun.data.local.mapper.WorkEntityMapper
import com.shi.yun.data.model.Anthology
import com.shi.yun.data.model.AnthologyItem
import com.shi.yun.data.local.entity.ComponentType
import com.shi.yun.data.model.AnthologyWorkItem
import com.shi.yun.data.model.AuxiliaryContent
import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.Work
import com.shi.yun.data.model.WorkListItem
import com.shi.yun.data.model.toWork
import com.shi.yun.data.repository.WorkRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 基于 Room 关系数据库的诗词数据仓库实现 (RoomWorkRepository)
 *
 * 严格按照诗云数据库 V1.0 架构与规范实现：
 * 1. 面向统一 [WorkRepository] 接口，具备与 [com.shi.yun.data.repository.LocalJsonWorkRepository] 100% 行为对齐的读取与操作能力；
 * 2. 多表数据聚合组装：通过 [WorkEntityMapper] 将六大业务区域的规范表结构安全映射至现有 [Work] 领域模型；
 * 3. 版本解析策略：优先读取当前激活发布指针 (app_release_pointer) 绑定的版本；若未设置发布指针，后备使用该作品的最新版本 (version_code 降序首位)；
 * 4. 严守业务隔离与安全底线：五维内容组件严格解耦，创作背景与文学赏析严格隔离，作者生平不泄露至作品组件；
 * 5. 用户资产独立安全：收藏直接持久化至 user_favorites 表，具备强外键保护与响应式 [StateFlow] 实时同步。
 */
class RoomWorkRepository(
    private val workDao: WorkDao,
    private val categoryDao: CategoryDao,
    private val releaseDao: ReleaseDao,
    private val favoriteDao: FavoriteDao,
    private val anthologyDao: AnthologyDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : WorkRepository {

    constructor(
        database: ShiYunDatabase,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        workDao = database.workDao(),
        categoryDao = database.categoryDao(),
        releaseDao = database.releaseDao(),
        favoriteDao = database.favoriteDao(),
        anthologyDao = database.anthologyDao(),
        dispatcher = dispatcher
    )

    private val repositoryScope = CoroutineScope(dispatcher + SupervisorJob())
    private val _favoriteIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    override val favoriteIdsFlow: StateFlow<Set<String>> = _favoriteIdsFlow.asStateFlow()

    init {
        repositoryScope.launch {
            try {
                val initialIds = favoriteDao.getAllFavoriteWorkIds()
                _favoriteIdsFlow.value = initialIds.toSet()
                favoriteDao.getAllFavoriteWorkIdsFlow().collect { ids ->
                    _favoriteIdsFlow.value = ids.toSet()
                }
            } catch (e: Exception) {
                AppLogger.w("RoomWorkRepository", "收藏流初始化异常: ${e.message}")
            }
        }
    }

    override suspend fun getAllWorks(): List<Work> = withContext(dispatcher) {
        val activeWorks = workDao.getAllActiveWorks()
        activeWorks.mapNotNull { buildWork(it) }
    }

    @Volatile
    private var cachedWorkList: List<WorkListItem>? = null

    override suspend fun getWorkList(): List<WorkListItem> = withContext(dispatcher) {
        cachedWorkList?.let { return@withContext it }
        val startTime = System.currentTimeMillis()
        val projections = workDao.getAllWorkListProjections()
        val list = projections.map { proj ->
            val resolvedGenre = Genre.fromCodeOrNull(proj.genreCode)
                ?: Genre.entries.firstOrNull { it.displayName == proj.genreCode }
                ?: when {
                    proj.genreCode?.contains("词", ignoreCase = true) == true -> Genre.CI
                    proj.genreCode?.contains("曲", ignoreCase = true) == true -> Genre.QU
                    proj.genreCode?.contains("文", ignoreCase = true) == true -> Genre.WEN
                    else -> Genre.POEM
                }
            WorkListItem(
                id = proj.workId,
                title = proj.standardTitle,
                author = proj.authorName?.takeIf { it.isNotBlank() } ?: "佚名",
                dynasty = proj.dynastyName ?: "",
                genre = resolvedGenre,
                excerpt = proj.excerpt ?: ""
            )
        }
        cachedWorkList = list
        val duration = System.currentTimeMillis() - startTime
        AppLogger.i("RoomWorkRepository", "轻量作品列表单次投影装载成功: 总数=${list.size}首, 耗时=${duration}ms")
        list
    }

    @Volatile
    private var cachedRecommendationCandidateIds: List<String>? = null

    override suspend fun getHomeRecommendations(count: Int): List<Work> = withContext(dispatcher) {
        val startTime = System.currentTimeMillis()
        try {
            var candidateIds = cachedRecommendationCandidateIds
            if (candidateIds == null || candidateIds.isEmpty()) {
                candidateIds = workDao.getWorkIdsByRelease("release_v1_1")
                if (candidateIds.isEmpty()) {
                    candidateIds = workDao.getWorkIdsByRelease("release_v1_0")
                }
                cachedRecommendationCandidateIds = candidateIds
            }

            val targetCount = count.coerceAtLeast(1)
            val selectedIds = if (!candidateIds.isNullOrEmpty()) {
                candidateIds.shuffled().take(targetCount)
            } else {
                emptyList()
            }

            if (selectedIds.isEmpty()) {
                val duration = System.currentTimeMillis() - startTime
                AppLogger.w("RoomWorkRepository", "推荐候选集为空，回退到普通列表装载 (耗时=${duration}ms)")
                return@withContext getWorkList().shuffled().take(targetCount).map { it.toWork() }
            }

            val projections = workDao.getWorkListProjectionsByIds(selectedIds)
            val works = projections.map { proj ->
                val resolvedGenre = Genre.fromCodeOrNull(proj.genreCode)
                    ?: Genre.entries.firstOrNull { it.displayName == proj.genreCode }
                    ?: when {
                        proj.genreCode?.contains("词", ignoreCase = true) == true -> Genre.CI
                        proj.genreCode?.contains("曲", ignoreCase = true) == true -> Genre.QU
                        proj.genreCode?.contains("文", ignoreCase = true) == true -> Genre.WEN
                        else -> Genre.POEM
                    }
                WorkListItem(
                    id = proj.workId,
                    title = proj.standardTitle,
                    author = proj.authorName?.takeIf { it.isNotBlank() } ?: "佚名",
                    dynasty = proj.dynastyName ?: "",
                    genre = resolvedGenre,
                    excerpt = proj.excerpt ?: ""
                ).toWork()
            }
            val duration = System.currentTimeMillis() - startTime
            AppLogger.i("RoomWorkRepository", "首页极速推荐集装载成功: count=${works.size}, 耗时=${duration}ms")
            works
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            AppLogger.e("RoomWorkRepository", "首页推荐集装载失败: 耗时=${duration}ms, 原因=${e.message}", e)
            getWorkList().shuffled().take(count).map { it.toWork() }
        }
    }

    override suspend fun getWorkById(id: String): Work? = withContext(dispatcher) {
        val startMs = System.currentTimeMillis()
        val work = workDao.getWorkById(id)
        if (work == null || work.identityStatus.equals("DEPRECATED", ignoreCase = true)) {
            val duration = System.currentTimeMillis() - startMs
            AppLogger.dataOp("getWorkById", id, "NOT_FOUND", duration)
            return@withContext null
        }
        val res = buildWork(work)
        val duration = System.currentTimeMillis() - startMs
        AppLogger.dataOp("getWorkById", id, if (res != null) "SUCCESS" else "BUILD_FAILED", duration, res?.title)
        res
    }

    /**
     * 统一解析作品当前生效的版本实体（优先读取激活发布指针所指向的版本，后备降序首位的最新版本）
     * 保证正文内容与辅助内容（注释、译文、赏析）始终使用 100% 相同一致的版本
     *
     * 严格安全约束：
     * 1. 只有版本实体非空且其所属作品 workId 与目标 workId 完全一致时，才采用该版本；
     * 2. 若版本不存在、所属作品不匹配（防止跨作品错配）或发布指针为空，一律安全回退到该作品自身版本列表（按 version_code DESC 降序取最高版本）；
     * 3. 绝不泄露返回其他作品的版本。
     */
    internal suspend fun resolveActiveWorkVersion(workId: String): WorkVersionEntity? {
        val versionId = releaseDao.getActiveVersionIdForWork(workId)
        val candidateVersion = if (versionId != null) workDao.getVersionById(versionId) else null
        return if (candidateVersion != null && candidateVersion.workId == workId) {
            candidateVersion
        } else {
            workDao.getVersionsByWorkId(workId).firstOrNull()
        }
    }

    override suspend fun getAuxiliaryContent(workId: String): AuxiliaryContent? = withContext(dispatcher) {
        val startMs = System.currentTimeMillis()
        val version = resolveActiveWorkVersion(workId)
        if (version == null) {
            val duration = System.currentTimeMillis() - startMs
            AppLogger.dataOp("getAuxiliaryContent", workId, "NO_VERSION", duration)
            return@withContext null
        }
        val components = workDao.getComponentsByVersionId(version.workVersionId)
        val auxComponents = components.filter {
            it.componentType == ComponentType.ANNOTATION.code ||
            it.componentType == ComponentType.TRANSLATION.code ||
            it.componentType == ComponentType.APPRECIATION.code ||
            it.componentType == ComponentType.BACKGROUND.code
        }
        if (auxComponents.isEmpty()) {
            val duration = System.currentTimeMillis() - startMs
            AppLogger.dataOp("getAuxiliaryContent", workId, "EMPTY", duration)
            return@withContext null
        }

        val componentIds = auxComponents.map { it.componentId }
        val attributions = if (componentIds.isNotEmpty()) {
            workDao.getAttributionsByComponentIds(componentIds)
        } else emptyList()

        val sourceLinkIds = attributions.mapNotNull { it.externalSourceLinkId }.distinct()
        val externalSourceLinks = if (sourceLinkIds.isNotEmpty()) {
            workDao.getExternalSourceLinksByIds(sourceLinkIds)
        } else emptyList()

        val sourceLinkMap = externalSourceLinks.associateBy { it.linkId }

        val res = WorkEntityMapper.resolveAuxiliary(auxComponents, attributions, sourceLinkMap)
        val duration = System.currentTimeMillis() - startMs
        AppLogger.dataOp("getAuxiliaryContent", workId, "SUCCESS", duration, "组件数=${auxComponents.size}")
        res
    }

    override suspend fun getRandomWork(): Work? = withContext(dispatcher) {
        val randomWork = workDao.getRandomWork() ?: return@withContext null
        buildWork(randomWork)
    }

    override suspend fun getWorksByGenre(genre: String): List<Work> = withContext(dispatcher) {
        val trimmed = genre.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val parsedGenre = Genre.fromCodeOrNull(trimmed)
        val candidates = if (parsedGenre != null) {
            listOf(parsedGenre.code, parsedGenre.code.uppercase(), "genre_${parsedGenre.code}", parsedGenre.displayName)
        } else {
            listOf(trimmed, trimmed.lowercase(), trimmed.uppercase(), "genre_$trimmed")
        }

        val matchingWorks = categoryDao.getWorksByGenreCandidates(candidates)
        matchingWorks.mapNotNull { buildWork(it) }
    }

    override suspend fun getWorksByDynasty(dynasty: String): List<Work> = withContext(dispatcher) {
        val trimmed = dynasty.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val matchingWorks = categoryDao.getWorksByDynastyFlexible(trimmed)
        matchingWorks.mapNotNull { buildWork(it) }
    }

    override suspend fun searchWorks(keyword: String): List<Work> = withContext(dispatcher) {
        val startMs = System.currentTimeMillis()
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val daoStartMs = System.currentTimeMillis()
        val projections = workDao.searchWorkProjections(trimmed, limit = 100)
        val daoDuration = System.currentTimeMillis() - daoStartMs

        val mapStartMs = System.currentTimeMillis()
        val list = projections.map { proj ->
            val resolvedGenre = Genre.fromCodeOrNull(proj.genreCode)
                ?: Genre.entries.firstOrNull { it.displayName == proj.genreCode }
                ?: when {
                    proj.genreCode?.contains("词", ignoreCase = true) == true -> Genre.CI
                    proj.genreCode?.contains("曲", ignoreCase = true) == true -> Genre.QU
                    proj.genreCode?.contains("文", ignoreCase = true) == true -> Genre.WEN
                    else -> Genre.POEM
                }
            WorkListItem(
                id = proj.workId,
                title = proj.standardTitle,
                author = proj.authorName?.takeIf { it.isNotBlank() } ?: "佚名",
                dynasty = proj.dynastyName ?: "",
                genre = resolvedGenre,
                excerpt = proj.excerpt ?: ""
            ).toWork()
        }
        val mapDuration = System.currentTimeMillis() - mapStartMs
        val totalDuration = System.currentTimeMillis() - startMs
        AppLogger.i("RoomWorkRepository", "单次投影搜索完成: 关键词='$trimmed', 命中=${list.size}首, DAO耗时=${daoDuration}ms, 转换耗时=${mapDuration}ms, 总耗时=${totalDuration}ms")
        AppLogger.dataOp("searchWorks", "len=${trimmed.length}", "SUCCESS", totalDuration, "命中=${list.size}首 (DAO=${daoDuration}ms, MAP=${mapDuration}ms)")
        list
    }

    override suspend fun getFavoriteWorks(): List<Work> = withContext(dispatcher) {
        val favEntities = favoriteDao.getFavoriteWorks()
        favEntities.mapNotNull { buildWork(it) }
    }

    override suspend fun toggleFavorite(id: String): Boolean = withContext(dispatcher) {
        val startMs = System.currentTimeMillis()
        // 验证目标作品是否真实存在
        val work = workDao.getWorkById(id)
        if (work == null || work.identityStatus.equals("DEPRECATED", ignoreCase = true)) {
            val duration = System.currentTimeMillis() - startMs
            AppLogger.dataOp("toggleFavorite", id, "FAIL", duration, "目标作品不存在或已被废弃")
            AppLogger.w("RoomWorkRepository", "收藏操作忽略: 目标作品不存在或已被废弃: $id")
            return@withContext false
        }

        val isFav = favoriteDao.isFavorite(id)
        if (isFav) {
            favoriteDao.deleteFavoriteByWorkId(id)
            _favoriteIdsFlow.update { it - id }
            val duration = System.currentTimeMillis() - startMs
            AppLogger.dataOp("toggleFavorite", id, "SUCCESS", duration, "取消收藏")
            AppLogger.i("RoomWorkRepository", "取消收藏作品: id=$id")
            false
        } else {
            favoriteDao.insertFavorite(
                UserFavoriteEntity(
                    favoriteId = "fav_${id}_${System.currentTimeMillis()}",
                    workId = id,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
            _favoriteIdsFlow.update { it + id }
            val duration = System.currentTimeMillis() - startMs
            AppLogger.dataOp("toggleFavorite", id, "SUCCESS", duration, "加入收藏")
            AppLogger.i("RoomWorkRepository", "加入收藏作品: id=$id")
            true
        }
    }

    private var cachedAnthologies: List<Anthology>? = null

    override suspend fun getAnthologies(): List<Anthology> = withContext(dispatcher) {
        cachedAnthologies?.let { return@withContext it }
        val booksWithCount = anthologyDao.getAllAnthologyBooksWithCount()
        val result = booksWithCount.map { b ->
            val cleanDesc = b.description.trim().takeIf {
                it.isNotEmpty() && !it.contains("海棠") && !it.contains("保序")
            } ?: ""
            val rawEditionNote = b.edition_note.trim().takeIf {
                it.isNotEmpty() && !it.contains("海棠") && !it.contains("保序")
            }
            val effectiveEditionNote = rawEditionNote ?: cleanDesc
            Anthology(
                id = b.book_id,
                code = b.book_code,
                name = b.standard_title,
                compiler = b.compiler,
                dynasty = "",
                targetCount = b.item_count,
                editionNote = effectiveEditionNote,
                description = cleanDesc
            )
        }
        cachedAnthologies = result
        result
    }

    override suspend fun getAnthologyById(id: String): Anthology? = withContext(dispatcher) {
        val book = anthologyDao.getAnthologyBookById(id)
            ?: anthologyDao.getAnthologyBookByCode(id)
            ?: return@withContext null

        val latestVersion = anthologyDao.getLatestBookVersion(book.bookId)
        val count = anthologyDao.getItemCountByBookId(book.bookId)
        val rawEditionNote = latestVersion?.editionNote?.trim()?.takeIf {
            it.isNotEmpty() && !it.contains("海棠") && !it.contains("保序")
        }
        val cleanDesc = book.description?.trim()?.takeIf {
            it.isNotEmpty() && !it.contains("海棠") && !it.contains("保序")
        } ?: ""
        val effectiveEditionNote = rawEditionNote ?: cleanDesc
        Anthology(
            id = book.bookId,
            code = book.bookCode,
            name = book.standardTitle,
            compiler = book.compiler ?: "",
            dynasty = "",
            targetCount = count,
            editionNote = effectiveEditionNote,
            description = cleanDesc
        )
    }

    override suspend fun getWorksByAnthology(anthologyId: String): List<AnthologyWorkItem> = withContext(dispatcher) {
        val book = anthologyDao.getAnthologyBookById(anthologyId)
            ?: anthologyDao.getAnthologyBookByCode(anthologyId)
            ?: return@withContext emptyList()

        val items = anthologyDao.getItemsByBookId(book.bookId)
        val result = ArrayList<AnthologyWorkItem>(items.size)
        val workListMap = getWorkList().associateBy { it.id }

        for (item in items) {
            val work = workListMap[item.workId]?.toWork() ?: getWorkById(item.workId)
            if (work != null) {
                val node = anthologyDao.getNodeById(item.nodeId)
                val domainItem = AnthologyItem(
                    id = item.itemId,
                    anthologyId = book.bookId,
                    workId = item.workId,
                    orderIndex = item.itemOrder,
                    volume = node?.nodeTitle ?: ""
                )
                result.add(AnthologyWorkItem(domainItem, work))
            } else {
                AppLogger.w("RoomWorkRepository", "选本条目关联作品不存在: book=${book.bookId}, work=${item.workId}")
            }
        }
        result
    }

    /**
     * 根据作品实体组装完整的 [Work] 领域模型
     */
    private suspend fun buildWork(work: WorkEntity): Work? {
        // 1. 确定当前展示的作品版本（统一版本解析策略，保证正文与辅助组件版本强一致）
        val version = resolveActiveWorkVersion(work.workId) ?: return null

        // 2. 获取该版本挂载的内容组件
        val components = workDao.getComponentsByVersionId(version.workVersionId)
        val versionContents = workDao.getVersionContents(version.workVersionId)

        // 3. 获取作者与署名关联
        val authors = workDao.getAuthorsByVersionId(version.workVersionId)
        val workAuthors = workDao.getVersionAuthors(version.workVersionId)

        // 4. 获取七维分类判定记录
        val classifications = workDao.getVersionClassifications(version.workVersionId)

        // 5. 分类字典实体解析
        val dynastyId = classifications.firstOrNull { it.dimensionType == "DYNASTY" }?.classificationId
            ?: authors.firstOrNull()?.dynastyId
        val dynasty = dynastyId?.let { categoryDao.getDynastyById(it) }

        val genreId = classifications.firstOrNull { it.dimensionType == "GENRE" }?.classificationId
        val genre = genreId?.let { categoryDao.getGenreById(it) }

        val cipaiId = classifications.firstOrNull { it.dimensionType == "CIPAI" }?.classificationId
        val cipai = cipaiId?.let { categoryDao.getCipaiById(it) }

        val qupaiId = classifications.firstOrNull { it.dimensionType == "QUPAI" }?.classificationId
        val qupai = qupaiId?.let { categoryDao.getQupaiById(it) }

        val formId = classifications.firstOrNull { it.dimensionType == "FORM" }?.classificationId
        val form = formId?.let { categoryDao.getFormById(it) }

        // 6. 版权溯源与外部链接解析
        val componentIds = components.map { it.componentId }
        val attributions = if (componentIds.isNotEmpty()) {
            workDao.getAttributionsByComponentIds(componentIds)
        } else emptyList()

        val workSourceLinks = workDao.getWorkSourceLinksByWorkId(work.workId)
        val sourceLinkIds = (attributions.mapNotNull { it.externalSourceLinkId } + workSourceLinks.map { it.externalSourceLinkId }).distinct()
        val externalSourceLinks = if (sourceLinkIds.isNotEmpty()) {
            workDao.getExternalSourceLinksByIds(sourceLinkIds)
        } else emptyList()

        return WorkEntityMapper.toDomainWork(
            work = work,
            version = version,
            authors = authors,
            workAuthors = workAuthors,
            components = components,
            versionContents = versionContents,
            classifications = classifications,
            dynasty = dynasty,
            genre = genre,
            cipai = cipai,
            qupai = qupai,
            form = form,
            attributions = attributions,
            externalSourceLinks = externalSourceLinks,
            workSourceLinks = workSourceLinks
        )
    }
}
