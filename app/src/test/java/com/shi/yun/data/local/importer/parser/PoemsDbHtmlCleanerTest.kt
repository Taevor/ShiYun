package com.shi.yun.data.local.importer.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PoemsDbParser 正文安全清洗算法专项测试 (PoemsDbHtmlCleanerTest)
 *
 * 验证规则：
 * 1. 严格 ASCII HTML 标签白名单清洗：正确剥离/转换 p, div, span, br, hr, strong, b, i, em, font, a；
 * 2. 带属性的 HTML 标签正确清洗；
 * 3. 大小写不同的 HTML 标签正确清洗；
 * 4. 块级与换行标签合理换行，杜绝全拼一行，杜绝多余空行；
 * 5. 绝对保护特殊构字标记：<舟双>、<黑宛>、<石含> 100% 原样保留；
 * 6. HTML 与构字标记混合出现时，HTML 被清理，构字标记完好无损；
 * 7. 未知非白名单尖括号标记（如古籍文集夹注）100% 原样保留；
 * 8. 清洗前后字符保真对照，确认没有误删任何汉字或擅自改写文字；
 * 9. 基于 poems-db 真实作品 JSON 记录进行端到端解析验证（rawContent 保留底本，content 纯净就绪）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PoemsDbHtmlCleanerTest {

    private val parser = PoemsDbParser()

    @Test
    fun testStandardHtmlTagsCleaning() {
        val input = "<p>千里黄云白日曛，北风吹雁雪纷纷。</p><p>莫愁前路无知己，天下谁人不识君。</p>"
        val expected = "千里黄云白日曛，北风吹雁雪纷纷。\n莫愁前路无知己，天下谁人不识君。"
        val actual = ClassicalTextHtmlCleaner.clean(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testHtmlTagsWithAttributes() {
        val input = "<p class=\"poem-para\"><span style=\"font-family:SimSun; font-size:14px;\">丙辰中秋，欢饮达旦，大醉，作此篇，兼怀子由。</span></p>\n<p>明月几时有？把酒问青天。</p>"
        val expected = "丙辰中秋，欢饮达旦，大醉，作此篇，兼怀子由。\n明月几时有？把酒问青天。"
        val actual = ClassicalTextHtmlCleaner.clean(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testCaseInsensitiveHtmlTags() {
        val input = "<P>春眠不觉晓，<BR/>处处闻啼鸟。</P><DIV class=\"stanza\">夜来风雨声，<br >花落知多少。</DIV>"
        val expected = "春眠不觉晓，\n处处闻啼鸟。\n夜来风雨声，\n花落知多少。"
        val actual = ClassicalTextHtmlCleaner.clean(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testBlockTagsReasonableLineBreaks() {
        val input = "<div>白日依山尽，</div><div>黄河入海流。</div><hr/><p>欲穷千里目，</p><p>更上一层楼。</p>"
        val expected = "白日依山尽，\n黄河入海流。\n欲穷千里目，\n更上一层楼。"
        val actual = ClassicalTextHtmlCleaner.clean(input)
        assertEquals(expected, actual)
        // 验证没有拼成单行，且每行都是完整的诗句
        val lines = actual.split("\n")
        assertEquals(4, lines.size)
        assertEquals("白日依山尽，", lines[0])
        assertEquals("更上一层楼。", lines[3])
    }

    @Test
    fun testSpecialChineseCharBracketsPreserved() {
        // 验证 <舟双>、<黑宛>、<石含> 绝不被当作 HTML 删除
        val text1 = "西山像佛头，又如抹青黛。春风几点桃花雨，欲渡前溪问小<舟双>。"
        assertEquals(text1, ClassicalTextHtmlCleaner.clean(text1))

        val text2 = "忆昔临平山下过。无数荷花，照水无纤<黑宛>。短艇直疑天上坐。"
        assertEquals(text2, ClassicalTextHtmlCleaner.clean(text2))

        val text3 = "错列荆榛对孤驿...文彩天开海岳图，面面晶光盈十尺...<石含>"
        assertEquals(text3, ClassicalTextHtmlCleaner.clean(text3))
    }

    @Test
    fun testHtmlAndSpecialCharsMixed() {
        val input = "<p class=\"verse\">忆昔临平山下过。无数荷花，照水无纤<黑宛>。</p>\n" +
                "<p class=\"verse\">春风几点桃花雨，欲渡前溪问小<舟双>。</p>"
        val expected = "忆昔临平山下过。无数荷花，照水无纤<黑宛>。\n春风几点桃花雨，欲渡前溪问小<舟双>。"
        val actual = ClassicalTextHtmlCleaner.clean(input)

        assertEquals(expected, actual)
        assertTrue(actual.contains("<黑宛>"))
        assertTrue(actual.contains("<舟双>"))
        assertFalse(actual.contains("<p"))
        assertFalse(actual.contains("</p>"))
    }

    @Test
    fun testUnknownBracketTagsPreserved() {
        // 古籍文集中的夹注与其它未知尖括号标记原样保留
        val input = "<p>天宝初，客游金陵。<松风亭见东坡梅诗> <strong>名花倾国两相欢</strong>。</p>"
        val expected = "天宝初，客游金陵。<松风亭见东坡梅诗> 名花倾国两相欢。"
        val actual = ClassicalTextHtmlCleaner.clean(input)
        assertEquals(expected, actual)
        assertTrue(actual.contains("<松风亭见东坡梅诗>"))
    }

    @Test
    fun testNoUnintendedModification() {
        val originalNoHtml = "人生得意须尽欢，莫使金樽空对月。\n天生我材必有用，千金散尽还复来。"
        val actual = ClassicalTextHtmlCleaner.clean(originalNoHtml)
        assertEquals(originalNoHtml, actual)
    }

    @Test
    fun testRealPoemsDbRecordBieDongDa() {
        // 真实高适《别董大》原始记录（数组形式，带 <p> 标签与换行）
        val jsonStr = """
        {
            "_id": "5b9a0136367d5c96f4cd3001",
            "name": "别董大二首·其一",
            "author": "高适",
            "dynasty": "唐代",
            "format": "诗",
            "content": [
                "\n<p>千里黄云白日曛，北风吹雁雪纷纷。",
                "莫愁前路无知己，天下谁人不识君。</p>\n"
            ],
            "sourceLink": "https://so.gushiwen.cn/shiwenv_5b9a0136367d5c96f4cd3001.aspx"
        }
        """.trimIndent()

        val candidate = parser.parseJson(jsonStr).first()

        // 验证清洗后的 content
        assertEquals(
            "千里黄云白日曛，北风吹雁雪纷纷。\n莫愁前路无知己，天下谁人不识君。",
            candidate.content
        )

        // 验证 rawContent 100% 原始保留带 <p> 标签的底本
        assertTrue(candidate.rawContent!!.contains("<p>"))
        assertTrue(candidate.rawContent!!.contains("</p>"))
    }

    @Test
    fun testRealPoemsDbRecordShuiDiaoGeTou() {
        // 真实苏轼《水调歌头》原始记录（带 span 样式题记、异文括号与 <p> 标签）
        val jsonStr = """
        {
            "_id": "5b9a0136367d5c96f4cd3002",
            "name": "水调歌头·明月几时有",
            "author": "苏轼",
            "dynasty": "宋代",
            "format": "词",
            "content": [
                "\n<p><span style=\"font-family:SimSun;\">丙辰中秋，欢饮达旦，大醉，作此篇，兼怀子由。</span></p>\n<p>明月几时有？把酒问青天。不知天上宫阙，今夕是何年。我欲乘风归去，又恐琼楼玉宇，高处不胜寒。起舞弄清影，何似在人间？(何似 一作：何时；又恐 一作：惟 / 唯恐)",
                "转朱阁，低绮户，照无眠。不应有恨，何事长向别时圆？人有悲欢离合，月有阴晴圆缺，此事古难全。但愿人长久，千里共婵娟。(长向 一作：偏向)</p>\n"
            ],
            "sourceLink": "https://so.gushiwen.cn/shiwenv_5b9a0136367d5c96f4cd3002.aspx"
        }
        """.trimIndent()

        val candidate = parser.parseJson(jsonStr).first()

        // 验证 span 样式被清洗，但题记文字完整保留，分段规范
        val lines = candidate.content!!.split("\n")
        assertEquals(3, lines.size)
        assertEquals("丙辰中秋，欢饮达旦，大醉，作此篇，兼怀子由。", lines[0])
        assertTrue(lines[1].startsWith("明月几时有？把酒问青天。"))
        assertTrue(lines[2].startsWith("转朱阁，低绮户，照无眠。"))

        // 异文括号完整保留，无擅自删改
        assertTrue(candidate.content!!.contains("(何似 一作：何时；又恐 一作：惟 / 唯恐)"))
        assertTrue(candidate.content!!.contains("(长向 一作：偏向)"))

        // 题名正确拆分为词牌与真实词题
        assertEquals("明月几时有", candidate.title)
        assertEquals("水调歌头", candidate.cipai)
    }

    @Test
    fun testRealPoemsDbRecordZhaoChangQingDieLianHua() {
        // 真实赵长卿《蝶恋花》原始记录（包含 <黑宛> 生僻构字标记）
        val jsonStr = """
        {
            "_id": "5b9a0136367d5c96f4cd3003",
            "name": "蝶恋花",
            "author": "赵长卿",
            "dynasty": "宋代",
            "format": "词",
            "content": [
                "\n<p>忆昔临平山下过。无数荷花，照水无纤<黑宛>。短艇直疑天上坐。醉眠花里香无那。</p>\n",
                "\n<p>雨浥红妆娇娜娜。脉脉含情，欲向风前破。莫道晚来风景可。青房著子千千颗。</p>\n"
            ],
            "sourceLink": "https://so.gushiwen.cn/shiwenv_5b9a0136367d5c96f4cd3003.aspx"
        }
        """.trimIndent()

        val candidate = parser.parseJson(jsonStr).first()

        // 验证 <黑宛> 绝不被误删，100% 原始保留
        assertTrue(candidate.content!!.contains("照水无纤<黑宛>"))

        // 验证由于无独立词题，成功提取首句作为默认词题
        assertEquals("忆昔临平山下过", candidate.title)
        assertEquals("蝶恋花", candidate.cipai)
    }

    @Test
    fun testRealPoemsDbRecordWuRuZiWangXiShan() {
        // 真实吴孺子《望西山》原始记录（包含 <舟双> 构字标记）
        val jsonStr = """
        {
            "_id": "5b9a0136367d5c96f4cd3004",
            "name": "望西山",
            "author": "吴孺子",
            "dynasty": "明代",
            "format": "诗",
            "content": [
                "\n<p>西山像佛头，又如抹青黛。常疑路不通，遥遥削天外。</p>",
                "<p>白鹤飞去杳不双，明霞泼天红映江。春风几点桃花雨，欲渡前溪问小<舟双>。</p>\n"
            ],
            "sourceLink": "https://so.gushiwen.cn/shiwenv_5b9a0136367d5c96f4cd3004.aspx"
        }
        """.trimIndent()

        val candidate = parser.parseJson(jsonStr).first()

        // 验证 <舟双> 完好保留
        assertTrue(candidate.content!!.contains("欲渡前溪问小<舟双>"))
        assertEquals("望西山", candidate.title)
        assertEquals("诗", candidate.genre)
    }

    @Test
    fun testRealPoemsDbRecordFanQiDivSample() {
        // 真实梵琦《渔家傲》（包含 <div class="small"></div> 混合分段）
        val jsonStr = """
        {
            "_id": "5b9a0136367d5c96f4cd3005",
            "name": "渔家傲",
            "author": "梵琦",
            "dynasty": "元代",
            "format": "词",
            "content": [
                "\n<p>听说西方无量乐。娑婆已悔从前错。佛号自呼还自讷。</p><p>思量着。惟心净土谁云隔。</p><div class=\"small\"></div><p>一贯由来双五百。婴儿谩把空拳吓。拟议不来遭一掴。</p><p>诸禅客。凡情圣解曾销铄。</p>\n"
            ],
            "sourceLink": "https://so.gushiwen.cn/shiwenv_5b9a0136367d5c96f4cd3005.aspx"
        }
        """.trimIndent()

        val candidate = parser.parseJson(jsonStr).first()

        // 验证 div 与 p 被干净清除，4 个诗段规范以换行连接
        val lines = candidate.content!!.split("\n")
        assertEquals(4, lines.size)
        assertEquals("听说西方无量乐。娑婆已悔从前错。佛号自呼还自讷。", lines[0])
        assertEquals("思量着。惟心净土谁云隔。", lines[1])
        assertEquals("一贯由来双五百。婴儿谩把空拳吓。拟议不来遭一掴。", lines[2])
        assertEquals("诸禅客。凡情圣解曾销铄。", lines[3])
    }
}
