package com.shi.yun.ui.vertical

import android.graphics.Paint
import android.graphics.Typeface
import com.shi.yun.ui.detail.PoemTypography
import kotlin.math.max

/**
 * 单个字形在 Canvas 上的绝对绘制数据载体
 */
data class VerticalGlyphInfo(
    val char: Char,
    val x: Float,                      // Canvas 绘制绝对坐标 X（字符水平中心）
    val y: Float,                      // Canvas 绘制基线绝对坐标 Y (Baseline)
    val fontSizePx: Float,             // 字符绘制字号
    val isPunctuation: Boolean = false,// 是否为标点符号
    val isAttribution: Boolean = false,// 是否为出处落款文字
    val rotationDegrees: Float = 0f,   // 旋转角度（用于西文或特殊符号）
)

/**
 * 单列排版数据载体
 */
data class VerticalColumn(
    val columnIndex: Int,              // 列索引（从右向左：0 为最右侧出句列）
    val glyphs: List<VerticalGlyphInfo>,
    val columnWidth: Float,            // 列宽（即字号宽度）
    val columnHeight: Float,           // 列高（从顶部到最后一个字符的垂直距离）
    val isAttribution: Boolean = false,// 是否为作者/出处落款列
)

/**
 * 竖排排版计算引擎的输入参数
 *
 * 支持古典章法排版：右上起首正文，左下落款出处，中宫留白。
 */
data class VerticalLayoutInput(
    val text: String = "",                          // 待排版诗词正文（单句或完整文本）
    val clauses: List<String> = emptyList(),        // 结构化诗句分句列表（优先使用）
    val attribution: String = "",                   // 出处/作者落款（展示在左下角，兼容旧格式如 "《静夜思》李白" 或 "静夜思 李白"）
    val attributionTitle: String = "",              // 结构化落款作品名（可选，优先使用）
    val attributionAuthor: String = "",             // 结构化落款作者（可选，优先使用）
    val availableWidth: Float,                      // 可用渲染宽度（px）
    val availableHeight: Float,                     // 可用渲染高度（px）
    val baseFontSizePx: Float = 48f,                // 正文基准字号（px，约 22~24sp）
    val attributionFontSizePx: Float = 25f,         // 落款字号（px，约 12~13sp）
    val columnSpacingPx: Float = 56f,               // 正文列与列之间的间距（px，约 28~30dp）
    val lineSpacingPx: Float = 9f,                  // 正文列内字符基准字间距（px，紧凑自然，约 4~5dp）
    val attrLineSpacingPx: Float = 4f,              // 落款列内字符基准字间距（px，紧凑连贯，约 2dp）
    val attrTitleAuthorGapPx: Float = 14f,          // 落款作品名与作者之间的停顿留白间距（px，约 6~8dp）
    val paddingTopPx: Float = 48f,                  // 正文顶部留白（px）
    val paddingRightPx: Float = 48f,                // 正文右侧留白（px）
    val paddingLeftPx: Float = 48f,                 // 落款左侧留白（px）
    val paddingBottomPx: Float = 48f,               // 落款底部留白（px）
    val filterPunctuation: Boolean = true,          // 是否过滤正文中的横排标点符号（优先纯汉字古典排版）
    val typeface: Typeface? = null,                 // 绘制字体（可选）
)

/**
 * 竖排排版引擎计算结果
 */
data class VerticalLayoutResult(
    val columns: List<VerticalColumn>,
    val contentWidth: Float,                        // 内容总外接宽度
    val contentHeight: Float,                       // 内容总外接高度
    val offsetX: Float = 0f,                        // 兼容偏移量
    val offsetY: Float = 0f,
    val fontSizePx: Float,                          // 实际采用的正文字号
)

/**
 * 纯 Kotlin 古典竖排排版计算引擎
 *
 * 核心章法规范：
 * 1. 正文自右上角起首：第一列（出句）靠右，自上而下排列；后续列（对句）从右向左依次排列；
 * 2. 避免整体居中呆板：正文与落款解耦，各自分区，中间自然留白；
 * 3. 落款位于左下角：以左下角为锚点向上排列，底端下齐，仅展示《作品》作者，不带朝代；
 * 4. 标点纯净处理：优先呈现古典诗书画纯正汉字排列，彻底避免横排标点带来的视觉生硬。
 */
object VerticalLayoutEngine {

    private val PUNCTUATIONS = setOf(
        '，', '。', '！', '？', '；', '：', '、',
        '《', '》', '<', '>',
        ',', '.', '!', '?', ';', ':', '—', '…', '“', '”', '‘', '’',
    )

