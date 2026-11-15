package com.shi.yun.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shi.yun.data.local.converter.RoomConverters
import com.shi.yun.data.local.dao.AnthologyDao
import com.shi.yun.data.local.dao.CategoryDao
import com.shi.yun.data.local.dao.FavoriteDao
import com.shi.yun.data.local.dao.ReleaseDao
import com.shi.yun.data.local.dao.RemoteCacheDao
import com.shi.yun.data.local.dao.WorkDao
import com.shi.yun.data.local.entity.AnthologyBookEntity
import com.shi.yun.data.local.entity.AnthologyBookVersionEntity
import com.shi.yun.data.local.entity.AnthologyItemEntity
import com.shi.yun.data.local.entity.AnthologyNodeEntity
import com.shi.yun.data.local.entity.AppDatabaseMetadataEntity
import com.shi.yun.data.local.entity.AppReleasePointerEntity
import com.shi.yun.data.local.entity.AuthorAliasEntity
import com.shi.yun.data.local.entity.AuthorEntity
import com.shi.yun.data.local.entity.CachedRemoteWorkEntity
import com.shi.yun.data.local.entity.CipaiAliasEntity
import com.shi.yun.data.local.entity.CipaiEntity
import com.shi.yun.data.local.entity.ContentAttributionEntity
import com.shi.yun.data.local.entity.ContentComponentEntity
import com.shi.yun.data.local.entity.DataBatchEntity
import com.shi.yun.data.local.entity.DataReleaseEntity
import com.shi.yun.data.local.entity.DynastyEntity
import com.shi.yun.data.local.entity.ExternalSourceLinkEntity
import com.shi.yun.data.local.entity.FormEntity
import com.shi.yun.data.local.entity.GenreEntity
import com.shi.yun.data.local.entity.IdentityMergeEntity
import com.shi.yun.data.local.entity.QupaiAliasEntity
import com.shi.yun.data.local.entity.QupaiEntity
import com.shi.yun.data.local.entity.ReleaseWorkEntryEntity
import com.shi.yun.data.local.entity.TagAliasEntity
import com.shi.yun.data.local.entity.TagEntity
import com.shi.yun.data.local.entity.UserAnthologyEntity
import com.shi.yun.data.local.entity.UserAnthologyItemEntity
import com.shi.yun.data.local.entity.UserFavoriteEntity
import com.shi.yun.data.local.entity.UserNoteEntity
import com.shi.yun.data.local.entity.WorkAuthorEntity
import com.shi.yun.data.local.entity.WorkEntity
import com.shi.yun.data.local.entity.WorkSourceLinkEntity
import com.shi.yun.data.local.entity.WorkVersionClassificationEntity
import com.shi.yun.data.local.entity.WorkVersionContentEntity
import com.shi.yun.data.local.entity.WorkVersionEntity
import com.shi.yun.data.local.entity.WorkVersionTagEntity

/**
 * 诗云核心数据库 (ShiYunDatabase)
 *
 * 聚合六大业务区域的全部 36 张数据表，通过 SQLite 触发器保障单值分类多态外键、
 * 发布指针单行限制、身份归并防自环、五维内容组件类型及版本一致性等数据库级完整性。
 */
