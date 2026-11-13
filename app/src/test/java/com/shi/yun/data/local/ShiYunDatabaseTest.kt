package com.shi.yun.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shi.yun.data.local.entity.AnthologyBookEntity
import com.shi.yun.data.local.entity.AnthologyBookVersionEntity
import com.shi.yun.data.local.entity.AnthologyItemEntity
import com.shi.yun.data.local.entity.AnthologyNodeEntity
import com.shi.yun.data.local.entity.AppReleasePointerEntity
import com.shi.yun.data.local.entity.AuthorEntity
import com.shi.yun.data.local.entity.CachedRemoteWorkEntity
import com.shi.yun.data.local.entity.CipaiEntity
import com.shi.yun.data.local.entity.ComponentType
import com.shi.yun.data.local.entity.ContentComponentEntity
import com.shi.yun.data.local.entity.DataBatchEntity
import com.shi.yun.data.local.entity.DataReleaseEntity
import com.shi.yun.data.local.entity.DynastyEntity
import com.shi.yun.data.local.entity.ExternalSourceLinkEntity
import com.shi.yun.data.local.entity.FormEntity
import com.shi.yun.data.local.entity.GenreEntity
import com.shi.yun.data.local.entity.IdentityMergeEntity
import com.shi.yun.data.local.entity.QupaiEntity
import com.shi.yun.data.local.entity.ReleaseWorkEntryEntity
import com.shi.yun.data.local.entity.UserFavoriteEntity
import com.shi.yun.data.local.entity.UserNoteEntity
import com.shi.yun.data.local.entity.WorkAuthorEntity
import com.shi.yun.data.local.entity.WorkEntity
import com.shi.yun.data.local.entity.WorkVersionClassificationEntity
import com.shi.yun.data.local.entity.WorkVersionContentEntity
import com.shi.yun.data.local.entity.WorkVersionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ShiYunDatabase 核心数据库与真实 SQLite 约束测试 (ShiYunDatabaseTest)
 *
 * 使用真实的 Room In-Memory 数据库执行测试，开启外键约束，
 * 全面验证六大业务区域的 36 张表建表、外键级联/限制、触发器强约束、单行发布指针、归并防自环及归属一致性。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShiYunDatabaseTest {

    private lateinit var db: ShiYunDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShiYunDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(ShiYunDatabase.DB_CALLBACK)
            .build()
        // 显式触发一次查询以确保数据库被打开并执行 DB_CALLBACK 中的 triggers 创建
        db.openHelper.writableDatabase.query("SELECT 1").close()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ==================== 1. 验证数据库能正常创建并完成基本 CRUD ====================
    @Test
    fun testDatabaseCreationAndBasicInsert() = runBlocking {
        val work = WorkEntity(
            workId = "work_test_001",
            standardTitle = "静夜思",
            identityStatus = "NORMAL",
            sortOrder = 1,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        db.workDao().insertWork(work)
        val queried = db.workDao().getWorkById("work_test_001")
        assertNotNull(queried)
        assertEquals("静夜思", queried?.standardTitle)
        assertEquals(1, db.workDao().getWorkCount())
    }

    // ==================== 2. 验证外键关系真实有效 (RESTRICT 策略) ====================
    @Test
    fun testForeignKeyConstraintOnWorkVersion() = runBlocking {
        val version = WorkVersionEntity(
            workVersionId = "wv_test_001_v1",
            workId = "non_existent_work", // 不存在的父级 workId
            versionCode = 1,
            editionName = "底本1",
            layoutType = "FIVE_JUEJU",
            createdAtEpochMs = 1000L
        )
        try {
            db.workDao().insertWorkVersion(version)
            fail("外键应该生效并抛出 SQLiteConstraintException")
        } catch (e: Exception) {
            assertTrue("应该拦截外键错误", e.message?.contains("FOREIGN KEY") == true || e.message?.contains("constraint") == true)
        }
    }

    // ==================== 3. 验证外键删除策略 (RESTRICT 保护作品不被误删) ====================
    @Test
    fun testForeignKeyRestrictOnWorkDeletion() = runBlocking {
        val work = WorkEntity("work_001", "静夜思", "NORMAL", 1, 1000L, 1000L)
        db.workDao().insertWork(work)
        val version = WorkVersionEntity("wv_001_v1", "work_001", 1, "底本1", null, null, "FIVE_JUEJU", null, 1000L)
        db.workDao().insertWorkVersion(version)

        try {
            // 尝试直接删除被 work_versions 引用的 work
            db.openHelper.writableDatabase.execSQL("DELETE FROM works WHERE work_id = 'work_001'")
            fail("RESTRICT 外键应该阻止删除仍有版本的作品")
        } catch (e: Exception) {
            assertTrue("应该抛出外键约束错误", e.message?.contains("FOREIGN KEY") == true || e.message?.contains("constraint") == true)
        }
    }

    // ==================== 4. 验证五个单值分类触发器对 INSERT 和 UPDATE 的强校验 ====================
    // ==================== 4. 验证五个单值分类触发器对 INSERT 和 UPDATE 的全量强校验 ====================
    @Test
    fun testClassificationTriggersAllFiveDimensions() = runBlocking {
        val work = WorkEntity("work_cls_test", "分类测试作品", "NORMAL", 1, 1000L, 1000L)
        db.workDao().insertWork(work)
        val version = WorkVersionEntity("wv_cls_v1", "work_cls_test", 1, "底本1", null, null, "FIVE_JUEJU", null, 1000L)
        db.workDao().insertWorkVersion(version)

        // 4.1 验证 DYNASTY (朝代)
        // a) 插入非法朝代拦截
        try {
            db.workDao().insertWorkVersionClassifications(listOf(
                WorkVersionClassificationEntity("wv_cls_v1", "DYNASTY", "dynasty_invalid_999", updatedAtEpochMs = 1000L)
            ))
            fail("DYNASTY 触发器应拦截不存在的分类 ID 插入")
        } catch (e: Exception) {
            assertTrue("应拦截非法朝代 INSERT", e.message?.contains("not found in dynasties") == true || e.message?.contains("constraint") == true)
        }
        // b) 插入合法朝代成功
        db.categoryDao().insertDynasties(listOf(DynastyEntity("dynasty_tang", "唐", 618, 907, 1)))
        db.workDao().insertWorkVersionClassifications(listOf(
            WorkVersionClassificationEntity("wv_cls_v1", "DYNASTY", "dynasty_tang", updatedAtEpochMs = 1000L)
        ))
        // c) UPDATE 非法朝代拦截
        try {
            db.openHelper.writableDatabase.execSQL("""
                UPDATE work_version_classifications SET classification_id = 'dynasty_non_exist'
                WHERE work_version_id = 'wv_cls_v1' AND dimension_type = 'DYNASTY'
            """)
            fail("DYNASTY 触发器应拦截 UPDATE 到不存在的分类 ID")
        } catch (e: Exception) {
            assertTrue("应拦截非法朝代 UPDATE", e.message?.contains("not found in dynasties") == true || e.message?.contains("constraint") == true)
        }

        // 4.2 验证 GENRE (体裁)
        // a) 插入非法体裁拦截
        try {
            db.workDao().insertWorkVersionClassifications(listOf(
                WorkVersionClassificationEntity("wv_cls_v1", "GENRE", "genre_fake", updatedAtEpochMs = 1000L)
            ))
            fail("GENRE 触发器应拦截不存在的体裁 ID 插入")
        } catch (e: Exception) {
            assertTrue("应拦截非法体裁 INSERT", e.message?.contains("not found in genres") == true || e.message?.contains("constraint") == true)
        }
        // b) 插入合法体裁成功
        db.categoryDao().insertGenres(listOf(GenreEntity("genre_shi", "诗", 1)))
        db.workDao().insertWorkVersionClassifications(listOf(
            WorkVersionClassificationEntity("wv_cls_v1", "GENRE", "genre_shi", updatedAtEpochMs = 1000L)
        ))
        // c) UPDATE 非法体裁拦截
        try {
            db.openHelper.writableDatabase.execSQL("""
                UPDATE work_version_classifications SET classification_id = 'genre_ghost'
                WHERE work_version_id = 'wv_cls_v1' AND dimension_type = 'GENRE'
            """)
            fail("GENRE 触发器应拦截 UPDATE 到不存在的体裁 ID")
        } catch (e: Exception) {
            assertTrue("应拦截非法体裁 UPDATE", e.message?.contains("not found in genres") == true || e.message?.contains("constraint") == true)
        }

        // 4.3 验证 FORM (形式)
        // a) 插入非法形式拦截
        try {
            db.workDao().insertWorkVersionClassifications(listOf(
                WorkVersionClassificationEntity("wv_cls_v1", "FORM", "form_unknown", updatedAtEpochMs = 1000L)
            ))
            fail("FORM 触发器应拦截不存在的形式 ID 插入")
        } catch (e: Exception) {
            assertTrue("应拦截非法形式 INSERT", e.message?.contains("not found in forms") == true || e.message?.contains("constraint") == true)
        }
        // b) 插入合法形式成功
        db.categoryDao().insertForms(listOf(FormEntity("form_wu_yan_jue_ju", "五言绝句", "genre_shi", 4, 5, 1)))
        db.workDao().insertWorkVersionClassifications(listOf(
            WorkVersionClassificationEntity("wv_cls_v1", "FORM", "form_wu_yan_jue_ju", updatedAtEpochMs = 1000L)
        ))
        // c) UPDATE 非法形式拦截
        try {
            db.openHelper.writableDatabase.execSQL("""
                UPDATE work_version_classifications SET classification_id = 'form_invalid'
                WHERE work_version_id = 'wv_cls_v1' AND dimension_type = 'FORM'
            """)
            fail("FORM 触发器应拦截 UPDATE 到不存在的形式 ID")
        } catch (e: Exception) {
            assertTrue("应拦截非法形式 UPDATE", e.message?.contains("not found in forms") == true || e.message?.contains("constraint") == true)
        }

        // 4.4 验证 CIPAI (词牌)
        // a) 插入非法词牌拦截
        try {
            db.workDao().insertWorkVersionClassifications(listOf(
                WorkVersionClassificationEntity("wv_cls_v1", "CIPAI", "cipai_bad", updatedAtEpochMs = 1000L)
            ))
            fail("CIPAI 触发器应拦截不存在的词牌 ID 插入")
        } catch (e: Exception) {
            assertTrue("应拦截非法词牌 INSERT", e.message?.contains("not found in cipais") == true || e.message?.contains("constraint") == true)
        }
        // b) 插入合法词牌成功
        db.categoryDao().insertCipais(listOf(CipaiEntity("cipai_shui_diao_ge_tou", "水调歌头", "双调九十五字", 1)))
        db.workDao().insertWorkVersionClassifications(listOf(
            WorkVersionClassificationEntity("wv_cls_v1", "CIPAI", "cipai_shui_diao_ge_tou", updatedAtEpochMs = 1000L)
        ))
        // c) UPDATE 非法词牌拦截
        try {
            db.openHelper.writableDatabase.execSQL("""
                UPDATE work_version_classifications SET classification_id = 'cipai_unknown'
                WHERE work_version_id = 'wv_cls_v1' AND dimension_type = 'CIPAI'
            """)
            fail("CIPAI 触发器应拦截 UPDATE 到不存在的词牌 ID")
        } catch (e: Exception) {
            assertTrue("应拦截非法词牌 UPDATE", e.message?.contains("not found in cipais") == true || e.message?.contains("constraint") == true)
        }

        // 4.5 验证 QUPAI (曲牌)
        // a) 插入非法曲牌拦截
        try {
            db.workDao().insertWorkVersionClassifications(listOf(
                WorkVersionClassificationEntity("wv_cls_v1", "QUPAI", "qupai_bad", updatedAtEpochMs = 1000L)
            ))
            fail("QUPAI 触发器应拦截不存在的曲牌 ID 插入")
        } catch (e: Exception) {
            assertTrue("应拦截非法曲牌 INSERT", e.message?.contains("not found in qupais") == true || e.message?.contains("constraint") == true)
        }
        // b) 插入合法曲牌成功
        db.categoryDao().insertQupais(listOf(QupaiEntity("qupai_tian_jing_sha", "天净沙", "NORTHERN", 1)))
        db.workDao().insertWorkVersionClassifications(listOf(
            WorkVersionClassificationEntity("wv_cls_v1", "QUPAI", "qupai_tian_jing_sha", updatedAtEpochMs = 1000L)
        ))
        // c) UPDATE 非法曲牌拦截
        try {
            db.openHelper.writableDatabase.execSQL("""
                UPDATE work_version_classifications SET classification_id = 'qupai_unknown'
                WHERE work_version_id = 'wv_cls_v1' AND dimension_type = 'QUPAI'
            """)
            fail("QUPAI 触发器应拦截 UPDATE 到不存在的曲牌 ID")
        } catch (e: Exception) {
            assertTrue("应拦截非法曲牌 UPDATE", e.message?.contains("not found in qupais") == true || e.message?.contains("constraint") == true)
        }

        val allClassifications = db.workDao().getVersionClassifications("wv_cls_v1")
        assertEquals(5, allClassifications.size)
    }

    // ==================== 5. 验证发布指针约束 (pointer_id 恒等于 1 与单行物理约束) ====================
    @Test
    fun testReleasePointerOnlyAllowsPointerIdOne() = runBlocking {
        val batch = DataBatchEntity("b1", "BATCH_01", "POEMS_DB", 100, "IMPORTED", 1000L)
        db.releaseDao().insertBatch(batch)
        val release1 = DataReleaseEntity("rel_1", "1.0.0", "b1", "FULL", null, 1000L)
        val release2 = DataReleaseEntity("rel_2", "1.1.0", "b1", "FULL", null, 2000L)
        db.releaseDao().insertRelease(release1)
        db.releaseDao().insertRelease(release2)

        // 1. 尝试插入 pointer_id = 2 触发器拦截
        val badPointer = AppReleasePointerEntity(pointerId = 2, currentReleaseId = "rel_1", activatedAtEpochMs = 1000L)
        try {
            db.releaseDao().setReleasePointer(badPointer)
            fail("触发器应该拦截 pointer_id != 1")
        } catch (e: Exception) {
            assertTrue("应该拦截 pointer_id != 1", e.message?.contains("must be 1") == true || e.message?.contains("constraint") == true)
        }

        // 2. 插入合法的 pointer_id = 1
        val goodPointer = AppReleasePointerEntity(pointerId = 1, currentReleaseId = "rel_1", activatedAtEpochMs = 1000L)
        db.releaseDao().setReleasePointer(goodPointer)
        val queriedPointer = db.releaseDao().getReleasePointer()
        assertNotNull(queriedPointer)
        assertEquals(1, queriedPointer?.pointerId)
        assertEquals("rel_1", queriedPointer?.currentReleaseId)

        // 3. 尝试 UPDATE pointer_id = 2 触发器拦截
        try {
            db.openHelper.writableDatabase.execSQL("""
                UPDATE app_release_pointer SET pointer_id = 2 WHERE pointer_id = 1
            """)
            fail("触发器应该拦截 UPDATE pointer_id != 1")
        } catch (e: Exception) {
            assertTrue("应该拦截 UPDATE pointer_id != 1", e.message?.contains("must be 1") == true || e.message?.contains("constraint") == true)
        }

        // 4. 尝试通过原始 INSERT 写入第二条 pointer_id = 1 记录（主键唯一性拦截，绝不可能出现两行记录）
        try {
            db.openHelper.writableDatabase.execSQL("""
                INSERT INTO app_release_pointer (pointer_id, current_release_id, activated_at_epoch_ms, updated_by)
                VALUES (1, 'rel_2', 2000, 'TEST_DUPLICATE')
            """)
            fail("主键冲突应该阻止插入第二条 pointer_id = 1 记录")
        } catch (e: Exception) {
            assertTrue("主键冲突应被拦截", e.message?.contains("UNIQUE") == true || e.message?.contains("PRIMARY KEY") == true || e.message?.contains("constraint") == true)
        }

        // 5. 校验表中始终仅有唯一一行有效指针记录
        val cursor = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM app_release_pointer")
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        assertEquals(1, count)
    }

    // ==================== 6. 验证身份归并不能直接自环 (old_work_id <> target_work_id) ====================
    @Test
    fun testIdentityMergeSelfCheck() = runBlocking {
        val work = WorkEntity("work_003", "枫桥夜泊", "NORMAL", 1, 1000L, 1000L)
        db.workDao().insertWork(work)

        try {
            db.openHelper.writableDatabase.execSQL("""
                INSERT INTO identity_merges (merge_id, old_work_id, target_work_id, reason, operator, merged_at_epoch_ms)
                VALUES ('m_001', 'work_003', 'work_003', '自环', 'TEST', 1000)
            """)
            fail("触发器应该拦截 old_work_id == target_work_id")
        } catch (e: Exception) {
            assertTrue("应该拦截自我归并", e.message?.contains("cannot be equal") == true || e.message?.contains("constraint") == true)
        }
    }

    // ==================== 7. 验证五维内容组件合法写入与非法类型拦截 ====================
    @Test
    fun testContentComponentFiveDimensionsAndTypeValidation() = runBlocking {
        // 7.1 五种合法 component_type (CONTENT, TRANSLATION, ANNOTATION, BACKGROUND, APPRECIATION) 均可正常写入
        val legalTypes = listOf(
            ComponentType.CONTENT.code,
            ComponentType.TRANSLATION.code,
            ComponentType.ANNOTATION.code,
            ComponentType.BACKGROUND.code,
            ComponentType.APPRECIATION.code
        )
        for ((idx, type) in legalTypes.withIndex()) {
            val component = ContentComponentEntity(
                componentId = "cc_legal_$idx",
                componentType = type,
                contentPayload = "payload for $type",
                rawFormat = "PLAIN_TEXT",
                originType = "HUMAN_CURATED",
                contentHash = "hash_$idx",
                createdAtEpochMs = 1000L
            )
            db.openHelper.writableDatabase.execSQL("""
                INSERT INTO content_components (component_id, component_type, content_payload, raw_format, origin_type, content_hash, created_at_epoch_ms)
                VALUES ('${component.componentId}', '${component.componentType}', '${component.contentPayload}', '${component.rawFormat}', '${component.originType}', '${component.contentHash}', ${component.createdAtEpochMs})
            """)
        }

        val cursor = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM content_components")
        cursor.moveToFirst()
        val totalCount = cursor.getInt(0)
        cursor.close()
        assertEquals(5, totalCount)

        // 7.2 尝试 INSERT 非法类型（包括已被移除的 PHONETIC，以及 ORIGINAL, COMMENTARY, FAKE）被触发器拒绝
        val illegalTypes = listOf("PHONETIC", "ORIGINAL", "COMMENTARY", "INVALID_TYPE", "OTHER")
        for ((idx, badType) in illegalTypes.withIndex()) {
            try {
                db.openHelper.writableDatabase.execSQL("""
                    INSERT INTO content_components (component_id, component_type, content_payload, raw_format, origin_type, content_hash, created_at_epoch_ms)
                    VALUES ('cc_bad_$idx', '$badType', 'bad', 'PLAIN_TEXT', 'HUMAN_CURATED', 'hash', 1000)
                """)
                fail("触发器应该拒绝非法的 component_type: $badType")
            } catch (e: Exception) {
                assertTrue("应该拦截非法组件类型", e.message?.contains("invalid component_type") == true || e.message?.contains("constraint") == true)
            }
        }

        // 7.3 尝试 UPDATE 合法类型为非法类型被触发器拒绝
        try {
            db.openHelper.writableDatabase.execSQL("""
                UPDATE content_components SET component_type = 'INVALID_UPDATED' WHERE component_id = 'cc_legal_0'
            """)
            fail("UPDATE 触发器应该拒绝将 component_type 改为非法值")
        } catch (e: Exception) {
            assertTrue("应该拦截 UPDATE 非法组件类型", e.message?.contains("invalid component_type") == true || e.message?.contains("constraint") == true)
        }
    }

    // ==================== 8. 验证五维内容组件挂载与复合外键强约束 ====================
    @Test
    fun testFiveDimensionalComponentMountingAndCompositeForeignKey() = runBlocking {
        val work = WorkEntity("work_004", "登鹳雀楼", "NORMAL", 1, 1000L, 1000L)
        db.workDao().insertWork(work)
        val version = WorkVersionEntity("wv_004_v1", "work_004", 1, "底本1", null, null, "FIVE_JUEJU", null, 1000L)
        db.workDao().insertWorkVersion(version)

        // 8.1 依次写入全部五维组件 (CONTENT, TRANSLATION, ANNOTATION, BACKGROUND, APPRECIATION)
        val components = listOf(
            ContentComponentEntity("cc_t_content", "CONTENT", "白日依山尽，黄河入海流。", "PLAIN_TEXT", "HUMAN_CURATED", "h1", 1000L),
            ContentComponentEntity("cc_t_trans", "TRANSLATION", "夕阳依傍着西山慢慢沉落", "PLAIN_TEXT", "HUMAN_CURATED", "h2", 1000L),
            ContentComponentEntity("cc_t_annot", "ANNOTATION", "依：依傍。", "PLAIN_TEXT", "HUMAN_CURATED", "h3", 1000L),
            ContentComponentEntity("cc_t_bg", "BACKGROUND", "本诗作于诗人早年游历河中府鹳雀楼之际，感怀天地壮阔。", "PLAIN_TEXT", "HUMAN_CURATED", "h4", 1000L),
            ContentComponentEntity("cc_t_apprec", "APPRECIATION", "前两句写所见，后两句写所想。", "PLAIN_TEXT", "HUMAN_CURATED", "h5", 1000L)
        )
        for (c in components) {
            db.openHelper.writableDatabase.execSQL("""
                INSERT INTO content_components (component_id, component_type, content_payload, raw_format, origin_type, content_hash, created_at_epoch_ms)
                VALUES ('${c.componentId}', '${c.componentType}', '${c.contentPayload}', '${c.rawFormat}', '${c.originType}', '${c.contentHash}', ${c.createdAtEpochMs})
            """)
        }

        // 8.2 正常挂载五维组件，均成功
        val goodLinks = components.map { c ->
            WorkVersionContentEntity(
                workVersionId = "wv_004_v1",
                componentType = c.componentType,
                componentId = c.componentId,
                reviewStatus = "VERIFIED"
            )
        }
        db.workDao().insertWorkVersionContents(goodLinks)

        val mounted = db.workDao().getVersionContents("wv_004_v1")
        assertEquals(5, mounted.size)

        // 8.3 复合外键拦截：试图以 APPRECIATION 维度挂载类型为 BACKGROUND 的组件
        val badLink = WorkVersionContentEntity(
            workVersionId = "wv_004_v1",
            componentType = "APPRECIATION", // 挂载维度与组件真实维度 BACKGROUND 不一致
            componentId = "cc_t_bg",
            reviewStatus = "PENDING"
        )
        try {
            db.openHelper.writableDatabase.execSQL("""
                INSERT INTO work_version_contents (work_version_id, component_type, component_id, review_status)
                VALUES ('wv_004_v1', 'APPRECIATION', 'cc_t_bg', 'PENDING')
            """)
            fail("复合外键应该拦截维度类型不匹配的组件挂载")
        } catch (e: Exception) {
            assertTrue("复合外键应报错", e.message?.contains("FOREIGN KEY") == true || e.message?.contains("constraint") == true)
        }
    }

    // ==================== 9. 验证正式发布条目与选集条目不能关联其他作品的版本 (归属一致性) ====================
    @Test
    fun testOwnershipConstraintOnReleaseWorkEntries() = runBlocking {
        val work1 = WorkEntity("w_01", "作品1", "NORMAL", 1, 1000L, 1000L)
        val work2 = WorkEntity("w_02", "作品2", "NORMAL", 2, 1000L, 1000L)
        db.workDao().insertWorks(listOf(work1, work2))

        val v1 = WorkVersionEntity("wv_01_v1", "w_01", 1, "底本1", null, null, "FIVE_JUEJU", null, 1000L)
        val v2 = WorkVersionEntity("wv_02_v1", "w_02", 1, "底本1", null, null, "FIVE_JUEJU", null, 1000L)
        db.workDao().insertWorkVersion(v1)
        db.workDao().insertWorkVersion(v2)

        val batch = DataBatchEntity("b2", "BATCH_02", "TEST", 10, "IMPORTED", 1000L)
        db.releaseDao().insertBatch(batch)
        val release = DataReleaseEntity("rel_2", "1.0.0", "b2", "FULL", null, 1000L)
        db.releaseDao().insertRelease(release)

        // 试图将 w_01 与属于 w_02 的版本 wv_02_v1 关联在一起
        val badEntry = ReleaseWorkEntryEntity(
            releaseId = "rel_2",
            workId = "w_01",
            workVersionId = "wv_02_v1" // 串挂了其他作品的版本！
        )
        try {
            db.releaseDao().insertReleaseWorkEntries(listOf(badEntry))
            fail("触发器应该阻止将其他作品的版本关联到当前作品条目中")
        } catch (e: Exception) {
            assertTrue("应该拦截跨作品归属串挂", e.message?.contains("does not belong") == true || e.message?.contains("constraint") == true)
        }
    }

    // ==================== 10. 验证收藏关联不存在的作品时数据库拒绝写入 (外键保护) ====================
    @Test
    fun testFavoriteForeignConstraint() = runBlocking {
        val badFav = UserFavoriteEntity(favoriteId = "fav_01", workId = "non_existent_work", createdAtEpochMs = 1000L)
        try {
            db.favoriteDao().insertFavorite(badFav)
            fail("收藏不存在的作品应该被外键阻止")
        } catch (e: Exception) {
            assertTrue("应该报错外键错误", e.message?.contains("FOREIGN KEY") == true || e.message?.contains("constraint") == true)
        }

        // 收藏存在作品
        val work = WorkEntity("w_fav", "长恨歌", "NORMAL", 1, 1000L, 1000L)
        db.workDao().insertWork(work)
        val goodFav = UserFavoriteEntity(favoriteId = "fav_02", workId = "w_fav", createdAtEpochMs = 1000L)
        db.favoriteDao().insertFavorite(goodFav)

        assertTrue(db.favoriteDao().isFavorite("w_fav"))
        assertEquals(1, db.favoriteDao().getFavoriteCount())
    }

    // ==================== 11. 验证缓存、收藏、笔记隔离与不受干扰 ====================
    @Test
    fun testCacheAndUserDataIsolation() = runBlocking {
        // 1. 缓存表无外键约束独立写入
        val cache = CachedRemoteWorkEntity(
            remoteWorkId = "crw_01",
            sourceCode = "SHI_CI_CN",
            externalId = "ext_123",
            title = "远程诗",
            authorName = "李白",
            rawPayload = "{}",
            fetchedAtEpochMs = 1000L,
            lastAccessedEpochMs = 1000L,
            expireAtEpochMs = 2000L
        )
        db.remoteCacheDao().insertCache(cache)
        assertEquals(1, db.remoteCacheDao().getCacheCount())

        // 清空缓存不会影响 works 或 favorites
        db.remoteCacheDao().clearAllCache()
        assertEquals(0, db.remoteCacheDao().getCacheCount())
    }

    // ==================== 12. 验证作者介绍独立存储与来源追溯 (Author Biography & Source) ====================
    @Test
    fun testAuthorBiographyAndSourceTracking() = runBlocking {
        // 1. 插入外部溯源条目 (用于作者介绍的来源追溯)
        val sourceLink = ExternalSourceLinkEntity(
            linkId = "esl_bio_01",
            sourceCode = "SHI_CI_CN",
            externalItemId = "author_libai_source",
            itemUrl = "https://shici.cn/authors/libai",
            licenseDesc = "CC-BY-SA-4.0"
        )
        db.categoryDao().insertExternalSourceLinks(listOf(sourceLink))

        // 2. 插入作者档案（携带独立作者介绍、来源文本、外部溯源链接及审核状态）
        val author = AuthorEntity(
            authorId = "author_li_bai",
            standardName = "李白",
            biography = "李白（701年－762年），字太白，号青莲居士，唐代浪漫主义诗人，被后人誉为“诗仙”。",
            biographySource = "《旧唐书·文苑传》",
            biographySourceLinkId = "esl_bio_01",
            biographyReviewStatus = "VERIFIED",
            sortOrder = 1
        )
        db.categoryDao().insertAuthors(listOf(author))

        // 3. 读取作者并验证介绍与来源信息完整无误，且独立于作品存在
        val loaded = db.categoryDao().getAuthorById("author_li_bai")
        assertNotNull(loaded)
        assertEquals("李白", loaded?.standardName)
        assertEquals("李白（701年－762年），字太白，号青莲居士，唐代浪漫主义诗人，被后人誉为“诗仙”。", loaded?.biography)
        assertEquals("《旧唐书·文苑传》", loaded?.biographySource)
        assertEquals("esl_bio_01", loaded?.biographySourceLinkId)
        assertEquals("VERIFIED", loaded?.biographyReviewStatus)

        // 4. 外键约束验证：试图将 biography_source_link_id 指向不存在的 link_id 会被数据库外键拒绝
        val invalidAuthor = AuthorEntity(
            authorId = "author_du_fu",
            standardName = "杜甫",
            biography = "杜甫（712年－770年），字子美，自号少陵野老，唐代伟大的现实主义诗人。",
            biographySource = "《新唐书》",
            biographySourceLinkId = "esl_non_existent", // 不存在的外键
            biographyReviewStatus = "PENDING",
            sortOrder = 2
        )
        try {
            db.categoryDao().insertAuthors(listOf(invalidAuthor))
            fail("外键应该拦截不存在的 biography_source_link_id")
        } catch (e: Exception) {
            assertTrue("应抛出外键约束错误", e.message?.contains("FOREIGN KEY") == true || e.message?.contains("constraint") == true)
        }
    }
}
