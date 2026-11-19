package com.shi.yun.data.local.importer.merger

import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.model.AnnotationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 多来源数据整合与来源优先级测试套件 (MultiSourceWorkMergerTest)
 *
 * 覆盖阶段 4.2 核心要求九项测试指标：
 * 1. 同一正文在多个来源重复出现，只保留一份采用内容和一个采用来源；
 * 2. 同一作品的正文、译文、注释和赏析分别来自不同来源（细粒度拼装）；
 * 3. 古诗文网来源明确时，辅助内容优先采用该来源；
 * 4. 来源不明确时，绝不伪造来源归属（区分直接与上游来源）；
 * 5. 不同注释和赏析不被误判为正文重复或数据损坏，完整留存差异；
 * 6. 作者档案独立管理，作者介绍来源与作品正文来源相互独立；
 * 7. 朝代、体裁、形式等分类属性分别记录来源；
 * 8. 同一来源不同版本可追溯（版本号、Commit 与原始 Payload 留存）；
 * 9. 未知作者保持空关联，不自动填入虚假作者；未知分类保持 null；
 * 10. 严禁从赏析中自动机械拆分创作背景。
 */
class MultiSourceWorkMergerTest {

    private lateinit var merger: MultiSourceWorkMerger

    @Before
    fun setUp() {
        merger = MultiSourceWorkMerger()
    }

    @Test
    fun testIdenticalContentAcrossSources_keepsSingleAdoptedContentAndSource() {
        // 场景1：同一正文在 poems-db、Poetry_CN 和 chinese-poetry 中完全一致
        val rawText = "床前明月光，疑是地上霜。\n举头望明月，低头思故乡。"

        val candidate1 = SourceWorkCandidate(
            sourceName = "yxcs/poems-db",
            sourceCode = "POEMS_DB",
            externalWorkId = "poems_db_001",
            title = "静夜思",
            content = rawText
        )
        val candidate2 = SourceWorkCandidate(
            sourceName = "Poetry_CN",
            sourceCode = "POETRY_CN",
            externalWorkId = "poetry_cn_001",
            title = "静夜思",
            content = rawText
        )
        val candidate3 = SourceWorkCandidate(
            sourceName = "chinese-poetry",
            sourceCode = "CHINESE_POETRY",
            externalWorkId = "cp_001",
            title = "静夜思",
            content = rawText
        )

        val audit = merger.merge(listOf(candidate1, candidate2, candidate3))
        val resolved = audit.resolvedItem

        // 仅保留一份正文，且采纳第一优先级 POEMS_DB 的来源
        assertEquals(rawText, resolved.content)
        assertNotNull(resolved.contentSource)
        assertEquals("yxcs/poems-db", resolved.contentSource!!.sourceName)

        val contentDiff = audit.diffRecords.find { it.dimension == MergeDimension.CONTENT }
        assertNotNull(contentDiff)
        assertEquals(3, contentDiff!!.identicalSourceCount)
        assertTrue(contentDiff.alternativeSources.isEmpty())
        assertTrue(contentDiff.decisionReason.contains("完全相同"))
    }

