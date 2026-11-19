package com.shi.yun.data.model

/**
 * 诗词作品轻量列表项模型（用于首页推荐、分类列表等列表展示场景）
 *
 * 遵循阶段 5 性能优化规范：
 * 1. 仅装载列表与卡片渲染所必须的核心元数据（ID、标题、作者、朝代、体裁、摘要）；
 * 2. 杜绝在列表层贪婪加载全量正文、条目注释、白话译文、文学赏析与来源追溯；
 * 3. 配合 Room 单次高效投影（Projection）查询，彻底根绝 1+15N 级联查询开销；
 * 4. 作者为空时统一安全回退至“佚名”，杜绝空白作者；
 * 5. 支持与完整 [Work] 实体双向平滑互转。
 *
 * @property id 稳定唯一作品 ID
 * @property title 作品标准标题
 * @property author 作者名称（为空时回退至“佚名”）
 * @property dynasty 朝代（如“唐代”、“宋代”）
 * @property genre 体裁受控枚举（如 Genre.POEM, Genre.CI）
 * @property excerpt 推荐摘录或正文首句（供卡片及列表行展示）
 */
data class WorkListItem(
    val id: String,
    val title: String,
    val author: String,
    val dynasty: String,
    val genre: Genre,
    val excerpt: String,
)

/**
 * 格式化输出朝代与作者信息，作者为空时规范回退“佚名”
 */
fun WorkListItem.formatAuthorDynasty(): String {
    val d = dynasty.trim()
    val a = author.trim().ifEmpty { "佚名" }
    return when {
        d.isNotEmpty() && a.isNotEmpty() -> "〔$d〕$a"
        a.isNotEmpty() -> a
        d.isNotEmpty() -> "〔$d〕"
        else -> "佚名"
    }
}

/**
 * 格式化输出朝代、作者与体裁信息，供列表项与卡片统一渲染
 */
fun WorkListItem.formatAuthorDynastyGenre(): String {
    val authorDynasty = formatAuthorDynasty()
    return if (authorDynasty.isNotEmpty()) {
        "$authorDynasty  ·  ${genre.displayName}"
    } else {
        genre.displayName
    }
}

/**
 * 将轻量列表项 [WorkListItem] 转化为占位用 [Work] 实体（供转场过渡与详情页按需加载前使用）
 */
fun WorkListItem.toWork(): Work = Work(
    id = id,
    title = title,
    author = author,
    dynasty = dynasty,
    genre = genre,
    content = "",
    excerpt = excerpt,
)

/**
 * 将完整 [Work] 实体提炼为轻量列表项 [WorkListItem]
 */
fun Work.toWorkListItem(): WorkListItem = WorkListItem(
    id = id,
    title = title,
    author = author.ifBlank { "佚名" },
    dynasty = dynasty,
    genre = genre,
    excerpt = excerpt,
)
