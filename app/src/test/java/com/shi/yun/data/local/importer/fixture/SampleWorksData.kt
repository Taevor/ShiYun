package com.shi.yun.data.local.importer.fixture

import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.SourceImportInfo
import com.shi.yun.data.local.importer.model.WorkImportItem
import com.shi.yun.data.model.AnnotationItem
import com.shi.yun.data.model.LayoutType

/**
 * 阶段 3 步骤 5.3 批量导入全链路验证真实古典诗文样本数据集 (20 篇)
 *
 * 样本遴选标准：
 * 1. 真实可查：全部源自《诗经》《乐府诗集》《全唐诗》《东坡乐府》《全元散曲》等权威典籍；
 * 2. 覆盖多维：
 *    - 涵盖先秦、汉、三国、晋、唐、宋、元、清 8 个历史时期；
 *    - 涵盖诗（五绝、七绝、五律、七律、四言古诗）、词（如梦令、水调歌头、青玉案，含单调与双调）、曲（天净沙、山坡羊）；
 *    - 包含 4 篇无名氏/未知作者作品；
 *    - 包含 3 组同一作者多篇名作（李白、杜甫、苏轼）；
 *    - 包含作者别名（李太白、青莲居士、杜少陵、苏东坡、易安居士、稼轩居士等）；
 *    - 包含五维内容完备作品与仅有正文的极简作品；
 *    - 包含带序言、带专题标签、分类未知等各种边界样本。
 */
object SampleWorksData {

