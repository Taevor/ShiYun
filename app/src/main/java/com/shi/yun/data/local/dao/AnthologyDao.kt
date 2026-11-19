package com.shi.yun.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shi.yun.data.local.entity.AnthologyBookEntity
import com.shi.yun.data.local.entity.AnthologyBookVersionEntity
import com.shi.yun.data.local.entity.AnthologyItemEntity
import com.shi.yun.data.local.entity.AnthologyNodeEntity
import com.shi.yun.data.local.entity.WorkEntity

/**
 * 选本目录轻量单次投影实体（含收录篇数与版本说明）
 */
data class AnthologyBookWithCount(
    val book_id: String,
    val book_code: String,
    val standard_title: String,
    val compiler: String,
    val item_count: Int,
    val edition_note: String,
    val description: String,
)

/**
 * 典籍选集与目录结构数据访问对象 (AnthologyDao)
 */
@Dao
interface AnthologyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnthologyBook(book: AnthologyBookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnthologyBookVersion(version: AnthologyBookVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnthologyNodes(nodes: List<AnthologyNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnthologyItems(items: List<AnthologyItemEntity>)

    @Query("""
        SELECT * FROM anthology_books 
        ORDER BY 
            CASE WHEN book_id LIKE 'book_ht_%' THEN CAST(substr(book_id, 9) AS INTEGER) ELSE 999999 END ASC,
            book_id ASC
    """)
    suspend fun getAllAnthologyBooks(): List<AnthologyBookEntity>

    @Query("""
        SELECT 
            ab.book_id,
            ab.book_code,
            ab.standard_title,
            COALESCE(ab.compiler, '') AS compiler,
            COUNT(ai.item_id) AS item_count,
            COALESCE(abv.edition_note, '') AS edition_note,
            COALESCE(ab.description, '') AS description
        FROM anthology_books ab
        LEFT JOIN anthology_book_versions abv ON ab.book_id = abv.book_id
        LEFT JOIN anthology_nodes an ON abv.book_version_id = an.book_version_id
        LEFT JOIN anthology_items ai ON an.node_id = ai.node_id
        GROUP BY ab.book_id
        ORDER BY 
            CASE WHEN ab.book_id LIKE 'book_ht_%' THEN CAST(substr(ab.book_id, 9) AS INTEGER) ELSE 999999 END ASC,
            ab.book_id ASC
    """)
    suspend fun getAllAnthologyBooksWithCount(): List<AnthologyBookWithCount>

    @Query("SELECT * FROM anthology_books WHERE book_id = :bookId")
    suspend fun getAnthologyBookById(bookId: String): AnthologyBookEntity?

    @Query("SELECT * FROM anthology_books WHERE book_code = :bookCode")
    suspend fun getAnthologyBookByCode(bookCode: String): AnthologyBookEntity?

    @Query("SELECT * FROM anthology_nodes WHERE book_version_id = :bookVersionId ORDER BY sort_order ASC")
    suspend fun getNodesByBookVersion(bookVersionId: String): List<AnthologyNodeEntity>

    @Query("SELECT * FROM anthology_items WHERE node_id = :nodeId ORDER BY item_order ASC")
    suspend fun getItemsByNodeId(nodeId: String): List<AnthologyItemEntity>

    @Query("""
        SELECT w.* FROM works w
        INNER JOIN anthology_items ai ON w.work_id = ai.work_id
        WHERE ai.node_id = :nodeId
        ORDER BY ai.item_order ASC
    """)
    suspend fun getWorksByNodeId(nodeId: String): List<WorkEntity>

    @Query("""
        SELECT ai.* FROM anthology_items ai
        INNER JOIN anthology_nodes an ON ai.node_id = an.node_id
        INNER JOIN anthology_book_versions abv ON an.book_version_id = abv.book_version_id
        WHERE abv.book_id = :bookId
        ORDER BY an.sort_order ASC, ai.item_order ASC
    """)
    suspend fun getItemsByBookId(bookId: String): List<AnthologyItemEntity>

    @Query("SELECT * FROM anthology_nodes WHERE node_id = :nodeId")
    suspend fun getNodeById(nodeId: String): AnthologyNodeEntity?

    @Query("SELECT * FROM anthology_book_versions WHERE book_id = :bookId ORDER BY version_code DESC LIMIT 1")
    suspend fun getLatestBookVersion(bookId: String): AnthologyBookVersionEntity?

    @Query("""
        SELECT COUNT(ai.item_id) FROM anthology_items ai
        INNER JOIN anthology_nodes an ON ai.node_id = an.node_id
        INNER JOIN anthology_book_versions abv ON an.book_version_id = abv.book_version_id
        WHERE abv.book_id = :bookId
    """)
    suspend fun getItemCountByBookId(bookId: String): Int
}