    /**
     * 获取字体度量数据（字符高度与 ascent 基线）
     */
    private fun getCharMetrics(fontSizePx: Float, typeface: Typeface?): Pair<Float, Float> {
        return try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = fontSizePx
                if (typeface != null) this.typeface = typeface
            }
            val fm = paint.fontMetrics
            val height = if (fm != null && fm.descent != 0f) fm.descent - fm.ascent else fontSizePx * 1.15f
            val ascent = if (fm != null && fm.ascent != 0f) fm.ascent else -fontSizePx * 0.92f
            Pair(height, ascent)
        } catch (_: Throwable) {
            Pair(fontSizePx * 1.15f, -fontSizePx * 0.92f)
        }
    }

    /**
     * 执行竖排排版计算
     */
    fun layout(input: VerticalLayoutInput): VerticalLayoutResult {
        if (input.availableWidth <= 0f || input.availableHeight <= 0f) {
            return VerticalLayoutResult(emptyList(), 0f, 0f, 0f, 0f, input.baseFontSizePx)
        }

        // 1. 获取正文分句列表
        val rawClauses = if (input.clauses.isNotEmpty()) {
            input.clauses
        } else {
            PoemTypography.splitIntoClauses(input.text).ifEmpty {
                if (input.text.isNotBlank()) listOf(input.text.trim()) else emptyList()
            }
        }

        // 2. 纯净诗句切分（根据配置剥离句尾横排标点，呈现古典纯汉字列）
        val textClauses = rawClauses.mapNotNull { clause ->
            val cleaned = if (input.filterPunctuation) {
                clause.filter { it !in PUNCTUATIONS }.trim()
            } else {
                clause.trim()
            }
            cleaned.takeIf { it.isNotEmpty() }
        }

        if (textClauses.isEmpty() && input.attribution.isBlank() && input.attributionTitle.isBlank() && input.attributionAuthor.isBlank()) {
            return VerticalLayoutResult(emptyList(), 0f, 0f, 0f, 0f, input.baseFontSizePx)
        }

        // 3. 自适应字号测量（确保最长诗句在减去顶部与底部留白后能完整容纳）
        var currentFontSize = input.baseFontSizePx
        var currentAttributionFontSize = input.attributionFontSizePx

        val maxAllowedPoemHeight = max(input.availableHeight - input.paddingTopPx - input.paddingBottomPx, 100f)
        val maxCharsInPoem = textClauses.maxOfOrNull { it.length } ?: 5
        val baseLineSpacing = input.lineSpacingPx * (currentFontSize / input.baseFontSizePx)
        val estimatedPoemHeight = maxCharsInPoem * (currentFontSize * 1.15f) + (maxCharsInPoem - 1) * baseLineSpacing

        if (estimatedPoemHeight > maxAllowedPoemHeight && maxAllowedPoemHeight > 0f) {
            val scaleFactor = (maxAllowedPoemHeight / estimatedPoemHeight).coerceIn(0.68f, 1.0f)
            currentFontSize *= scaleFactor
            currentAttributionFontSize *= scaleFactor
        }

        val effectiveLineSpacing = input.lineSpacingPx * (currentFontSize / input.baseFontSizePx)
        val effectiveColumnSpacing = input.columnSpacingPx * (currentFontSize / input.baseFontSizePx)

        val (mainCharHeight, mainAscent) = getCharMetrics(currentFontSize, input.typeface)
        val (attrCharHeight, attrAscent) = getCharMetrics(currentAttributionFontSize, input.typeface)

        val columns = mutableListOf<VerticalColumn>()

        // 4. 正文排版：以右上角为起点，第一列（出句）靠右，后续列依次向左
        // 第 0 列的中心 X 坐标位于：可用宽度 - 右侧留白 - 半个列宽
        var colCenterX = input.availableWidth - input.paddingRightPx - (currentFontSize / 2f)

        for (i in textClauses.indices) {
            val clause = textClauses[i]
            val glyphs = mutableListOf<VerticalGlyphInfo>()
            var currentY = input.paddingTopPx

            for (char in clause) {
                val baselineY = currentY - mainAscent
                glyphs.add(
                    VerticalGlyphInfo(
                        char = char,
                        x = colCenterX,
                        y = baselineY,
                        fontSizePx = currentFontSize,
                        isPunctuation = false,
                        isAttribution = false,
                    )
                )
                currentY += mainCharHeight + effectiveLineSpacing
            }

            val colHeight = currentY - input.paddingTopPx - effectiveLineSpacing
            columns.add(
                VerticalColumn(
                    columnIndex = i,
                    glyphs = glyphs,
                    columnWidth = currentFontSize,
                    columnHeight = colHeight,
                    isAttribution = false,
                )
            )

            // 下一列向左递推
            colCenterX -= (currentFontSize + effectiveColumnSpacing)
        }

        // 5. 落款排版：以左下角为起点附近向上排列，底端下齐，绝不与正文混排
        // 结构化提取作品名与作者（若传入结构化字段则优先使用；否则从 attribution 中解析）
        val (rawTitle, rawAuthor) = when {
            input.attributionTitle.isNotEmpty() || input.attributionAuthor.isNotEmpty() -> {
                Pair(input.attributionTitle, input.attributionAuthor)
            }
            input.attribution.contains("《") && input.attribution.contains("》") -> {
                val start = input.attribution.indexOf('《')
                val end = input.attribution.indexOf('》')
                val t = input.attribution.substring(start + 1, end).trim()
                val a = (input.attribution.substring(0, start) + input.attribution.substring(end + 1)).trim()
                Pair(t, a)
            }
            input.attribution.contains(" ") -> {
                val parts = input.attribution.trim().split(Regex("\\s+"), limit = 2)
                Pair(parts[0], parts.getOrElse(1) { "" })
            }
            else -> {
                Pair(input.attribution.trim(), "")
            }
        }

        val cleanTitle = if (input.filterPunctuation) {
            rawTitle.filter { it !in PUNCTUATIONS && !it.isWhitespace() }
        } else {
            rawTitle.filter { !it.isWhitespace() }
        }
        val cleanAuthor = if (input.filterPunctuation) {
            rawAuthor.filter { it !in PUNCTUATIONS && !it.isWhitespace() }
        } else {
            rawAuthor.filter { !it.isWhitespace() }
        }

        if (cleanTitle.isNotEmpty() || cleanAuthor.isNotEmpty()) {
            val titleCount = cleanTitle.length
            val authorCount = cleanAuthor.length

            val effectiveAttrLineSpacing = input.attrLineSpacingPx * (currentAttributionFontSize / input.attributionFontSizePx)
            val effectiveTitleAuthorGap = input.attrTitleAuthorGapPx * (currentAttributionFontSize / input.attributionFontSizePx)

            val titleHeight = if (titleCount > 0) titleCount * attrCharHeight + (titleCount - 1) * effectiveAttrLineSpacing else 0f
            val authorHeight = if (authorCount > 0) authorCount * attrCharHeight + (authorCount - 1) * effectiveAttrLineSpacing else 0f
            val hasBoth = titleCount > 0 && authorCount > 0
            val totalAttrHeight = titleHeight + authorHeight + (if (hasBoth) effectiveTitleAuthorGap else 0f)

            // 落款 X 坐标：左侧留白 + 半个落款列宽
            val attrCenterX = input.paddingLeftPx + (currentAttributionFontSize / 2f)

            // 落款 Y 坐标：底端锚定在可用高度 - 底部留白处，向上延伸起始坐标
            val bottomAnchor = input.availableHeight - input.paddingBottomPx
            val attrStartY = max(bottomAnchor - totalAttrHeight, input.paddingTopPx)

            val attrGlyphs = mutableListOf<VerticalGlyphInfo>()
            var currAttrY = attrStartY

            // 排布作品名（居于落款列上方）
            for (char in cleanTitle) {
                val baselineY = currAttrY - attrAscent
                attrGlyphs.add(
                    VerticalGlyphInfo(
                        char = char,
                        x = attrCenterX,
                        y = baselineY,
                        fontSizePx = currentAttributionFontSize,
                        isPunctuation = false,
                        isAttribution = true,
                    )
                )
                currAttrY += attrCharHeight + effectiveAttrLineSpacing
            }

            // 若同时有作品名和作者，插入专属停顿留白间隔（替代生硬空格）
            if (hasBoth) {
                currAttrY = currAttrY - effectiveAttrLineSpacing + effectiveTitleAuthorGap
            }

            // 排布作者（居于落款列下方，下齐于底端锚点）
            for (char in cleanAuthor) {
                val baselineY = currAttrY - attrAscent
                attrGlyphs.add(
                    VerticalGlyphInfo(
                        char = char,
                        x = attrCenterX,
                        y = baselineY,
                        fontSizePx = currentAttributionFontSize,
                        isPunctuation = false,
                        isAttribution = true,
                    )
                )
                currAttrY += attrCharHeight + effectiveAttrLineSpacing
            }

            columns.add(
                VerticalColumn(
                    columnIndex = textClauses.size,
                    glyphs = attrGlyphs,
                    columnWidth = currentAttributionFontSize,
                    columnHeight = totalAttrHeight,
                    isAttribution = true,
                )
            )
        }

        return VerticalLayoutResult(
            columns = columns,
            contentWidth = input.availableWidth,
            contentHeight = input.availableHeight,
            fontSizePx = currentFontSize,
        )
    }
}
