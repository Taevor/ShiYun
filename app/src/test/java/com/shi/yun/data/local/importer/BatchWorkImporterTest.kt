package com.shi.yun.data.local.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.entity.AuthorAliasEntity
import com.shi.yun.data.local.entity.AuthorEntity
import com.shi.yun.data.local.entity.ComponentType
import com.shi.yun.data.local.entity.DataBatchEntity
import com.shi.yun.data.local.entity.DataReleaseEntity
import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.PrecheckDisposition
import com.shi.yun.data.local.importer.model.SourceImportInfo
import com.shi.yun.data.local.importer.model.WorkImportItem
import com.shi.yun.data.local.repository.RoomWorkRepository
import com.shi.yun.data.model.AnnotationItem
import com.shi.yun.data.model.LayoutType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 批量作品导入核心服务全面单元测试 (BatchWorkImporterTest)
 *
 * 严格覆盖阶段 3 步骤 5.2 两项定点修复及全部核心测试场景：
 * 1. 单篇完整作品导入，36 张表对应核心表全部正确填充；
 * 2. 最小作品导入（无作者时绝不创建虚假“佚名”档案，Repository 可正常读取）；
 * 3. 作者复用：已有作者标准名正确复用，不重复创建作者；
 * 4. 作者复用：通过历史别名 (author_aliases) 识别已有作者；
 * 5. 作者别名追加：已有作者导入新别名时自动追加至 author_aliases；
 * 6. 多作者作品入库：主要作者与合著作者均正确关联入库；
 * 7. 作者生平隔离：作者生平独立保存在 authors 表，不混入作品内容组件；
 * 8. 结构化注释入库：正确序列化为 STRUCTURED_JSON 格式存储；
 * 9. 纯文本注释入库：正确以 PLAIN_TEXT 格式存储；
 * 10. 完全重复作品识别与安全跳过 (CAN_SKIP)，不重复写库；
 * 11. 正文相同但辅助内容新增或变更：标记待确认 (NEEDS_CONFIRMATION)；
 * 12. 同名同作者异文分歧：标记待确认 (NEEDS_CONFIRMATION)，保留差异；
 * 13. 同名不同作者：各自作为独立作品完整入库；
 * 14. 校验失败数据被拦截，整批失败时批次更新为 FAILED，不留 IMPORTING；
 * 15. 混合批次（可导入/跳过/待确认/失败）准确定位，仅导入 READY 项，批次状态为 PARTIAL；
 * 16. 事务回滚保护：发生写入异常时作品数据完全回滚（0 脏数据），data_batches 标记为 FAILED；
 * 17. 正式发布包映射：传入 releaseId 时仅写入发布条目，不自动变更正式发布指针；
 * 18. 批次生命周期状态：批次创建初始严格为 IMPORTING；
 * 19. 全部重复且安全跳过时批次状态为 IMPORTED，新增作品数严格为 0；
 * 20. 提前返回路径覆盖：所有提前退出分支均正确更新批次状态，绝不遗留 IMPORTING。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatchWorkImporterTest {

    private lateinit var db: ShiYunDatabase
    private lateinit var importer: BatchWorkImporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShiYunDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()
        importer = BatchWorkImporter(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // 场景 1：单篇完整作品导入，核心多表正确填充
    // =========================================================================
    @Test
    fun testSingleWorkImport_AllTablesPopulated() = runBlocking {
        val item = WorkImportItem(
            title = "望庐山瀑布",
            subtitle = "其二",
            preface = "游庐山作",
            layoutType = LayoutType.SEVEN_JUEJU.code,
            author = AuthorImportItem(name = "李白", dynasty = "唐", aliases = listOf("青莲居士", "李太白")),
            dynasty = "唐",
            genre = "poem",
            form = "seven_jueju",
            tags = listOf("山水", "庐山"),
            content = "日照香炉生紫烟，遥看瀑布挂前川。\n飞流直下三千尺，疑是银河落九天。",
            contentSource = SourceImportInfo(sourceName = "全唐诗"),
            translation = "香炉峰在阳光照耀下升起紫色烟雾，远远望去瀑布宛如挂在山前。",
            translationSource = SourceImportInfo(sourceName = "白话唐诗"),
            annotations = "香炉：指香炉峰。\n挂前川：挂在山前。",
            annotationSource = SourceImportInfo(sourceName = "唐诗纪事"),
            background = "李白五十岁游历庐山时所作。",
            backgroundSource = SourceImportInfo(sourceName = "李白年谱"),
            appreciation = "极为奇绝的想象力，将瀑布飞泻比作银河倾落。",
            appreciationSource = SourceImportInfo(sourceName = "唐诗鉴赏"),
            externalSourceCode = "SHI_CI_CN",
            externalWorkId = "work_libai_wlspb"
        )

        val result = importer.importBatch(
            batchCode = "BATCH_TEST_001",
            batchSource = "全唐诗测试库",
            items = listOf(item)
        )

        assertTrue(result.isSuccessful)
        assertEquals(1, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(0, result.failedCount)
        assertEquals("IMPORTED", result.batchStatus)

        // 验证 works 表
        assertEquals(1, db.workDao().getWorkCount())
        val works = db.workDao().getWorksByTitle("望庐山瀑布")
        assertEquals(1, works.size)
        val work = works[0]

        // 验证 work_versions 表
        val versions = db.workDao().getVersionsByWorkId(work.workId)
        assertEquals(1, versions.size)
        val version = versions[0]
        assertEquals("其二", version.subtitle)
        assertEquals(LayoutType.SEVEN_JUEJU.code, version.layoutType)

        // 验证 authors 与 work_authors
        val authors = db.workDao().getAuthorsByVersionId(version.workVersionId)
        assertEquals(1, authors.size)
        assertEquals("李白", authors[0].standardName)

        // 验证 5 个五维内容组件全部落库
        val components = db.workDao().getComponentsByVersionId(version.workVersionId)
        assertEquals(5, components.size)
        val types = components.map { it.componentType }.toSet()
        assertTrue(types.contains(ComponentType.CONTENT.code))
        assertTrue(types.contains(ComponentType.TRANSLATION.code))
        assertTrue(types.contains(ComponentType.ANNOTATION.code))
        assertTrue(types.contains(ComponentType.BACKGROUND.code))
        assertTrue(types.contains(ComponentType.APPRECIATION.code))

        // 验证分类与标签
        val classifications = db.workDao().getVersionClassifications(version.workVersionId)
        assertTrue(classifications.any { it.dimensionType == "DYNASTY" && it.classificationId == "唐" })
        assertTrue(classifications.any { it.dimensionType == "GENRE" && it.classificationId == "poem" })

        val tags = db.workDao().getVersionTags(version.workVersionId)
        assertEquals(2, tags.size)

        // 验证外部数据源链接
        val sourceLinks = db.workDao().getWorkSourceLinksByWorkId(work.workId)
        assertEquals(1, sourceLinks.size)

        // 验证批次记录最终更新为 IMPORTED
        val batch = db.releaseDao().getBatchByCode("BATCH_TEST_001")
        assertNotNull(batch)
        assertEquals("IMPORTED", batch?.status)
    }

    // =========================================================================
    // 场景 2：最小作品导入（无作者时绝不创建“佚名”或虚假作者档案，允许作者为空）
    // =========================================================================
    @Test
    fun testMinimalWorkImport_OnlyTitleAndContent() = runBlocking {
        val item = WorkImportItem(
            title = "短歌",
            content = "对酒当歌，人生几何！"
        )

        val result = importer.importBatch(
            batchCode = "BATCH_MINIMAL_001",
            batchSource = "极简录入",
            items = listOf(item)
        )

        assertTrue(result.isSuccessful)
        assertEquals(1, result.importedCount)
        assertEquals("IMPORTED", result.batchStatus)

        val work = db.workDao().getWorksByTitle("短歌").first()
        val versions = db.workDao().getVersionsByWorkId(work.workId)
        val version = versions.first()

        // 验证：绝不创建统一的“author_anon”或任何“佚名”虚假作者档案
        val allAuthors = db.categoryDao().getAllAuthors()
        assertEquals(0, allAuthors.size)
        assertNull(db.categoryDao().findAuthorByNameOrAlias("佚名"))

        // 验证：没有产生任何 work_authors 关联记录（作者关联为空）
        val authors = db.workDao().getAuthorsByVersionId(version.workVersionId)
        assertEquals(0, authors.size)
        val workAuthors = db.workDao().getVersionAuthors(version.workVersionId)
        assertEquals(0, workAuthors.size)

        // 仅有 1 个 CONTENT 组件，无多余空组件
        val components = db.workDao().getComponentsByVersionId(version.workVersionId)
        assertEquals(1, components.size)
        assertEquals(ComponentType.CONTENT.code, components.first().componentType)

        // 验证现有 RoomWorkRepository 可以正常读取作者为空的作品
        val repository = RoomWorkRepository(db)
        val loadedWork = repository.getWorkById(work.workId)
        assertNotNull(loadedWork)
        assertEquals("短歌", loadedWork?.title)
        assertEquals("", loadedWork?.author) // 按照规范，作者呈现为空字符串
        assertEquals("对酒当歌，人生几何！", loadedWork?.content)
    }

    // =========================================================================
    // 场景 3：作者复用：已有作者标准名正确复用，不重复创建作者
    // =========================================================================
    @Test
    fun testAuthorReuse_ExistingStandardName() = runBlocking {
        // 先在数据库中预置李白
        val authorId = "author_libai_seed"
        db.categoryDao().insertAuthors(listOf(AuthorEntity(authorId = authorId, standardName = "李白")))

        val item = WorkImportItem(
            title = "静夜思",
            author = AuthorImportItem("李白"),
            content = "床前明月光，疑是地上霜。\n举头望明月，低头思故乡。"
        )

        val result = importer.importBatch("BATCH_REUSE_001", "诗歌录入", listOf(item))
        assertTrue(result.isSuccessful)
        assertEquals(1, result.importedCount)
        assertEquals("IMPORTED", result.batchStatus)

        // 验证 authors 表总数依然为 1，无重复插入
        val allAuthors = db.categoryDao().getAllAuthors()
        assertEquals(1, allAuthors.size)
        assertEquals(authorId, allAuthors[0].authorId)

        // 验证新作品关联的即为该已有作者
        val work = db.workDao().getWorksByTitle("静夜思").first()
        val version = db.workDao().getVersionsByWorkId(work.workId).first()
        val workAuthors = db.workDao().getVersionAuthors(version.workVersionId)
        assertEquals(authorId, workAuthors[0].authorId)
    }

    // =========================================================================
    // 场景 4：作者复用：通过历史别名 (author_aliases) 识别已有作者
    // =========================================================================
    @Test
    fun testAuthorReuse_ExistingAlias() = runBlocking {
        // 预置苏轼与其别名苏东坡
        val authorId = "author_sushi_seed"
        db.categoryDao().insertAuthors(listOf(AuthorEntity(authorId = authorId, standardName = "苏轼")))
        db.categoryDao().insertAuthorAliases(listOf(
            AuthorAliasEntity("aa_001", authorId, "苏东坡", "HAO"),
            AuthorAliasEntity("aa_002", authorId, "东坡居士", "HAO")
        ))

        // 导入署名为“苏东坡”的作品
        val item = WorkImportItem(
            title = "题西林壁",
            author = AuthorImportItem("苏东坡"),
            content = "横看成岭侧成峰，远近高低各不同。\n不识庐山真面目，只缘身在此山中。"
        )

        val result = importer.importBatch("BATCH_ALIAS_001", "东坡集", listOf(item))
        assertTrue(result.isSuccessful)
        assertEquals(1, result.importedCount)

        // authors 表依然仅有 1 位作者（苏轼）
        val allAuthors = db.categoryDao().getAllAuthors()
        assertEquals(1, allAuthors.size)
        assertEquals("苏轼", allAuthors[0].standardName)

        val work = db.workDao().getWorksByTitle("题西林壁").first()
        val version = db.workDao().getVersionsByWorkId(work.workId).first()
        val workAuthors = db.workDao().getVersionAuthors(version.workVersionId)
        assertEquals(authorId, workAuthors[0].authorId)
    }

    // =========================================================================
    // 场景 5：作者别名追加：已有作者导入新别名时自动追加至 author_aliases
    // =========================================================================
    @Test
    fun testAuthorNewAliasesAppended() = runBlocking {
        val authorId = "author_dufu_seed"
        db.categoryDao().insertAuthors(listOf(AuthorEntity(authorId = authorId, standardName = "杜甫")))
        db.categoryDao().insertAuthorAliases(listOf(
            AuthorAliasEntity("aa_df_1", authorId, "少陵野老", "HAO")
        ))

        // 导入杜甫作品，带有新别名“子美”和“诗圣”
        val item = WorkImportItem(
            title = "绝句",
            author = AuthorImportItem("杜甫", aliases = listOf("子美", "诗圣", "少陵野老")),
            content = "两个黄鹂鸣翠柳，一行白鹭上青天。\n窗含西岭千秋雪，门泊东吴万里船。"
        )

        importer.importBatch("BATCH_ALIAS_APPEND", "杜工部集", listOf(item))

        val aliasesInDb = db.categoryDao().getAliasesByAuthorId(authorId).map { it.aliasName }.toSet()
        assertTrue(aliasesInDb.contains("少陵野老"))
        assertTrue(aliasesInDb.contains("子美"))
        assertTrue(aliasesInDb.contains("诗圣"))
    }

    // =========================================================================
    // 场景 6：多作者作品入库：主要作者与合著作者均正确关联入库
    // =========================================================================
    @Test
    fun testMultiAuthorWork_PrimaryAndCoAuthors() = runBlocking {
        val item = WorkImportItem(
            title = "联句古诗",
            author = AuthorImportItem("韩愈"),
            coAuthors = listOf(AuthorImportItem("孟郊")),
            content = "城南有幽居，开门对春竹。"
        )

        val result = importer.importBatch("BATCH_MULTI_AUTHORS", "联句集", listOf(item))
        assertTrue(result.isSuccessful)
        assertEquals(1, result.importedCount)

        val work = db.workDao().getWorksByTitle("联句古诗").first()
        val version = db.workDao().getVersionsByWorkId(work.workId).first()
        val workAuthors = db.workDao().getVersionAuthors(version.workVersionId)
        assertEquals(2, workAuthors.size)

        val primary = workAuthors.first { it.authorOrder == 1 }
        assertEquals("PRIMARY", primary.authorRole)

        val coAuthor = workAuthors.first { it.authorOrder == 2 }
        assertEquals("CO_AUTHOR", coAuthor.authorRole)
    }

    // =========================================================================
    // 场景 7：作者生平隔离：作者生平独立保存在 authors 表，不混入作品内容组件
    // =========================================================================
    @Test
    fun testAuthorBiographyIsolation_SavedInAuthorOnly() = runBlocking {
        val bioText = "王维（701年－761年），字摩诘，号摩诘居士，唐朝著名诗人、画家，官至尚书右丞，世称王右丞。"
        val item = WorkImportItem(
            title = "鹿柴",
            author = AuthorImportItem(
                name = "王维",
                biography = bioText,
                biographySource = "旧唐书·王维传"
            ),
            content = "空山不见人，但闻人语响。\n返景入深林，复照青苔上。"
        )

        val result = importer.importBatch("BATCH_BIO_ISOLATION", "王摩诘诗集", listOf(item))
        assertTrue(result.isSuccessful)

        // 验证生平安全保存在 authors 表
        val author = db.categoryDao().findAuthorByNameOrAlias("王维")
        assertNotNull(author)
        assertEquals(bioText, author?.biography)
        assertEquals("旧唐书·王维传", author?.biographySource)

        // 验证作品版本的内容组件中绝对不包含作者生平
        val work = db.workDao().getWorksByTitle("鹿柴").first()
        val version = db.workDao().getVersionsByWorkId(work.workId).first()
        val components = db.workDao().getComponentsByVersionId(version.workVersionId)

        assertEquals(1, components.size) // 仅 CONTENT
        assertFalse(components.any { it.contentPayload.contains("王右丞") })
    }

    // =========================================================================
    // 场景 8：结构化注释入库：正确序列化为 STRUCTURED_JSON 格式存储
    // =========================================================================
    @Test
    fun testStructuredAnnotationItems_SerializedAsStructuredJson() = runBlocking {
        val items = listOf(
            AnnotationItem(index = 1, quote = "但闻", note = "只听见", phonetic = "dàn wén", targetLine = 2),
            AnnotationItem(index = 2, quote = "返景", note = "落日返照之光", phonetic = "fǎn yǐng", targetLine = 3)
        )
        val item = WorkImportItem(
            title = "鹿柴注释本",
            author = AuthorImportItem("王维"),
            content = "空山不见人，但闻人语响。\n返景入深林，复照青苔上。",
            annotationItems = items
        )

        val result = importer.importBatch("BATCH_ANNOT_JSON", "王摩诘笺注", listOf(item))
        assertTrue(result.isSuccessful)

        val work = db.workDao().getWorksByTitle("鹿柴注释本").first()
        val version = db.workDao().getVersionsByWorkId(work.workId).first()
        val components = db.workDao().getComponentsByVersionId(version.workVersionId)
        val annotComp = components.first { it.componentType == ComponentType.ANNOTATION.code }

        assertEquals("STRUCTURED_JSON", annotComp.rawFormat)
        assertTrue(annotComp.contentPayload.contains("落日返照之光"))
        assertTrue(annotComp.contentPayload.contains("dàn wén"))
    }

    // =========================================================================
    // 场景 9：纯文本注释入库：正确以 PLAIN_TEXT 格式存储
    // =========================================================================
    @Test
    fun testPlainTextAnnotations_SavedAsPlainText() = runBlocking {
        val plainNote = "但闻：只听见。\n返景：日光也。"
        val item = WorkImportItem(
            title = "鹿柴文本本",
            author = AuthorImportItem("王维"),
            content = "空山不见人，但闻人语响。\n返景入深林，复照青苔上。",
            annotations = plainNote
        )

        importer.importBatch("BATCH_ANNOT_TEXT", "文本测试", listOf(item))

        val work = db.workDao().getWorksByTitle("鹿柴文本本").first()
        val version = db.workDao().getVersionsByWorkId(work.workId).first()
        val components = db.workDao().getComponentsByVersionId(version.workVersionId)
        val annotComp = components.first { it.componentType == ComponentType.ANNOTATION.code }

        assertEquals("PLAIN_TEXT", annotComp.rawFormat)
        assertEquals(plainNote, annotComp.contentPayload)
    }

    // =========================================================================
    // 场景 10：完全重复作品识别与安全跳过 (CAN_SKIP)，不重复写库
    // =========================================================================
    @Test
    fun testDuplicateWork_SkippedWithoutDuplication() = runBlocking {
        val item = WorkImportItem(
            title = "春晓",
            author = AuthorImportItem("孟浩然"),
            content = "春眠不觉晓，处处闻啼鸟。\n夜来风雨声，花落知多少。"
        )

        // 第一次导入成功
        val res1 = importer.importBatch("BATCH_DUP_1", "全唐诗", listOf(item))
        assertEquals(1, res1.importedCount)
        assertEquals("IMPORTED", res1.batchStatus)
        assertEquals(1, db.workDao().getWorkCount())

        // 第二次导入同一作品：全部重复且安全跳过，状态为 IMPORTED，但新增数量为 0
        val res2 = importer.importBatch("BATCH_DUP_2", "全唐诗重跑", listOf(item))
        assertEquals(0, res2.importedCount)
        assertEquals(1, res2.skippedCount)
        assertEquals(PrecheckDisposition.CAN_SKIP, res2.items[0].disposition)
        assertEquals("IMPORTED", res2.batchStatus)

        // 验证数据库批次表状态也为 IMPORTED
        val batch2 = db.releaseDao().getBatchByCode("BATCH_DUP_2")
        assertEquals("IMPORTED", batch2?.status)

        // 数据库总作品数严格保持为 1
        assertEquals(1, db.workDao().getWorkCount())
    }

    // =========================================================================
    // 场景 11：正文相同但辅助内容新增或变更：标记待确认 (NEEDS_CONFIRMATION)，批次为 PARTIAL
    // =========================================================================
    @Test
    fun testDuplicateWork_DifferentAuxiliary_FlagsNeedsConfirmation() = runBlocking {
        val baseItem = WorkImportItem(
            title = "春晓",
            author = AuthorImportItem("孟浩然"),
            content = "春眠不觉晓，处处闻啼鸟。\n夜来风雨声，花落知多少。"
        )
        importer.importBatch("BATCH_BASE", "首批录入", listOf(baseItem))

        // 第二批录入带有新增译文
        val newItem = WorkImportItem(
            title = "春晓",
            author = AuthorImportItem("孟浩然"),
            content = "春眠不觉晓，处处闻啼鸟。\n夜来风雨声，花落知多少。",
            translation = "春日里贪睡不知不觉天已破晓，到处都可以听到清脆的鸟鸣。"
        )

        val result = importer.importBatch("BATCH_NEW_TRANSLATION", "译文补充", listOf(newItem))
        assertEquals(0, result.importedCount)
        assertEquals(1, result.needsConfirmationCount)
        assertEquals(PrecheckDisposition.NEEDS_CONFIRMATION, result.items[0].disposition)
        assertTrue(result.items[0].message.contains("辅助内容"))
        assertEquals("PARTIAL", result.batchStatus)

        val batch = db.releaseDao().getBatchByCode("BATCH_NEW_TRANSLATION")
        assertEquals("PARTIAL", batch?.status)

        // 数据库作品数依然为 1，无擅自覆盖
        assertEquals(1, db.workDao().getWorkCount())
    }

    // =========================================================================
    // 场景 12：同名同作者异文分歧：标记待确认 (NEEDS_CONFIRMATION)，保留差异
    // =========================================================================
    @Test
    fun testDuplicateWork_VariantText_FlagsNeedsConfirmation() = runBlocking {
        val item1 = WorkImportItem(
            title = "静夜思",
            author = AuthorImportItem("李白"),
            content = "床前看月光，疑是地上霜。\n举头望山月，低头思故乡。"
        )
        importer.importBatch("BATCH_JYS_1", "唐本", listOf(item1))

        val item2 = WorkImportItem(
            title = "静夜思",
            author = AuthorImportItem("李白"),
            content = "床前明月光，疑是地上霜。\n举头望明月，低头思故乡。"
        )

        val result = importer.importBatch("BATCH_JYS_2", "宋本", listOf(item2))
        assertEquals(0, result.importedCount)
        assertEquals(1, result.needsConfirmationCount)
        assertEquals(PrecheckDisposition.NEEDS_CONFIRMATION, result.items[0].disposition)
        assertTrue(result.items[0].message.contains("异文分歧"))
        assertEquals("PARTIAL", result.batchStatus)

        val batch = db.releaseDao().getBatchByCode("BATCH_JYS_2")
        assertEquals("PARTIAL", batch?.status)
    }

    // =========================================================================
    // 场景 13：同名不同作者：各自作为独立作品完整入库
    // =========================================================================
    @Test
    fun testSameTitleDifferentAuthor_BothImported() = runBlocking {
        val libai = WorkImportItem(
            title = "塞下曲",
            author = AuthorImportItem("李白"),
            content = "五月天山雪，无花只有寒。"
        )
        val lulun = WorkImportItem(
            title = "塞下曲",
            author = AuthorImportItem("卢纶"),
            content = "月黑雁飞高，单于夜遁逃。\n欲将轻骑逐，大雪满弓刀。"
        )

        val res1 = importer.importBatch("BATCH_SXQ_1", "李白集", listOf(libai))
        val res2 = importer.importBatch("BATCH_SXQ_2", "卢纶集", listOf(lulun))

        assertEquals(1, res1.importedCount)
        assertEquals(1, res2.importedCount)
        assertEquals("IMPORTED", res1.batchStatus)
        assertEquals("IMPORTED", res2.batchStatus)

        // 数据库中存在 2 篇同名但属于不同作者的作品
        assertEquals(2, db.workDao().getWorkCount())
        val works = db.workDao().getWorksByTitle("塞下曲")
        assertEquals(2, works.size)
    }

    // =========================================================================
    // 场景 14：校验失败数据被拦截，整批均失败时批次标记为 FAILED，不留 IMPORTING
    // =========================================================================
    @Test
    fun testValidationFailure_NotWritten() = runBlocking {
        val invalidItem = WorkImportItem(
            title = "", // 标题为空违规
            content = "日出江花红胜火，春来江水绿如蓝。"
        )

        val result = importer.importBatch("BATCH_INVALID", "测试", listOf(invalidItem))
        assertFalse(result.isSuccessful)
        assertEquals(0, result.importedCount)
        assertEquals(1, result.failedCount)
        assertEquals(PrecheckDisposition.VALIDATION_FAILED, result.items[0].disposition)
        assertEquals("FAILED", result.batchStatus)

        // 验证数据库批次表状态更新为 FAILED，绝不遗留 IMPORTING
        val batch = db.releaseDao().getBatchByCode("BATCH_INVALID")
        assertNotNull(batch)
        assertEquals("FAILED", batch?.status)

        assertEquals(0, db.workDao().getWorkCount())
    }

    // =========================================================================
    // 场景 15：混合批次准确定位，仅导入 READY 项，批次状态为 PARTIAL
    // =========================================================================
    @Test
    fun testMixedBatch_OnlyReadyItemsImported() = runBlocking {
        // 先播种作品 A
        val seededWork = WorkImportItem(
            title = "相思",
            author = AuthorImportItem("王维"),
            content = "红豆生南国，春来发几枝。\n愿君多采撷，此物最相思。"
        )
        importer.importBatch("BATCH_SEED", "前置底本", listOf(seededWork))

        val items = listOf(
            // 0: 正常新作品 -> 可导入
            WorkImportItem(
                title = "九月九日忆山东兄弟",
                author = AuthorImportItem("王维"),
                content = "独在异乡为异客，每逢佳节倍思亲。\n遥知兄弟登高处，遍插茱萸少一人。"
            ),
            // 1: 格式损坏 -> 校验失败
            WorkImportItem(
                title = "",
                content = "白日依山尽，黄河入海流。"
            ),
            // 2: 完全重复 -> 可跳过
            WorkImportItem(
                title = "相思",
                author = AuthorImportItem("王维"),
                content = "红豆生南国，春来发几枝。\n愿君多采撷，此物最相思。"
            ),
            // 3: 异文存疑 -> 待确认
            WorkImportItem(
                title = "相思",
                author = AuthorImportItem("王维"),
                content = "红豆生南国，秋来发几枝。\n愿君多采撷，此物最相思。"
            )
        )

        val result = importer.importBatch("BATCH_MIXED_001", "混合导入测试", items)

        assertEquals(4, result.totalInputCount)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.failedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(1, result.needsConfirmationCount)
        // 存在失败与存疑条目，批次状态标记为 PARTIAL
        assertEquals("PARTIAL", result.batchStatus)

        val batch = db.releaseDao().getBatchByCode("BATCH_MIXED_001")
        assertEquals("PARTIAL", batch?.status)

        // 数据库总作品数：1 (seeded) + 1 (新作品) = 2
        assertEquals(2, db.workDao().getWorkCount())
        val newlyImported = db.workDao().getWorksByTitle("九月九日忆山东兄弟")
        assertEquals(1, newlyImported.size)
    }

    // =========================================================================
    // 场景 16：事务回滚保护：发生写入异常时作品数据完全回滚（0 脏数据），data_batches 标记为 FAILED
    // =========================================================================
    @Test
    fun testAtomicRollbackOnFailure_ZeroDirtyData() = runBlocking {
        // 配置模拟注入异常（在事务提交前抛出致命错误）
        importer.onBeforeTransactionCommit = {
            throw IllegalStateException("模拟磁盘空间写满或外键损坏导致的事务灾难")
        }

        val item1 = WorkImportItem(title = "作品甲", content = "天地玄黄，宇宙洪荒。")
        val item2 = WorkImportItem(title = "作品乙", content = "日月盈昃，辰宿列张。")

        val result = importer.importBatch(
            batchCode = "BATCH_CRASH_TEST",
            batchSource = "崩溃恢复测试",
            items = listOf(item1, item2)
        )

        assertFalse(result.isSuccessful)
        assertEquals(0, result.importedCount)
        assertEquals("FAILED", result.batchStatus)
        assertTrue(result.errorMessage?.contains("已全部安全回滚") == true)

        // 验证数据库作品核心表被完全回滚，保持绝对纯净（0 脏数据）
        assertEquals(0, db.workDao().getWorkCount())

        // 验证 data_batches 批次管理表在事务外被正确更新为 FAILED，保证失败历史可追踪
        val batch = db.releaseDao().getBatchByCode("BATCH_CRASH_TEST")
        assertNotNull(batch)
        assertEquals("FAILED", batch?.status)
    }

    // =========================================================================
    // 场景 17：正式发布包映射：传入 releaseId 时仅写入发布条目，不自动变更正式发布指针
    // =========================================================================
    @Test
    fun testReleaseIdBinding_WorkEntriesCreated() = runBlocking {
        // 先在数据库中建立发布包主表记录
        val batchId = "batch_release_seed"
        db.releaseDao().insertBatch(
            DataBatchEntity(batchId, "BATCH_REL_001", "发布包初始化", 1, "IMPORTED", 1000L)
        )
        val releaseId = "rel_official_v1_0"
        db.releaseDao().insertRelease(
            DataReleaseEntity(
                releaseId = releaseId,
                releaseCode = "REL_2026_V1",
                batchId = batchId,
                releaseType = "FULL",
                publishedAtEpochMs = 1000L
            )
        )

        val item = WorkImportItem(
            title = "早发白帝城",
            author = AuthorImportItem("李白"),
            content = "朝辞白帝彩云间，千里江陵一日还。\n两岸猿声啼不住，轻舟已过万重山。"
        )

        val result = importer.importBatch(
            batchCode = "BATCH_REL_WORKS",
            batchSource = "正式包增补",
            items = listOf(item),
            releaseId = releaseId
        )

        assertTrue(result.isSuccessful)
        assertEquals(1, result.importedCount)

        // 关键断言：导入后正式发布指针未被自动修改（仍为空）
        val pointerBefore = db.releaseDao().getReleasePointer()
        assertNull(pointerBefore)

        // 只有显式执行正式发布流程更新指针后，新发布包的作品才激活
        db.releaseDao().setReleasePointer(
            com.shi.yun.data.local.entity.AppReleasePointerEntity(
                currentReleaseId = releaseId,
                activatedAtEpochMs = 1000L
            )
        )

        val activeWorks = db.releaseDao().getActiveReleaseWorks()
        assertEquals(1, activeWorks.size)
        assertEquals("早发白帝城", activeWorks[0].standardTitle)
    }

    // =========================================================================
    // 场景 18：批次生命周期状态：批次刚创建时状态严格为 IMPORTING
    // =========================================================================
    @Test
    fun testBatchStatus_InitialImporting() = runBlocking {
        var observedStatusAtCreation: String? = null
        importer.onAfterBatchCreated = { batchId ->
            observedStatusAtCreation = db.releaseDao().getBatchById(batchId)?.status
        }

        val item = WorkImportItem(
            title = "春夜喜雨",
            author = AuthorImportItem("杜甫"),
            content = "好雨知时节，当春乃发生。\n随风潜入夜，润物细无声。"
        )

        val result = importer.importBatch("BATCH_INITIAL_TEST", "初始状态测试", listOf(item))
        assertTrue(result.isSuccessful)

        // 验证创建那一刻在数据库中的状态严格为 IMPORTING
        assertEquals("IMPORTING", observedStatusAtCreation)
        // 最终成功后转换为 IMPORTED
        assertEquals("IMPORTED", result.batchStatus)
        assertEquals("IMPORTED", db.releaseDao().getBatchByCode("BATCH_INITIAL_TEST")?.status)
    }

    // =========================================================================
    // 场景 19：全部重复且安全跳过时批次状态为 IMPORTED，新增作品数严格为 0
    // =========================================================================
    @Test
    fun testBatchStatus_AllSkipped_ImportedWithZeroAdded() = runBlocking {
        val item = WorkImportItem(
            title = "枫桥夜泊",
            author = AuthorImportItem("张继"),
            content = "月落乌啼霜满天，江枫渔火对愁眠。\n姑苏城外寒山寺，夜半钟声到客船。"
        )
        // 首次导入
        importer.importBatch("BATCH_FQYB_1", "原版导入", listOf(item))
        assertEquals(1, db.workDao().getWorkCount())

        // 再次导入完全相同内容
        val result = importer.importBatch("BATCH_FQYB_2", "全跳过导入", listOf(item))

        // 验证：批次状态为 IMPORTED，新增作品严格为 0，跳过数为 1
        assertEquals("IMPORTED", result.batchStatus)
        assertEquals(0, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals("IMPORTED", db.releaseDao().getBatchByCode("BATCH_FQYB_2")?.status)
        assertEquals(1, db.workDao().getWorkCount())
    }

    // =========================================================================
    // 场景 20：所有提前返回路径覆盖：绝无遗留 IMPORTING
    // =========================================================================
    @Test
    fun testBatchStatus_NoEarlyReturnLeavesImporting() = runBlocking {
        // 路径 1：整批数据校验失败 -> 提前返回 -> FAILED
        val failResult = importer.importBatch(
            "BATCH_EARLY_FAIL",
            "校验失败提前返回",
            listOf(WorkImportItem(title = "", content = "无名无姓"))
        )
        assertEquals("FAILED", failResult.batchStatus)
        assertEquals("FAILED", db.releaseDao().getBatchByCode("BATCH_EARLY_FAIL")?.status)

        // 路径 2：整批数据完全重复 -> 提前返回 -> IMPORTED
        val workItem = WorkImportItem(title = "登高", author = AuthorImportItem("杜甫"), content = "风急天高猿啸哀，渚清沙白鸟飞回。")
        importer.importBatch("BATCH_DG_SEED", "底本", listOf(workItem))

        val skipResult = importer.importBatch("BATCH_EARLY_SKIP", "完全重复提前返回", listOf(workItem))
        assertEquals("IMPORTED", skipResult.batchStatus)
        assertEquals("IMPORTED", db.releaseDao().getBatchByCode("BATCH_EARLY_SKIP")?.status)

        // 路径 3：整批无新增，包含异文待确认 -> 提前返回 -> PARTIAL
        val variantItem = WorkImportItem(title = "登高", author = AuthorImportItem("杜甫"), content = "风急天高猿叫哀，渚清沙白鸟飞回。")
        val partialResult = importer.importBatch("BATCH_EARLY_PARTIAL", "异文提前返回", listOf(variantItem))
        assertEquals("PARTIAL", partialResult.batchStatus)
        assertEquals("PARTIAL", db.releaseDao().getBatchByCode("BATCH_EARLY_PARTIAL")?.status)
    }
}
