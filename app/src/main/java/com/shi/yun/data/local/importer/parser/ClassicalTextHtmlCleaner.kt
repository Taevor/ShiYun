package com.shi.yun.data.local.importer.parser

/**
 * 古典文学文本 ASCII HTML 标签安全清洗器 (ClassicalTextHtmlCleaner)
 *
 * 专门用于清理 yxcs/poems-db 等来源中残存的网页排版容器标签：
 * 1. 严格白名单机制：只识别并处理确认属于标准 HTML 排版容器的 ASCII 英文标签
 *    (p, div, span, br, hr, strong, b, i, em, font, a)；
 * 2. 块级与换行标签合理分行：<br>, <hr>, </p>, </div>, <p>, <div> 转换为换行符 \n，防止诗句挤压；
 * 3. 行内标签安全剥离：<span>, <strong>, <b>, <i>, <em>, <font>, <a> 仅去除标签本身，100% 保留包裹的正文字符；
 * 4. 绝对保护非 HTML 尖括号标记：严禁使用泛化的 Regex("<[^>]+>")，确保古籍 IDS 构字标记（如 <舟双>、<黑宛>、<石含>）
 *    及古人诗文夹注（如 <松风亭见东坡梅诗>）完全不受破坏、原样留存；
 * 5. 换行与空白标准化：去除每行首尾多余空格，折叠连续空白行，保持古典诗词典雅规整的分行排版。
 */
object ClassicalTextHtmlCleaner {

    // 块级起始、闭合及显式换行标签正则（大小写不敏感，支持任意属性）
    // 匹配: <br>, <br/>, <br />, <hr>, </p>, </div>, <p...>, <div...>
    private val BLOCK_BREAK_REGEX = Regex("(?i)<(?:br|hr)\\s*/?>|</(?:p|div)>|<(?:p|div)(?:\\s+[^>]*)?>")

    // 行内修饰标签正则（大小写不敏感，支持任意属性）
    // 匹配: <span>, </span>, <strong>, </strong>, <b>, <i>, <em>, <font>, <a> 等
    private val INLINE_TAG_REGEX = Regex("(?i)</?(?:span|strong|b|i|em|font|a)(?:\\s+[^>]*)?>")

    /**
     * 安全清洗输入文本中的 HTML 标签
     * @param text 待清洗文本，若为 null 或空白字符串则返回空字符串
     * @return 清洗并规范换行后的纯净正文文本
     */
    fun clean(text: String?): String {
        if (text.isNullOrBlank()) return ""

        // 1. 将块级容器与换行标签统一替换为换行符
        var result = BLOCK_BREAK_REGEX.replace(text, "\n")

        // 2. 剥离行内样式与修饰标签，保留内部正文文字
        result = INLINE_TAG_REGEX.replace(result, "")

        // 3. 按行规整空白，过滤连续纯空行，保持规范换行
        val lines = result.split('\n')
        val nonBlankLines = lines.map { it.trim() }.filter { it.isNotEmpty() }

        return nonBlankLines.joinToString("\n")
    }
}
