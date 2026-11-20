package com.shi.yun.data.util

import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.Work
import kotlin.math.abs

/**
 * 首页推荐双句诗提炼载体
 *
 * 规范：内容选取与竖排排版绘制彻底解耦，为未来无缝切换至独立名句库奠定清晰边界。
 *
 * @property workId 作品唯一 ID
 * @property line1 出句（纯净汉字文本，已过滤末尾横排标点）
 * @property line2 对句（纯净汉字文本，已过滤末尾横排标点）
 * @property title 作品标题（如 "静夜思"，已剥离书名号与首尾空白）
 * @property author 作者署名（如 "李白"，已严格剔除朝代信息与多余点号标点）
 * @property formattedWithPunctuation 兼容横排小组件的带标点完整展示文本（如 "床前明月光，疑是地上霜。"）
 */
data class HomePoemCouplet(
    val workId: String,
    val line1: String,
    val line2: String,
    val title: String,
    val author: String,
    val formattedWithPunctuation: String,
)

/**
 * 诗词双句提取工具
 *
 * 核心规则：
 * 1. 严格限定诗类作品 (Genre.POEM)，长篇文章、散文、杂文一律跳过；
 * 2. 每次提取原作品中相邻的完整两句（出句与对句），保留原有顺序；
 * 3. 绝不跨作品随意拼接两句；
 * 4. 长度对仗校验：单句字数介于 4~10 字之间（五言、七言、四言、六言），两句长度差不超过 1；
 * 5. 自动剔除朝代标注与多余标点，输出符合古典审美的纯净作品名与作者名；
 * 6. 若某作品正文不符合规范，返回 null，由上游筛选机制优雅跳过。
 */
object PoemCoupletExtractor {

    private val PUNCTUATIONS = setOf(
        '，', '。', '！', '？', '；', '：', '、',
        ',', '.', '!', '?', ';', ':', '—', '…', '“', '”', '‘', '’'
    )

    private val DYNASTY_PREFIX_REGEX = Regex("^[·•\\[〔【(（].*?[·•\\]〕】)）]")
    private val LEADING_SYMBOLS_REGEX = Regex("^[·•\\-—\\s]+")

    /**
     * 从指定作品中提取符合首页要求的完整两句诗
     */
    fun extractCouplet(work: Work): HomePoemCouplet? {
        // 1. 严格体裁过滤：暂时只从诗类作品中选择，绝不混入散文、长篇文章
        if (work.genre != Genre.POEM) {
            return null
        }

        // 2. 优先尝试从已有 excerpt 中提取（若 excerpt 恰好是整饬对仗的两句）
        val fromExcerpt = tryExtractFromText(work.excerpt, work)
        if (fromExcerpt != null) {
            return fromExcerpt
        }

        // 3. 其次从完整正文 content 中提取前两句
        return tryExtractFromText(work.content, work)
    }

    private fun tryExtractFromText(text: String, work: Work): HomePoemCouplet? {
        if (text.isBlank()) return null

        // 按行或常用句读标点切分候选分句（保留切分后非空片段）
        val rawClauses = text.lines()
            .flatMap { line ->
                line.split(Regex("(?<=[，。！？；：、,!?;:])"))
            }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (rawClauses.size < 2) return null

        // 提取相邻的前两句作为出句与对句
        val rawLine1 = rawClauses[0]
        val rawLine2 = rawClauses[1]

        // 剔除标点符号后的纯汉字正文
        val pure1 = rawLine1.filter { it !in PUNCTUATIONS }.trim()
        val pure2 = rawLine2.filter { it !in PUNCTUATIONS }.trim()

        // 单句字数合理性检验：四言、五言、六言、七言等古典诗体（4 ~ 10 字）
        if (pure1.length !in 4..10 || pure2.length !in 4..10) {
            return null
        }

        // 两句字数必须对仗对称（长度差异不超过 1 字）
        if (abs(pure1.length - pure2.length) > 1) {
            return null
        }

        // 规范化作者名：去除朝代标注（如 "〔唐〕"、"[唐]"、"唐·" 等）与悬挂符号
        val cleanAuthor = work.author.trim()
            .replace(DYNASTY_PREFIX_REGEX, "")
            .replace(LEADING_SYMBOLS_REGEX, "")
            .trim()
            .ifBlank { "佚名" }

        // 规范化作品名：去除外层书名号
        val cleanTitle = work.title.trim()
            .removePrefix("《").removeSuffix("》")
            .trim()
            .ifBlank { "无题" }

        val formattedWithPunctuation = "${pure1}，${pure2}。"

        return HomePoemCouplet(
            workId = work.id,
            line1 = pure1,
            line2 = pure2,
            title = cleanTitle,
            author = cleanAuthor,
            formattedWithPunctuation = formattedWithPunctuation,
        )
    }
}
