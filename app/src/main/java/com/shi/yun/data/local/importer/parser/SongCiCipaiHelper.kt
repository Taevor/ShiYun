package com.shi.yun.data.local.importer.parser

/**
 * 宋词词牌工具集与知名词牌权威词典 (SongCiCipaiHelper)
 *
 * 专门用于离线数据源（如 poems-db, Poetry_CN, chinese-poetry 等）宋词题名的智能解析与规范化：
 * 1. 词牌与词题解耦：提供可核实的经典词牌词表；
 * 2. 识别并拆分「词牌·词题」以及「词牌（词题）」格式；
 * 3. 识别完全等于词牌名称的脏污 title；
 * 4. 可靠正文首句提取：严格排除小序与作者附记，不篡改原文，提取第一完整分句。
 */
object SongCiCipaiHelper {

    /**
     * 常见经典词牌集合（权威词牌，用于高精度匹配，绝不使用模糊相似度猜测）
     */
    val KNOWN_CIPAIS: Set<String> = setOf(
        "水调歌头", "青玉案", "如梦令", "念奴娇", "满江红",
        "菩萨蛮", "西江月", "蝶恋花", "浣溪沙", "临江仙",
        "定风波", "破阵子", "相见欢", "生查子", "卜算子",
        "虞美人", "清平乐", "玉楼春", "鹧鸪天", "踏莎行",
        "点绛唇", "忆江南", "采桑子", "浪淘沙", "渔家傲",
        "醉花阴", "一剪梅", "声声慢", "贺新郎", "永遇乐",
        "江城子", "雨霖铃", "水龙吟", "苏幕遮", "暗香",
        "疏影", "木兰花", "减字木兰花", "南乡子", "摸鱼儿",
        "南歌子", "千秋岁", "天净沙", "满庭芳", "八声甘州",
        "望海潮", "摊破浣溪沙", "昭君怨", "风入松", "祝英台近",
        "导引", "导引子", "更漏子", "喜迁莺", "解连环",
        "六丑", "兰陵王", "御街行", "离亭燕", "石州慢",
        "忆秦娥", "长相思", "诉衷情", "女冠子", "更衣记",
        "霜天晓角", "柳梢青", "好事近", "锦缠道", "阮郎归",
        // 本次确认补充的经典词牌（9个）
        "殢人娇", "桃源忆故人", "月上瓜洲", "凤衔杯", "瑞鹧鸪",
        "绿罗裙", "夜飞鹊", "酹江月", "鱼游春水"
    )

    /**
     * 解析后的词牌与词题结构
     */
    data class ParsedCipaiTitle(
        /** 原始标题 */
        val rawTitle: String,
        /** 识别提取出的词牌 */
        val cipai: String?,
        /** 识别提取出的独立词题 (若无则为 null) */
        val citi: String?,
        /** 是否匹配为标准词牌格式 */
        val isMatched: Boolean
    )

    /**
     * 针对输入标题与可选体裁/现有词牌进行分析
     *
     * 处理场景：
     * A. title 包含全角/半角括号，形如「蝶恋花（和任路分荷花）」或「蝶恋花(和任路分荷花)」；
     * B. title 包含分隔符（如 ·、•、/、空格），形如「水调歌头·明月几时有」或「水调歌头 明月几时有」；
     * C. title 完全等于已知词牌（如「水调歌头」）；
     * D. 外部已提供 cipaiHint（例如 chinese-poetry 的 rhythmic = "水调歌头"）；
     * E. 无法可靠识别：保持原样，不猜测。
     */
    fun parseTitleAndCipai(rawTitle: String?, cipaiHint: String? = null): ParsedCipaiTitle {
        val trimmedTitle = rawTitle?.trim().orEmpty()
        val trimmedHint = cipaiHint?.trim()?.ifEmpty { null }

        // 如果没有标题
        if (trimmedTitle.isEmpty()) {
            return ParsedCipaiTitle(
                rawTitle = "",
                cipai = trimmedHint,
                citi = null,
                isMatched = trimmedHint != null
            )
        }

        // 场景 A：检查括号形式的词牌词题分离（支持全角括号 （...） 与半角括号 (...)）
        val bracketRegex = Regex("""^(.+?)[（(]([^）)]+)[）)](.*)$""")
        val bracketMatch = bracketRegex.find(trimmedTitle)
        if (bracketMatch != null) {
            val potentialCipai = bracketMatch.groupValues[1].trim()
            val bracketContent = bracketMatch.groupValues[2].trim()
            val trailing = bracketMatch.groupValues[3].trim()
            if (isKnownCipai(potentialCipai) || (trimmedHint != null && potentialCipai == trimmedHint)) {
                val combinedCiti = if (trailing.isNotEmpty()) "$bracketContent $trailing" else bracketContent
                return ParsedCipaiTitle(
                    rawTitle = trimmedTitle,
                    cipai = potentialCipai,
                    citi = combinedCiti.ifEmpty { null },
                    isMatched = true
                )
            }
        }

        // 场景 B：检查是否为「词牌·词题」格式（支持常见分隔符：中圆点 ·, •, 顿号 、, 斜杠 /, 空格）
        val separatorRegex = Regex("[·•\\s/、_#-]")
        val parts = trimmedTitle.split(separatorRegex).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val potentialCipai = parts[0]
            val potentialCiti = parts.subList(1, parts.size).joinToString(" ")
            if (isKnownCipai(potentialCipai) || potentialCipai == trimmedHint) {
                return ParsedCipaiTitle(
                    rawTitle = trimmedTitle,
                    cipai = potentialCipai,
                    citi = potentialCiti.ifEmpty { null },
                    isMatched = true
                )
            }
        }