    @Test
    fun testFineGrainedMultiSourceAssembly_eachComponentFromDifferentSource() {
        // 场景2：正文来自 A，译文来自 B，注释来自 C，赏析来自 D，作者来自 E
        val candidateA = SourceWorkCandidate(
            sourceName = "chinese-poetry",
            sourceCode = "CHINESE_POETRY",
            externalWorkId = "cp_tang_001",
            title = "登鹳雀楼",
            content = "白日依山尽，黄河入海流。\n欲穷千里目，更上一层楼。"
        )
        val candidateB = SourceWorkCandidate(
            sourceName = "shi-ci.cn",
            sourceCode = "SHI_CI_CN",
            externalWorkId = "shici_dengguanquelou",
            title = "登鹳雀楼",
            translation = "夕阳依傍着西山慢慢沉落，滔滔黄河奔流直入大海。"
        )
        val candidateC = SourceWorkCandidate(
            sourceName = "yxcs/poems-db",
            sourceCode = "POEMS_DB",
            externalWorkId = "pdb_002",
            title = "登鹳雀楼",
            annotations = "白日：指傍晚落日的余晖。\n鹳雀楼：旧址在今山西永济。"
        )
        val candidateD = SourceWorkCandidate(
            sourceName = "Poetry_CN",
            sourceCode = "POETRY_CN",
            externalWorkId = "pcn_002",
            title = "登鹳雀楼",
            appreciation = "这首诗前两句写所见，后两句写所想，境界壮阔，寓意深远。"
        )
        val candidateE = SourceWorkCandidate(
            sourceName = "唐代诗人名录库",
            sourceCode = "POEMS_DB",
            externalWorkId = "author_wangzhihuan",
            title = "登鹳雀楼",
            author = AuthorImportItem(
                name = "王之涣",
                dynasty = "唐",
                biography = "王之涣（688年—742年），盛唐著名边塞诗人。",
                biographySource = "唐才子传"
            ),
            dynasty = "唐",
            genre = "SHI",
            form = "五言绝句"
        )

        val audit = merger.merge(listOf(candidateA, candidateB, candidateC, candidateD, candidateE))
        val resolved = audit.resolvedItem

        // 验证正文来自 candidateA (唯一的非空正文)
        assertEquals("白日依山尽，黄河入海流。\n欲穷千里目，更上一层楼。", resolved.content)
        assertEquals("chinese-poetry", resolved.contentSource?.sourceName)

        // 验证译文来自 candidateB
        assertEquals("夕阳依傍着西山慢慢沉落，滔滔黄河奔流直入大海。", resolved.translation)
        assertEquals("shi-ci.cn", resolved.translationSource?.sourceName)

        // 验证注释来自 candidateC
        assertEquals("白日：指傍晚落日的余晖。\n鹳雀楼：旧址在今山西永济。", resolved.annotations)
        assertEquals("yxcs/poems-db", resolved.annotationSource?.sourceName)

        // 验证赏析来自 candidateD
        assertEquals("这首诗前两句写所见，后两句写所想，境界壮阔，寓意深远。", resolved.appreciation)
        assertEquals("Poetry_CN", resolved.appreciationSource?.sourceName)

        // 验证作者来自 candidateE，且作者生平独立
        assertNotNull(resolved.author)
        assertEquals("王之涣", resolved.author?.name)
        assertEquals("唐才子传", resolved.author?.biographySource)
        assertEquals("唐", resolved.dynasty)
        assertEquals("SHI", resolved.genre)
    }

    @Test
    fun testGushiwenPriority_whenExplicitlyConfirmed_adoptedFirst() {
        // 场景3：古诗文网来源明确时，辅助内容享受最高优先级
        val generalPdbCandidate = SourceWorkCandidate(
            sourceName = "yxcs/poems-db",
            sourceCode = "POEMS_DB",
            title = "春晓",
            content = "春眠不觉晓，处处闻啼鸟。\n夜来风雨声，花落知多少。",
            translation = "春天睡得香，不知不觉天亮了...",
            isGushiwenConfirmed = false
        )
        val gushiwenCandidate = SourceWorkCandidate(
            sourceName = "古诗文网 (gushiwen.cn)",
            sourceCode = "GUSHIWEN",
            title = "春晓",
            translation = "春日里贪睡不知不觉天已破晓，到处都可以听到清脆的鸟鸣声。",
            annotations = "晓：天刚亮。\n啼鸟：鸟叫。",
            appreciation = "这首诗写的是春日早晨的景致与诗人的恬淡心情...",
            isGushiwenConfirmed = true,
            referenceUrl = "https://www.gushiwen.cn/shiwenv_123.aspx"
        )

        val audit = merger.merge(listOf(generalPdbCandidate, gushiwenCandidate))
        val resolved = audit.resolvedItem

        // 译文采纳古诗文网内容
        assertEquals("春日里贪睡不知不觉天已破晓，到处都可以听到清脆的鸟鸣声。", resolved.translation)
        assertEquals("古诗文网 (gushiwen.cn)", resolved.translationSource?.sourceName)

        val transDiff = audit.diffRecords.find { it.dimension == MergeDimension.TRANSLATION }
        assertNotNull(transDiff)
        assertTrue(transDiff!!.decisionReason.contains("明确古诗文网来源凭据"))
        assertEquals(1, transDiff.alternativeSources.size)
        assertEquals("yxcs/poems-db", transDiff.alternativeSources.first().sourceName)
    }

