package com.shi.yun.data.local.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.entity.AuthorEntity
import com.shi.yun.data.local.entity.ComponentType
import com.shi.yun.data.local.entity.ContentComponentEntity
import com.shi.yun.data.local.entity.ExternalSourceLinkEntity
import com.shi.yun.data.local.entity.WorkAuthorEntity
import com.shi.yun.data.local.entity.WorkEntity
import com.shi.yun.data.local.entity.WorkSourceLinkEntity
import com.shi.yun.data.local.entity.WorkVersionContentEntity
import com.shi.yun.data.local.entity.WorkVersionEntity
import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.PrecheckDisposition
import com.shi.yun.data.local.importer.model.SourceImportInfo
import com.shi.yun.data.local.importer.model.WorkImportItem
import com.shi.yun.data.local.importer.validator.WorkImportValidator
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
 * 作品导入前置校验与查重判定全面单元测试 (WorkImportPrecheckTest)
 *
 * 严格覆盖阶段 3 步骤 5.1 的全部 12 项测试目标：
 * 1. 正常作品通过校验与预检 (READY_FOR_IMPORT)；
 * 2. 标题为空被拦截 (VALIDATION_FAILED)；
 * 3. 正文为空被拦截 (VALIDATION_FAILED)；
 * 4. 辅助内容全部为空仍然允许通过；
 * 5. 五种内容组件规范（CONTENT, TRANSLATION, ANNOTATION, BACKGROUND, APPRECIATION）正确承载；
 * 6. 作者生平介绍与作品内容严格隔离，混入时被拦截；
 * 7. 同一外部来源与编号能够识别强匹配 (CAN_SKIP)；
 * 8. 同名不同作者绝不误判为重复 (READY_FOR_IMPORT)；
 * 9. 同名同作者但存在异文分歧时标记为待确认 (NEEDS_CONFIRMATION)，绝不自动覆盖；
 * 10. 分类未知时不擅自猜测，保留为空；
 * 11. 批量输入中混合有效与无效数据时各自准确定位问题；
 * 12. 预检过程执行纯只读检索，数据库记录数严格保持不变（0 写入）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkImportPrecheckTest {

    private lateinit var db: ShiYunDatabase
    private lateinit var prechecker: WorkImportPrechecker

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShiYunDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()
        prechecker = WorkImportPrechecker(db.workDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // 测试 1：正常完整作品通过校验与预检 (READY_FOR_IMPORT)
    // =========================================================================
    @Test
    fun testNormalWork_PassesPrecheck() = runBlocking {
        val item = WorkImportItem(
            title = "望庐山瀑布",
            author = AuthorImportItem("李白", dynasty = "唐"),
            dynasty = "唐",
            genre = "poem",
            layoutType = LayoutType.SEVEN_JUEJU.code,
            content = "日照香炉生紫烟，遥看瀑布挂前川。\n飞流直下三千尺，疑是银河落九天。",
            contentSource = SourceImportInfo(sourceName = "全唐诗"),
            translation = "香炉峰在阳光照耀下升起紫色烟雾，远远望去瀑布宛如挂在山前。",
            translationSource = SourceImportInfo(sourceName = "白话唐诗"),
            background = "李白游历庐山时所作。",
            backgroundSource = SourceImportInfo(sourceName = "李白年谱"),
            appreciation = "极为奇绝的想象力，将瀑布飞泻比作银河倾落。",
            appreciationSource = SourceImportInfo(sourceName = "唐诗鉴赏")
        )

        val summary = prechecker.precheckBatch(listOf(item))
        assertEquals(1, summary.totalCount)
        assertEquals(1, summary.readyCount)
        assertEquals(0, summary.failedCount)
        assertEquals(PrecheckDisposition.READY_FOR_IMPORT, summary.items[0].disposition)
    }

    // =========================================================================
    // 测试 2：标题为空被拦截 (VALIDATION_FAILED)
    // =========================================================================
    @Test
    fun testEmptyTitle_FailsValidation() = runBlocking {
        val item = WorkImportItem(
            title = "   ",
            content = "白日依山尽，黄河入海流。"
        )

        val summary = prechecker.precheckBatch(listOf(item))
        assertEquals(1, summary.failedCount)
        assertEquals(PrecheckDisposition.VALIDATION_FAILED, summary.items[0].disposition)
        assertTrue(summary.items[0].reason.contains("作品标题不能为空白"))
    }

    // =========================================================================
    // 测试 3：正文为空被拦截 (VALIDATION_FAILED)
    // =========================================================================
    @Test
    fun testEmptyContent_FailsValidation() = runBlocking {
        val item = WorkImportItem(
            title = "登鹳雀楼",
            content = ""
        )

        val summary = prechecker.precheckBatch(listOf(item))
        assertEquals(1, summary.failedCount)
        assertEquals(PrecheckDisposition.VALIDATION_FAILED, summary.items[0].disposition)
        assertTrue(summary.items[0].reason.contains("作品正文不能为空白"))
    }

    // =========================================================================
    // 测试 4：辅助内容全部为空仍然允许通过
    // =========================================================================
    @Test
    fun testEmptyAuxiliary_StillAllowed() = runBlocking {
        val item = WorkImportItem(
            title = "春晓",
            author = AuthorImportItem("孟浩然"),
            content = "春眠不觉晓，处处闻啼鸟。\n夜来风雨声，花落知多少。",
            translation = null,
            annotations = null,
            background = null,
            appreciation = null
        )

        val summary = prechecker.precheckBatch(listOf(item))
        assertEquals(1, summary.readyCount)
        assertEquals(0, summary.failedCount)
        assertEquals(PrecheckDisposition.READY_FOR_IMPORT, summary.items[0].disposition)
    }

    // =========================================================================
    // 测试 5：五种内容组件正确承载（结构化注释解析与格式校验）
    // =========================================================================
    @Test
    fun testFiveComponentsAndStructuredAnnotations() = runBlocking {
        // 5.1 结构化 JSON 注释合法
        val validItem = WorkImportItem(
            title = "静夜思",
            content = "床前看月光，疑是地上霜。",
            annotations = """[{"index":1,"quote":"看月光","note":"一作‘明月光’。"}]""",
            rawFormat = "STRUCTURED_JSON"
        )
        val validResult = WorkImportValidator.validate(validItem)
        assertTrue(validResult.isValid)

        // 5.2 注释 JSON 损坏被拦截
        val brokenItem = WorkImportItem(
            title = "静夜思",
            content = "床前看月光，疑是地上霜。",
            annotations = """[{"index":1, quote: broken json""",
            rawFormat = "STRUCTURED_JSON"
        )
        val brokenResult = WorkImportValidator.validate(brokenItem)
        assertFalse(brokenResult.isValid)
        assertTrue(brokenResult.issues.any { it.message.contains("注释数据格式损坏") })
    }

    // =========================================================================
    // 测试 6：作者生平介绍与作品内容严格隔离（混入时被拦截）
    // =========================================================================
    @Test
    fun testAuthorBiographyIsolation() = runBlocking {
        val bioText = "杜甫（712年—770年），字子美，自号少陵野老，唐代伟大的现实主义诗人，被后人尊称为“诗圣”。"

        // 错误情况 A：将作者生平直接填入正文组件
        val mixedContent = WorkImportItem(
            title = "春望",
            author = AuthorImportItem("杜甫", biography = bioText),
            content = bioText
        )
        val resultA = WorkImportValidator.validate(mixedContent)
        assertFalse(resultA.isValid)
        assertTrue(resultA.issues.any { it.message.contains("作者生平介绍被错误填入了正文组件") })

        // 错误情况 B：将作者生平填入创作背景组件
        val mixedBackground = WorkImportItem(
            title = "春望",
            author = AuthorImportItem("杜甫", biography = bioText),
            content = "国破山河在，城春草木深。",
            background = bioText
        )
        val resultB = WorkImportValidator.validate(mixedBackground)
        assertFalse(resultB.isValid)
        assertTrue(resultB.issues.any { it.message.contains("作者生平介绍被错误填入了创作背景组件") })
    }

    // =========================================================================
    // 测试 7：同一外部来源、同一外部编号识别强匹配 (CAN_SKIP)
    // =========================================================================
    @Test
    fun testExternalSource_StrongMatchCanSkip() = runBlocking {
        // 先在库中播种一条作品及外部来源
        seedDatabaseWork(
            workId = "work_tang_001",
            title = "江雪",
            authorName = "柳宗元",
            content = "千山鸟飞绝，万径人踪灭。\n孤舟蓑笠翁，独钓寒江雪。",
            sourceCode = "SHI_CI_CN",
            externalItemId = "ext_1001"
        )

        // 待导入作品拥有完全一致的外部编号和相同正文
        val importItem = WorkImportItem(
            title = "江雪",
            author = AuthorImportItem("柳宗元"),
            content = "千山鸟飞绝，万径人踪灭。\n孤舟蓑笠翁，独钓寒江雪。",
            externalSourceCode = "SHI_CI_CN",
            externalWorkId = "ext_1001"
        )

        val summary = prechecker.precheckBatch(listOf(importItem))
        assertEquals(1, summary.skipCount)
        assertEquals(PrecheckDisposition.CAN_SKIP, summary.items[0].disposition)
        assertEquals("work_tang_001", summary.items[0].matchedWorkId)
        assertTrue(summary.items[0].reason.contains("已存在于库中作品"))
    }

    // =========================================================================
    // 测试 8：同名不同作者绝不误判为重复 (READY_FOR_IMPORT)
    // =========================================================================
    @Test
    fun testSameTitleDifferentAuthor_NotDuplicate() = runBlocking {
        // 库中已有李白的《塞下曲》
        seedDatabaseWork(
            workId = "work_libai_sxq",
            title = "塞下曲",
            authorName = "李白",
            content = "五月天山雪，无花只有寒。"
        )

        // 待导入的是卢纶的《塞下曲》
        val lulunItem = WorkImportItem(
            title = "塞下曲",
            author = AuthorImportItem("卢纶"),
            content = "月黑雁飞高，单于夜遁逃。\n欲将轻骑逐，大雪满弓刀。"
        )

        val summary = prechecker.precheckBatch(listOf(lulunItem))
        assertEquals(1, summary.readyCount)
        assertEquals(0, summary.skipCount)
        assertEquals(PrecheckDisposition.READY_FOR_IMPORT, summary.items[0].disposition)
    }

    // =========================================================================
    // 测试 9：同名同作者但存在异文分歧时标记为待确认 (NEEDS_CONFIRMATION)
    // =========================================================================
    @Test
    fun testVariantText_MarkedNeedsConfirmation() = runBlocking {
        // 库中已有通行本《静夜思》
        seedDatabaseWork(
            workId = "work_jys_base",
            title = "静夜思",
            authorName = "李白",
            content = "床前看月光，疑是地上霜。\n举头望山月，低头思故乡。"
        )

        // 待导入宋本异文：“床前明月光……望明月”
        val variantItem = WorkImportItem(
            title = "静夜思",
            author = AuthorImportItem("李白"),
            content = "床前明月光，疑是地上霜。\n举头望明月，低头思故乡。"
        )

        val summary = prechecker.precheckBatch(listOf(variantItem))
        assertEquals(1, summary.needsConfirmationCount)
        assertEquals(PrecheckDisposition.NEEDS_CONFIRMATION, summary.items[0].disposition)
        assertEquals("work_jys_base", summary.items[0].matchedWorkId)
        assertTrue(summary.items[0].reason.contains("异文分歧"))
    }

    // =========================================================================
    // 测试 10：分类未知时不擅自猜测，保留为空
    // =========================================================================
    @Test
    fun testUnknownClassification_NoAutoGuess() = runBlocking {
        val anonymousItem = WorkImportItem(
            title = "古诗十九首之一",
            author = null, // 未知作者
            dynasty = null, // 未知朝代
            genre = null, // 未知体裁
            content = "行行重行行，与君生别离。"
        )

        val summary = prechecker.precheckBatch(listOf(anonymousItem))
        assertEquals(1, summary.readyCount)
        val report = summary.items[0]
        assertEquals(PrecheckDisposition.READY_FOR_IMPORT, report.disposition)
        assertEquals("佚名", report.author)
        // 验证没有被强行注入猜测的朝代或体裁
        assertNull(anonymousItem.dynasty)
        assertNull(anonymousItem.genre)
    }

    // =========================================================================
    // 测试 11：批量作品中有效与无效数据并存时各自准确定位
    // =========================================================================
    @Test
    fun testMixedBatch_AccurateIssuesPerItem() = runBlocking {
        val items = listOf(
            // 项 0: 正常作品
            WorkImportItem(title = "小池", author = AuthorImportItem("杨万里"), content = "泉眼无声惜细流，树阴照水爱晴柔。"),
            // 项 1: 缺少标题
            WorkImportItem(title = "", author = AuthorImportItem("杜牧"), content = "清明时节雨纷纷，路上行人欲断魂。"),
            // 项 2: 缺少正文
            WorkImportItem(title = "回乡偶书", author = AuthorImportItem("贺知章"), content = "   "),
            // 项 3: 批次内部与项 0 重复
            WorkImportItem(title = "小池", author = AuthorImportItem("杨万里"), content = "泉眼无声惜细流，树阴照水爱晴柔。")
        )

        val summary = prechecker.precheckBatch(items)
        assertEquals(4, summary.totalCount)
        assertEquals(1, summary.readyCount) // 项 0
        assertEquals(2, summary.failedCount) // 项 1, 项 2
        assertEquals(1, summary.skipCount) // 项 3 (批次内重复)

        assertEquals(PrecheckDisposition.READY_FOR_IMPORT, summary.items[0].disposition)
        assertEquals(PrecheckDisposition.VALIDATION_FAILED, summary.items[1].disposition)
        assertEquals(PrecheckDisposition.VALIDATION_FAILED, summary.items[2].disposition)
        assertEquals(PrecheckDisposition.CAN_SKIP, summary.items[3].disposition)
    }

    // =========================================================================
    // 测试 12：预检过程执行纯只读检索，数据库记录数严格保持不变（0 写入）
    // =========================================================================
    @Test
    fun testPrecheck_PerformsZeroDatabaseWrites() = runBlocking {
        // 记录预检前的数据库作品数
        val countBefore = db.workDao().getWorkCount()

        val items = listOf(
            WorkImportItem(title = "游子吟", author = AuthorImportItem("孟郊"), content = "慈母手中线，游子身上衣。"),
            WorkImportItem(title = "早春", author = AuthorImportItem("韩愈"), content = "天街小雨润如酥，草色遥看近却无。")
        )

        val summary = prechecker.precheckBatch(items)
        assertEquals(2, summary.readyCount)

        // 验证预检后数据库作品总数严格一致，没有任何数据写入
        val countAfter = db.workDao().getWorkCount()
        assertEquals(countBefore, countAfter)
    }

    // =========================================================================
    // 数据库基线数据播种辅助方法
    // =========================================================================
    private suspend fun seedDatabaseWork(
        workId: String,
        title: String,
        authorName: String,
        content: String,
        sourceCode: String? = null,
        externalItemId: String? = null
    ) {
        val authorId = "author_$workId"
        db.categoryDao().insertAuthors(listOf(AuthorEntity(authorId, authorName)))

        val work = WorkEntity(workId = workId, standardTitle = title, sortOrder = 1, createdAtEpochMs = 1000L, updatedAtEpochMs = 1000L)
        db.workDao().insertWork(work)

        val versionId = "wv_$workId"
        val version = WorkVersionEntity(workVersionId = versionId, workId = workId, versionCode = 1, editionName = "底本", layoutType = "FIVE_JUEJU", createdAtEpochMs = 1000L)
        db.workDao().insertWorkVersion(version)

        db.workDao().insertWorkAuthors(listOf(WorkAuthorEntity(versionId, authorId, "PRIMARY", 1, "CONFIRMED")))

        val compId = "cc_$workId"
        val comp = ContentComponentEntity(componentId = compId, componentType = ComponentType.CONTENT.code, contentPayload = content, contentHash = "hash_$workId", createdAtEpochMs = 1000L)
        db.workDao().insertComponent(comp)
        db.workDao().insertWorkVersionContents(listOf(WorkVersionContentEntity(versionId, ComponentType.CONTENT.code, compId, "VERIFIED")))

        if (sourceCode != null && externalItemId != null) {
            val linkId = "link_$workId"
            db.categoryDao().insertExternalSourceLinks(listOf(
                ExternalSourceLinkEntity(linkId = linkId, sourceCode = sourceCode, externalItemId = externalItemId)
            ))
            db.workDao().insertWorkSourceLinks(listOf(
                WorkSourceLinkEntity(linkId = "wsl_$workId", workId = workId, externalSourceLinkId = linkId, isPrimary = 1)
            ))
        }
    }
}
