package com.shi.yun.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 朝代字典表 (dynasties)
 */
@Entity(
    tableName = "dynasties",
    indices = [
        Index(value = ["standard_name"], unique = true, name = "idx_dynasties_name"),
        Index(value = ["sort_order"], name = "idx_dynasties_sort")
    ]
)
data class DynastyEntity(
    @PrimaryKey
    @ColumnInfo(name = "dynasty_id")
    val dynastyId: String,

    @ColumnInfo(name = "standard_name")
    val standardName: String,

    @ColumnInfo(name = "start_year")
    val startYear: Int? = null,

    @ColumnInfo(name = "end_year")
    val endYear: Int? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * 体裁大类字典表 (genres)
 */
@Entity(
    tableName = "genres",
    indices = [
        Index(value = ["standard_name"], unique = true, name = "idx_genres_name")
    ]
)
data class GenreEntity(
    @PrimaryKey
    @ColumnInfo(name = "genre_id")
    val genreId: String,

    @ColumnInfo(name = "standard_name")
    val standardName: String,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * 体式细类字典表 (forms)
 */
@Entity(
    tableName = "forms",
    foreignKeys = [
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["genre_id"],
            childColumns = ["genre_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["genre_id"], name = "idx_forms_genre")
    ]
)
data class FormEntity(
    @PrimaryKey
    @ColumnInfo(name = "form_id")
    val formId: String,

    @ColumnInfo(name = "standard_name")
    val standardName: String,

    @ColumnInfo(name = "genre_id")
    val genreId: String,

    @ColumnInfo(name = "line_count")
    val lineCount: Int? = null,

    @ColumnInfo(name = "char_per_line")
    val charPerLine: Int? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * 词牌字典表 (cipais)
 */
@Entity(
    tableName = "cipais",
    indices = [
        Index(value = ["standard_name"], unique = true, name = "idx_cipais_name")
    ]
)
data class CipaiEntity(
    @PrimaryKey
    @ColumnInfo(name = "cipai_id")
    val cipaiId: String,

    @ColumnInfo(name = "standard_name")
    val standardName: String,

    @ColumnInfo(name = "cipai_pattern_summary")
    val cipaiPatternSummary: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * 词牌别名表 (cipai_aliases)
 */
@Entity(
    tableName = "cipai_aliases",
    foreignKeys = [
        ForeignKey(
            entity = CipaiEntity::class,
            parentColumns = ["cipai_id"],
            childColumns = ["cipai_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cipai_id"], name = "idx_ca_cipai_id"),
        Index(value = ["alias_name"], name = "idx_ca_alias_name")
    ]
)
data class CipaiAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "alias_id")
    val aliasId: String,

    @ColumnInfo(name = "cipai_id")
    val cipaiId: String,

    @ColumnInfo(name = "alias_name")
    val aliasName: String
)

/**
 * 曲牌字典表 (qupais)
 */
@Entity(
    tableName = "qupais",
    indices = [
        Index(value = ["standard_name"], unique = true, name = "idx_qupais_name")
    ]
)
data class QupaiEntity(
    @PrimaryKey
    @ColumnInfo(name = "qupai_id")
    val qupaiId: String,

    @ColumnInfo(name = "standard_name")
    val standardName: String,

    @ColumnInfo(name = "qupai_category")
    val qupaiCategory: String, // NORTHERN, SOUTHERN

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * 曲牌别名表 (qupai_aliases)
 */
@Entity(
    tableName = "qupai_aliases",
    foreignKeys = [
        ForeignKey(
            entity = QupaiEntity::class,
            parentColumns = ["qupai_id"],
            childColumns = ["qupai_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["qupai_id"], name = "idx_qa_qupai_id"),
        Index(value = ["alias_name"], name = "idx_qa_alias_name")
    ]
)
data class QupaiAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "alias_id")
    val aliasId: String,

    @ColumnInfo(name = "qupai_id")
    val qupaiId: String,

    @ColumnInfo(name = "alias_name")
    val aliasName: String
)

/**
 * 专题标签字典表 (tags)
 */
@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["standard_name"], unique = true, name = "idx_tags_name")
    ]
)
data class TagEntity(
    @PrimaryKey
    @ColumnInfo(name = "tag_id")
    val tagId: String,

    @ColumnInfo(name = "standard_name")
    val standardName: String,

    @ColumnInfo(name = "tag_category")
    val tagCategory: String, // THEME, SCENERY, EMOTION, FESTIVAL

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

/**
 * 专题标签别名表 (tag_aliases)
 */
@Entity(
    tableName = "tag_aliases",
    foreignKeys = [
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["tag_id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tag_id"], name = "idx_ta_tag_id"),
        Index(value = ["alias_name"], name = "idx_ta_alias_name")
    ]
)
data class TagAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "alias_id")
    val aliasId: String,

    @ColumnInfo(name = "tag_id")
    val tagId: String,

    @ColumnInfo(name = "alias_name")
    val aliasName: String
)