        // 场景 C：title 完全等于已知词牌或等于传入的 cipaiHint
        if (isKnownCipai(trimmedTitle) || (trimmedHint != null && trimmedTitle == trimmedHint)) {
            return ParsedCipaiTitle(
                rawTitle = trimmedTitle,
                cipai = trimmedTitle,
                citi = null, // title 是词牌名，不能当作词题
                isMatched = true
            )
        }

        // 场景 D：若已有外部确定的词牌 hint，检查 title 是否以词牌开头但未用分隔符（如「水调歌头明月几时有」）
        if (trimmedHint != null && trimmedTitle.startsWith(trimmedHint) && trimmedTitle.length > trimmedHint.length) {
            val remainder = trimmedTitle.substring(trimmedHint.length).trim()
            if (remainder.isNotEmpty()) {
                return ParsedCipaiTitle(
                    rawTitle = trimmedTitle,
                    cipai = trimmedHint,
                    citi = remainder,
                    isMatched = true
                )
            }
        }

        // 场景 E：无法可靠识别
        return ParsedCipaiTitle(
            rawTitle = trimmedTitle,
            cipai = trimmedHint,
            citi = trimmedTitle,
            isMatched = false
        )
    }

    /**
     * 判断是否为已知词牌
     */
    fun isKnownCipai(name: String): Boolean {
        val clean = name.trim()
        return KNOWN_CIPAIS.contains(clean)
    }

    /**
     * 从宋词正文提取首句作为默认词题
     *
     * 约束规则：
     * 1. 不得篡改原文，不清洗字符，保留首句原始文字；
     * 2. 严格排除小序/题记：
     *    - 小序标志（如以「并序」「序曰」结尾，或包含「作此篇」「欢饮达旦」「遇雨」等典型词序模式，或在括号/中括号/双换行前）；
     * 3. 提取第一完整分句（以标点 ，。！？；：、 为边界）；
     * 4. 无法可靠提取时返回 null。
     */
    fun extractFirstLineAsTitle(content: String?): String? {
        if (content.isNullOrBlank()) return null

        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        // 寻找正文第一行（跳过独立成段的序言）
        var firstPoemLine: String? = null
        for (line in lines) {
            if (isPrefaceLine(line)) {
                continue
            }
            firstPoemLine = line
            break
        }

        if (firstPoemLine == null) return null

        // 若该行内部开头包含括号括起的小序（如（丙辰中秋...）或 [中秋]），去除首部括号
        var cleanLine = firstPoemLine
        if (cleanLine.startsWith("（") && cleanLine.contains("）")) {
            val endIdx = cleanLine.indexOf("）")
            cleanLine = cleanLine.substring(endIdx + 1).trim()
        } else if (cleanLine.startsWith("(") && cleanLine.contains(")")) {
            val endIdx = cleanLine.indexOf(")")
            cleanLine = cleanLine.substring(endIdx + 1).trim()
        } else if (cleanLine.startsWith("【") && cleanLine.contains("】")) {
            val endIdx = cleanLine.indexOf("】")
            cleanLine = cleanLine.substring(endIdx + 1).trim()
        } else if (cleanLine.startsWith("[") && cleanLine.contains("]")) {
            val endIdx = cleanLine.indexOf("]")
            cleanLine = cleanLine.substring(endIdx + 1).trim()
        }

        if (cleanLine.isEmpty()) return null

        // 按中文与英文常见句读标点分割首句
        val punctuationRegex = Regex("[，。！？；：、,!?;:]")
        val tokens = cleanLine.split(punctuationRegex).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val firstSentence = tokens[0]

        // 进一步防护：若提取结果依然像小序（例如仍包含"作此篇"等），则判定无法可靠提取
        if (isPrefaceContent(firstSentence)) {
            return null
        }

        // 首句长度校验：正常词句通常在 2 到 15 字之间
        if (firstSentence.length < 2 || firstSentence.length > 20) {
            return null
        }

        return firstSentence
    }

    /**
     * 判断整行是否为小序/题记
     */
    private fun isPrefaceLine(line: String): Boolean {
        val trimmed = line.trim()
        // 括号包裹
        if ((trimmed.startsWith("（") && trimmed.endsWith("）")) ||
            (trimmed.startsWith("(") && trimmed.endsWith(")")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
            (trimmed.startsWith("【") && trimmed.endsWith("】"))
        ) {
            return true
        }

        // 显式小序结尾词或常见词序特征
        if (trimmed.endsWith("并序") || trimmed.endsWith("序曰") || trimmed.endsWith("自序") || trimmed.endsWith("小序")) {
            return true
        }

        if (trimmed.contains("作此篇") || trimmed.contains("作此词") || trimmed.contains("为作此") ||
            trimmed.contains("欢饮达旦") || trimmed.contains("沙湖道中遇雨")
        ) {
            // 这类典型小序如果较长且包含句号/叹号等叙述，明确属于小序
            if (trimmed.length > 10) return true
        }

        return false
    }

    /**
     * 判断截取的短句是否含有明显的词序词汇
     */
    private fun isPrefaceContent(sentence: String): Boolean {
        return sentence.contains("作此篇") || sentence.contains("并序") || sentence.contains("序曰") ||
            sentence.contains("欢饮达旦") || sentence.contains("大醉") || sentence.contains("命仆")
    }

    /**
     * 判断某条原始记录是否具备可靠的词作身份依据
     *
     * 规则要求：
     * 1. 明确的诗、律诗、绝句、古诗、曲、赋、散文等体裁信息必须优先，绝不识别为词；
     * 2. 不能仅凭标题命中词牌字典就强制改成 CI；
     * 3. 只有存在可靠的词作身份依据时（如体裁明确为词、标签明确含词、或标题为明确的「词牌·词题」/「词牌（词题）」复合结构且无反向诗曲信息），才识别为词；
     * 4. 信息不足时保持 false，不盲目猜测体裁。
     */
    fun isReliableCi(rawGenre: String?, tags: List<String>, rawTitle: String?, dynasty: String? = null): Boolean {
        val titleClean = rawTitle?.trim().orEmpty()
        val genreClean = rawGenre?.trim()?.lowercase().orEmpty()
        val allTagsClean = tags.map { it.trim().lowercase() }

        // 1. 明确的非词体裁信息优先判定（诗、律诗、绝句、古风、曲、散曲、赋、文、散文、游记等绝不识别为词）
        val nonCiGenreKeywords = listOf(
            "诗", "律", "绝", "古风", "歌行", "乐府",
            "曲", "散曲", "杂剧", "小令", "套数",
            "赋", "辞赋", "文", "散文", "游记"
        )
        val hasExplicitNonCiGenre = nonCiGenreKeywords.any { keyword ->
            if (genreClean == "古诗词" || genreClean == "诗词") false
            else if (genreClean.contains("词") && !genreClean.contains("诗词")) false
            else genreClean.contains(keyword)
        }
        if (hasExplicitNonCiGenre) {
            return false
        }

        // 明确的词作权威标签优先（如「宋词三百首」「宋词精选」「宋词」等），绝不被教材通识标签（如「初中古诗」「高中古诗」）误拒
        if (allTagsClean.any { it == "词" || it == "宋词" || it == "宋词精选" || it == "宋词三百首" || it == "词选" }) {
            return true
        }

        val hasExplicitNonCiTag = allTagsClean.any { tag ->
            tag.contains("唐诗") || tag.contains("古诗") || tag.contains("律诗") ||
            tag.contains("绝句") || tag.contains("元曲") || tag.contains("散曲") ||
            tag.contains("散文") || tag.contains("游记") || tag.contains("文言文")
        }
        if (hasExplicitNonCiTag) {
            return false
        }

        // 2. 检查是否有可靠的词作身份依据
        // A. 来源字段明确声明体裁为词
        if (genreClean == "词" || genreClean == "宋词" || genreClean == "ci" ||
            (genreClean.contains("词") && !genreClean.contains("诗词") && !genreClean.contains("词话"))
        ) {
            return true
        }

        // B. 标题包含明确的「词牌·词题」或「词牌（词题）」复合结构（且词牌属于已知权威词牌），且非元代散曲（如天净沙）
        val parsed = parseTitleAndCipai(titleClean)
        if (parsed.isMatched && parsed.cipai != null && isKnownCipai(parsed.cipai)) {
            if (dynasty != null && (dynasty.contains("元") || dynasty.contains("金")) && parsed.cipai == "天净沙") {
                return false
            }
            // 必须拆分出独立词题（如 · 或 （） 后有词题）；若仅纯词牌名且无其它词作支持时不盲目判定为词
            if (parsed.citi != null) {
                return true
            }
        }

        // 3. 仅凭纯标题等于词牌名称（如 title = "长相思"），无任何词作体裁或标签支持时，信息不足，绝不猜测为词
        return false
    }
}
