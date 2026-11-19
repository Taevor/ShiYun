package com.shi.yun.data.local.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.entity.ComponentType
import com.shi.yun.data.local.entity.DataBatchEntity
import com.shi.yun.data.local.entity.DataReleaseEntity
import com.shi.yun.data.local.importer.fixture.SampleWorksData
import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.WorkImportItem
import com.shi.yun.data.local.repository.RoomWorkRepository
import com.shi.yun.data.model.Genre
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
 * 阶段 3 步骤 5.3 批量导入完整链路验证端到端测试 (BatchWorkImporterEndToEndTest)
 *
 * 验证目标：
 * 使用 20 篇真实、权威的古典文学样本数据，对导入数据准备、预检、查重、批量写入、
 * 来源追溯到现有 Repository 读取的完整闭环链路进行全景端到端验证。
 *
 * 核心验证范围：
 * 1. 20 篇真实样本首轮全量导入 (Round 1) 成功，36 表核心表关联完备，RoomWorkRepository 正常组装读取；
 * 2. 20 篇真实样本次轮重复导入 (Round 2) 全量精准识别安全跳过 (CAN_SKIP)，0 新增作品，批次状态为 IMPORTED；
 * 3. 辅助内容变更（译文、注释、背景、赏析变动）准确触发待确认 (NEEDS_CONFIRMATION)，批次状态为 PARTIAL；
 * 4. 作者别名（如苏东坡）跨批次精准关联与复用已有作者实体；
 * 5. 部分预检失败与全部预检失败的批次状态流转（PARTIAL 与 FAILED，绝不遗留 IMPORTING）；
 * 6. 事务中途异常回滚与 0 脏数据防护，批次外层状态标记为 FAILED；
 * 7. 正式发布包映射边界防护：传入 releaseId 时仅写映射，不自动篡改正式发布指针。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatchWorkImporterEndToEndTest {

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
    // 验证场景 1：20 篇真实样本首轮全链路导入与 Repository 聚合读取 (Round 1)
    // =========================================================================
    @Test
    fun testRound1_FullPipeline_TwentyRealSamples_ImportAndRepositoryRead() = runBlocking {
        val twentySamples = SampleWorksData.getTwentySampleWorks()
        assertEquals(20, twentySamples.size)

        // 1. 执行首轮批量导入
        val result = importer.importBatch(
            batchCode = "E2E_BATCH_001",
            batchSource = "经典古诗文20篇权威样本库",
            items = twentySamples
        )

        // 2. 验证批次导入汇总指标
        assertTrue("首轮批量导入必须整体成功", result.isSuccessful)
        assertEquals("批次状态必须为 IMPORTED", "IMPORTED", result.batchStatus)
        assertEquals("总条目数必须为 20", 20, result.totalInputCount)
        assertEquals("导入成功作品数必须为 20", 20, result.importedCount)
        assertEquals("跳过数必须为 0", 0, result.skippedCount)
        assertEquals("失败数必须为 0", 0, result.failedCount)
        assertEquals("待确认数必须为 0", 0, result.needsConfirmationCount)
        assertEquals("明细列表长度必须为 20", 20, result.items.size)
        assertTrue("所有明细条目必须成功", result.items.all { it.isSuccess })

        // 3. 验证数据库实体底层完整性
        val workDao = db.workDao()
        val categoryDao = db.categoryDao()
        val totalWorks = workDao.getWorkCount()
        assertEquals("works 表记录数必须严格为 20", 20, totalWorks)

        // (1) 作者表去重与未知作者验证：
        // 20 篇样本中：4 篇无作者（关雎、江南、涉江采芙蓉、越人歌），
        // 16 篇有作者：曹操、陶渊明、李白(x2)、杜甫(x2)、柳宗元、李商隐、李煜、李清照、苏轼(x2)、辛弃疾、马致远、张养浩、郑板桥
        // 去重后真实作者数严格为 13
        val distinctAuthors = categoryDao.getAllAuthors()
        assertEquals("去重后的真实作者实体数必须严格为 13", 13, distinctAuthors.size)

        // 严禁生成任何匿名或佚名作者记录
        val anonAuthors = distinctAuthors.filter {
            it.standardName == "佚名" || it.authorId.contains("anon") || it.authorId == "author_anon"
        }
        assertTrue("绝对不能创建名为'佚名'或ID含'anon'的伪作者实体", anonAuthors.isEmpty())

        // 验证 4 篇未知作者作品在 work_authors 中无任何关联
        val allWorkEntities = workDao.getAllActiveWorks()
        val anonTitles = setOf("关雎", "江南", "涉江采芙蓉", "越人歌")
        for (work in allWorkEntities.filter { it.standardTitle in anonTitles }) {
            val versions = workDao.getVersionsByWorkId(work.workId)
            val version = versions.first()
            val authors = workDao.getVersionAuthors(version.workVersionId)
            assertTrue("未知作者作品 [${work.standardTitle}] 的 work_authors 关联必须为空", authors.isEmpty())
        }

        // (2) 同作者多篇作品 ID 复用验证
        val libaiAuthors = distinctAuthors.filter { it.standardName == "李白" }
        assertEquals("李白在 authors 表中必须只有一条记录", 1, libaiAuthors.size)
        val libaiId = libaiAuthors.first().authorId

        val libaiWorks = allWorkEntities.filter { it.standardTitle in setOf("静夜思", "望庐山瀑布") }
        assertEquals("李白的名作必须有 2 篇入库", 2, libaiWorks.size)
        for (work in libaiWorks) {
            val versions = workDao.getVersionsByWorkId(work.workId)
            val authors = workDao.getVersionAuthors(versions.first().workVersionId)
            assertEquals("作品 [${work.standardTitle}] 必须关联李白", 1, authors.size)
            assertEquals("作品 [${work.standardTitle}] 关联的作者 ID 必须一致", libaiId, authors.first().authorId)
        }

        // 同样验证杜甫与苏轼的 ID 复用
        val dufuId = distinctAuthors.first { it.standardName == "杜甫" }.authorId
        val dufuWorks = allWorkEntities.filter { it.standardTitle in setOf("春望", "登高") }
        assertEquals(2, dufuWorks.size)
        assertTrue(dufuWorks.all {
            val vId = workDao.getVersionsByWorkId(it.workId).first().workVersionId
            workDao.getVersionAuthors(vId).first().authorId == dufuId
        })

        val sushiId = distinctAuthors.first { it.standardName == "苏轼" }.authorId
        val sushiWorks = allWorkEntities.filter { it.standardTitle in setOf("水调歌头·明月几时有", "如梦令·春思") }
        assertEquals(2, sushiWorks.size)
        assertTrue(sushiWorks.all {
            val vId = workDao.getVersionsByWorkId(it.workId).first().workVersionId
            workDao.getVersionAuthors(vId).first().authorId == sushiId
        })

        // (3) 五维内容组件完备性验证
        // 《关雎》与《春望》具备全部五维内容
        val guanjuWork = allWorkEntities.first { it.standardTitle == "关雎" }
        val guanjuVersion = workDao.getVersionsByWorkId(guanjuWork.workId).first()
        val guanjuComponents = workDao.getComponentsByVersionId(guanjuVersion.workVersionId)
        val guanjuCompTypes = guanjuComponents.map { it.componentType }.toSet()
        assertTrue(
            "《关雎》必须具备五维内容组件",
            guanjuCompTypes.containsAll(
                listOf(
                    ComponentType.CONTENT.name,
                    ComponentType.TRANSLATION.name,
                    ComponentType.ANNOTATION.name,
                    ComponentType.BACKGROUND.name,
                    ComponentType.APPRECIATION.name
                )
            )
        )
        // 验证结构化注释存储格式为 STRUCTURED_JSON
        val guanjuAnnotationComp = guanjuComponents.first { it.componentType == ComponentType.ANNOTATION.name }
        assertEquals("STRUCTURED_JSON", guanjuAnnotationComp.rawFormat)
        assertTrue(guanjuAnnotationComp.contentPayload.contains("关关"))

        // 《望庐山瀑布》的普通文本注释格式为 PLAIN_TEXT
        val wlspbWork = allWorkEntities.first { it.standardTitle == "望庐山瀑布" }
        val wlspbVersion = workDao.getVersionsByWorkId(wlspbWork.workId).first()
        val wlspbComponents = workDao.getComponentsByVersionId(wlspbVersion.workVersionId)
        val wlspbAnnotationComp = wlspbComponents.first { it.componentType == ComponentType.ANNOTATION.name }
        assertEquals("PLAIN_TEXT", wlspbAnnotationComp.rawFormat)

        // (4) 来源追溯验证
        val guanjuSourceLinks = workDao.getSourceLinksByWorkId(guanjuWork.workId)
        assertTrue("《关雎》必须具备外部来源链接记录", guanjuSourceLinks.isNotEmpty())

        // 4. 关键闭环：使用现有 RoomWorkRepository 读取并组装所有作品
        val repository = RoomWorkRepository(db)
        val repoWorks = repository.getAllWorks()
        assertEquals("Repository 读取出来的作品总数必须为 20", 20, repoWorks.size)

        // 验证未知作者作品在 Repository 中的表达（author 为空字符串）
        val repoGuanju = repoWorks.first { it.title == "关雎" }
        assertEquals("未知作者作品的 author 必须为空字符串", "", repoGuanju.author)
        assertEquals("先秦", repoGuanju.dynasty)
        assertNotNull(repoGuanju.auxiliary?.translation)
        assertNotNull(repoGuanju.auxiliary?.annotations)
        assertNotNull(repoGuanju.auxiliary?.background)
        assertNotNull(repoGuanju.auxiliary?.appreciation)

        // 验证已知作者作品在 Repository 中的组装
        val repoLibai = repoWorks.first { it.title == "望庐山瀑布" }
        assertEquals("李白", repoLibai.author)
        assertEquals("唐", repoLibai.dynasty)
        assertEquals(Genre.POEM, repoLibai.genre)
        assertTrue(repoLibai.content.contains("飞流直下三千尺"))

        // 验证朝代聚合读取
        val tangWorks = repository.getWorksByDynasty("唐")
        // 20 篇中唐代作品 7 篇：望庐山瀑布、静夜思、春望、登高、相思、春晓、赋得古原草送别
        assertEquals("唐代作品必须有 7 篇", 7, tangWorks.size)

        // 验证体裁聚合读取
        val ciWorks = repository.getWorksByGenre("ci")
        // 20 篇中词作 4 篇：虞美人、如梦令、水调歌头、青玉案
        assertEquals("词作品必须有 4 篇", 4, ciWorks.size)

        val quWorks = repository.getWorksByGenre("qu")
        // 20 篇中曲作 2 篇：天净沙·秋思、山坡羊·潼关怀古
        assertEquals("散曲作品必须有 2 篇", 2, quWorks.size)

        // 验证搜索能力
        val searchDufu = repository.searchWorks("杜甫")
        assertEquals("搜索杜甫必须返回 2 篇作品", 2, searchDufu.size)
        assertTrue(searchDufu.any { it.title == "春望" })
        assertTrue(searchDufu.any { it.title == "登高" })

        // 验证分类未知作品（越人歌）在 Repository 中的平稳读取
        val repoYuerenge = repoWorks.first { it.title == "越人歌" }
        assertNotNull(repoYuerenge)
        assertEquals("", repoYuerenge.author)
        assertEquals("", repoYuerenge.dynasty)
        assertTrue(repoYuerenge.content.contains("山有木兮木有枝"))
    }

    // =========================================================================
    // 验证场景 2：20 篇样本同库二次重复导入查重与全量安全跳过 (Round 2)
    // =========================================================================
    @Test
    fun testRound2_Deduplication_AllTwentyWorksSkipped() = runBlocking {
        val twentySamples = SampleWorksData.getTwentySampleWorks()

        // 1. 先执行首轮导入
        val round1Result = importer.importBatch(
            batchCode = "E2E_BATCH_R1",
            batchSource = "权威样本库_第1轮",
            items = twentySamples
        )
        assertTrue(round1Result.isSuccessful)
        assertEquals(20, round1Result.importedCount)
        assertEquals(20, db.workDao().getWorkCount())

        val cursor1 = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM content_components")
        cursor1.moveToFirst()
        val initialComponentCount = cursor1.getInt(0)
        cursor1.close()

        // 2. 在同一数据库中执行次轮全量重复导入
        val round2Result = importer.importBatch(
            batchCode = "E2E_BATCH_R2",
            batchSource = "权威样本库_第2轮重复",
            items = twentySamples
        )

        // 3. 验证查重与跳过行为
        assertTrue("次轮全量重复导入必须成功返回", round2Result.isSuccessful)
        assertEquals("全部重复跳过时批次状态必须为 IMPORTED", "IMPORTED", round2Result.batchStatus)
        assertEquals("次轮总条目数必须为 20", 20, round2Result.totalInputCount)
        assertEquals("次轮新增导入数必须严格为 0", 0, round2Result.importedCount)
        assertEquals("次轮安全跳过数必须严格为 20", 20, round2Result.skippedCount)
        assertEquals("次轮失败数必须为 0", 0, round2Result.failedCount)
        assertEquals("次轮待确认数必须为 0", 0, round2Result.needsConfirmationCount)

        // 4. 验证数据库底表无任何冗余膨胀
        assertEquals("works 表记录数保持为 20，绝不产生重复记录", 20, db.workDao().getWorkCount())
        assertEquals("authors 表记录数保持为 13", 13, db.categoryDao().getAllAuthors().size)

        val cursor2 = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM content_components")
        cursor2.moveToFirst()
        val round2ComponentCount = cursor2.getInt(0)
        cursor2.close()
        assertEquals("content_components 表组件数保持不变", initialComponentCount, round2ComponentCount)

        // 5. 验证两个批次在 data_batches 表中均记录完好
        val releaseDao = db.releaseDao()
        val batchR1 = releaseDao.getBatchByCode("E2E_BATCH_R1")
        val batchR2 = releaseDao.getBatchByCode("E2E_BATCH_R2")
        assertNotNull(batchR1)
        assertNotNull(batchR2)
        assertEquals("IMPORTED", batchR1?.status)
        assertEquals("IMPORTED", batchR2?.status)
    }

    // =========================================================================
    // 验证场景 3：辅助内容变更（译文/注释/背景/赏析变动）准确触发 NEEDS_CONFIRMATION
    // =========================================================================
    @Test
    fun testAuxiliaryVariations_TranslationAnnotationBackgroundAppreciation() = runBlocking {
        // 先导入基线样本库
        importer.importBatch("E2E_BASE_BATCH", "基线样本库", SampleWorksData.getTwentySampleWorks())

        // 构造四种不同辅助维度变更的待导入项（均基于 20 篇样本库中已有的名篇）
        val variationItems = listOf(
            // 变体 1：正文与作者一致，但译文变更（春望）
            WorkImportItem(
                title = "春望",
                author = AuthorImportItem("杜甫"),
                content = "国破山河在，城春草木深。\n感时花溅泪，恨别鸟惊心。\n烽火连三月，家书抵万金。\n白头搔更短，浑欲不胜簪。",
                translation = "【新版本译文】国家残破仅剩山河，春天长安城草木荒芜。"
            ),
            // 变体 2：正文与作者一致，但新增注释（登高）
            WorkImportItem(
                title = "登高",
                author = AuthorImportItem("杜甫"),
                content = "风急天高猿啸哀，渚清沙白鸟飞回。\n无边落木萧萧下，不尽长江滚滚来。\n万里悲秋常作客，百年多病独登台。\n艰难苦恨繁霜鬓，潦倒新停浊酒杯。",
                annotations = "萧萧：树木落叶声。"
            ),
            // 变体 3：正文与作者一致，但创作背景变更（水调歌头·明月几时有）
            WorkImportItem(
                title = "水调歌头·明月几时有",
                author = AuthorImportItem("苏轼"),
                content = "明月几时有？把酒问青天。\n不知天上宫阙，今夕是何年。\n我欲乘风归去，又恐琼楼玉宇，高处不胜寒。\n起舞弄清影，何似在人间。\n\n转朱阁，低绮户，照无眠。\n不应有恨，何事长向别时圆？\n人有悲欢离合，月有阴晴圆缺，此事古难全。\n但愿人长久，千里共婵娟。",
                background = "【新编考订创作背景】熙宁九年苏轼在密州，因思念苏辙并对人生哲理有所感悟而作。"
            ),
            // 变体 4：正文与作者一致，但文学赏析变更（赋得古原草送别）
            WorkImportItem(
                title = "赋得古原草送别",
                author = AuthorImportItem("白居易"),
                content = "离离原上草，一岁一枯荣。\n野火烧不尽，春风吹又生。\n远芳侵古道，晴翠接荒城。\n又送王孙去，萋萋满别情。",
                appreciation = "【新编文学赏析】全诗借古原野草借物喻理，表现出蓬勃昂扬的生命力量。"
            )
        )

        // 执行导入变体批次
        val result = importer.importBatch(
            batchCode = "E2E_VARIATION_BATCH",
            batchSource = "辅助内容变体测试",
            items = variationItems
        )

        // 验证全部 4 篇变体均被准确识别为待确认 (NEEDS_CONFIRMATION)
        assertEquals("批次未产生新导入且包含待确认项时状态必须为 PARTIAL", "PARTIAL", result.batchStatus)
        assertEquals(4, result.totalInputCount)
        assertEquals("待确认条目数必须为 4", 4, result.needsConfirmationCount)
        assertEquals("安全跳过数必须为 0", 0, result.skippedCount)
        assertEquals("新增导入数必须为 0", 0, result.importedCount)
        assertTrue("所有变体条目均被标记为待人工确认", result.items.all {
            it.disposition == com.shi.yun.data.local.importer.model.PrecheckDisposition.NEEDS_CONFIRMATION
        })

        // 验证数据库作品总数依然为 20，未发生盲目覆盖或重复插入
        assertEquals(20, db.workDao().getWorkCount())
    }

    // =========================================================================
    // 验证场景 4：作者别名（苏东坡）跨批次精准关联与复用已有作者实体
    // =========================================================================
    @Test
    fun testAuthorAlias_DeduplicationMatching() = runBlocking {
        // 先导入 20 篇样本（其中包含苏轼，别名列表中已有“苏东坡”）
        importer.importBatch("E2E_SU_BATCH", "苏轼基线", SampleWorksData.getTwentySampleWorks())

        val categoryDao = db.categoryDao()
        val workDao = db.workDao()
        val authorsBefore = categoryDao.getAllAuthors()
        val sushiEntity = authorsBefore.first { it.standardName == "苏轼" }

        // 导入一篇新作品，作者名为其别名“苏东坡”，且未传别名列表
        val newWorkByDongpo = WorkImportItem(
            title = "定风波·莫听穿林打叶声",
            author = AuthorImportItem(name = "苏东坡", dynasty = "宋"),
            dynasty = "宋",
            genre = "ci",
            cipai = "定风波",
            content = "莫听穿林打叶声，何妨吟啸且徐行。竹杖芒鞋轻胜马，谁怕？一蓑烟雨任平生。\n料峭春风吹酒醒，微冷，山头斜照却相迎。回首向来萧瑟处，归去，也无风雨也无晴。"
        )

        val result = importer.importBatch(
            batchCode = "E2E_DONGPO_BATCH",
            batchSource = "东坡词增补",
            items = listOf(newWorkByDongpo)
        )

        assertTrue(result.isSuccessful)
        assertEquals(1, result.importedCount)

        // 关键断言：作者总数依然为 13，苏东坡被正确识别为苏轼的别名，复用了苏轼的 authorId
        val authorsAfter = categoryDao.getAllAuthors()
        assertEquals("复用已有作者实体后，作者总数必须依然为 13", 13, authorsAfter.size)

        // 验证新作品关联的作者 ID 与已有的苏轼 ID 完全一致
        val newWorkEntity = workDao.getAllActiveWorks().first { it.standardTitle == "定风波·莫听穿林打叶声" }
        val versions = workDao.getVersionsByWorkId(newWorkEntity.workId)
        val linkedAuthors = workDao.getVersionAuthors(versions.first().workVersionId)
        assertEquals(1, linkedAuthors.size)
        assertEquals("关联的作者 ID 必须与苏轼实体 ID 一致", sushiEntity.authorId, linkedAuthors.first().authorId)
    }

    // =========================================================================
    // 验证场景 5：部分预检失败与全部预检失败的批次状态流转
    // =========================================================================
    @Test
    fun testPrecheckFailures_PartialAndAllFailed() = runBlocking {
        // 1. 部分预检失败：1 篇有效作品 + 1 篇正文为空的无效作品
        val partialItems = listOf(
            WorkImportItem(
                title = "登乐游原",
                author = AuthorImportItem("李商隐"),
                content = "向晚意不适，驱车登古原。\n夕阳无限好，只是近黄昏。"
            ),
            WorkImportItem(
                title = "空内容残卷",
                author = AuthorImportItem("未知"),
                content = "   " // 正文空白，校验必须拦截
            )
        )

        val partialResult = importer.importBatch(
            batchCode = "E2E_PARTIAL_BATCH",
            batchSource = "部分失败测试批次",
            items = partialItems
        )

        assertTrue(partialResult.isSuccessful)
        assertEquals("部分失败批次状态必须为 PARTIAL", "PARTIAL", partialResult.batchStatus)
        assertEquals(1, partialResult.importedCount)
        assertEquals(1, partialResult.failedCount)

        // 2. 全部预检失败：所有作品均不合法
        val allFailedItems = listOf(
            WorkImportItem(title = "", content = "无标题内容"),
            WorkImportItem(title = "有标题但无内容", content = "")
        )

        val allFailedResult = importer.importBatch(
            batchCode = "E2E_ALL_FAILED_BATCH",
            batchSource = "全部失败测试批次",
            items = allFailedItems
        )

        assertFalse("全部失败批次整体标记为不成功", allFailedResult.isSuccessful)
        assertEquals("全部预检失败时批次状态必须更新为 FAILED", "FAILED", allFailedResult.batchStatus)
        assertEquals(0, allFailedResult.importedCount)
        assertEquals(2, allFailedResult.failedCount)

        // 验证 data_batches 批次表中状态严格为 FAILED，绝不遗留 IMPORTING
        val failedBatch = db.releaseDao().getBatchByCode("E2E_ALL_FAILED_BATCH")
        assertNotNull(failedBatch)
        assertEquals("FAILED", failedBatch?.status)
    }

    // =========================================================================
    // 验证场景 6：事务中途异常回滚与 0 脏数据防护
    // =========================================================================
    @Test
    fun testTransactionRollback_ZeroDirtyData() = runBlocking {
        val initialWorkCount = db.workDao().getWorkCount()
        assertEquals(0, initialWorkCount)

        // 注入事务提交前人为抛出异常的 Hook
        importer.onBeforeTransactionCommit = {
            throw java.sql.SQLException("模拟数据库写入中途严重 I/O 故障")
        }

        val testItems = listOf(
            WorkImportItem(
                title = "崩溃回滚测试作品",
                author = AuthorImportItem("测试诗人"),
                content = "这是一首用于测试事务安全回滚的作品正文。"
            )
        )

        val result = importer.importBatch(
            batchCode = "E2E_CRASH_BATCH",
            batchSource = "事务回滚异常测试",
            items = testItems
        )

        // 清理 Hook
        importer.onBeforeTransactionCommit = null

        // 验证执行结果
        assertFalse("事务异常时结果必须标记为失败", result.isSuccessful)
        assertEquals(0, result.importedCount)
        assertEquals("FAILED", result.batchStatus)
        assertTrue(result.errorMessage?.contains("已全部安全回滚") == true)

        // 核心验证：数据库作品表完全纯净，0 脏数据残留
        assertEquals("数据库作品数必须严格保持为 0", 0, db.workDao().getWorkCount())
        assertEquals("数据库作者数必须严格保持为 0", 0, db.categoryDao().getAllAuthors().size)

        // 验证外层批次记录在事务外被正确记录为 FAILED
        val batch = db.releaseDao().getBatchByCode("E2E_CRASH_BATCH")
        assertNotNull(batch)
        assertEquals("FAILED", batch?.status)
    }

    // =========================================================================
    // 验证场景 7：正式发布包映射边界防护（传入 releaseId 不动正式指针）
    // =========================================================================
    @Test
    fun testReleasePointerProtection() = runBlocking {
        // 先在正式发布表初始化一个发布包
        val batchId = "batch_seed_rel"
        db.releaseDao().insertBatch(
            DataBatchEntity(batchId, "BATCH_REL_SEED", "正式发布初始化", 1, "IMPORTED", 1000L)
        )
        val releaseId = "rel_official_v1_0"
        db.releaseDao().insertRelease(
            DataReleaseEntity(
                releaseId = releaseId,
                releaseCode = "REL_2026_OFFICIAL",
                batchId = batchId,
                releaseType = "FULL",
                publishedAtEpochMs = 1000L
            )
        )

        val item = WorkImportItem(
            title = "逢入京使",
            author = AuthorImportItem("岑参"),
            content = "故园东望路漫漫，双袖龙钟泪不干。\n马上相逢无纸笔，凭君传语报平安。"
        )

        val result = importer.importBatch(
            batchCode = "E2E_REL_BATCH",
            batchSource = "带发布包映射导入",
            items = listOf(item),
            releaseId = releaseId
        )

        assertTrue(result.isSuccessful)
        assertEquals(1, result.importedCount)

        // 核心边界验证 1：正式发布指针绝未被自动激活或修改
        val pointerBefore = db.releaseDao().getReleasePointer()
        assertNull("批量导入绝对不能自动篡改正式发布指针 app_release_pointer", pointerBefore)

        // 验证正式发布条目在 release_work_entries 中存在
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM release_work_entries WHERE release_id = ?",
            arrayOf(releaseId)
        )
        cursor.moveToFirst()
        val entryCount = cursor.getInt(0)
        cursor.close()
        assertEquals("必须在 release_work_entries 中建立条目", 1, entryCount)

        // 核心边界验证 2：只有显式激活发布指针后，正式发布下的作品才能被感知
        db.releaseDao().setReleasePointer(
            com.shi.yun.data.local.entity.AppReleasePointerEntity(
                currentReleaseId = releaseId,
                activatedAtEpochMs = 1000L
            )
        )
        val activeWorks = db.releaseDao().getActiveReleaseWorks()
        assertEquals(1, activeWorks.size)
        assertEquals("逢入京使", activeWorks[0].standardTitle)
    }
}
