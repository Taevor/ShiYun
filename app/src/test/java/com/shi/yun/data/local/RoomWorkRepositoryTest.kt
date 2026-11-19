package com.shi.yun.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shi.yun.data.local.entity.AnthologyBookEntity
import com.shi.yun.data.local.entity.AnthologyBookVersionEntity
import com.shi.yun.data.local.entity.AnthologyItemEntity
import com.shi.yun.data.local.entity.AnthologyNodeEntity
import com.shi.yun.data.local.entity.AppReleasePointerEntity
import com.shi.yun.data.local.entity.AuthorEntity
import com.shi.yun.data.local.entity.CipaiEntity
import com.shi.yun.data.local.entity.ComponentType
import com.shi.yun.data.local.entity.ContentAttributionEntity
import com.shi.yun.data.local.entity.ContentComponentEntity
import com.shi.yun.data.local.entity.DataBatchEntity
import com.shi.yun.data.local.entity.DataReleaseEntity
import com.shi.yun.data.local.entity.DynastyEntity
import com.shi.yun.data.local.entity.ExternalSourceLinkEntity
import com.shi.yun.data.local.entity.GenreEntity
import com.shi.yun.data.local.entity.ReleaseWorkEntryEntity
import com.shi.yun.data.local.entity.WorkAuthorEntity
import com.shi.yun.data.local.entity.WorkEntity
import com.shi.yun.data.local.entity.WorkSourceLinkEntity
import com.shi.yun.data.local.entity.WorkVersionClassificationEntity
import com.shi.yun.data.local.entity.WorkVersionContentEntity
import com.shi.yun.data.local.entity.WorkVersionEntity
import com.shi.yun.data.local.mapper.WorkEntityMapper
import com.shi.yun.data.local.repository.RoomWorkRepository
import com.shi.yun.data.model.AttributionStatus
import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.LayoutType
import com.shi.yun.data.model.formatAuthorDynasty
import com.shi.yun.data.model.formatAuthorDynastyGenre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RoomWorkRepository 与 WorkEntityMapper 详尽单元测试
 *
 * 全面验证：
 * 1. 单篇作品读取与组装完整性（核心字段、五维内容、排版、底本）；
 * 2. 五维内容组件独立装配：正文、译文、注释、背景、赏析，特别是创作背景与文学赏析严格隔离；
 * 3. 绝不泄露作者文人生平 biography 至作品组件；
 * 4. 辅助内容可选性（全空时 auxiliary 为 null，部分存在时各负其责）；
 * 5. 多作者合著展示（顿号分隔）与归属争议解析；
 * 6. 多版本解析与激活发布指针优先级覆盖；
 * 7. 七维分类（体裁、朝代、词牌）中英文及代码匹配筛选；
 * 8. 全文关键字模糊检索与废弃作品安全过滤；
 * 9. 随机推荐与全部有效作品列表；
 * 10. 用户收藏独立管理、防竞态 StateFlow 与不存在作品拦截；
 * 11. 典籍选集与篇目多级目录节点严格按序装配。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomWorkRepositoryTest {

    private lateinit var db: ShiYunDatabase
    private lateinit var repository: RoomWorkRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShiYunDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()
        repository = RoomWorkRepository(db, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // 测试 1：单篇作品读取与完整多表组装 (getWorkById)
    // =========================================================================
    @Test
    fun testGetWorkById_SingleWorkCompleteAssembly() = runBlocking {
        insertBaseMetadataAndDictionaries()

        // 插入李白核心信息（包含生平介绍，用于验证不泄露）
        val author = AuthorEntity(
            authorId = "author_libai",
            standardName = "李白",
            dynastyId = "dynasty_tang",
            biography = "李白，字太白，号青莲居士，唐代伟大的浪漫主义诗人，被后人誉为“诗仙”。",
            biographySource = "《新唐书·李白传》",
            biographyReviewStatus = "VERIFIED"
        )
        db.categoryDao().insertAuthors(listOf(author))

        // 插入作品核心与版本
        val work = WorkEntity(
            workId = "work_jys",
            standardTitle = "静夜思",
            identityStatus = "NORMAL",
            sortOrder = 1,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        db.workDao().insertWork(work)

        val version = WorkVersionEntity(
            workVersionId = "wv_jys_v1",
            workId = "work_jys",
            versionCode = 1,
            editionName = "唐诗别裁集本",
            layoutType = LayoutType.FIVE_JUEJU.code,
            excerpt = "举头望明月，低头思故乡。",
            preface = null,
            createdAtEpochMs = 1000L
        )
        db.workDao().insertWorkVersion(version)

        // 关联作者
        val workAuthor = WorkAuthorEntity(
            workVersionId = "wv_jys_v1",
            authorId = "author_libai",
            authorRole = "PRIMARY",
            authorOrder = 1,
            determinationStatus = "CONFIRMED"
        )
        db.workDao().insertWorkAuthors(listOf(workAuthor))

        // 关联分类：朝代=唐，体裁=诗
        val classifications = listOf(
            WorkVersionClassificationEntity(
                workVersionId = "wv_jys_v1",
                dimensionType = "DYNASTY",
                classificationId = "dynasty_tang",
                updatedAtEpochMs = 1000L
            ),
            WorkVersionClassificationEntity(
                workVersionId = "wv_jys_v1",
                dimensionType = "GENRE",
                classificationId = "genre_poem",
                updatedAtEpochMs = 1000L
            )
        )
        db.workDao().insertWorkVersionClassifications(classifications)

        // 插入五维内容组件
        val contentComp = ContentComponentEntity(
            componentId = "cc_jys_content",
            componentType = ComponentType.CONTENT.code,
            contentPayload = "床前看月光，疑是地上霜。\n举头望山月，低头思故乡。",
            contentHash = "hash_c",
            createdAtEpochMs = 1000L
        )
        val transComp = ContentComponentEntity(
            componentId = "cc_jys_trans",
            componentType = ComponentType.TRANSLATION.code,
            contentPayload = "明亮的月光洒在床前，好像地上铺了一层洁白的霜。",
            contentHash = "hash_t",
            createdAtEpochMs = 1000L
        )
        val annotComp = ContentComponentEntity(
            componentId = "cc_jys_annot",
            componentType = ComponentType.ANNOTATION.code,
            contentPayload = """[{"index":1,"quote":"看月光","note":"一作‘明月光’。"},{"index":2,"quote":"望山月","note":"一作‘望明月’。"}]""",
            rawFormat = "STRUCTURED_JSON",
            contentHash = "hash_a",
            createdAtEpochMs = 1000L
        )
        val bgComp = ContentComponentEntity(
            componentId = "cc_jys_bg",
            componentType = ComponentType.BACKGROUND.code,
            contentPayload = "唐玄宗开元十四年（726年）九月十五日夜，李白旅居扬州病舍，见月思乡所作。",
            contentHash = "hash_b",
            createdAtEpochMs = 1000L
        )
        val apprecComp = ContentComponentEntity(
            componentId = "cc_jys_apprec",
            componentType = ComponentType.APPRECIATION.code,
            contentPayload = "全诗无一字言思乡，而字字皆显客愁，信手拈来，韵味无穷。",
            contentHash = "hash_ap",
            createdAtEpochMs = 1000L
        )
        db.workDao().insertComponents(listOf(contentComp, transComp, annotComp, bgComp, apprecComp))

        // 挂载至版本
        val versionContents = listOf(
            WorkVersionContentEntity("wv_jys_v1", ComponentType.CONTENT.code, "cc_jys_content", "VERIFIED"),
            WorkVersionContentEntity("wv_jys_v1", ComponentType.TRANSLATION.code, "cc_jys_trans", "VERIFIED"),
            WorkVersionContentEntity("wv_jys_v1", ComponentType.ANNOTATION.code, "cc_jys_annot", "VERIFIED"),
            WorkVersionContentEntity("wv_jys_v1", ComponentType.BACKGROUND.code, "cc_jys_bg", "VERIFIED"),
            WorkVersionContentEntity("wv_jys_v1", ComponentType.APPRECIATION.code, "cc_jys_apprec", "VERIFIED")
        )
        db.workDao().insertWorkVersionContents(versionContents)

        // 插入版权溯源凭证
        val attributions = listOf(
            ContentAttributionEntity("ca_1", "cc_jys_content", curatorName = "中华书局底本", licenseType = "PUBLIC_DOMAIN"),
            ContentAttributionEntity("ca_2", "cc_jys_trans", curatorName = "古典诗词研究中心", licenseType = "CC_BY_SA_4_0"),
            ContentAttributionEntity("ca_3", "cc_jys_annot", curatorName = "古籍整理组", licenseType = "PUBLIC_DOMAIN"),
            ContentAttributionEntity("ca_4", "cc_jys_bg", curatorName = "李白年谱考订", licenseType = "PUBLIC_DOMAIN"),
            ContentAttributionEntity("ca_5", "cc_jys_apprec", curatorName = "唐诗鉴赏辞典", licenseType = "AUTHORIZED")
        )
        db.workDao().insertContentAttributions(attributions)

        // 执行读取
        val domainWork = repository.getWorkById("work_jys")
        assertNotNull(domainWork)
        assertEquals("work_jys", domainWork!!.id)
        assertEquals("静夜思", domainWork.title)
        assertEquals("李白", domainWork.author)
        assertEquals("唐", domainWork.dynasty)
        assertEquals(Genre.POEM, domainWork.genre)
        assertEquals(LayoutType.FIVE_JUEJU, domainWork.layout)
        assertEquals("举头望明月，低头思故乡。", domainWork.excerpt)
        assertTrue(domainWork.content.contains("床前看月光"))

        // 验证底本来源
        assertNotNull(domainWork.textSource)
        assertEquals("中华书局底本", domainWork.textSource?.sourceName)

        // 验证五维辅助内容：译文、注释、创作背景、文学赏析全部齐全
        val aux = domainWork.auxiliary
        assertNotNull(aux)
        assertEquals("明亮的月光洒在床前，好像地上铺了一层洁白的霜。", aux?.translation)
        assertEquals("古典诗词研究中心", aux?.translationSource?.sourceName)

        assertEquals(2, aux?.annotations?.size)
        assertEquals("看月光", aux?.annotations?.get(0)?.quote)
        assertEquals("一作‘明月光’。", aux?.annotations?.get(0)?.note)
        assertEquals("古籍整理组", aux?.annotationSource?.sourceName)

        // 严格验证 BACKGROUND 与 APPRECIATION 互不混淆
        assertEquals("唐玄宗开元十四年（726年）九月十五日夜，李白旅居扬州病舍，见月思乡所作。", aux?.background)
        assertEquals("李白年谱考订", aux?.backgroundSource?.sourceName)

        assertEquals("全诗无一字言思乡，而字字皆显客愁，信手拈来，韵味无穷。", aux?.appreciation)
        assertEquals("唐诗鉴赏辞典", aux?.appreciationSource?.sourceName)

        assertNotEquals(aux?.background, aux?.appreciation)

        // 严格验证作者生平 biography 绝不泄露到作品组件或辅助内容中
        assertFalse(domainWork.content.contains("诗仙"))
        assertFalse(aux?.background?.contains("诗仙") ?: false)
        assertFalse(aux?.appreciation?.contains("诗仙") ?: false)
    }

    // =========================================================================
    // 测试 2：多作者合著场景（顿号连接）与归属考订争议
    // =========================================================================
    @Test
    fun testWorkAuthor_CoAuthorAndAttributionDisputed() = runBlocking {
        insertBaseMetadataAndDictionaries()

        val author1 = AuthorEntity("author_su_shi", "苏轼", "dynasty_song")
        val author2 = AuthorEntity("author_su_zhe", "苏辙", "dynasty_song")
        db.categoryDao().insertAuthors(listOf(author1, author2))

        val work = WorkEntity("work_he_zuo", "对月作", sortOrder = 10, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        db.workDao().insertWork(work)

        val version = WorkVersionEntity("wv_he_zuo_v1", "work_he_zuo", 1, "初刻本", layoutType = LayoutType.FIVE_JUEJU.code, createdAtEpochMs = 1000L)
        db.workDao().insertWorkVersion(version)

        val content = ContentComponentEntity("cc_hz_1", ComponentType.CONTENT.code, "月明照高楼，兄弟共游赏。", "PLAIN_TEXT", "HUMAN_CURATED", "hash_hz", 1000L)
        db.workDao().insertComponent(content)
        db.workDao().insertWorkVersionContents(listOf(WorkVersionContentEntity("wv_he_zuo_v1", ComponentType.CONTENT.code, "cc_hz_1", "VERIFIED")))

        // 插入多作者（苏轼为主，苏辙为合著），且苏辙署名存疑
        val workAuthors = listOf(
            WorkAuthorEntity("wv_he_zuo_v1", "author_su_shi", "PRIMARY", 1, "CONFIRMED"),
            WorkAuthorEntity("wv_he_zuo_v1", "author_su_zhe", "CO_AUTHOR", 2, "DISPUTED", evidence = "栾城集未载，见东坡别集补遗")
        )
        db.workDao().insertWorkAuthors(workAuthors)

        val domainWork = repository.getWorkById("work_he_zuo")
        assertNotNull(domainWork)
        assertEquals("苏轼、苏辙", domainWork!!.author)

        // 验证归属考订
        assertNotNull(domainWork.attribution)
        assertEquals(AttributionStatus.DISPUTED, domainWork.attribution?.status)
        assertEquals("栾城集未载，见东坡别集补遗", domainWork.attribution?.note)
        assertEquals(listOf("苏辙"), domainWork.attribution?.candidateAuthors)
    }

    // =========================================================================
    // 测试 3：辅助内容可选性（全空时 auxiliary 为 null，部分存在时各负其责）
    // =========================================================================
    @Test
    fun testAuxiliaryContent_PartialAndEmpty() = runBlocking {
        insertBaseMetadataAndDictionaries()

        // 1. 只有正文，无任何辅助内容
        val work1 = WorkEntity("work_plain", "寻隐者不遇", sortOrder = 20, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        db.workDao().insertWork(work1)
        val v1 = WorkVersionEntity("wv_p_1", "work_plain", 1, "通行本", layoutType = LayoutType.FIVE_JUEJU.code, createdAtEpochMs = 1000L)
        db.workDao().insertWorkVersion(v1)
        val c1 = ContentComponentEntity("cc_p_1", ComponentType.CONTENT.code, "松下问童子，言师采药去。", "PLAIN_TEXT", "HUMAN_CURATED", "hash_p", 1000L)
        db.workDao().insertComponent(c1)
        db.workDao().insertWorkVersionContents(listOf(WorkVersionContentEntity("wv_p_1", ComponentType.CONTENT.code, "cc_p_1", "VERIFIED")))

        val domainPlain = repository.getWorkById("work_plain")
        assertNotNull(domainPlain)
        assertNull(domainPlain!!.auxiliary)

        // 2. 只有正文与译文，无注释、背景、赏析
        val work2 = WorkEntity("work_trans_only", "塞下曲", sortOrder = 21, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        db.workDao().insertWork(work2)
        val v2 = WorkVersionEntity("wv_t_1", "work_trans_only", 1, "通行本", layoutType = LayoutType.FIVE_JUEJU.code, createdAtEpochMs = 1000L)
        db.workDao().insertWorkVersion(v2)
        val c2 = ContentComponentEntity("cc_t_c", ComponentType.CONTENT.code, "月黑雁飞高，单于夜遁逃。", "PLAIN_TEXT", "HUMAN_CURATED", "hash_c2", 1000L)
        val tr2 = ContentComponentEntity("cc_t_tr", ComponentType.TRANSLATION.code, "黑夜里鸿雁高飞，敌军首领趁夜潜逃。", "PLAIN_TEXT", "HUMAN_CURATED", "hash_tr2", 1000L)
        db.workDao().insertComponents(listOf(c2, tr2))
        db.workDao().insertWorkVersionContents(listOf(
            WorkVersionContentEntity("wv_t_1", ComponentType.CONTENT.code, "cc_t_c", "VERIFIED"),
            WorkVersionContentEntity("wv_t_1", ComponentType.TRANSLATION.code, "cc_t_tr", "VERIFIED")
        ))
        db.workDao().insertContentAttributions(listOf(
            ContentAttributionEntity("ca_tr2", "cc_t_tr", curatorName = "译者小明", licenseType = "PUBLIC_DOMAIN")
        ))

        val domainTransOnly = repository.getWorkById("work_trans_only")
        assertNotNull(domainTransOnly)
        assertNotNull(domainTransOnly!!.auxiliary)
        assertEquals("黑夜里鸿雁高飞，敌军首领趁夜潜逃。", domainTransOnly.auxiliary?.translation)
        assertEquals("译者小明", domainTransOnly.auxiliary?.translationSource?.sourceName)
        assertNull(domainTransOnly.auxiliary?.annotations)
        assertNull(domainTransOnly.auxiliary?.background)
        assertNull(domainTransOnly.auxiliary?.appreciation)
    }

    // =========================================================================
    // 测试 4：多版本解析与当前发布指针优先级覆盖
    // =========================================================================
    @Test
    fun testMultiVersion_ReleasePointerOverride() = runBlocking {
        insertBaseMetadataAndDictionaries()

        val work = WorkEntity("work_chibi", "念奴娇·赤壁怀古", sortOrder = 30, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        db.workDao().insertWork(work)

        // 插入两版版本：v1（初校本），v2（精校定本）
        val v1 = WorkVersionEntity("wv_cb_v1", "work_chibi", 1, "初校本", layoutType = LayoutType.CI_DOUBLE.code, subtitle = "大江东去", createdAtEpochMs = 1000L)
        val v2 = WorkVersionEntity("wv_cb_v2", "work_chibi", 2, "精校定本", layoutType = LayoutType.CI_DOUBLE.code, subtitle = "三国周郎", createdAtEpochMs = 2000L)
        db.workDao().insertWorkVersion(v1)
        db.workDao().insertWorkVersion(v2)

        val c1 = ContentComponentEntity("cc_cb_1", ComponentType.CONTENT.code, "大江东去，浪淘尽，千古风流人物。（初版）", "PLAIN_TEXT", "HUMAN_CURATED", "hash_cb1", 1000L)
        val c2 = ContentComponentEntity("cc_cb_2", ComponentType.CONTENT.code, "大江东去，浪淘尽，千古风流人物。（精校版）", "PLAIN_TEXT", "HUMAN_CURATED", "hash_cb2", 2000L)
        db.workDao().insertComponents(listOf(c1, c2))
        db.workDao().insertWorkVersionContents(listOf(
            WorkVersionContentEntity("wv_cb_v1", ComponentType.CONTENT.code, "cc_cb_1", "VERIFIED"),
            WorkVersionContentEntity("wv_cb_v2", ComponentType.CONTENT.code, "cc_cb_2", "VERIFIED")
        ))

        // 情况 A：无发布指针时，默认读取最新版本 (v2)
        val workDefault = repository.getWorkById("work_chibi")
        assertNotNull(workDefault)
        assertEquals("三国周郎", workDefault!!.subtitle)
        assertTrue(workDefault.content.contains("精校版"))

        // 情况 B：设置发布指针锁定在 v1
        val batch = DataBatchEntity("batch_01", "BATCH_2026_01", "INTERNAL_TEST", 10, "IMPORTED", 1000L)
        db.releaseDao().insertBatch(batch)
        val release = DataReleaseEntity("rel_01", "REL_2026_01", "batch_01", "FULL", "首发包", 1000L)
        db.releaseDao().insertRelease(release)
        val entry = ReleaseWorkEntryEntity("rel_01", "work_chibi", "wv_cb_v1")
        db.releaseDao().insertReleaseWorkEntries(listOf(entry))
        db.releaseDao().setReleasePointer(AppReleasePointerEntity(1, "rel_01", 1000L))

        // 重新读取，验证指针覆盖起效，读取到 v1
        val workPointer = repository.getWorkById("work_chibi")
        assertNotNull(workPointer)
        assertEquals("大江东去", workPointer!!.subtitle)
        assertTrue(workPointer.content.contains("初版"))
    }

    // =========================================================================
    // 测试 5：分类检索（按体裁、按朝代中英文与编码混合测试）
    // =========================================================================
    @Test
    fun testClassificationFilters() = runBlocking {
        insertBaseMetadataAndDictionaries()

        // 建立一首诗和一首词
        val poemWork = WorkEntity("work_poem_1", "春晓", sortOrder = 1, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        val ciWork = WorkEntity("work_ci_1", "相见欢", sortOrder = 2, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        db.workDao().insertWorks(listOf(poemWork, ciWork))

        val vPoem = WorkVersionEntity("wv_poem_1", "work_poem_1", 1, "底本", layoutType = LayoutType.FIVE_JUEJU.code, createdAtEpochMs = 1000L)
        val vCi = WorkVersionEntity("wv_ci_1", "work_ci_1", 1, "底本", layoutType = LayoutType.CI_DOUBLE.code, createdAtEpochMs = 1000L)
        db.workDao().insertWorkVersion(vPoem)
        db.workDao().insertWorkVersion(vCi)

        val cPoem = ContentComponentEntity("cc_poem_1", ComponentType.CONTENT.code, "春眠不觉晓，处处闻啼鸟。", "PLAIN_TEXT", "HUMAN_CURATED", "hash_p1", 1000L)
        val cCi = ContentComponentEntity("cc_ci_1", ComponentType.CONTENT.code, "无言独上西楼，月如钩。", "PLAIN_TEXT", "HUMAN_CURATED", "hash_c1", 1000L)
        db.workDao().insertComponents(listOf(cPoem, cCi))
        db.workDao().insertWorkVersionContents(listOf(
            WorkVersionContentEntity("wv_poem_1", ComponentType.CONTENT.code, "cc_poem_1", "VERIFIED"),
            WorkVersionContentEntity("wv_ci_1", ComponentType.CONTENT.code, "cc_ci_1", "VERIFIED")
        ))

        // 关联分类
        db.workDao().insertWorkVersionClassifications(listOf(
            WorkVersionClassificationEntity("wv_poem_1", "GENRE", "genre_poem", updatedAtEpochMs = 1000L),
            WorkVersionClassificationEntity("wv_poem_1", "DYNASTY", "dynasty_tang", updatedAtEpochMs = 1000L),
            WorkVersionClassificationEntity("wv_ci_1", "GENRE", "genre_ci", updatedAtEpochMs = 1000L),
            WorkVersionClassificationEntity("wv_ci_1", "DYNASTY", "dynasty_tang", updatedAtEpochMs = 1000L)
        ))

        // 按体裁筛选：支持 "poem", "POEM", "诗"
        val poemResultsCode = repository.getWorksByGenre("poem")
        assertEquals(1, poemResultsCode.size)
        assertEquals("work_poem_1", poemResultsCode[0].id)

        val poemResultsName = repository.getWorksByGenre("诗")
        assertEquals(1, poemResultsName.size)
        assertEquals("work_poem_1", poemResultsName[0].id)

        val ciResults = repository.getWorksByGenre("ci")
        assertEquals(1, ciResults.size)
        assertEquals("work_ci_1", ciResults[0].id)

        // 按朝代筛选：支持 "唐", "dynasty_tang"
        val dynastyResultsName = repository.getWorksByDynasty("唐")
        assertEquals(2, dynastyResultsName.size)

        val dynastyResultsId = repository.getWorksByDynasty("dynasty_tang")
        assertEquals(2, dynastyResultsId.size)
    }

    // =========================================================================
    // 测试 6：全文检索与废弃 (DEPRECATED) 状态过滤
    // =========================================================================
    @Test
    fun testSearchWorks_AndDeprecatedExclusion() = runBlocking {
        insertBaseMetadataAndDictionaries()

        val author = AuthorEntity("author_mb", "孟浩然", "dynasty_tang")
        db.categoryDao().insertAuthors(listOf(author))

        val workValid = WorkEntity("work_valid", "宿建德江", identityStatus = "NORMAL", sortOrder = 1, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        val workDeprecated = WorkEntity("work_deprecated", "宿建德江旧版", identityStatus = "DEPRECATED", sortOrder = 2, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        db.workDao().insertWorks(listOf(workValid, workDeprecated))

        val v1 = WorkVersionEntity("wv_val_1", "work_valid", 1, "底本", layoutType = LayoutType.FIVE_JUEJU.code, createdAtEpochMs = 1000L)
        val v2 = WorkVersionEntity("wv_dep_1", "work_deprecated", 1, "旧版", layoutType = LayoutType.FIVE_JUEJU.code, createdAtEpochMs = 1000L)
        db.workDao().insertWorkVersion(v1)
        db.workDao().insertWorkVersion(v2)

        val c1 = ContentComponentEntity("cc_v_1", ComponentType.CONTENT.code, "移舟泊烟渚，日暮客愁新。", "PLAIN_TEXT", "HUMAN_CURATED", "hash_v", 1000L)
        val c2 = ContentComponentEntity("cc_d_1", ComponentType.CONTENT.code, "移舟泊烟渚，日暮客愁新。（废弃）", "PLAIN_TEXT", "HUMAN_CURATED", "hash_d", 1000L)
        db.workDao().insertComponents(listOf(c1, c2))
        db.workDao().insertWorkVersionContents(listOf(
            WorkVersionContentEntity("wv_val_1", ComponentType.CONTENT.code, "cc_v_1", "VERIFIED"),
            WorkVersionContentEntity("wv_dep_1", ComponentType.CONTENT.code, "cc_d_1", "VERIFIED")
        ))
        db.workDao().insertWorkAuthors(listOf(
            WorkAuthorEntity("wv_val_1", "author_mb", "PRIMARY", 1, "CONFIRMED"),
            WorkAuthorEntity("wv_dep_1", "author_mb", "PRIMARY", 1, "CONFIRMED")
        ))

        // 搜索作者名 "孟浩然"
        val searchByAuthor = repository.searchWorks("孟浩然")
        assertEquals(1, searchByAuthor.size)
        assertEquals("work_valid", searchByAuthor[0].id)

        // 搜索正文关键字 "烟渚"
        val searchByContent = repository.searchWorks("烟渚")
        assertEquals(1, searchByContent.size)
        assertEquals("work_valid", searchByContent[0].id)

        // 搜索标题 "宿建德江"
        val searchByTitle = repository.searchWorks("宿建德江")
        assertEquals(1, searchByTitle.size)
        assertEquals("work_valid", searchByTitle[0].id)

        // 直接通过 getWorkById 查询废弃作品应返回 null
        val directDeprecated = repository.getWorkById("work_deprecated")
        assertNull(directDeprecated)

        // getAllWorks 亦不应包含废弃作品
        val allWorks = repository.getAllWorks()
        assertEquals(1, allWorks.size)
        assertEquals("work_valid", allWorks[0].id)
    }

    // =========================================================================
    // 测试 7：随机作品与空关键字保护
    // =========================================================================
    @Test
    fun testRandomWorkAndEmptySearch() = runBlocking {
        insertBaseMetadataAndDictionaries()

        val work = WorkEntity("work_rnd", "登鹳雀楼", sortOrder = 1, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        db.workDao().insertWork(work)
        val v = WorkVersionEntity("wv_rnd_1", "work_rnd", 1, "底本", layoutType = LayoutType.FIVE_JUEJU.code, createdAtEpochMs = 1000L)
        db.workDao().insertWorkVersion(v)
        val c = ContentComponentEntity("cc_rnd_1", ComponentType.CONTENT.code, "白日依山尽，黄河入海流。", "PLAIN_TEXT", "HUMAN_CURATED", "hash_rnd", 1000L)
        db.workDao().insertComponent(c)
        db.workDao().insertWorkVersionContents(listOf(WorkVersionContentEntity("wv_rnd_1", ComponentType.CONTENT.code, "cc_rnd_1", "VERIFIED")))

        val randomWork = repository.getRandomWork()
        assertNotNull(randomWork)
        assertEquals("work_rnd", randomWork!!.id)

        // 空白关键字搜索返回 emptyList
        assertTrue(repository.searchWorks("").isEmpty())
        assertTrue(repository.searchWorks("   ").isEmpty())
    }

    // =========================================================================
    // 测试 8：用户资产——收藏操作、StateFlow 与拦截
    // =========================================================================
    @Test
    fun testFavoriteOperations() = runBlocking {
        insertBaseMetadataAndDictionaries()

        val work = WorkEntity("work_fav_test", "望庐山瀑布", sortOrder = 1, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        db.workDao().insertWork(work)
        val v = WorkVersionEntity("wv_fav_1", "work_fav_test", 1, "底本", layoutType = LayoutType.FIVE_JUEJU.code, createdAtEpochMs = 1000L)
        db.workDao().insertWorkVersion(v)
        val c = ContentComponentEntity("cc_fav_1", ComponentType.CONTENT.code, "日照香炉生紫烟，遥看瀑布挂前川。", "PLAIN_TEXT", "HUMAN_CURATED", "hash_fav", 1000L)
        db.workDao().insertComponent(c)
        db.workDao().insertWorkVersionContents(listOf(WorkVersionContentEntity("wv_fav_1", ComponentType.CONTENT.code, "cc_fav_1", "VERIFIED")))

        // 初始无收藏
        assertTrue(repository.favoriteIdsFlow.first().isEmpty())

        // 1. 添加收藏
        val added = repository.toggleFavorite("work_fav_test")
        assertTrue(added)
        assertTrue(repository.favoriteIdsFlow.value.contains("work_fav_test"))

        val favWorks = repository.getFavoriteWorks()
        assertEquals(1, favWorks.size)
        assertEquals("work_fav_test", favWorks[0].id)

        // 2. 取消收藏
        val removed = repository.toggleFavorite("work_fav_test")
        assertFalse(removed)
        assertFalse(repository.favoriteIdsFlow.value.contains("work_fav_test"))
        assertTrue(repository.getFavoriteWorks().isEmpty())

        // 3. 收藏不存在的作品应被安全拦截
        val fakeAdded = repository.toggleFavorite("work_not_exist")
        assertFalse(fakeAdded)
    }

    // =========================================================================
    // 测试 9：典籍选集与篇目多级目录节点严格按序装配
    // =========================================================================
    @Test
    fun testAnthologyOperations() = runBlocking {
        insertBaseMetadataAndDictionaries()

        // 插入作品
        val w1 = WorkEntity("work_anth_1", "关雎", sortOrder = 1, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        val w2 = WorkEntity("work_anth_2", "葛覃", sortOrder = 2, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        db.workDao().insertWorks(listOf(w1, w2))

        val v1 = WorkVersionEntity("wv_a_1", "work_anth_1", 1, "通行本", layoutType = LayoutType.FIVE_JUEJU.code, createdAtEpochMs = 1000L)
        val v2 = WorkVersionEntity("wv_a_2", "work_anth_2", 1, "通行本", layoutType = LayoutType.FIVE_JUEJU.code, createdAtEpochMs = 1000L)
        db.workDao().insertWorkVersion(v1)
        db.workDao().insertWorkVersion(v2)

        val c1 = ContentComponentEntity("cc_a_1", ComponentType.CONTENT.code, "关关雎鸠，在河之洲。", "PLAIN_TEXT", "HUMAN_CURATED", "h1", 1000L)
        val c2 = ContentComponentEntity("cc_a_2", ComponentType.CONTENT.code, "葛之覃兮，施于中谷。", "PLAIN_TEXT", "HUMAN_CURATED", "h2", 1000L)
        db.workDao().insertComponents(listOf(c1, c2))
        db.workDao().insertWorkVersionContents(listOf(
            WorkVersionContentEntity("wv_a_1", ComponentType.CONTENT.code, "cc_a_1", "VERIFIED"),
            WorkVersionContentEntity("wv_a_2", ComponentType.CONTENT.code, "cc_a_2", "VERIFIED")
        ))

        // 插入发布批次与发布包
        val batch = DataBatchEntity("batch_anth", "BATCH_ANTH_01", "INTERNAL_TEST", 2, "IMPORTED", 1000L)
        db.releaseDao().insertBatch(batch)
        val release = DataReleaseEntity("rel_anth", "REL_ANTH_01", "batch_anth", "FULL", "选本包", 1000L)
        db.releaseDao().insertRelease(release)

        // 插入选本图书及版本
        val book = AnthologyBookEntity("book_shijing", "《诗经》", "shijing", "孔子", 1, "先秦诗歌总集")
        db.anthologyDao().insertAnthologyBook(book)
        val bookVersion = AnthologyBookVersionEntity("abv_shijing_1", "book_shijing", 1, "毛诗注疏本", "rel_anth")
        db.anthologyDao().insertAnthologyBookVersion(bookVersion)

        // 插入目录节点
        val node = AnthologyNodeEntity("node_guofeng_zhounan", "abv_shijing_1", null, "国风·周南", "VOLUME", sortOrder = 1)
        db.anthologyDao().insertAnthologyNodes(listOf(node))

        // 挂载篇目（设置次序 1 与 2）
        val item1 = AnthologyItemEntity("item_01", "node_guofeng_zhounan", "work_anth_1", "wv_a_1", itemOrder = 1)
        val item2 = AnthologyItemEntity("item_02", "node_guofeng_zhounan", "work_anth_2", "wv_a_2", itemOrder = 2)
        db.anthologyDao().insertAnthologyItems(listOf(item1, item2))

        // 1. 获取选本列表
        val anthologies = repository.getAnthologies()
        assertEquals(1, anthologies.size)
        assertEquals("book_shijing", anthologies[0].id)
        assertEquals("shijing", anthologies[0].code)
        assertEquals("《诗经》", anthologies[0].name)
        assertEquals("毛诗注疏本", anthologies[0].editionNote)
        assertEquals(2, anthologies[0].targetCount)

        // 2. 按 ID / Code 获取单选本
        val bookById = repository.getAnthologyById("book_shijing")
        assertNotNull(bookById)
        assertEquals("《诗经》", bookById?.name)

        val bookByCode = repository.getAnthologyById("shijing")
        assertNotNull(bookByCode)
        assertEquals("《诗经》", bookByCode?.name)

        // 3. 获取选本篇目列表（严格按次序装配）
        val workItems = repository.getWorksByAnthology("book_shijing")
        assertEquals(2, workItems.size)
        assertEquals(1, workItems[0].item.orderIndex)
        assertEquals("work_anth_1", workItems[0].work.id)
        assertEquals("关雎", workItems[0].work.title)
        assertEquals("国风·周南", workItems[0].item.volume)

        assertEquals(2, workItems[1].item.orderIndex)
        assertEquals("work_anth_2", workItems[1].work.id)
        assertEquals("葛覃", workItems[1].work.title)
        assertEquals("国风·周南", workItems[1].item.volume)
    }

    // =========================================================================
    // 测试 10：WorkEntityMapper 单独纯单元测试
    // =========================================================================
    @Test
    fun testWorkEntityMapper_DirectMapping() {
        val work = WorkEntity("work_m_1", "忆江南", sortOrder = 1, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        val version = WorkVersionEntity("wv_m_1", "work_m_1", 1, "通行本", layoutType = LayoutType.CI_DOUBLE.code, subtitle = "江南好", createdAtEpochMs = 1000L)
        val author = AuthorEntity("author_bjy", "白居易", "dynasty_tang")
        val content = ContentComponentEntity("cc_m_1", ComponentType.CONTENT.code, "江南好，风景旧曾谙。", "PLAIN_TEXT", "HUMAN_CURATED", "hash_m", 1000L)
        val cipai = CipaiEntity("cipai_yjn", "忆江南")
        val dynasty = DynastyEntity("dynasty_tang", "唐")
        val genre = GenreEntity("genre_ci", "词")

        val result = WorkEntityMapper.toDomainWork(
            work = work,
            version = version,
            authors = listOf(author),
            components = listOf(content),
            dynasty = dynasty,
            genre = genre,
            cipai = cipai
        )

        assertEquals("work_m_1", result.id)
        assertEquals("忆江南", result.title)
        assertEquals("白居易", result.author)
        assertEquals("唐", result.dynasty)
        assertEquals(Genre.CI, result.genre)
        assertEquals("忆江南", result.cipai)
        assertEquals("江南好", result.subtitle)
        assertEquals("江南好，风景旧曾谙。", result.content)
        assertNull(result.auxiliary)
    }

    // =========================================================================
    // 测试 12：轻量列表投影高效查询 (getWorkList)
    // =========================================================================
    @Test
    fun testGetWorkList_SingleProjectionEfficiency() = runBlocking {
        insertBaseMetadataAndDictionaries()

        val author = AuthorEntity(authorId = "author_libai", standardName = "李白", dynastyId = "dynasty_tang")
        db.categoryDao().insertAuthors(listOf(author))

        val work = WorkEntity(
            workId = "work_jys",
            standardTitle = "静夜思",
            identityStatus = "NORMAL",
            sortOrder = 1,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        db.workDao().insertWork(work)

        val version = WorkVersionEntity(
            workVersionId = "wv_jys_v1",
            workId = "work_jys",
            versionCode = 1,
            editionName = "底本",
            layoutType = LayoutType.FIVE_JUEJU.code,
            excerpt = "床前明月光，疑是地上霜。",
            preface = null,
            createdAtEpochMs = 1000L
        )
        db.workDao().insertWorkVersion(version)

        val workAuthor = WorkAuthorEntity(workVersionId = "wv_jys_v1", authorId = "author_libai", authorOrder = 1)
        db.workDao().insertWorkAuthors(listOf(workAuthor))

        val classificationDynasty = WorkVersionClassificationEntity("wv_jys_v1", "DYNASTY", "dynasty_tang", updatedAtEpochMs = 1000L)
        val classificationGenre = WorkVersionClassificationEntity("wv_jys_v1", "GENRE", "genre_poem", updatedAtEpochMs = 1000L)
        db.workDao().insertWorkVersionClassifications(listOf(classificationDynasty, classificationGenre))

        val list = repository.getWorkList()
        assertEquals(1, list.size)
        val item = list.first()
        assertEquals("work_jys", item.id)
        assertEquals("静夜思", item.title)
        assertEquals("李白", item.author)
        assertEquals("唐", item.dynasty)
        assertEquals(Genre.POEM, item.genre)
        assertEquals("床前明月光，疑是地上霜。", item.excerpt)
        assertEquals("〔唐〕李白", item.formatAuthorDynasty())
        assertEquals("〔唐〕李白  ·  诗", item.formatAuthorDynastyGenre())
    }

    // =========================================================================
    // 测试 13：赏析内部元数据清洗 (cleanAppreciationPayload)
    // =========================================================================
    @Test
    fun testCleanAppreciationPayload() {
        val jsonPayload = """
            [
              {
                "bookId": "60fc038f34bfda01e0e8f5ac",
                "bookTitle": "唐宋词鉴赏辞典",
                "articleId": "610e74b17d0ac1145494bb7a",
                "shortContent": "这是一篇关于苏轼赤壁怀古的精美赏析。",
                "authors": ["高", "原"],
                "pressName": "上海辞书出版社"
              },
              {
                "bookId": "60fc038f34bfda01e0e8f5ad",
                "bookTitle": "宋词鉴赏辞典",
                "articleId": "610e74b17d0ac1145494bb7b",
                "shortContent": "这是一篇关于苏轼赤壁怀古的精美赏析。",
                "authors": ["高", "原"],
                "pressName": "上海辞书出版社"
              }
            ]
        """.trimIndent()

        val cleaned = WorkEntityMapper.cleanAppreciationPayload(jsonPayload)
        assertNotNull(cleaned)
        // 验证已彻底剥离内部 ID
        assertFalse(cleaned!!.contains("bookId"))
        assertFalse(cleaned.contains("articleId"))
        assertFalse(cleaned.contains("60fc038f34bfda01e0e8f5ac"))
        // 验证去重后只包含一篇内容
        assertTrue(cleaned.contains("这是一篇关于苏轼赤壁怀古的精美赏析。"))
        assertTrue(cleaned.contains("选自《唐宋词鉴赏辞典》· 高原"))

        // 测试普通纯文本残留清洗
        val plainTextWithId = "正常赏析正文第一段。\nbookId: 1234567\narticleId: 890123\n正常赏析正文第二段。"
        val cleanedPlain = WorkEntityMapper.cleanAppreciationPayload(plainTextWithId)
        assertEquals("正常赏析正文第一段。\n正常赏析正文第二段。", cleanedPlain)
    }

    // =========================================================================
    // 辅助数据填充
    // =========================================================================
    private suspend fun insertBaseMetadataAndDictionaries() {
        val dynasties = listOf(
            DynastyEntity("dynasty_tang", "唐", 618, 907, 1),
            DynastyEntity("dynasty_song", "宋", 960, 1279, 2)
        )
        val genres = listOf(
            GenreEntity("genre_poem", "诗", 1),
            GenreEntity("genre_ci", "词", 2)
        )
        val cipais = listOf(
            CipaiEntity("cipai_yjn", "忆江南", sortOrder = 1),
            CipaiEntity("cipai_nnj", "念奴娇", sortOrder = 2)
        )
        db.categoryDao().insertDynasties(dynasties)
        db.categoryDao().insertGenres(genres)
        db.categoryDao().insertCipais(cipais)
    }
}
