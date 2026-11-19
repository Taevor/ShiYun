package com.shi.yun.ui.detail

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle

/**
 * 诗词完整句子切分与排版断行工具类
 *
 * 核心规则：
 * 1. 严格以完整诗句/分句为基本排版单元，绝不允许在单个诗句中间随意断行；
 * 2. 对仗诗句（出句与对句）优先作为一个完整排版组，当行宽能完整容纳时排在同一行；
 * 3. 当行宽无法容纳完整两句时，整句换行，绝不允许只把最后一两个字挤到下一行；
 * 4. 词同样按完整分句为单位换行，保留原作品句子顺序与节奏。
 */
object PoemTypography {

    private val CLAUSE_DELIMITER_REGEX = Regex("(?<=[，。！？；：、,!?;:])")

    /**
     * 将单行文本按标点符号切分为独立不可拆分的分句单元（保留标点于句尾）
     */
    fun splitIntoClauses(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val parts = trimmed.split(CLAUSE_DELIMITER_REGEX)
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 将长诗句或短句按语义标点拆分为适合卡片或行宽的整句列表
     */
    fun splitSentenceIntoLines(text: String, thresholdChars: Int = 11): List<String> {
        val trimmed = text.trim()
        if (trimmed.length <= thresholdChars) return listOf(trimmed)
        val clauses = splitIntoClauses(trimmed)
        if (clauses.size <= 1) return listOf(trimmed)

        // 若刚好是对仗两句（如五言对仗12字、七言对仗16字），直接分为两行整句
        if (clauses.size == 2) {
            return clauses
        }

        val lines = mutableListOf<String>()
        var current = ""
        for (clause in clauses) {
            val candidate = current + clause
            if (candidate.length <= thresholdChars + 4) {
                current = candidate
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current)
                }
                current = clause
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current)
        }
        return lines
    }

    /**
     * 基于真实文本排版测量器 (TextMeasurer) 与容器最大像素宽度，将分句贪心打包为完整句子行
     *
     * 保证每一行都是完整分句，绝不中途裁断汉字。
     */
    fun packClausesIntoLines(
        clauses: List<String>,
        textMeasurer: TextMeasurer,
        style: TextStyle,
        maxWidthPx: Float,
    ): List<String> {
        if (clauses.isEmpty()) return emptyList()
        if (clauses.size == 1) return clauses

        val result = mutableListOf<String>()
        var currentLine = ""

        for (clause in clauses) {
            val candidate = if (currentLine.isEmpty()) clause else currentLine + clause
            val candidateWidth = textMeasurer.measure(
                text = candidate,
                style = style,
            ).size.width

            if (candidateWidth <= maxWidthPx) {
                currentLine = candidate
            } else {
                if (currentLine.isNotEmpty()) {
                    result.add(currentLine)
                    currentLine = clause
                } else {
                    result.add(clause)
                    currentLine = ""
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            result.add(currentLine)
        }
        return result
    }
}
