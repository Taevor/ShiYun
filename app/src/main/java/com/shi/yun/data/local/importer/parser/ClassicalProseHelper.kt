package com.shi.yun.data.local.importer.parser

/**
 * 古典散文与文言文识别辅助工具 (ClassicalProseHelper)
 *
 * 专门用于在从开源数据源（如 poems-db）导入古籍文献时，准确识别散文（文言文）体裁：
 * 1. 严格排他规则：优先排除绝句、律诗、词、曲、楚辞、诗经等具体韵文体式；
 * 2. 标签与体裁证据：识别「散文」、「游记」、「古文观止」等明确散文标签；
 * 3. 散文文体名考辨：结合「文言文」标签，识别以「记、志、序、说、传、论、表、铭、赋、书、辩」等文体结尾的作品；
 * 4. 映射为受控枚举标准编码 "WEN"，绝不误判或回退为 "POEM"（诗）。
 */
object ClassicalProseHelper {

    /**
     * 常见古典散文文体后缀
     */
    val PROSE_SUFFIXES: Set<String> = setOf(
        "记", "志", "序", "说", "传", "论", "表", "铭", "赋", "书", "辩", "原", "赞", "策", "疏", "吊"
    )

    /**
     * 明确的散文/游记标签
     */
    val EXPLICIT_PROSE_TAGS: Set<String> = setOf(
        "散文", "游记", "古文观止", "传记"
    )

    /**
     * 明确的韵文体裁标签（用于严格排除）
     */
    private val RHYMED_VERSE_KEYWORDS: List<String> = listOf(
        "绝句", "律诗", "七绝", "五绝", "七律", "五律",
        "词", "宋词", "元曲", "散曲", "诗经", "楚辞", "乐府"
    )

    /**
     * 判断一条古籍记录是否具备可靠的散文（文言文）依据
     *
     * 约束：
     * 1. 明确的韵文体裁标签必须优先排除；
     * 2. 注意 poems-db 的 format="古诗词" 属于全库通用容器标签，不能作为诗的排他依据；
     * 3. 具备明确散文/游记标签，或具备文言文标签且标题具有典型散文文体后缀时，判定为散文。
     */
    fun isReliableProse(rawGenre: String?, tags: List<String>, rawTitle: String?): Boolean {
        val titleClean = rawTitle?.trim().orEmpty()
        val genreClean = rawGenre?.trim()?.lowercase().orEmpty()
        val allTagsClean = tags.map { it.trim().lowercase() }

        // 1. 排除明确的韵文体式标签
        val hasRhymedTag = allTagsClean.any { tag ->
            RHYMED_VERSE_KEYWORDS.any { keyword -> tag.contains(keyword.lowercase()) }
        }
        if (hasRhymedTag) {
            return false
        }

        // 2. 排除明确的韵文体裁字段（排除通用“古诗词”与“诗词”）
        if (genreClean.isNotEmpty() && genreClean != "古诗词" && genreClean != "诗词") {
            if (RHYMED_VERSE_KEYWORDS.any { genreClean.contains(it.lowercase()) }) {
                return false
            }
        }

        // 3. 证据 A：标签明确包含散文、游记、古文观止等
        if (allTagsClean.any { tag -> EXPLICIT_PROSE_TAGS.any { tag.contains(it.lowercase()) } }) {
            return true
        }

        // 4. 证据 B：体裁明确为文言文或散文
        if (genreClean == "文言文" || genreClean == "散文" || genreClean == "wen") {
            return true
        }

        // 5. 证据 C：标签含文言文且标题以典型散文文体名结尾（如《真州东园记》《项脊轩志》）
        val hasWenyanTag = allTagsClean.any { it.contains("文言文") }
        if (hasWenyanTag && PROSE_SUFFIXES.any { titleClean.endsWith(it) }) {
            return true
        }

        return false
    }
}
