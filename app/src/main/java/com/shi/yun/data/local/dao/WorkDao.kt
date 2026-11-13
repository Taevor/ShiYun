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