    @Test
    fun testDoNotForgeGushiwenSource_whenNotConfirmed() {
        // 场景4：来源不明确时，绝不伪造为古诗文网来源
        val candidate = SourceWorkCandidate(
            sourceName = "yxcs/poems-db",
            sourceCode = "POEMS_DB",
            title = "鹿柴",
            content = "空山不见人，但闻人语响。\n返景入深林，复照青苔上。",
            translation = "空静的山林中看不见人影，只听到有人说话的声音。",
            isGushiwenConfirmed = false // 仅为普通爬取，未核验确凿古诗文网出处
        )

        val audit = merger.merge(listOf(candidate))
        val resolved = audit.resolvedItem

        // 必须标为真实的 "yxcs/poems-db"，绝不能被偷换成 "古诗文网 (gushiwen.cn)"
        assertEquals("yxcs/poems-db", resolved.translationSource?.sourceName)
        assertFalse(resolved.translationSource?.sourceName?.contains("gushiwen") == true)
    }

    @Test
    fun testDifferentAnnotationsAndAppreciations_preservedInAuditDiff() {
        // 场景5：不同注释和赏析不被误判为正文重复或数据损坏
        val candidate1 = SourceWorkCandidate(
            sourceName = "yxcs/poems-db",
            sourceCode = "POEMS_DB",
            title = "江雪",
            content = "千山鸟飞绝，万径人踪灭。\n孤舟蓑笠翁，独钓寒江雪。",
            annotations = "绝：无，绝迹。\n径：小路。"
        )
        val candidate2 = SourceWorkCandidate(
            sourceName = "Poetry_CN",
            sourceCode = "POETRY_CN",
            title = "江雪",
            content = "千山鸟飞绝，万径人踪灭。\n孤舟蓑笠翁，独钓寒江雪。",
            annotations = "绝：飞尽，一只也没有。\n人踪：人的踪影。\n蓑笠：蓑衣和斗笠。"
        )

        val audit = merger.merge(listOf(candidate1, candidate2))
        val resolved = audit.resolvedItem

        // 采纳第一优先级 POEMS_DB 的注释
        assertEquals("绝：无，绝迹。\n径：小路。", resolved.annotations)

        // 审计材料中完整保留 Poetry_CN 的备选注释，未当作数据损坏丢弃
        val noteDiff = audit.diffRecords.find { it.dimension == MergeDimension.ANNOTATION }
        assertNotNull(noteDiff)
        assertEquals(1, noteDiff!!.alternativeSources.size)
        assertEquals("Poetry_CN", noteDiff.alternativeSources.first().sourceName)
    }

