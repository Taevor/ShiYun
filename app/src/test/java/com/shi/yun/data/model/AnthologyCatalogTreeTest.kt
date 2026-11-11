package com.shi.yun.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AnthologyCatalogTreeTest {

    /**
     * 验证用户指定的核心混合排序场景：
     * - 篇目 A：sortOrder=1
     * - 子目录 B：sortOrder=2，内部有篇目 B1、B2
     * - 篇目 C：sortOrder=3
     * 最终必须严格得到：A、B1、B2、C，且序号 1, 2, 3, 4 连续自增。
     */
    @Test
    fun testInterleavedEntriesAndSubNodesSorting() {
        val entryA = AnthologyEntry(entryId = "entry_a", workId = "work_a", sortOrder = 1)
        val entryB1 = AnthologyEntry(entryId = "entry_b1", workId = "work_b1", sortOrder = 1)
        val entryB2 = AnthologyEntry(entryId = "entry_b2", workId = "work_b2", sortOrder = 2)
        val entryC = AnthologyEntry(entryId = "entry_c", workId = "work_c", sortOrder = 3)

        val subNodeB = AnthologyCatalogNode(
            nodeId = "node_b",
            name = "中卷",
            sortOrder = 2,
            entries = listOf(entryB2, entryB1) // 故意乱序放入，检验内部排序
        )

        val rootNode = AnthologyCatalogNode(
            nodeId = "root_vol",
            name = "正集",
            sortOrder = 1,
            subNodes = listOf(subNodeB),
            entries = listOf(entryC, entryA) // 故意乱序放入
        )

        val book = AnthologyBook(
            id = "test_book",
            title = "测试选集",
            rootNodes = listOf(rootNode)
        )

        val flattened = book.flattenEntries()
        assertEquals(4, flattened.size)

        // 验证作品 ID 顺序
        assertEquals("work_a", flattened[0].entry.workId)
        assertEquals("work_b1", flattened[1].entry.workId)
        assertEquals("work_b2", flattened[2].entry.workId)
        assertEquals("work_c", flattened[3].entry.workId)

        // 验证全书连续自增序号
        assertEquals(1, flattened[0].bookOrderIndex)
        assertEquals(2, flattened[1].bookOrderIndex)
        assertEquals(3, flattened[2].bookOrderIndex)
        assertEquals(4, flattened[3].bookOrderIndex)

        // 验证面包屑路径
        assertEquals(listOf("正集"), flattened[0].path)
        assertEquals(listOf("正集", "中卷"), flattened[1].path)
        assertEquals(listOf("正集", "中卷"), flattened[2].path)
        assertEquals(listOf("正集"), flattened[3].path)
    }

    /**
     * 验证重复 sortOrder 的确定性决胜规则（Tie-breaker）：
     * 1. 篇目优先于子目录
     * 2. 篇目之间按 entryId 字典序
     * 3. 目录之间按 nodeId 字典序
     */
    @Test
    fun testDuplicateSortOrderTieBreakerRules() {
        val entryX = AnthologyEntry(entryId = "e_x", workId = "work_x", sortOrder = 1)
        val entryA = AnthologyEntry(entryId = "e_a", workId = "work_a", sortOrder = 1)

        val subNodeN = AnthologyCatalogNode(
            nodeId = "node_n",
            name = "卷N",
            sortOrder = 1,
            entries = listOf(AnthologyEntry("e_sub", "work_sub", 1))
        )
        val subNodeM = AnthologyCatalogNode(
            nodeId = "node_m",
            name = "卷M",
            sortOrder = 1,
            entries = listOf(AnthologyEntry("e_sub2", "work_sub2", 1))
        )

        val root = AnthologyCatalogNode(
            nodeId = "root",
            name = "卷首",
            sortOrder = 1,
            subNodes = listOf(subNodeN, subNodeM),
            entries = listOf(entryX, entryA)
        )

        val book = AnthologyBook(
            id = "tie_breaker_book",
            title = "平局测试",
            rootNodes = listOf(root)
        )

        val flattened = book.flattenEntries()
        // 同为 sortOrder=1:
        // entryA(e_a) vs entryX(e_x) -> entryA 字典序优先
        // entryX -> 篇目优先于节点
        // subNodeM(node_m) vs subNodeN(node_n) -> subNodeM 字典序优先
        assertEquals("work_a", flattened[0].entry.workId)
        assertEquals("work_x", flattened[1].entry.workId)
        assertEquals("work_sub2", flattened[2].entry.workId)
        assertEquals("work_sub", flattened[3].entry.workId)
    }

    /**
     * 验证三层以上深层树形结构（卷 -> 体裁 -> 组诗 -> 篇目）
     */
    @Test
    fun testDeepHierarchicalThreePlusLevels() {
        val poem1 = AnthologyEntry(entryId = "ganyu_1", workId = "tang_000001", sortOrder = 1)
        val poem2 = AnthologyEntry(entryId = "ganyu_2", workId = "tang_000002", sortOrder = 2)

        val groupNode = AnthologyCatalogNode(
            nodeId = "group_ganyu",
            name = "感遇二首",
            sortOrder = 1,
            nodeType = "GROUP",
            entries = listOf(poem1, poem2)
        )

        val genreNode = AnthologyCatalogNode(
            nodeId = "genre_wugu",
            name = "五言古诗",
            sortOrder = 1,
            nodeType = "GENRE",
            subNodes = listOf(groupNode)
        )

        val volumeNode = AnthologyCatalogNode(
            nodeId = "vol_1",
            name = "卷一",
            sortOrder = 1,
            nodeType = "VOLUME",
            subNodes = listOf(genreNode)
        )

        val book = AnthologyBook(
            id = "tang300",
            title = "唐诗三百首",
            editor = "〔清〕蘅塘退士辑",
            edition = "乾隆二十八年原刊本",
            rootNodes = listOf(volumeNode)
        )

        val flattened = book.flattenEntries()
        assertEquals(2, flattened.size)
        assertEquals("tang_000001", flattened[0].entry.workId)
        assertEquals("tang_000002", flattened[1].entry.workId)
        assertEquals(listOf("卷一", "五言古诗", "感遇二首"), flattened[0].path)
        assertEquals(listOf("卷一", "五言古诗", "感遇二首"), flattened[1].path)
        assertEquals(1, flattened[0].bookOrderIndex)
        assertEquals(2, flattened[1].bookOrderIndex)
    }

    /**
     * 验证同一作品跨不同选集引用，各自独立编排与独立命名
     */
    @Test
    fun testSameWorkAcrossDifferentAnthologiesIndependentOrdering() {
        val sharedWorkId = "tang_000180" // 假定为李白《早发白帝城》

        val tang300Book = AnthologyBook(
            id = "tang300",
            title = "唐诗三百首",
            directEntries = listOf(
                AnthologyEntry(
                    entryId = "t300_180",
                    workId = sharedWorkId,
                    sortOrder = 180,
                    originalTitle = "早发白帝城",
                    note = "又题下江陵"
                )
            )
        )

        val libaiAnthology = AnthologyBook(
            id = "libai_xuanji",
            title = "李太白全集选",
            directEntries = listOf(
                AnthologyEntry(
                    entryId = "lb_001",
                    workId = sharedWorkId,
                    sortOrder = 1,
                    originalTitle = "下江陵"
                )
            )
        )

        val item1 = tang300Book.flattenEntries().first()
        val item2 = libaiAnthology.flattenEntries().first()

        assertEquals(sharedWorkId, item1.entry.workId)
        assertEquals(sharedWorkId, item2.entry.workId)

        // 在唐诗三百首中序号为 180，原标题为 早发白帝城
        assertEquals(1, item1.bookOrderIndex)
        assertEquals(180, item1.entry.sortOrder)
        assertEquals("早发白帝城", item1.entry.originalTitle)

        // 在李太白全集选序号为 1，原标题为 下江陵
        assertEquals(1, item2.bookOrderIndex)
        assertEquals(1, item2.entry.sortOrder)
        assertEquals("下江陵", item2.entry.originalTitle)
    }
}
