package com.shi.yun.data.local.importer.parser

/**
 * 元曲曲牌与散曲识别工具集 (YuanQuQupaiHelper)
 *
 * 专门用于离线数据源中元曲（散曲/小令/套数）题名的智能解析与体裁确权：
 * 1. 规范识别权威曲牌及带过曲（如「醉高歌带摊破喜春来」）；
 * 2. 妥善兼容与剥离宫调前缀（如【越调】、【双调】、【中吕】）；
 * 3. 曲牌与曲题解耦（如「天净沙·秋思」拆分为曲牌「天净沙」与曲题「秋思」）；
 * 4. 严谨的体裁确权准则（isReliableQu）：仅当朝代为元代/金代且具备可靠曲牌，或来源明确标记为元曲时才识别为 QU，
 *    严禁将元代所有作品盲目归入 QU。
 */
object YuanQuQupaiHelper {

    /**
     * 常见经典元曲曲牌集合（北曲散曲小令常用曲牌）
     */
    val KNOWN_QUPAIS: Set<String> = setOf(
        "天净沙", "沉醉东风", "折桂令", "清江引", "鹦鹉曲",
        "喜春来", "摊破喜春来", "山坡羊", "落梅风", "水仙子",
        "红绣鞋", "白鹤子", "塞鸿秋", "四块玉", "普天乐",
        "朝天子", "小梁州", "雁儿落", "得胜令", "凭阑人",
        "拨不断", "醉太平", "快活三", "满庭芳", "乔木查",
        "骂玉郎", "感皇恩", "采茶歌", "脱布衫", "小桃红",
        "醉高歌", "叨叨令", "黑漆弩", "沽美酒", "太平令",
        "醉花阴", "寄生草", "后庭花", "青杏儿", "庆东原",
        "骤雨打新荷", "殿前欢", "蟾宫曲", "寿阳曲", "拨不断"
    )

    /**
     * 元曲常见宫调名称
     */
    val KNOWN_GONGDIAO: Set<String> = setOf(
        "越调", "双调", "中吕", "正宫", "南吕", "仙吕", "黄钟", "大石", "商调", "般涉调",
        "中吕宫", "正宫调", "仙吕宫", "南吕宫", "黄钟宫"
    )

    /**
     * 解析后的曲牌与曲题结构
     */
    data class ParsedQupaiTitle(
        /** 原始标题 */
        val rawTitle: String,
        /** 提取出的宫调名称（若有） */
        val gongdiao: String?,
        /** 提取出的曲牌名称（含带过曲） */
        val qupai: String?,
        /** 提取出的独立曲题 (若无则为 null) */
        val quti: String?,
        /** 是否匹配为标准曲牌格式 */
        val isMatched: Boolean
    )

    /**
     * 解析元曲标题中的宫调、曲牌与曲题
     *
     * 处理场景：
     * 1. 包含宫调前缀：如《【越调】天净沙·秋思》、《【中吕】醉高歌带摊破喜春来·旅中》；
     * 2. 「曲牌·曲题」或「曲牌 曲题」：如《天净沙·秋思》、《鹦鹉曲·都门感旧》；
     * 3. 复合带过曲：如《醉高歌带摊破喜春来·旅中》；
     * 4. 纯曲牌名：如《天净沙》。
     */
    fun parseTitleAndQupai(rawTitle: String?, qupaiHint: String? = null): ParsedQupaiTitle {
        val trimmedTitle = rawTitle?.trim().orEmpty()
        val trimmedHint = qupaiHint?.trim()?.ifEmpty { null }

        if (trimmedTitle.isEmpty()) {
            return ParsedQupaiTitle(
                rawTitle = "",
                gongdiao = null,
                qupai = trimmedHint,
                quti = null,
                isMatched = trimmedHint != null
            )
        }

        // 步骤 1：检查并提取宫调前缀，如【越调】或 [双调]
        var gongdiao: String? = null
        var remainder = trimmedTitle
        val gongdiaoRegex = Regex("""^[【\[]([^】\]]+)[】\]](.*)$""")
        val gongdiaoMatch = gongdiaoRegex.find(trimmedTitle)
        if (gongdiaoMatch != null) {
            val potentialGongdiao = gongdiaoMatch.groupValues[1].trim()
            if (isKnownGongdiao(potentialGongdiao) || potentialGongdiao.endsWith("调") || potentialGongdiao.endsWith("宫")) {
                gongdiao = potentialGongdiao
                remainder = gongdiaoMatch.groupValues[2].trim()
            }
        }

        // 步骤 2：检查是否包含分隔符（如 ·、•、空格、顿号等）
        val separatorRegex = Regex("[·•\\s/、_#-]")
        val parts = remainder.split(separatorRegex).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val potentialQupai = parts[0]
            val potentialQuti = parts.subList(1, parts.size).joinToString(" ")
            if (isKnownQupaiOrCompound(potentialQupai) || potentialQupai == trimmedHint) {
                return ParsedQupaiTitle(
                    rawTitle = trimmedTitle,
                    gongdiao = gongdiao,
                    qupai = potentialQupai,
                    quti = potentialQuti.ifEmpty { null },
                    isMatched = true
                )
            }
        }

