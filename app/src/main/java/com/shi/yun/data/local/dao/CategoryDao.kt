package com.shi.yun.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shi.yun.data.local.entity.AuthorAliasEntity
import com.shi.yun.data.local.entity.AuthorEntity
import com.shi.yun.data.local.entity.CipaiEntity
import com.shi.yun.data.local.entity.DynastyEntity
import com.shi.yun.data.local.entity.ExternalSourceLinkEntity
import com.shi.yun.data.local.entity.FormEntity
import com.shi.yun.data.local.entity.GenreEntity
import com.shi.yun.data.local.entity.QupaiEntity
import com.shi.yun.data.local.entity.TagEntity
import com.shi.yun.data.local.entity.WorkEntity

/**
 * 七维分类字典与分类检索数据访问对象 (CategoryDao)
 */
@Dao
interface CategoryDao {

    // ==================== 1. 朝代 (Dynasty) ====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDynasties(dynasties: List<DynastyEntity>)

    @Query("SELECT * FROM dynasties ORDER BY sort_order ASC")
    suspend fun getAllDynasties(): List<DynastyEntity>

    @Query("SELECT * FROM dynasties WHERE dynasty_id = :dynastyId")
    suspend fun getDynastyById(dynastyId: String): DynastyEntity?

    // ==================== 2. 体裁 (Genre) ====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenres(genres: List<GenreEntity>)

    @Query("SELECT * FROM genres ORDER BY sort_order ASC")
    suspend fun getAllGenres(): List<GenreEntity>

    // ==================== 3. 形式 (Form) ====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForms(forms: List<FormEntity>)

    @Query("SELECT * FROM forms WHERE genre_id = :genreId ORDER BY sort_order ASC")
    suspend fun getFormsByGenre(genreId: String): List<FormEntity>

    // ==================== 4. 词牌 (Cipai) ====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCipais(cipais: List<CipaiEntity>)

    @Query("SELECT * FROM cipais ORDER BY sort_order ASC")
    suspend fun getAllCipais(): List<CipaiEntity>

    // ==================== 5. 曲牌 (Qupai) ====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQupais(qupais: List<QupaiEntity>)

    @Query("SELECT * FROM qupais ORDER BY sort_order ASC")
    suspend fun getAllQupais(): List<QupaiEntity>

    // ==================== 6. 专题标签 (Tag) ====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("SELECT * FROM tags WHERE tag_category = :category ORDER BY sort_order ASC")
    suspend fun getTagsByCategory(category: String): List<TagEntity>

    // ==================== 7. 作者 (Author) ====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthors(authors: List<AuthorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthorAliases(aliases: List<AuthorAliasEntity>)

    @Query("SELECT * FROM authors WHERE author_id = :authorId")
    suspend fun getAuthorById(authorId: String): AuthorEntity?

    @Query("SELECT * FROM authors WHERE standard_name = :name LIMIT 1")
    suspend fun getAuthorByName(name: String): AuthorEntity?

    @Query("""
        SELECT a.* FROM authors a
        INNER JOIN author_aliases aa ON a.author_id = aa.author_id
        WHERE aa.alias_name = :aliasName
        LIMIT 1
    """)
    suspend fun getAuthorByAlias(aliasName: String): AuthorEntity?

    @Query("""
        SELECT a.* FROM authors a
        LEFT JOIN author_aliases aa ON a.author_id = aa.author_id
        WHERE a.standard_name = :nameOrAlias OR aa.alias_name = :nameOrAlias
        LIMIT 1
    """)
    suspend fun findAuthorByNameOrAlias(nameOrAlias: String): AuthorEntity?

    @Query("SELECT * FROM author_aliases WHERE author_id = :authorId")
    suspend fun getAliasesByAuthorId(authorId: String): List<AuthorAliasEntity>

    @Query("SELECT * FROM authors ORDER BY sort_order ASC")
    suspend fun getAllAuthors(): List<AuthorEntity>

    // ==================== 8. 外部数据源与溯源定义 (ExternalSourceLink) ====================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExternalSourceLinks(links: List<ExternalSourceLinkEntity>)

    @Query("SELECT * FROM external_source_links WHERE link_id = :linkId")
    suspend fun getExternalSourceLinkById(linkId: String): ExternalSourceLinkEntity?

