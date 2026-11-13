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
import com.shi.yun.data.local.mapper.WorkEntityMapper
import com.shi.yun.data.model.Anthology
import com.shi.yun.data.model.AnthologyItem
import com.shi.yun.data.model.AnthologyWorkItem
import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.Work
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

    override suspend fun getWorkById(id: String): Work? = withContext(dispatcher) {
        val work = workDao.getWorkById(id) ?: return@withContext null
        if (work.identityStatus.equals("DEPRECATED", ignoreCase = true)) {
            return@withContext null
        }
        buildWork(work)
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
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val matchingWorks = workDao.searchWorks(trimmed)
        matchingWorks.mapNotNull { buildWork(it) }
    }

    override suspend fun getFavoriteWorks(): List<Work> = withContext(dispatcher) {
        val favEntities = favoriteDao.getFavoriteWorks()
        favEntities.mapNotNull { buildWork(it) }
    }

    override suspend fun toggleFavorite(id: String): Boolean = withContext(dispatcher) {
        // 验证目标作品是否真实存在
        val work = workDao.getWorkById(id)
        if (work == null || work.identityStatus.equals("DEPRECATED", ignoreCase = true)) {
            AppLogger.w("RoomWorkRepository", "收藏操作忽略: 目标作品不存在或已被废弃: $id")
            return@withContext false
        }

        val isFav = favoriteDao.isFavorite(id)
        if (isFav) {
            favoriteDao.deleteFavoriteByWorkId(id)
            _favoriteIdsFlow.update { it - id }
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
            AppLogger.i("RoomWorkRepository", "加入收藏作品: id=$id")
            true
        }
    }

    override suspend fun getAnthologies(): List<Anthology> = withContext(dispatcher) {
        val books = anthologyDao.getAllAnthologyBooks()
        books.map { book ->
            val latestVersion = anthologyDao.getLatestBookVersion(book.bookId)
            val count = anthologyDao.getItemCountByBookId(book.bookId)
            Anthology(
                id = book.bookId,
                code = book.bookCode,
                name = book.standardTitle,
                compiler = book.compiler ?: "",
                dynasty = "",
                targetCount = count,
                editionNote = latestVersion?.editionNote ?: (book.description ?: "")
            )
        }
    }

    override suspend fun getAnthologyById(id: String): Anthology? = withContext(dispatcher) {
        val book = anthologyDao.getAnthologyBookById(id)
            ?: anthologyDao.getAnthologyBookByCode(id)
            ?: return@withContext null

        val latestVersion = anthologyDao.getLatestBookVersion(book.bookId)
        val count = anthologyDao.getItemCountByBookId(book.bookId)
        Anthology(
            id = book.bookId,
            code = book.bookCode,
            name = book.standardTitle,
            compiler = book.compiler ?: "",
            dynasty = "",
            targetCount = count,
            editionNote = latestVersion?.editionNote ?: (book.description ?: "")
        )
    }

    override suspend fun getWorksByAnthology(anthologyId: String): List<AnthologyWorkItem> = withContext(dispatcher) {
        val book = anthologyDao.getAnthologyBookById(anthologyId)
            ?: anthologyDao.getAnthologyBookByCode(anthologyId)
            ?: return@withContext emptyList()

        val items = anthologyDao.getItemsByBookId(book.bookId)
        val result = ArrayList<AnthologyWorkItem>(items.size)

        for (item in items) {
            val work = getWorkById(item.workId)
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
        // 1. 确定当前展示的作品版本（优先激活发布指针，后备最新版本）
        val versionId = releaseDao.getActiveVersionIdForWork(work.workId)
        val version = if (versionId != null) {
            workDao.getVersionById(versionId)
        } else {
            workDao.getVersionsByWorkId(work.workId).firstOrNull()
        } ?: return null

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
