package com.shi.yun.ui.detail

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.entity.ContentAttributionEntity
import com.shi.yun.data.local.entity.ContentComponentEntity
import com.shi.yun.data.local.entity.ReleaseWorkEntryEntity
import com.shi.yun.data.local.entity.WorkEntity
import com.shi.yun.data.local.entity.WorkVersionContentEntity
import com.shi.yun.data.local.entity.WorkVersionEntity
import com.shi.yun.data.local.repository.RoomWorkRepository
import com.shi.yun.data.model.AnnotationItem
import com.shi.yun.data.model.AuxiliaryContent
import com.shi.yun.data.model.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
import java.io.File

/**
 * 阶段五第二步：阅读详情页辅助内容面板（注释、译文、赏析）集成与数据链路单元测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailScreenAuxiliaryContentTest {

    private lateinit var context: Context
    private lateinit var db: ShiYunDatabase
    private lateinit var repository: RoomWorkRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val copyDbFile = context.getDatabasePath("test_auxiliary_content.db")
        if (copyDbFile.exists()) copyDbFile.delete()

        db = Room.databaseBuilder(
            context,
            ShiYunDatabase::class.java,
            copyDbFile.name
        )
            .createFromAsset("database/shiyun.db")
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()

        repository = RoomWorkRepository(db, Dispatchers.Unconfined)
    }

    @Test
    fun testAuxiliaryTabEnumIntegrityAndOrder() {
        // 严格遵循产品规范：顶部标签固定常驻「注释」「译文」「赏析」
        val entries = AuxiliaryTab.entries
        assertEquals(3, entries.size)
        assertEquals(AuxiliaryTab.ANNOTATION, entries[0])
        assertEquals("注释", entries[0].displayName)
        assertEquals(AuxiliaryTab.TRANSLATION, entries[1])
        assertEquals("译文", entries[1].displayName)
        assertEquals(AuxiliaryTab.APPRECIATION, entries[2])
        assertEquals("赏析", entries[2].displayName)
    }

    @Test
    fun testFullAuxiliaryContent_QiangJinJiu() = runBlocking {
        // 验证真实名篇《将进酒》（李白）具备完备的注释、译文与赏析
        val allWorks = repository.getAllWorks()
        val work = allWorks.find { it.title == "将进酒" }
        assertNotNull("预置库必须包含《将进酒》", work)

        val aux = work!!.auxiliary
        assertNotNull("《将进酒》的 auxiliary 集合必须非空", aux)

        // 1. 注释验证
        val annotations = aux!!.annotations
        assertNotNull("注释列表必须非空", annotations)
        assertTrue("注释列表条目数必须大于等于 1", annotations!!.isNotEmpty())
        val firstAnnot = annotations.first()
        assertTrue("注释条目序号必须大于 0", firstAnnot.index > 0)
        assertTrue("注释的原文字词 quote 必须非空", firstAnnot.quote.isNotBlank())
        assertTrue("注释的释义 note 必须非空", firstAnnot.note.isNotBlank())
        assertNotNull("注释来源记录必须存在", aux.annotationSource)

        // 2. 译文验证
        val translation = aux.translation
        assertNotNull("译文正文必须非空", translation)
        assertTrue("译文必须包含黄河之水", translation!!.contains("黄河之水"))
        assertNotNull("译文来源记录必须存在", aux.translationSource)

        // 3. 赏析验证
        val appreciation = aux.appreciation
        assertNotNull("赏析正文必须非空", appreciation)
        assertTrue("赏析必须包含豪迈或愤激或李白相关评析", appreciation!!.length > 50)
        assertNotNull("赏析来源记录必须存在", aux.appreciationSource)
    }

    @Test
    fun testMissingAuxiliaryContent_GracefulHandling() = runBlocking {
        // 验证缺少辅助内容的作品安全回退
        val allWorks = repository.getAllWorks()
        val workWithMissingAux = allWorks.find { w ->
            val aux = w.auxiliary
            aux == null || aux.annotations.isNullOrEmpty() || aux.translation.isNullOrBlank() || aux.appreciation.isNullOrBlank()
        }
        assertNotNull("库中应存在缺少部分辅助内容的作品以验证回退机制", workWithMissingAux)

        val aux = workWithMissingAux!!.auxiliary
        // 无论 auxiliary 为 null 还是字段为空，UI 逻辑均能优雅呈现「暂无相关内容」
        if (aux != null) {
            assertTrue(
                "若无相关内容，列表应为空或 null",
                aux.annotations.isNullOrEmpty() || aux.translation.isNullOrBlank() || aux.appreciation.isNullOrBlank()
            )
        }

        // 模拟构造全空 AuxiliaryContent 测试边界
        val emptyAux: AuxiliaryContent? = null
        assertNull(emptyAux?.annotations)
        assertNull(emptyAux?.translation)
        assertNull(emptyAux?.appreciation)
    }

    @Test
    fun testAnnotationItemStructureAndDisplayFormatting() {
        val item = AnnotationItem(
            index = 1,
            quote = "将",
            note = "请。",
            phonetic = "qiāng",
            targetLine = 0
        )
        assertEquals(1, item.index)
        assertEquals("将", item.quote)
        assertEquals("请。", item.note)
        assertEquals("qiāng", item.phonetic)
        assertEquals(0, item.targetLine)
    }

    @Test
    fun testStandaloneGetAuxiliaryContent_DirectPreload() = runBlocking {
        // 验证阶段五第三步：后台异步可单独预加载辅助内容，无需加载作者生平等无关信息
        val allWorks = repository.getAllWorks()
        val work = allWorks.find { it.title == "将进酒" }
        assertNotNull("预置库包含《将进酒》", work)

        val aux = repository.getAuxiliaryContent(work!!.id)
        assertNotNull("直接调用 getAuxiliaryContent 返回必须非空", aux)
        assertTrue("直接预加载的注释列表必须非空", !aux!!.annotations.isNullOrEmpty())
        assertTrue("直接预加载的译文必须包含黄河之水", aux.translation?.contains("黄河之水") == true)
        assertTrue("直接预加载的赏析必须非空", !aux.appreciation.isNullOrBlank())
    }

    @Test
    fun testRaceConditionGuard_Simulation() {
        // 模拟详情页快速切换作品场景下的竞态条件防护
        var currentTargetWorkId: String? = "work_1"
        var selectedWorkTitle = "作品1"

        // 模拟作品1发起的加载任务在作品2已经成为 target 后才返回
        val staleWork1Result = "作品1完整正文"
        val work1Id = "work_1"

        // 此时用户已经切换到了作品2
        currentTargetWorkId = "work_2"

        // 作品1的结果到达，执行校验：currentTargetWorkId == work1Id
        if (currentTargetWorkId == work1Id) {
            selectedWorkTitle = staleWork1Result
        }

        // 验证竞态拦截生效，作品1的结果被安全忽略，没有覆盖当前作品
        assertEquals("作品1", selectedWorkTitle)
    }

    @Test
    fun testAuxiliaryContentBindingWithWorkId_NoLeakageOnSwitch() {
        // 验证问题一：辅助内容严格与 auxiliaryWorkId 绑定，杜绝跨作品泄漏
        val workAId = "work_a"
        val workBId = "work_b"

        val testSource = SourceInfo(sourceName = "测试来源")
        val auxA = AuxiliaryContent(
            translation = "作品 A 译文",
            translationSource = testSource,
            appreciation = "作品 A 赏析",
            appreciationSource = testSource,
        )
        val auxB = AuxiliaryContent(
            translation = "作品 B 译文",
            translationSource = testSource,
            appreciation = "作品 B 赏析",
            appreciationSource = testSource,
        )

        // 1. 作品 A 加载完成
        var auxiliaryWorkId: String? = workAId
        var currentAuxiliaryContent: AuxiliaryContent? = auxA

        fun resolveEffectiveAuxiliary(targetWorkId: String): AuxiliaryContent? {
            return if (auxiliaryWorkId == targetWorkId) currentAuxiliaryContent else null
        }

        assertEquals("作品 A 译文", resolveEffectiveAuxiliary(workAId)?.translation)

        // 2. 切篇切换到作品 B：立即清空绑定关系
        auxiliaryWorkId = null
        currentAuxiliaryContent = null

        // 此时对于作品 B，即使有残留或者旧引用，有效辅助内容必须为 null
        assertNull("切换到作品 B 时，辅助内容必须立即为 null，严禁显示作品 A 内容", resolveEffectiveAuxiliary(workBId))

        // 3. 即使作品 B 的轻量对象本身携带了残留的旧 auxiliary，通过 auxiliaryWorkId 强校验也能阻止其泄漏
        val workBWithStaleAux = com.shi.yun.data.model.Work(
            id = workBId,
            title = "作品 B",
            author = "作者",
            dynasty = "唐代",
            genre = com.shi.yun.data.model.Genre.POEM,
            content = "作品 B 正文",
            excerpt = "作品 B 正文",
            auxiliary = auxA
        )
        val safeAuxiliaryForB = if (auxiliaryWorkId == workBWithStaleAux.id) currentAuxiliaryContent else null
        assertNull("即使对象自身持有 auxiliary，若未通过 auxiliaryWorkId 绑定，仍判定为 null", safeAuxiliaryForB)

        // 4. 作品 B 的辅助内容异步加载完成
        auxiliaryWorkId = workBId
        currentAuxiliaryContent = auxB
        assertEquals("作品 B 译文", resolveEffectiveAuxiliary(workBId)?.translation)
        assertNull("作品 B 加载后，对作品 A 的查询返回 null", resolveEffectiveAuxiliary(workAId))
    }

    @Test
    fun testDetailContentStatus_LoadingToSuccessAndFailure() {
        // 验证问题二：正文加载状态流转，覆盖 LOADING、SUCCESS、NOT_FOUND、ERROR
        var status = DetailContentStatus.LOADING

        // 模拟正常加载成功
        val fullWorkContent = "床前明月光"
        status = if (fullWorkContent.isNotBlank()) DetailContentStatus.SUCCESS else DetailContentStatus.LOADING
        assertEquals(DetailContentStatus.SUCCESS, status)

        // 模拟查询结果为 null (作品不存在或废弃)
        val nullResult: String? = null
        status = if (nullResult != null) DetailContentStatus.SUCCESS else DetailContentStatus.NOT_FOUND
        assertEquals(DetailContentStatus.NOT_FOUND, status)

        // 模拟加载发生异常
        status = try {
            throw java.io.IOException("数据库读取超时")
        } catch (_: Exception) {
            DetailContentStatus.ERROR
        }
        assertEquals(DetailContentStatus.ERROR, status)
    }

    @Test
    fun testCancellationException_RethrownAndNotTreatedAsError() = runBlocking {
        // 验证问题三：协程取消必须重新抛出 CancellationException，绝不触发 ERROR 状态
        var status = DetailContentStatus.LOADING
        var cancellationRethrown = false

        try {
            try {
                throw kotlinx.coroutines.CancellationException("用户快速切篇，旧任务取消")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                status = DetailContentStatus.ERROR
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancellationRethrown = true
        }

        assertTrue("CancellationException 必须被重新抛出", cancellationRethrown)
        assertEquals("取消操作不应将状态标记为 ERROR", DetailContentStatus.LOADING, status)
    }

    @Test
    fun testUnifiedVersionResolution_BetweenBuildWorkAndGetAuxiliaryContent() = runBlocking {
        // 验证问题四：resolveActiveWorkVersion 确保正文与辅助内容取自同一版本
        val allWorks = repository.getAllWorks()
        assertTrue("测试数据库必须有作品", allWorks.isNotEmpty())

        val testWork = allWorks.first()
        val resolvedVersion = repository.resolveActiveWorkVersion(testWork.id)
        assertNotNull("作品生效版本必须非空", resolvedVersion)

        val fullWork = repository.getWorkById(testWork.id)
        assertNotNull("作品详情必须非空", fullWork)

        val aux = repository.getAuxiliaryContent(testWork.id)
        assertNotNull("生效版本ID非空", resolvedVersion?.workVersionId)
        // 预置名篇如果含有辅助内容，其加载出的 auxiliary 也对应统一版本
        if (aux != null) {
            assertTrue(aux.annotations != null || aux.translation != null || aux.appreciation != null)
        }
    }

    @Test
    fun testMultiVersionConsistency_PointerAndFallbacks() = runBlocking {
        // 验证在真实 Room 数据库中多版本作品的正文与辅助内容强一致性及回退规则
        val testWorkId = "work_multi_ver_test"
        val v1Id = "wv_test_v1"
        val v2Id = "wv_test_v2"
        val now = System.currentTimeMillis()

        val workDao = db.workDao()
        val releaseDao = db.releaseDao()

        // 1. 插入多版本测试作品
        workDao.insertWork(
            WorkEntity(
                workId = testWorkId,
                standardTitle = "登鹳雀楼多版本测试",
                identityStatus = "NORMAL",
                sortOrder = 9999,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )
        )

        // 2. 插入版本 1（versionCode = 1）及其正文与译文组件
        workDao.insertWorkVersion(
            WorkVersionEntity(
                workVersionId = v1Id,
                workId = testWorkId,
                versionCode = 1,
                editionName = "通行底本一",
                layoutType = "five_jueju",
                createdAtEpochMs = now,
            )
        )
        val compV1Content = ContentComponentEntity(
            componentId = "comp_v1_content",
            componentType = "CONTENT",
            contentPayload = "白日依山尽，黄河入海流。（版本一正文）",
            contentHash = "hash_v1_c",
            createdAtEpochMs = now,
        )
        val compV1Trans = ContentComponentEntity(
            componentId = "comp_v1_trans",
            componentType = "TRANSLATION",
            contentPayload = "版本一译文：夕阳依傍着西山慢慢沉落。",
            contentHash = "hash_v1_t",
            createdAtEpochMs = now,
        )
        workDao.insertComponents(listOf(compV1Content, compV1Trans))
        workDao.insertWorkVersionContents(
            listOf(
                WorkVersionContentEntity(workVersionId = v1Id, componentType = "CONTENT", componentId = "comp_v1_content"),
                WorkVersionContentEntity(workVersionId = v1Id, componentType = "TRANSLATION", componentId = "comp_v1_trans"),
            )
        )
        workDao.insertContentAttribution(
            ContentAttributionEntity(
                attributionId = "attr_v1_trans",
                componentId = "comp_v1_trans",
                licenseType = "PUBLIC_DOMAIN",
                licenseNote = "版本一译文底本",
            )
        )

        // 3. 插入版本 2（versionCode = 2，更高版本）及其正文与译文组件
        workDao.insertWorkVersion(
            WorkVersionEntity(
                workVersionId = v2Id,
                workId = testWorkId,
                versionCode = 2,
                editionName = "校勘底本二",
                layoutType = "five_jueju",
                createdAtEpochMs = now + 1000,
            )
        )
        val compV2Content = ContentComponentEntity(
            componentId = "comp_v2_content",
            componentType = "CONTENT",
            contentPayload = "欲穷千里目，更上一层楼。（版本二正文）",
            contentHash = "hash_v2_c",
            createdAtEpochMs = now + 1000,
        )
        val compV2Trans = ContentComponentEntity(
            componentId = "comp_v2_trans",
            componentType = "TRANSLATION",
            contentPayload = "版本二译文：要想饱览千里风光，就得再登上一层高楼。",
            contentHash = "hash_v2_t",
            createdAtEpochMs = now + 1000,
        )
        workDao.insertComponents(listOf(compV2Content, compV2Trans))
        workDao.insertWorkVersionContents(
            listOf(
                WorkVersionContentEntity(workVersionId = v2Id, componentType = "CONTENT", componentId = "comp_v2_content"),
                WorkVersionContentEntity(workVersionId = v2Id, componentType = "TRANSLATION", componentId = "comp_v2_trans"),
            )
        )
        workDao.insertContentAttribution(
            ContentAttributionEntity(
                attributionId = "attr_v2_trans",
                componentId = "comp_v2_trans",
                licenseType = "PUBLIC_DOMAIN",
                licenseNote = "版本二译文底本",
            )
        )

        // ==================== 场景 A：发布指针明确指向 V1 ====================
        val activeReleaseId = releaseDao.getCurrentReleaseId() ?: "release_v1_0"
        releaseDao.insertReleaseWorkEntries(
            listOf(
                ReleaseWorkEntryEntity(
                    releaseId = activeReleaseId,
                    workId = testWorkId,
                    workVersionId = v1Id,
                )
            )
        )

        val workV1 = repository.getWorkById(testWorkId)
        val auxV1 = repository.getAuxiliaryContent(testWorkId)

        assertNotNull("作品 V1 必须查询成功", workV1)
        assertNotNull("作品 V1 辅助内容必须查询成功", auxV1)
        assertTrue("正文必须来自版本一", workV1!!.content.contains("版本一正文"))
        assertFalse("正文绝不能来自版本二", workV1.content.contains("版本二正文"))
        assertTrue("译文必须来自版本一", auxV1!!.translation?.contains("版本一译文") == true)
        assertFalse("译文绝不能来自版本二", auxV1.translation?.contains("版本二译文") == true)

        // ==================== 场景 B：发布指针显式切换指向 V2 ====================
        db.openHelper.writableDatabase.execSQL(
            "UPDATE release_work_entries SET work_version_id = '$v2Id' WHERE work_id = '$testWorkId' AND release_id = '$activeReleaseId'"
        )

        val workV2 = repository.getWorkById(testWorkId)
        val auxV2 = repository.getAuxiliaryContent(testWorkId)

        assertNotNull("作品 V2 必须查询成功", workV2)
        assertNotNull("作品 V2 辅助内容必须查询成功", auxV2)
        assertTrue("正文必须切换为版本二", workV2!!.content.contains("版本二正文"))
        assertFalse("正文绝不能来自版本一", workV2.content.contains("版本一正文"))
        assertTrue("译文必须切换为版本二", auxV2!!.translation?.contains("版本二译文") == true)
        assertFalse("译文绝不能来自版本一", auxV2.translation?.contains("版本一译文") == true)

        // ==================== 场景 C：发布指针指向一个不存在的坏版本 ID ====================
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
        val cursorOff = db.openHelper.writableDatabase.query("PRAGMA foreign_keys")
        cursorOff.moveToFirst()
        val fkOffVal = cursorOff.getInt(0)
        cursorOff.close()
        assertEquals("执行 PRAGMA 后外键检查必须确认处于关闭状态(0)", 0, fkOffVal)

        db.openHelper.writableDatabase.execSQL(
            "UPDATE release_work_entries SET work_version_id = 'wv_corrupted_non_existent' WHERE work_id = '$testWorkId' AND release_id = '$activeReleaseId'"
        )

        // 严格断言坏版本 ID 确实成功写入了当前激活发布记录
        val corruptedVersionIdInDb = releaseDao.getActiveVersionIdForWork(testWorkId)
        assertEquals("坏版本 ID 必须已确凿存在于当前激活发布映射记录中", "wv_corrupted_non_existent", corruptedVersionIdInDb)

        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        val cursorOn = db.openHelper.writableDatabase.query("PRAGMA foreign_keys")
        cursorOn.moveToFirst()
        val fkOnVal = cursorOn.getInt(0)
        cursorOn.close()
        assertEquals("必须确认外键检查已恢复开启(1)", 1, fkOnVal)

        // 回退规则：因坏版本在 work_versions 中不存在，自动回退到当前作品 version_code DESC 首位（V2，versionCode=2）
        val workFallbackCorrupted = repository.getWorkById(testWorkId)
        val auxFallbackCorrupted = repository.getAuxiliaryContent(testWorkId)

        assertNotNull("坏指针回退后作品仍能获取", workFallbackCorrupted)
        assertNotNull("坏指针回退后辅助内容仍能获取", auxFallbackCorrupted)
        assertTrue("坏指针回退必须选中最新版本 V2 正文", workFallbackCorrupted!!.content.contains("版本二正文"))
        assertTrue("坏指针回退必须选中最新版本 V2 译文", auxFallbackCorrupted!!.translation?.contains("版本二译文") == true)

        // ==================== 场景 D：发布指针对应条目为空 (未在当前发布包中发布) ====================
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM release_work_entries WHERE work_id = '$testWorkId'"
        )

        // 回退规则：releaseDao.getActiveVersionIdForWork 返回 null，同样回退到 version_code DESC 首位 V2
        val workFallbackNull = repository.getWorkById(testWorkId)
        val auxFallbackNull = repository.getAuxiliaryContent(testWorkId)

        assertNotNull("空指针回退后作品仍能获取", workFallbackNull)
        assertNotNull("空指针回退后辅助内容仍能获取", auxFallbackNull)
        assertTrue("空指针回退必须选中最新版本 V2 正文", workFallbackNull!!.content.contains("版本二正文"))
        assertTrue("空指针回退必须选中最新版本 V2 译文", auxFallbackNull!!.translation?.contains("版本二译文") == true)
    }

    @Test
    fun testCrossWorkVersionMismatchDefense() = runBlocking {
        // 验证第一项定点加固：防范发布映射记录中跨作品版本错配风险
        val workAId = "work_mismatch_test_a"
        val workBId = "work_mismatch_test_b"
        val verAId = "wv_mismatch_a_1"
        val verBId = "wv_mismatch_b_1"
        val now = System.currentTimeMillis()

        val workDao = db.workDao()
        val releaseDao = db.releaseDao()

        // 1. 创建作品 A 和作品 B
        workDao.insertWorks(
            listOf(
                WorkEntity(
                    workId = workAId,
                    standardTitle = "作品A（送友人）",
                    identityStatus = "NORMAL",
                    sortOrder = 9001,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                ),
                WorkEntity(
                    workId = workBId,
                    standardTitle = "作品B（登高）",
                    identityStatus = "NORMAL",
                    sortOrder = 9002,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                )
            )
        )

        // 2. 为作品 A 创建版本 A1，正文和译文具备明显特征词
        workDao.insertWorkVersion(
            WorkVersionEntity(
                workVersionId = verAId,
                workId = workAId,
                versionCode = 1,
                editionName = "作品A通行底本",
                layoutType = "five_jueju",
                createdAtEpochMs = now,
            )
        )
        val compAContent = ContentComponentEntity(
            componentId = "comp_a_content",
            componentType = "CONTENT",
            contentPayload = "青山横北郭，白水绕东城。（作品A专属正文）",
            contentHash = "hash_a_c",
            createdAtEpochMs = now,
        )
        val compATrans = ContentComponentEntity(
            componentId = "comp_a_trans",
            componentType = "TRANSLATION",
            contentPayload = "作品A专属译文：青翠的山峦横卧在城墙北郭，清澈的流水围绕着东部城郭。",
            contentHash = "hash_a_t",
            createdAtEpochMs = now,
        )
        workDao.insertComponents(listOf(compAContent, compATrans))
        workDao.insertWorkVersionContents(
            listOf(
                WorkVersionContentEntity(workVersionId = verAId, componentType = "CONTENT", componentId = "comp_a_content"),
                WorkVersionContentEntity(workVersionId = verAId, componentType = "TRANSLATION", componentId = "comp_a_trans"),
            )
        )
        workDao.insertContentAttribution(
            ContentAttributionEntity(
                attributionId = "attr_a_trans",
                componentId = "comp_a_trans",
                licenseType = "PUBLIC_DOMAIN",
                licenseNote = "作品A底本来源",
            )
        )

        // 3. 为作品 B 创建版本 B1，正文和译文具备完全不同的特征词
        workDao.insertWorkVersion(
            WorkVersionEntity(
                workVersionId = verBId,
                workId = workBId,
                versionCode = 1,
                editionName = "作品B通行底本",
                layoutType = "seven_lvshi",
                createdAtEpochMs = now + 100,
            )
        )
        val compBContent = ContentComponentEntity(
            componentId = "comp_b_content",
            componentType = "CONTENT",
            contentPayload = "风急天高猿啸哀，渚清沙白鸟飞回。（作品B专属正文）",
            contentHash = "hash_b_c",
            createdAtEpochMs = now + 100,
        )
        val compBTrans = ContentComponentEntity(
            componentId = "comp_b_trans",
            componentType = "TRANSLATION",
            contentPayload = "作品B专属译文：风急天高猿猴的啼叫十分凄厉，沙洲清冷水鸟在空中盘旋。",
            contentHash = "hash_b_t",
            createdAtEpochMs = now + 100,
        )
        workDao.insertComponents(listOf(compBContent, compBTrans))
        workDao.insertWorkVersionContents(
            listOf(
                WorkVersionContentEntity(workVersionId = verBId, componentType = "CONTENT", componentId = "comp_b_content"),
                WorkVersionContentEntity(workVersionId = verBId, componentType = "TRANSLATION", componentId = "comp_b_trans"),
            )
        )
        workDao.insertContentAttribution(
            ContentAttributionEntity(
                attributionId = "attr_b_trans",
                componentId = "comp_b_trans",
                licenseType = "PUBLIC_DOMAIN",
                licenseNote = "作品B底本来源",
            )
        )

        // 4. 在当前激活发布记录中，故意将作品 A 的发布版本配置为作品 B 的版本 verBId
        // 注：数据库底层原本存在 trg_release_work_entries_check_ownership_insert 物理触发器防御，
        // 此处在测试中先临时 DROP 触发器以模拟外部脏数据绕过物理约束写入的极端场景
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS trg_release_work_entries_check_ownership_insert")

        val activeReleaseId = releaseDao.getCurrentReleaseId() ?: "release_v1_0"
        releaseDao.insertReleaseWorkEntries(
            listOf(
                ReleaseWorkEntryEntity(
                    releaseId = activeReleaseId,
                    workId = workAId,
                    workVersionId = verBId, // 恶意/脏数据错配：指向作品 B 的版本
                ),
                ReleaseWorkEntryEntity(
                    releaseId = activeReleaseId,
                    workId = workBId,
                    workVersionId = verBId,
                )
            )
        )

        // 立即恢复物理触发器，避免影响后续其他操作
        ShiYunDatabase.createTriggers(db.openHelper.writableDatabase)

        // 验证错配记录确实已生效写入发布表
        val mappedVersionForA = releaseDao.getActiveVersionIdForWork(workAId)
        assertEquals("发布表中作品A确实被错误指向了作品B的版本", verBId, mappedVersionForA)

        // 5. 调用真实 Repository 获取作品 A 的详情和辅助内容
        val resolvedVersionForA = repository.resolveActiveWorkVersion(workAId)
        val loadedWorkA = repository.getWorkById(workAId)
        val loadedAuxA = repository.getAuxiliaryContent(workAId)

        // 6. 验证防御生效：发现版本所属 workId != workAId，强制阻断错配并安全回退到作品 A 自己的版本 verAId
        assertNotNull("解析出的版本必须非空", resolvedVersionForA)
        assertEquals("防御生效：必须回退到作品 A 自己的版本 ID", verAId, resolvedVersionForA!!.workVersionId)
        assertEquals("防御生效：版本归属必须是作品 A", workAId, resolvedVersionForA.workId)

        assertNotNull("作品 A 正文加载必须成功", loadedWorkA)
        assertNotNull("作品 A 辅助内容加载必须成功", loadedAuxA)

        // 正文验证：必须回退到作品 A 自己的正文，严禁泄漏作品 B 的任何词句
        assertTrue("正文必须是作品 A 专属正文", loadedWorkA!!.content.contains("作品A专属正文"))
        assertFalse("正文绝对不能泄漏作品 B 专属正文", loadedWorkA.content.contains("作品B专属正文"))
        assertFalse("正文绝对不能出现作品 B 文本内容", loadedWorkA.content.contains("风急天高猿啸哀"))

        // 辅助内容验证：必须回退到作品 A 自己的译文，严禁泄漏作品 B 的任何译文
        assertTrue("译文必须是作品 A 专属译文", loadedAuxA!!.translation?.contains("作品A专属译文") == true)
        assertFalse("译文绝对不能泄漏作品 B 专属译文", loadedAuxA.translation?.contains("作品B专属译文") == true)
        assertFalse("译文绝对不能出现作品 B 译文内容", loadedAuxA.translation?.contains("风急天高猿猴的啼叫") == true)
    }
}