    @Test
    fun testAuthorProfileIndependence_andZeroFakeAuthorForUnknown() {
        // 场景6与9：未知作者保持为 null，不创建匿名实体；有作者时生平与作品内容解耦
        val anonymousCandidate = SourceWorkCandidate(
            sourceName = "chinese-poetry",
            sourceCode = "CHINESE_POETRY",
            title = "古诗十九首·行行重行行",
            content = "行行重行行，与君生别离。\n相去万余里，各在天一涯。",
            author = null // 无名氏
        )

        val auditAnon = merger.merge(listOf(anonymousCandidate))
        assertNull(auditAnon.resolvedItem.author)

        val authorDiff = auditAnon.diffRecords.find { it.dimension == MergeDimension.AUTHOR }
        assertNotNull(authorDiff)
        assertTrue(authorDiff!!.decisionReason.contains("坚决不创建虚假作者实体"))

        // 有作者的场景：李白
        val libaiCandidate = SourceWorkCandidate(
            sourceName = "yxcs/poems-db",
            sourceCode = "POEMS_DB",
            title = "早发白帝城",
            content = "朝辞白帝彩云间，千里江陵一日还。\n两岸猿声啼不住，轻舟已过万重山。",
            author = AuthorImportItem(
                name = "李白",
                dynasty = "唐",
                aliases = listOf("李太白", "青莲居士"),
                biography = "李白，字太白，号青莲居士，唐代伟大的浪漫主义诗人。",
                biographySource = "新唐书·李白传"
            )
        )

        val auditLibai = merger.merge(listOf(libaiCandidate))
        assertNotNull(auditLibai.resolvedItem.author)
        assertEquals("李白", auditLibai.resolvedItem.author?.name)
        assertEquals("新唐书·李白传", auditLibai.resolvedItem.author?.biographySource)
        // 确认正文、译文等未混入生平
        assertFalse(auditLibai.resolvedItem.content.contains("李白传"))
    }

    @Test
    fun testDoNotSplitBackgroundFromAppreciation() {
        // 场景10：严禁自动从赏析中拆分创作背景
        val candidate = SourceWorkCandidate(
            sourceName = "shi-ci.cn",
            sourceCode = "SHI_CI_CN",
            title = "水调歌头",
            content = "明月几时有？把酒问青天。",
            appreciation = "《水调歌头》作于公元1076年（宋神宗熙宁九年）中秋节。此词表达了作者对胞弟苏辙的怀念与旷达情怀。",
            background = null // 来源未提供独立背景
        )

        val audit = merger.merge(listOf(candidate))
        val resolved = audit.resolvedItem

        // 赏析完整保留
        assertNotNull(resolved.appreciation)
        // 创作背景严格保持为 null，绝不机械强行截取
        assertNull(resolved.background)

        val bgDiff = audit.diffRecords.find { it.dimension == MergeDimension.BACKGROUND }
        assertNotNull(bgDiff)
        assertTrue(bgDiff!!.decisionReason.contains("严禁自动从赏析中强行拆分"))
    }

    @Test
    fun testTraceabilityAndRawPayloadRetention() {
        // 场景8：版本与来源追溯及原始 Payload 完整留存
        val candidate1 = SourceWorkCandidate(
            sourceName = "yxcs/poems-db",
            sourceCode = "POEMS_DB",
            sourceVersion = "git-commit-e8f9a2",
            externalWorkId = "mongo_id_5c220864",
            title = "枫桥夜泊",
            content = "月落乌啼霜满天，江枫渔火对愁眠。\n姑苏城外寒山寺，夜半钟声到客船。"
        )
        val candidate2 = SourceWorkCandidate(
            sourceName = "chinese-poetry",
            sourceCode = "CHINESE_POETRY",
            sourceVersion = "v2.0.1",
            externalWorkId = "cp_tang_9988",
            title = "枫桥夜泊",
            content = "月落乌啼霜满天，江枫渔火对愁眠。\n姑苏城外寒山寺，夜半钟声到客船。"
        )

        val audit = merger.merge(listOf(candidate1, candidate2))
        val resolved = audit.resolvedItem

        assertNotNull(resolved.rawPayload)
        assertTrue(resolved.rawPayload!!.contains("mongo_id_5c220864"))
        assertTrue(resolved.rawPayload!!.contains("cp_tang_9988"))
        assertEquals("POEMS_DB", resolved.externalSourceCode)
        assertEquals("mongo_id_5c220864", resolved.externalWorkId)
    }
}
