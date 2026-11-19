package com.shi.yun.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shi.yun.data.local.entity.AuthorEntity
import com.shi.yun.data.local.entity.ContentAttributionEntity
import com.shi.yun.data.local.entity.ContentComponentEntity
import com.shi.yun.data.local.entity.ExternalSourceLinkEntity
import com.shi.yun.data.local.entity.WorkAuthorEntity
import com.shi.yun.data.local.entity.WorkEntity
import com.shi.yun.data.local.entity.WorkSourceLinkEntity
import com.shi.yun.data.local.entity.WorkVersionClassificationEntity
import com.shi.yun.data.local.entity.WorkVersionContentEntity
import com.shi.yun.data.local.entity.WorkVersionEntity
import com.shi.yun.data.local.entity.WorkVersionTagEntity
import kotlinx.coroutines.flow.Flow

/**
 * 作品核心与版本内容数据访问对象 (WorkDao)
 */
@Dao
interface WorkDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWork(work: WorkEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorks(works: List<WorkEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkVersion(version: WorkVersionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkAuthors(authors: List<WorkAuthorEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkVersionContents(contents: List<WorkVersionContentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkVersionClassifications(classifications: List<WorkVersionClassificationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkVersionTags(tags: List<WorkVersionTagEntity>)

    @Query("SELECT * FROM works WHERE work_id = :workId")
    suspend fun getWorkById(workId: String): WorkEntity?

    @Query("SELECT * FROM works WHERE identity_status != 'DEPRECATED' ORDER BY sort_order ASC, created_at_epoch_ms ASC")
    suspend fun getAllActiveWorks(): List<WorkEntity>

    @Query("""
        SELECT 
            w.work_id AS work_id,
            w.standard_title AS standard_title,
            COALESCE(a.standard_name, '佚名') AS author_name,
            COALESCE(d.standard_name, da.standard_name, '') AS dynasty_name,
            COALESCE(g.standard_name, wvc_g.classification_id, 'poem') AS genre_code,
            COALESCE(wv.excerpt, substr(cc.content_payload, 1, instr(cc.content_payload || char(10), char(10)) - 1), '') AS excerpt
        FROM works w
        LEFT JOIN release_work_entries rwe ON w.work_id = rwe.work_id
        LEFT JOIN app_release_pointer arp ON rwe.release_id = arp.current_release_id AND arp.pointer_id = 1
        LEFT JOIN work_versions wv ON (
            (rwe.work_version_id IS NOT NULL AND wv.work_version_id = rwe.work_version_id)
            OR (rwe.work_version_id IS NULL AND wv.work_id = w.work_id)
        )
        LEFT JOIN work_authors wa ON wv.work_version_id = wa.work_version_id AND wa.author_order = 1
        LEFT JOIN authors a ON wa.author_id = a.author_id
        LEFT JOIN work_version_classifications wvc_d ON wv.work_version_id = wvc_d.work_version_id AND wvc_d.dimension_type = 'DYNASTY'
        LEFT JOIN dynasties d ON wvc_d.classification_id = d.dynasty_id
        LEFT JOIN dynasties da ON a.dynasty_id = da.dynasty_id
        LEFT JOIN work_version_classifications wvc_g ON wv.work_version_id = wvc_g.work_version_id AND wvc_g.dimension_type = 'GENRE'
        LEFT JOIN genres g ON wvc_g.classification_id = g.genre_id
        LEFT JOIN work_version_contents wvc_c ON wv.work_version_id = wvc_c.work_version_id AND wvc_c.component_type = 'CONTENT'
        LEFT JOIN content_components cc ON wvc_c.component_id = cc.component_id
        WHERE w.identity_status != 'DEPRECATED'
        GROUP BY w.work_id
        ORDER BY w.sort_order ASC, w.created_at_epoch_ms ASC
    """)
    suspend fun getAllWorkListProjections(): List<WorkListItemProjection>

    @Query("SELECT work_id FROM release_work_entries WHERE release_id = :releaseId")
    suspend fun getWorkIdsByRelease(releaseId: String): List<String>

    @Query("""
        SELECT 
            w.work_id AS work_id,
            w.standard_title AS standard_title,
            COALESCE(a.standard_name, '佚名') AS author_name,
            COALESCE(d.standard_name, da.standard_name, '') AS dynasty_name,
            COALESCE(g.standard_name, wvc_g.classification_id, 'poem') AS genre_code,
            COALESCE(wv.excerpt, substr(cc.content_payload, 1, instr(cc.content_payload || char(10), char(10)) - 1), '') AS excerpt
        FROM works w
        LEFT JOIN release_work_entries rwe ON w.work_id = rwe.work_id AND rwe.release_id = 'release_v1_1'
        LEFT JOIN work_versions wv ON (
            (rwe.work_version_id IS NOT NULL AND wv.work_version_id = rwe.work_version_id)
            OR (rwe.work_version_id IS NULL AND wv.work_id = w.work_id)
        )
        LEFT JOIN work_authors wa ON wv.work_version_id = wa.work_version_id AND wa.author_order = 1
        LEFT JOIN authors a ON wa.author_id = a.author_id
        LEFT JOIN work_version_classifications wvc_d ON wv.work_version_id = wvc_d.work_version_id AND wvc_d.dimension_type = 'DYNASTY'
        LEFT JOIN dynasties d ON wvc_d.classification_id = d.dynasty_id
        LEFT JOIN dynasties da ON a.dynasty_id = da.dynasty_id
        LEFT JOIN work_version_classifications wvc_g ON wv.work_version_id = wvc_g.work_version_id AND wvc_g.dimension_type = 'GENRE'
        LEFT JOIN genres g ON wvc_g.classification_id = g.genre_id
        LEFT JOIN work_version_contents wvc_c ON wv.work_version_id = wvc_c.work_version_id AND wvc_c.component_type = 'CONTENT'
        LEFT JOIN content_components cc ON wvc_c.component_id = cc.component_id
        WHERE w.work_id IN (:workIds)
        GROUP BY w.work_id
    """)
    suspend fun getWorkListProjectionsByIds(workIds: List<String>): List<WorkListItemProjection>

    @Query("SELECT * FROM work_versions WHERE work_id = :workId ORDER BY version_code DESC")
    suspend fun getVersionsByWorkId(workId: String): List<WorkVersionEntity>

    @Query("SELECT * FROM work_versions WHERE work_version_id = :workVersionId")
    suspend fun getVersionById(workVersionId: String): WorkVersionEntity?

    @Query("SELECT * FROM work_version_contents WHERE work_version_id = :workVersionId")
    suspend fun getVersionContents(workVersionId: String): List<WorkVersionContentEntity>

    @Query("SELECT * FROM work_authors WHERE work_version_id = :workVersionId ORDER BY author_order ASC")
    suspend fun getVersionAuthors(workVersionId: String): List<WorkAuthorEntity>

    @Query("SELECT * FROM work_version_classifications WHERE work_version_id = :workVersionId")
    suspend fun getVersionClassifications(workVersionId: String): List<WorkVersionClassificationEntity>

    @Query("SELECT * FROM work_version_tags WHERE work_version_id = :workVersionId")
    suspend fun getVersionTags(workVersionId: String): List<WorkVersionTagEntity>

    @Query("SELECT * FROM works WHERE identity_status != 'DEPRECATED' ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomWork(): WorkEntity?

    @Query("""
        SELECT w.* FROM works w
        INNER JOIN work_versions wv ON w.work_id = wv.work_id
        INNER JOIN work_version_contents wvc ON wv.work_version_id = wvc.work_version_id AND wvc.component_type = 'CONTENT'
        INNER JOIN content_components cc ON wvc.component_id = cc.component_id
        LEFT JOIN work_authors wa ON wv.work_version_id = wa.work_version_id
        LEFT JOIN authors a ON wa.author_id = a.author_id
        WHERE w.identity_status != 'DEPRECATED'
          AND (w.standard_title LIKE '%' || :keyword || '%'
               OR cc.content_payload LIKE '%' || :keyword || '%'
               OR a.standard_name LIKE '%' || :keyword || '%')
        GROUP BY w.work_id
        ORDER BY w.sort_order ASC
    """)
    suspend fun searchWorks(keyword: String): List<WorkEntity>

    @Query("""
        SELECT 
            w.work_id AS work_id,
            w.standard_title AS standard_title,
            COALESCE(a.standard_name, '佚名') AS author_name,
            COALESCE(d.standard_name, da.standard_name, '') AS dynasty_name,
            COALESCE(g.standard_name, wvc_g.classification_id, 'poem') AS genre_code,
            COALESCE(wv.excerpt, substr(cc.content_payload, 1, instr(cc.content_payload || char(10), char(10)) - 1), '') AS excerpt
        FROM works w
        INNER JOIN work_versions wv ON w.work_id = wv.work_id
        INNER JOIN work_version_contents wvc ON wv.work_version_id = wvc.work_version_id AND wvc.component_type = 'CONTENT'
        INNER JOIN content_components cc ON wvc.component_id = cc.component_id
        LEFT JOIN work_authors wa ON wv.work_version_id = wa.work_version_id
        LEFT JOIN authors a ON wa.author_id = a.author_id
        LEFT JOIN work_version_classifications wvc_d ON wv.work_version_id = wvc_d.work_version_id AND wvc_d.dimension_type = 'DYNASTY'
        LEFT JOIN dynasties d ON wvc_d.classification_id = d.dynasty_id
        LEFT JOIN dynasties da ON a.dynasty_id = da.dynasty_id
        LEFT JOIN work_version_classifications wvc_g ON wv.work_version_id = wvc_g.work_version_id AND wvc_g.dimension_type = 'GENRE'
        LEFT JOIN genres g ON wvc_g.classification_id = g.genre_id
        WHERE w.identity_status != 'DEPRECATED'
          AND (w.standard_title LIKE '%' || :keyword || '%'
               OR cc.content_payload LIKE '%' || :keyword || '%'
               OR a.standard_name LIKE '%' || :keyword || '%'
               OR d.standard_name LIKE '%' || :keyword || '%'
               OR da.standard_name LIKE '%' || :keyword || '%')
        GROUP BY w.work_id
        ORDER BY
            CASE
                WHEN a.standard_name = :keyword THEN 1
                WHEN w.standard_title = :keyword THEN 2
                WHEN a.standard_name LIKE :keyword || '%' THEN 3
                WHEN w.standard_title LIKE :keyword || '%' THEN 4
                ELSE 5
            END,
            w.sort_order ASC
        LIMIT :limit
    """)
    suspend fun searchWorkProjections(keyword: String, limit: Int = 100): List<WorkListItemProjection>

    @Query("SELECT COUNT(*) FROM works")
    suspend fun getWorkCount(): Int

    @Query("SELECT * FROM works WHERE standard_title = :title AND identity_status != 'DEPRECATED'")
    suspend fun getWorksByTitle(title: String): List<WorkEntity>

    @Query("""
        SELECT w.* FROM works w
        INNER JOIN work_source_links wsl ON w.work_id = wsl.work_id
        INNER JOIN external_source_links esl ON wsl.external_source_link_id = esl.link_id
        WHERE esl.source_code = :sourceCode AND esl.external_item_id = :externalItemId
          AND w.identity_status != 'DEPRECATED'
    """)
    suspend fun getWorksByExternalSource(sourceCode: String, externalItemId: String): List<WorkEntity>

    // ==================== 内容组件与版权溯源 ====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponent(component: ContentComponentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponents(components: List<ContentComponentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentAttribution(attribution: ContentAttributionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentAttributions(attributions: List<ContentAttributionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkSourceLink(link: WorkSourceLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkSourceLinks(links: List<WorkSourceLinkEntity>)

    @Query("""
        SELECT cc.* FROM content_components cc
        INNER JOIN work_version_contents wvc ON cc.component_id = wvc.component_id AND cc.component_type = wvc.component_type
        WHERE wvc.work_version_id = :workVersionId
    """)
    suspend fun getComponentsByVersionId(workVersionId: String): List<ContentComponentEntity>

    @Query("SELECT * FROM content_components WHERE component_id = :componentId")
    suspend fun getComponentById(componentId: String): ContentComponentEntity?

    @Query("SELECT * FROM content_attributions WHERE component_id IN (:componentIds)")
    suspend fun getAttributionsByComponentIds(componentIds: List<String>): List<ContentAttributionEntity>

    @Query("SELECT * FROM work_source_links WHERE work_id = :workId ORDER BY is_primary DESC")
    suspend fun getWorkSourceLinksByWorkId(workId: String): List<WorkSourceLinkEntity>

    @Query("""
        SELECT esl.* FROM external_source_links esl
        INNER JOIN work_source_links wsl ON esl.link_id = wsl.external_source_link_id
        WHERE wsl.work_id = :workId
        ORDER BY wsl.is_primary DESC
    """)
    suspend fun getSourceLinksByWorkId(workId: String): List<ExternalSourceLinkEntity>

    @Query("SELECT * FROM external_source_links WHERE link_id IN (:linkIds)")
    suspend fun getExternalSourceLinksByIds(linkIds: List<String>): List<ExternalSourceLinkEntity>

    @Query("""
        SELECT a.* FROM authors a
        INNER JOIN work_authors wa ON a.author_id = wa.author_id
        WHERE wa.work_version_id = :workVersionId
        ORDER BY wa.author_order ASC
    """)
    suspend fun getAuthorsByVersionId(workVersionId: String): List<AuthorEntity>
}

/**
 * 首页与分类列表轻量投影数据类
 */
data class WorkListItemProjection(
    @androidx.room.ColumnInfo(name = "work_id") val workId: String,
    @androidx.room.ColumnInfo(name = "standard_title") val standardTitle: String,
    @androidx.room.ColumnInfo(name = "author_name") val authorName: String?,
    @androidx.room.ColumnInfo(name = "dynasty_name") val dynastyName: String?,
    @androidx.room.ColumnInfo(name = "genre_code") val genreCode: String?,
    @androidx.room.ColumnInfo(name = "excerpt") val excerpt: String?
)