    fun getTwentySampleWorks(): List<WorkImportItem> {
        return listOf(
            // 1. 先秦 · 诗歌 · 未知作者 · 五维完备 · 结构化注释
            WorkImportItem(
                title = "关雎",
                author = null, // 未知作者
                dynasty = "先秦",
                genre = "poem",
                form = "four_gushi",
                layoutType = LayoutType.FIVE_GUSHI.code,
                tags = listOf("诗经", "周南", "爱情"),
                content = "关关雎鸠，在河之洲。窈窕淑女，君子好逑。\n参差荇菜，左右流之。窈窕淑女，寤寐求之。\n求之不得，寤寐思服。悠哉悠哉，辗转反侧。\n参差荇菜，左右采之。窈窕淑女，琴瑟友之。\n参差荇菜，左右芼之。窈窕淑女，钟鼓乐之。",
                contentSource = SourceImportInfo(sourceName = "诗经·周南", licenseType = "PUBLIC_DOMAIN"),
                translation = "关关鸣叫的水鸟，栖息在河中的沙洲。美丽贤淑的女子，正是君子的好配偶。",
                translationSource = SourceImportInfo(sourceName = "诗经今译"),
                annotationItems = listOf(
                    AnnotationItem(1, "关关", "象声词，水鸟啼鸣之声", "guān guān", 1),
                    AnnotationItem(2, "雎鸠", "一种水鸟，传说雌雄相爱不离", "jū jiū", 1),
                    AnnotationItem(3, "好逑", "良好的配偶", "hǎo qiú", 1),
                    AnnotationItem(4, "寤寐", "醒时和睡时，指日夜思念", "wù mèi", 2)
                ),
                annotationSource = SourceImportInfo(sourceName = "毛诗注疏"),
                background = "《毛诗序》认为此诗为后妃之德，现代学者普遍认为是先秦时期的民间恋歌。",
                backgroundSource = SourceImportInfo(sourceName = "先秦文学史"),
                appreciation = "《诗经》开篇之作，采用重章叠句、赋比兴结合的手法，艺术感染力极强。",
                appreciationSource = SourceImportInfo(sourceName = "诗经鉴赏辞典"),
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "shijing_001"
            ),

            // 2. 汉代 · 乐府诗 · 未知作者 · 纯正文带译文
            WorkImportItem(
                title = "江南",
                author = null, // 未知作者
                dynasty = "汉",
                genre = "poem",
                layoutType = LayoutType.FIVE_GUSHI.code,
                tags = listOf("乐府", "采莲", "江南"),
                content = "江南可采莲，莲叶何田田。\n鱼戏莲叶间。\n鱼戏莲叶东，鱼戏莲叶西，\n鱼戏莲叶南，鱼戏莲叶北。",
                contentSource = SourceImportInfo(sourceName = "乐府诗集·相和歌辞"),
                translation = "江南水乡正是采莲的好时节，荷叶茂密青翠。鱼儿在碧绿的莲叶间嬉戏追逐。",
                translationSource = SourceImportInfo(sourceName = "汉魏六朝诗选"),
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "yuefu_jiangnan"
            ),

            // 3. 汉代 · 古诗 · 未知作者 · 纯正文
            WorkImportItem(
                title = "涉江采芙蓉",
                author = null, // 未知作者
                dynasty = "汉",
                genre = "poem",
                layoutType = LayoutType.FIVE_GUSHI.code,
                tags = listOf("古诗十九首", "思妇", "离别"),
                content = "涉江采芙蓉，兰泽多芳草。\n采之欲遗谁？所思在远道。\n还顾望旧乡，长路漫浩浩。\n同心而离居，忧伤以终老。",
                contentSource = SourceImportInfo(sourceName = "文选·古诗十九首"),
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "gushi19_006"
            ),

            // 4. 三国 · 四言古诗 · 曹操
            WorkImportItem(
                title = "短歌行",
                author = AuthorImportItem(name = "曹操", dynasty = "三国", aliases = listOf("魏武帝", "孟德")),
                dynasty = "三国",
                genre = "poem",
                form = "four_gushi",
                layoutType = LayoutType.FIVE_GUSHI.code,
                tags = listOf("慷慨", "求贤", "忧思"),
                content = "对酒当歌，人生几何！譬如朝露，去日苦多。\n慨当以慷，忧思难忘。何以解忧？唯有杜康。\n青青子衿，悠悠我心。但为君故，沉吟至今。\n呦呦鹿鸣，食野之苹。我有嘉宾，鼓瑟吹笙。\n明明如月，何时可掇？忧从中来，不可断绝。\n越陌度阡，枉用相存。契阔谈讌，心念旧恩。\n月明星稀，乌鹊南飞。绕树三匝，何枝可依？\n山不厌高，海不厌深。周公吐哺，天下归心。",
                contentSource = SourceImportInfo(sourceName = "三国志·魏书·武帝纪"),
                translation = "面对美酒应当高声放歌，人生能有多少光阴！好比晨露转瞬即逝，失去的日子实在太多。",
                background = "建安十三年赤壁之战前夕，曹操在江陵大宴群僚时所作。",
                appreciation = "全诗格调高亢苍凉，表现出招纳贤才、统一天下的宏伟抱负。",
                externalSourceCode = "PALEMOKY",
                externalWorkId = "caocao_duangehang"
            ),

            // 5. 晋代 · 田园诗 · 陶渊明
            WorkImportItem(
                title = "归园田居·其一",
                author = AuthorImportItem(
                    name = "陶渊明",
                    dynasty = "晋",
                    aliases = listOf("陶潜", "五柳先生", "靖节先生"),
                    biography = "陶渊明（约365年—427年），字元亮，号五柳先生，浔阳柴桑人，东晋末至刘宋初杰出诗人，田园诗派创始人。"
                ),
                dynasty = "晋",
                genre = "poem",
                layoutType = LayoutType.FIVE_GUSHI.code,
                tags = listOf("田园", "隐居", "自然"),
                content = "少无适俗韵，性本爱丘山。\n误落尘网中，一去三十年。\n羁鸟恋旧林，池鱼思故渊。\n开荒南野际，守拙归园田。\n方宅十余亩，草屋八九间。\n榆柳荫后檐，桃李罗堂前。\n暧暧远人村，依依墟里烟。\n狗吠深巷中，鸡鸣桑树颠。\n户庭无尘杂，虚室有余闲。\n久在樊笼里，复得返自然。",
                contentSource = SourceImportInfo(sourceName = "陶渊明集"),
                translation = "年轻时就没有迎合世俗的性情，天性本就热爱大自然的山丘田园。",
                appreciation = "语言质朴自然，描绘出一幅淡远宁谧的田园生活图卷。",
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "taoyuanming_gytj1"
            ),

            // 6. 唐代 · 七绝 · 李白 (篇一) · 五维完备
            WorkImportItem(
                title = "望庐山瀑布",
                subtitle = "其二",
                author = AuthorImportItem(
                    name = "李白",
                    dynasty = "唐",
                    aliases = listOf("李太白", "青莲居士", "诗仙"),
                    biography = "李白（701年—762年），字太白，号青莲居士，唐代伟大的浪漫主义诗人，世称“诗仙”。"
                ),
                dynasty = "唐",
                genre = "poem",
                form = "seven_jueju",
                layoutType = LayoutType.SEVEN_JUEJU.code,
                tags = listOf("山水", "庐山", "豪迈"),
                content = "日照香炉生紫烟，遥看瀑布挂前川。\n飞流直下三千尺，疑是银河落九天。",
                contentSource = SourceImportInfo(sourceName = "全唐诗"),
                translation = "香炉峰在阳光照耀下升起紫色烟雾，远远望去瀑布宛如挂在山前。",
                translationSource = SourceImportInfo(sourceName = "白话唐诗选"),
                annotations = "香炉：指香炉峰。\n挂前川：挂在山前江河之上。",
                annotationSource = SourceImportInfo(sourceName = "唐诗纪事"),
                background = "唐玄宗开元十三年，李白初出巴蜀游历庐山时所作。",
                backgroundSource = SourceImportInfo(sourceName = "李白年谱"),
                appreciation = "极为奇绝的想象力，将瀑布飞泻比作银河倾落，气魄雄伟奔放。",
                appreciationSource = SourceImportInfo(sourceName = "唐诗鉴赏辞典"),
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "libai_wlspb"
            ),

            // 7. 唐代 · 五绝 · 李白 (篇二 · 同一作者) · 仅正文
            WorkImportItem(
                title = "静夜思",
                author = AuthorImportItem(name = "李白", dynasty = "唐"),
                dynasty = "唐",
                genre = "poem",
                form = "five_jueju",
                layoutType = LayoutType.FIVE_JUEJU.code,
                tags = listOf("思乡", "月夜"),
                content = "床前明月光，疑是地上霜。\n举头望明月，低头思故乡。",
                contentSource = SourceImportInfo(sourceName = "全唐诗"),
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "libai_jys"
            ),

            // 8. 唐代 · 五律 · 杜甫 (篇一) · 五维完备
            WorkImportItem(
                title = "春望",
                author = AuthorImportItem(
                    name = "杜甫",
                    dynasty = "唐",
                    aliases = listOf("杜少陵", "杜工部", "子美", "诗圣"),
                    biography = "杜甫（712年—770年），字子美，自号少陵野老，唐代伟大的现实主义诗人，世称“诗圣”。"
                ),
                dynasty = "唐",
                genre = "poem",
                form = "five_lvshi",
                layoutType = LayoutType.FIVE_LVSHI.code,
                tags = listOf("忧国", "伤时", "安史之乱"),
                content = "国破山河在，城春草木深。\n感时花溅泪，恨别鸟惊心。\n烽火连三月，家书抵万金。\n白头搔更短，浑欲不胜簪。",
                contentSource = SourceImportInfo(sourceName = "杜工部集"),
                translation = "长安沦陷唯有山河依旧，春天城池荒芜草木丛生。感伤国事面对繁花落泪，怅恨离别听见啼鸟心惊。",
                translationSource = SourceImportInfo(sourceName = "唐诗今读"),
                annotationItems = listOf(
                    AnnotationItem(1, "国破", "国都长安沦陷", null, 1),
                    AnnotationItem(2, "城春", "长安城春天，荒凉萧条", null, 1),
                    AnnotationItem(3, "烽火", "战争战火，指安史之乱", null, 3),
                    AnnotationItem(4, "浑欲", "简直想要", null, 4)
                ),
                annotationSource = SourceImportInfo(sourceName = "杜诗详注"),
                background = "唐肃宗至德二载，杜甫身陷长安叛军占领区，目睹满目疮痍，忧国伤时而作。",
                backgroundSource = SourceImportInfo(sourceName = "杜甫评传"),
                appreciation = "沉郁顿挫，将家仇国难融为一体，苍凉悲壮，情深意切。",
                appreciationSource = SourceImportInfo(sourceName = "唐诗名篇赏析"),
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "dufu_chunwang"
            ),

            // 9. 唐代 · 七律 · 杜甫 (篇二 · 同一作者)
            WorkImportItem(
                title = "登高",
                author = AuthorImportItem(name = "杜甫", dynasty = "唐"),
                dynasty = "唐",
                genre = "poem",
                form = "seven_lvshi",
                layoutType = LayoutType.SEVEN_LVSHI.code,
                tags = listOf("悲秋", "登高", "身世"),
                content = "风急天高猿啸哀，渚清沙白鸟飞回。\n无边落木萧萧下，不尽长江滚滚来。\n万里悲秋常作客，百年多病独登台。\n艰难苦恨繁霜鬓，潦倒新停浊酒杯。",
                contentSource = SourceImportInfo(sourceName = "杜工部集"),
                translation = "风势迅疾天宇高远猿猴啼叫凄哀，江渚清幽沙洲洁白水鸟飞旋回转。",
                appreciation = "全诗八句皆律，情景交融，慷慨苍凉，被誉为杜集七律之冠。",
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "dufu_denggao"
            ),

            // 10. 唐代 · 五绝 · 王维 · 仅正文
            WorkImportItem(
                title = "相思",
                author = AuthorImportItem(
                    name = "王维",
                    dynasty = "唐",
                    aliases = listOf("摩诘居士", "王右丞", "诗佛")
                ),
                dynasty = "唐",
                genre = "poem",
                form = "five_jueju",
                layoutType = LayoutType.FIVE_JUEJU.code,
                tags = listOf("相思", "红豆", "友谊"),
                content = "红豆生南国，春来发几枝。\n愿君多采撷，此物最相思。",
                contentSource = SourceImportInfo(sourceName = "王右丞集"),
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "wangwei_xiangsi"
            ),

            // 11. 唐代 · 五绝 · 孟浩然
            WorkImportItem(
                title = "春晓",
                author = AuthorImportItem(name = "孟浩然", dynasty = "唐", aliases = listOf("孟襄阳", "鹿门处士")),
                dynasty = "唐",
                genre = "poem",
                form = "five_jueju",
                layoutType = LayoutType.FIVE_JUEJU.code,
                tags = listOf("春天", "惜春", "自然"),
                content = "春眠不觉晓，处处闻啼鸟。\n夜来风雨声，花落知多少。",
                contentSource = SourceImportInfo(sourceName = "全唐诗"),
                translation = "春夜好眠不知不觉已是天明，到处都能听到清脆的鸟啼。",
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "menghaoran_chunxiao"
            ),

            // 12. 唐代 · 五律 · 白居易
            WorkImportItem(
                title = "赋得古原草送别",
                author = AuthorImportItem(name = "白居易", dynasty = "唐", aliases = listOf("香山居士", "白乐天")),
                dynasty = "唐",
                genre = "poem",
                form = "five_lvshi",
                layoutType = LayoutType.FIVE_LVSHI.code,
                tags = listOf("送别", "生命", "草原"),
                content = "离离原上草，一岁一枯荣。\n野火烧不尽，春风吹又生。\n远芳侵古道，晴翠接荒城。\n又送王孙去，萋萋满别情。",
                contentSource = SourceImportInfo(sourceName = "白氏长庆集"),
                translation = "长势茂盛的古原野草，一年一度枯萎又一度繁荣。野火无法烧尽它的根脉，春风一吹又重新生机勃勃。",
                annotations = "赋得：按指定的题目赋诗。\n离离：草木茂盛貌。\n枯荣：枯萎与繁荣。",
                appreciation = "此诗借古原草生生不息之生命力寄托离别深情，意境浑朴，历久弥新。",
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "baijuyi_guyuan"
            ),

            // 13. 宋代 · 词（双调）· 苏轼 (篇一) · 带序言
            WorkImportItem(
                title = "水调歌头·明月几时有",
                author = AuthorImportItem(
                    name = "苏轼",
                    dynasty = "宋",
                    aliases = listOf("苏东坡", "东坡居士", "苏文忠"),
                    biography = "苏轼（1037年—1101年），字子瞻，号东坡居士，世称苏东坡，北宋著名文学家、书法家，唐宋八大家之一。"
                ),
                dynasty = "宋",
                genre = "ci",
                cipai = "水调歌头",
                form = "ci_double",
                layoutType = LayoutType.CI_DOUBLE.code,
                preface = "丙辰中秋，欢饮达旦，大醉，作此篇，兼怀子由。",
                tags = listOf("中秋", "明月", "怀亲", "旷达"),
                content = "明月几时有？把酒问青天。\n不知天上宫阙，今夕是何年。\n我欲乘风归去，又恐琼楼玉宇，高处不胜寒。\n起舞弄清影，何似在人间。\n\n转朱阁，低绮户，照无眠。\n不应有恨，何事长向别时圆？\n人有悲欢离合，月有阴晴圆缺，此事古难全。\n但愿人长久，千里共婵娟。",
                contentSource = SourceImportInfo(sourceName = "东坡乐府"),
                translation = "明月什么时候开始有的？端起酒杯询问青天。不知道仙宫琼阁，今晚是哪一年。",
                translationSource = SourceImportInfo(sourceName = "宋词名篇今读"),
                annotationItems = listOf(
                    AnnotationItem(1, "把酒", "端着酒杯", "bǎ jiǔ", 1),
                    AnnotationItem(2, "琼楼玉宇", "美玉砌成的仙家楼阁", null, 3),
                    AnnotationItem(3, "绮户", "雕饰华美的门窗", "qǐ hù", 5),
                    AnnotationItem(4, "婵娟", "美好的月色，也指美好的人", "chán juān", 8)
                ),
                annotationSource = SourceImportInfo(sourceName = "东坡词笺注"),
                background = "宋神宗熙宁九年中秋，苏轼在密州任上怀念弟弟苏辙，兼感慨仕途坎坷而作。",
                backgroundSource = SourceImportInfo(sourceName = "苏轼传"),
                appreciation = "词作融写景、抒情、议论于一体，将人生哲理与宇宙探索融会贯通，千古流传。",
                appreciationSource = SourceImportInfo(sourceName = "宋词鉴赏辞典"),
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "sushi_sdgt"
            ),

            // 14. 宋代 · 词（单调）· 苏轼 (篇二 · 同一作者)
            WorkImportItem(
                title = "如梦令·春思",
                author = AuthorImportItem(name = "苏轼", dynasty = "宋"),
                dynasty = "宋",
                genre = "ci",
                cipai = "如梦令",
                form = "ci_single",
                layoutType = LayoutType.CI_SINGLE.code,
                tags = listOf("惜春", "暮春"),
                content = "手种堂前桃李，无限绿阴青子。\n帘外百舌儿，惊起五更春睡。\n居士，居士，莫忘小桥流水。",
                contentSource = SourceImportInfo(sourceName = "东坡乐府"),
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "sushi_rml"
            ),

            // 15. 宋代 · 词（单调）· 李清照
            WorkImportItem(
                title = "如梦令·常记溪亭日暮",
                author = AuthorImportItem(
                    name = "李清照",
                    dynasty = "宋",
                    aliases = listOf("易安居士", "千古第一才女")
                ),
                dynasty = "宋",
                genre = "ci",
                cipai = "如梦令",
                form = "ci_single",
                layoutType = LayoutType.CI_SINGLE.code,
                tags = listOf("青春", "泛舟", "记忆"),
                content = "常记溪亭日暮，沉醉不知归路。\n兴尽晚回舟，误入藕花深处。\n争渡，争渡，惊起一滩鸥鹭。",
                contentSource = SourceImportInfo(sourceName = "漱玉词"),
                translation = "时常记起那次在溪边小亭游玩直到黄昏，沉醉于美景竟忘记了回家的道路。",
                appreciation = "词笔明朗活泼，语言清新天然，表现出少女时代洒脱无拘的青春情怀。",
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "liqingzhao_rml"
            ),

            // 16. 宋代 · 词（双调）· 辛弃疾
            WorkImportItem(
                title = "青玉案·元夕",
                author = AuthorImportItem(
                    name = "辛弃疾",
                    dynasty = "宋",
                    aliases = listOf("稼轩居士", "辛稼轩")
                ),
                dynasty = "宋",
                genre = "ci",
                cipai = "青玉案",
                form = "ci_double",
                layoutType = LayoutType.CI_DOUBLE.code,
                tags = listOf("元宵", "灯节", "品格"),
                content = "东风夜放花千树，更吹落、星如雨。\n宝马雕车香满路。\n凤箫声动，玉壶光转，一夜鱼龙舞。\n\n蛾儿雪柳黄金缕，笑语盈盈暗香去。\n众里寻他千百度，蓦然回首，那人却在，灯火阑珊处。",
                contentSource = SourceImportInfo(sourceName = "稼轩长短句"),
                translation = "东风在夜里吹开了千树银花，又吹落满天星雨。",
                appreciation = "王国维《人间词话》誉之为“古今之成大事业、大学问者”之第三最高境界。",
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "xinqiji_qya"
            ),

            // 17. 元代 · 元曲（散曲小令）· 马致远
            WorkImportItem(
                title = "天净沙·秋思",
                author = AuthorImportItem(
                    name = "马致远",
                    dynasty = "元",
                    aliases = listOf("东篱", "马东篱")
                ),
                dynasty = "元",
                genre = "qu",
                qupai = "天净沙",
                layoutType = LayoutType.PROSE.code,
                tags = listOf("秋天", "游子", "悲秋"),
                content = "枯藤老树昏鸦，小桥流水人家，古道西风瘦马。\n夕阳西下，断肠人在天涯。",
                contentSource = SourceImportInfo(sourceName = "东篱乐府"),
                translation = "枯萎的藤蔓缠绕着老树，黄昏时乌鸦归巢。小桥下流水潺潺，掩映着农户人家。荒凉的古道上秋风萧瑟，行进着一匹疲惫的瘦马。",
                appreciation = "全曲纯用名词意象并置，二十八字勾勒出一幅苍凉动人的深秋行旅图，被誉为“秋思之祖”。",
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "mazhiyuan_tjs"
            ),

            // 18. 元代 · 元曲（散曲）· 张养浩
            WorkImportItem(
                title = "山坡羊·潼关怀古",
                author = AuthorImportItem(name = "张养浩", dynasty = "元", aliases = listOf("云庄", "张云庄")),
                dynasty = "元",
                genre = "qu",
                qupai = "山坡羊",
                layoutType = LayoutType.PROSE.code,
                tags = listOf("怀古", "潼关", "民生"),
                content = "峰峦如聚，波涛如怒，山河表里潼关路。\n望西都，意踌躇。\n伤心秦汉经行处，宫阙万间都做了土。\n兴，百姓苦；亡，百姓苦。",
                contentSource = SourceImportInfo(sourceName = "全元散曲"),
                translation = "华山峰峦在此汇聚，黄河波涛奔腾翻滚，潼关地势险峻。",
                appreciation = "结语“兴，百姓苦；亡，百姓苦”振聋发聩，深具人道关怀与历史洞察力。",
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "zhangyanghao_spy"
            ),

            // 19. 分类未知作品（朝代、体裁等均未知/存疑，保留留空）
            WorkImportItem(
                title = "越人歌",
                author = null, // 未知作者
                dynasty = null, // 时代存疑保留为空
                genre = null,   // 体裁未知保留为空
                form = null,
                layoutType = LayoutType.PROSE.code,
                tags = listOf("先秦楚越", "古歌谣", "情歌"),
                content = "今夕何夕兮，搴舟中流。\n今日何日兮，得与王子同舟。\n蒙羞被好兮，不訾诟耻。\n心几烦而不绝兮，得知王子。\n山有木兮木有枝，心悦君兮君不知。",
                contentSource = SourceImportInfo(sourceName = "说苑·善说"),
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "yuerenge_ancient"
            ),

            // 20. 极简作品 · 只有正文 · 清代 · 郑板桥
            WorkImportItem(
                title = "咏雪",
                author = AuthorImportItem(name = "郑板桥", dynasty = "清", aliases = listOf("郑燮", "板桥道人")),
                dynasty = "清",
                genre = "poem",
                form = "seven_jueju",
                layoutType = LayoutType.SEVEN_JUEJU.code,
                tags = listOf("咏物", "雪景", "趣诗"),
                content = "一片两片三四片，五六七八九十片。\n千片万片无数片，飞入梅花总不见。",
                contentSource = SourceImportInfo(sourceName = "郑板桥全集"),
                externalSourceCode = "SHI_CI_CN",
                externalWorkId = "zhengbaqiao_yongxue"
            )
        )
    }
}