        // 步骤 3：无分隔符，检查 remainder 是否完全等于已知曲牌或带过曲
        if (isKnownQupaiOrCompound(remainder) || (trimmedHint != null && remainder == trimmedHint)) {
            return ParsedQupaiTitle(
                rawTitle = trimmedTitle,
                gongdiao = gongdiao,
                qupai = remainder,
                quti = null,
                isMatched = true
            )
        }

        // 步骤 4：若已有外部曲牌提示，检查标题是否以曲牌开头
        if (trimmedHint != null && remainder.startsWith(trimmedHint) && remainder.length > trimmedHint.length) {
            val qutiPart = remainder.substring(trimmedHint.length).trim()
            return ParsedQupaiTitle(
                rawTitle = trimmedTitle,
                gongdiao = gongdiao,
                qupai = trimmedHint,
                quti = qutiPart.ifEmpty { null },
                isMatched = true
            )
        }

        return ParsedQupaiTitle(
            rawTitle = trimmedTitle,
            gongdiao = gongdiao,
            qupai = trimmedHint,
            quti = trimmedTitle,
            isMatched = false
        )
    }

    /**
     * 判断是否为已知曲牌或合法的带过曲（如「醉高歌带摊破喜春来」）
     */
    fun isKnownQupaiOrCompound(name: String): Boolean {
        val clean = name.trim()
        if (KNOWN_QUPAIS.contains(clean)) return true

        // 检查带过曲格式（用“带”连接两个曲牌）
        if (clean.contains("带")) {
            val parts = clean.split("带").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2 && parts.any { KNOWN_QUPAIS.contains(it) || it.endsWith("曲") }) {
                return true
            }
        }

        // 常见以“曲”结尾的曲牌（排除《塞上曲》等传统乐府古题）
        if (clean.endsWith("曲") && !clean.endsWith("塞上曲") && clean.length in 3..6) {
            return true
        }

        return false
    }

    /**
     * 判断是否为已知宫调
     */
    fun isKnownGongdiao(name: String): Boolean {
        val clean = name.trim()
        return KNOWN_GONGDIAO.contains(clean) || clean.endsWith("调") || clean.endsWith("宫")
    }

    /**
     * 判断某条原始记录是否具备可靠的元曲（散曲）身份依据
     *
     * 约束：
     * 1. 明确的诗、词、散文、游记体裁优先排除；
     * 2. 只有当朝代为元代/金代且曲牌匹配成功，或来源明确标记为元曲/散曲时才识别为 QU；
     * 3. 严禁把元代所有作品直接标记为 QU。
     */
    fun isReliableQu(rawGenre: String?, tags: List<String>, rawTitle: String?, dynasty: String? = null): Boolean {
        val titleClean = rawTitle?.trim().orEmpty()
        val genreClean = rawGenre?.trim()?.lowercase().orEmpty()
        val allTagsClean = tags.map { it.trim().lowercase() }
        val dynastyClean = dynasty?.trim().orEmpty()

        // 1. 排除明确的非曲体裁信息（诗、律诗、绝句、词、散文、游记等绝不识别为曲）
        val nonQuKeywords = listOf(
            "律诗", "绝句", "古风", "诗经", "楚辞",
            "宋词", "词选", "词话", "散文", "游记", "文言文"
        )
        if (allTagsClean.any { tag -> nonQuKeywords.any { tag.contains(it.lowercase()) } }) {
            return false
        }
        if (genreClean.isNotEmpty() && genreClean != "古诗词" && genreClean != "诗词") {
            if (nonQuKeywords.any { genreClean.contains(it.lowercase()) }) {
                return false
            }
        }

        // 2. 证据 A：来源体裁或标签明确为元曲或散曲
        if (genreClean == "元曲" || genreClean == "散曲" || genreClean == "qu" ||
            (genreClean.contains("曲") && !genreClean.contains("古诗词") && !genreClean.contains("歌曲"))
        ) {
            return true
        }
        if (allTagsClean.any { it == "元曲" || it == "散曲" }) {
            return true
        }

        // 3. 证据 B：朝代为元代或金代，且标题解析为标准曲牌复合格式
        val isYuanOrJin = dynastyClean.contains("元") || dynastyClean.contains("金")
        if (isYuanOrJin) {
            val parsed = parseTitleAndQupai(titleClean)
            if (parsed.isMatched && parsed.qupai != null && isKnownQupaiOrCompound(parsed.qupai)) {
                return true
            }
        }

        return false
    }

    /**
     * 从元曲正文提取首句作为默认曲题（无法提取时返回 null）
     */
    fun extractFirstLineAsTitle(content: String?): String? {
        if (content.isNullOrBlank()) return null
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val firstLine = lines.first()
        val punctuationRegex = Regex("[，。！？；：、,!?;:]")
        val tokens = firstLine.split(punctuationRegex).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val sentence = tokens[0]
        if (sentence.length in 2..20) {
            return sentence
        }
        return null
    }
}