@Database(
    entities = [
        // 一、作品与作者区 (5 张表)
        WorkEntity::class,
        WorkVersionEntity::class,
        AuthorEntity::class,
        AuthorAliasEntity::class,
        WorkAuthorEntity::class,

        // 二、七维分类字典区 (9 张表)
        DynastyEntity::class,
        GenreEntity::class,
        FormEntity::class,
        CipaiEntity::class,
        CipaiAliasEntity::class,
        QupaiEntity::class,
        QupaiAliasEntity::class,
        TagEntity::class,
        TagAliasEntity::class,

        // 三、内容版本与来源追溯区 (7 张表)
        ContentComponentEntity::class,
        WorkVersionContentEntity::class,
        WorkVersionClassificationEntity::class,
        WorkVersionTagEntity::class,
        ExternalSourceLinkEntity::class,
        WorkSourceLinkEntity::class,
        ContentAttributionEntity::class,

        // 四、正式批次与发布区 (9 张表)
        DataBatchEntity::class,
        DataReleaseEntity::class,
        ReleaseWorkEntryEntity::class,
        AnthologyBookEntity::class,
        AnthologyBookVersionEntity::class,
        AnthologyNodeEntity::class,
        AnthologyItemEntity::class,
        AppReleasePointerEntity::class,
        AppDatabaseMetadataEntity::class,

        // 五、在线作品缓存区 (1 张表)
        CachedRemoteWorkEntity::class,

        // 六、用户私有资产区 (5 张表)
        UserFavoriteEntity::class,
        UserAnthologyEntity::class,
        UserAnthologyItemEntity::class,
        UserNoteEntity::class,
        IdentityMergeEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class ShiYunDatabase : RoomDatabase() {

    abstract fun workDao(): WorkDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun anthologyDao(): AnthologyDao
    abstract fun remoteCacheDao(): RemoteCacheDao
    abstract fun releaseDao(): ReleaseDao

    companion object {
        const val DATABASE_NAME = "shiyun.db"

        /**
         * 数据库初始化回调：开启外键约束，创建真实 SQLite 完整性触发器
         */
        val DB_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // 1. 严格开启外键约束
                db.execSQL("PRAGMA foreign_keys = ON;")

                // 2. 建立 SQLite 触发器与物理约束
                createTriggers(db)
            }

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // 首次建库执行
                db.execSQL("PRAGMA foreign_keys = ON;")
                createTriggers(db)
            }
        }

        /**
         * 创建在 SQLite 层面的物理完整性触发器
         */
        fun createTriggers(db: SupportSQLiteDatabase) {
            // ==================== 1. 五个单值分类维度外键校验触发器 (INSERT & UPDATE) ====================
            // 1.1 DYNASTY
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_wvc_check_dynasty_insert
                BEFORE INSERT ON work_version_classifications
                WHEN NEW.dimension_type = 'DYNASTY' AND NEW.classification_id IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Foreign key mismatch: classification_id not found in dynasties')
                    WHERE (SELECT 1 FROM dynasties WHERE dynasty_id = NEW.classification_id) IS NULL;
                END;
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_wvc_check_dynasty_update
                BEFORE UPDATE ON work_version_classifications
                WHEN NEW.dimension_type = 'DYNASTY' AND NEW.classification_id IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Foreign key mismatch: classification_id not found in dynasties')
                    WHERE (SELECT 1 FROM dynasties WHERE dynasty_id = NEW.classification_id) IS NULL;
                END;
            """)

            // 1.2 GENRE
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_wvc_check_genre_insert
                BEFORE INSERT ON work_version_classifications
                WHEN NEW.dimension_type = 'GENRE' AND NEW.classification_id IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Foreign key mismatch: classification_id not found in genres')
                    WHERE (SELECT 1 FROM genres WHERE genre_id = NEW.classification_id) IS NULL;
                END;
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_wvc_check_genre_update
                BEFORE UPDATE ON work_version_classifications
                WHEN NEW.dimension_type = 'GENRE' AND NEW.classification_id IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Foreign key mismatch: classification_id not found in genres')
                    WHERE (SELECT 1 FROM genres WHERE genre_id = NEW.classification_id) IS NULL;
                END;
            """)

            // 1.3 FORM
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_wvc_check_form_insert
                BEFORE INSERT ON work_version_classifications
                WHEN NEW.dimension_type = 'FORM' AND NEW.classification_id IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Foreign key mismatch: classification_id not found in forms')
                    WHERE (SELECT 1 FROM forms WHERE form_id = NEW.classification_id) IS NULL;
                END;
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_wvc_check_form_update
                BEFORE UPDATE ON work_version_classifications
                WHEN NEW.dimension_type = 'FORM' AND NEW.classification_id IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Foreign key mismatch: classification_id not found in forms')
                    WHERE (SELECT 1 FROM forms WHERE form_id = NEW.classification_id) IS NULL;
                END;
            """)

            // 1.4 CIPAI
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_wvc_check_cipai_insert
                BEFORE INSERT ON work_version_classifications
                WHEN NEW.dimension_type = 'CIPAI' AND NEW.classification_id IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Foreign key mismatch: classification_id not found in cipais')
                    WHERE (SELECT 1 FROM cipais WHERE cipai_id = NEW.classification_id) IS NULL;
                END;
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_wvc_check_cipai_update
                BEFORE UPDATE ON work_version_classifications
                WHEN NEW.dimension_type = 'CIPAI' AND NEW.classification_id IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Foreign key mismatch: classification_id not found in cipais')
                    WHERE (SELECT 1 FROM cipais WHERE cipai_id = NEW.classification_id) IS NULL;
                END;
            """)

            // 1.5 QUPAI
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_wvc_check_qupai_insert
                BEFORE INSERT ON work_version_classifications
                WHEN NEW.dimension_type = 'QUPAI' AND NEW.classification_id IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Foreign key mismatch: classification_id not found in qupais')
                    WHERE (SELECT 1 FROM qupais WHERE qupai_id = NEW.classification_id) IS NULL;
                END;
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_wvc_check_qupai_update
                BEFORE UPDATE ON work_version_classifications
                WHEN NEW.dimension_type = 'QUPAI' AND NEW.classification_id IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Foreign key mismatch: classification_id not found in qupais')
                    WHERE (SELECT 1 FROM qupais WHERE qupai_id = NEW.classification_id) IS NULL;
                END;
            """)

            // ==================== 2. 发布指针单行约束 (pointer_id 必须为 1) ====================
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_release_pointer_check_insert
                BEFORE INSERT ON app_release_pointer
                WHEN NEW.pointer_id != 1
                BEGIN
                    SELECT RAISE(ABORT, 'Constraint violation: app_release_pointer.pointer_id must be 1');
                END;
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_release_pointer_check_update
                BEFORE UPDATE OF pointer_id ON app_release_pointer
                WHEN NEW.pointer_id != 1
                BEGIN
                    SELECT RAISE(ABORT, 'Constraint violation: app_release_pointer.pointer_id must be 1');
                END;
            """)

            // ==================== 3. 身份归并防直接自环约束 (old_work_id <> target_work_id) ====================
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_identity_merges_check_self_insert
                BEFORE INSERT ON identity_merges
                WHEN NEW.old_work_id = NEW.target_work_id
                BEGIN
                    SELECT RAISE(ABORT, 'Constraint violation: old_work_id cannot be equal to target_work_id');
                END;
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_identity_merges_check_self_update
                BEFORE UPDATE ON identity_merges
                WHEN NEW.old_work_id = NEW.target_work_id
                BEGIN
                    SELECT RAISE(ABORT, 'Constraint violation: old_work_id cannot be equal to target_work_id');
                END;
            """)

            // ==================== 4. 内容组件类型合法性约束 ====================
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_content_components_check_type_insert
                BEFORE INSERT ON content_components
                WHEN NEW.component_type NOT IN ('CONTENT', 'TRANSLATION', 'ANNOTATION', 'BACKGROUND', 'APPRECIATION')
                BEGIN
                    SELECT RAISE(ABORT, 'Constraint violation: invalid component_type');
                END;
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_content_components_check_type_update
                BEFORE UPDATE OF component_type ON content_components
                WHEN NEW.component_type NOT IN ('CONTENT', 'TRANSLATION', 'ANNOTATION', 'BACKGROUND', 'APPRECIATION')
                BEGIN
                    SELECT RAISE(ABORT, 'Constraint violation: invalid component_type');
                END;
            """)

            // ==================== 5. 版本归属一致性触发器 (Release & Anthology) ====================
            // 5.1 release_work_entries: work_version_id 必须属于同一 work_id
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_release_work_entries_check_ownership_insert
                BEFORE INSERT ON release_work_entries
                BEGIN
                    SELECT RAISE(ABORT, 'Constraint violation: work_version_id does not belong to the specified work_id')
                    WHERE (SELECT work_id FROM work_versions WHERE work_version_id = NEW.work_version_id) != NEW.work_id;
                END;
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_release_work_entries_check_ownership_update
                BEFORE UPDATE ON release_work_entries
                BEGIN
                    SELECT RAISE(ABORT, 'Constraint violation: work_version_id does not belong to the specified work_id')
                    WHERE (SELECT work_id FROM work_versions WHERE work_version_id = NEW.work_version_id) != NEW.work_id;
                END;
            """)

            // 5.2 anthology_items: work_version_id 必须属于同一 work_id
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_anthology_items_check_ownership_insert
                BEFORE INSERT ON anthology_items
                BEGIN
                    SELECT RAISE(ABORT, 'Constraint violation: work_version_id does not belong to the specified work_id')
                    WHERE (SELECT work_id FROM work_versions WHERE work_version_id = NEW.work_version_id) != NEW.work_id;
                END;
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_anthology_items_check_ownership_update
                BEFORE UPDATE ON anthology_items
                BEGIN
                    SELECT RAISE(ABORT, 'Constraint violation: work_version_id does not belong to the specified work_id')
                    WHERE (SELECT work_id FROM work_versions WHERE work_version_id = NEW.work_version_id) != NEW.work_id;
                END;
            """)
        }

        @Volatile
        private var INSTANCE: ShiYunDatabase? = null

        fun getInstance(context: Context): ShiYunDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShiYunDatabase::class.java,
                    DATABASE_NAME
                )
                    .createFromAsset("database/shiyun.db")
                    .addCallback(DB_CALLBACK)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