    @Query("SELECT * FROM external_source_links WHERE source_code = :sourceCode AND external_item_id = :itemId LIMIT 1")
    suspend fun getExternalSourceLinkByCodeAndItem(sourceCode: String, itemId: String): ExternalSourceLinkEntity?

    // ==================== 关联检索 (Filter Works by Dimension) ====================
    @Query("""
        SELECT w.* FROM works w
        INNER JOIN work_versions wv ON w.work_id = wv.work_id
        INNER JOIN work_version_classifications wvc ON wv.work_version_id = wvc.work_version_id
        WHERE wvc.dimension_type = 'DYNASTY' AND wvc.classification_id = :dynastyId
          AND w.identity_status != 'DEPRECATED'
        GROUP BY w.work_id
        ORDER BY w.sort_order ASC
    """)
    suspend fun getWorksByDynasty(dynastyId: String): List<WorkEntity>

    @Query("""
        SELECT w.* FROM works w
        INNER JOIN work_versions wv ON w.work_id = wv.work_id
        INNER JOIN work_version_classifications wvc ON wv.work_version_id = wvc.work_version_id
        WHERE wvc.dimension_type = 'GENRE' AND wvc.classification_id = :genreId
          AND w.identity_status != 'DEPRECATED'
        GROUP BY w.work_id
        ORDER BY w.sort_order ASC
    """)
    suspend fun getWorksByGenre(genreId: String): List<WorkEntity>

    @Query("""
        SELECT w.* FROM works w
        INNER JOIN work_versions wv ON w.work_id = wv.work_id
        INNER JOIN work_authors wa ON wv.work_version_id = wa.work_version_id
        WHERE wa.author_id = :authorId AND w.identity_status != 'DEPRECATED'
        GROUP BY w.work_id
        ORDER BY w.sort_order ASC
    """)
    suspend fun getWorksByAuthor(authorId: String): List<WorkEntity>

    @Query("SELECT * FROM genres WHERE genre_id = :genreId")
    suspend fun getGenreById(genreId: String): GenreEntity?

    @Query("SELECT * FROM forms WHERE form_id = :formId")
    suspend fun getFormById(formId: String): FormEntity?

    @Query("SELECT * FROM cipais WHERE cipai_id = :cipaiId")
    suspend fun getCipaiById(cipaiId: String): CipaiEntity?

    @Query("SELECT * FROM qupais WHERE qupai_id = :qupaiId")
    suspend fun getQupaiById(qupaiId: String): QupaiEntity?

    @Query("""
        SELECT w.* FROM works w
        INNER JOIN work_versions wv ON w.work_id = wv.work_id
        INNER JOIN work_version_classifications wvc ON wv.work_version_id = wvc.work_version_id
        LEFT JOIN genres g ON wvc.classification_id = g.genre_id
        WHERE wvc.dimension_type = 'GENRE'
          AND (wvc.classification_id IN (:candidates) OR g.standard_name IN (:candidates))
          AND w.identity_status != 'DEPRECATED'
        GROUP BY w.work_id
        ORDER BY w.sort_order ASC
    """)
    suspend fun getWorksByGenreCandidates(candidates: List<String>): List<WorkEntity>

    @Query("""
        SELECT w.* FROM works w
        INNER JOIN work_versions wv ON w.work_id = wv.work_id
        LEFT JOIN work_version_classifications wvc ON wv.work_version_id = wvc.work_version_id AND wvc.dimension_type = 'DYNASTY'
        LEFT JOIN dynasties d ON wvc.classification_id = d.dynasty_id
        LEFT JOIN work_authors wa ON wv.work_version_id = wa.work_version_id
        LEFT JOIN authors a ON wa.author_id = a.author_id
        LEFT JOIN dynasties da ON a.dynasty_id = da.dynasty_id
        WHERE w.identity_status != 'DEPRECATED'
          AND (wvc.classification_id = :dynasty
               OR d.standard_name = :dynasty
               OR a.dynasty_id = :dynasty
               OR da.standard_name = :dynasty)
        GROUP BY w.work_id
        ORDER BY w.sort_order ASC
    """)
    suspend fun getWorksByDynastyFlexible(dynasty: String): List<WorkEntity>
}
