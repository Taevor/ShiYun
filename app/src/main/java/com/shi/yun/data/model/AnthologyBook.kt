package com.shi.yun.data.model

/**
 * 选集条目实体
 *
 * 记录某一作品在特定选集中的引用、编排序号、原书标题及注文。
 * 作品与选集解耦：同一作品可属于多个选集，在各选集中的位置、排序和题名均独立保存。
 *
 * @property entryId 条目在选集内的唯一标识（如 "tang300_e001"）
 * @property workId 关联的规范作品 ID（如 "tang_000001"）
 * @property sortOrder 同一层级内的展示与编排序号（>= 1）
 * @property originalTitle 该选集底本所使用的原标题（可选，如与 Work.title 存在异体字或卷名差异时使用）
 * @property originalAuthor 该选集底本所署名的原作者（可选）
 * @property note 选集编者的卷端注或评注（可选）
 */
data class AnthologyEntry(
    val entryId: String,
    val workId: String,
    val sortOrder: Int,
    val originalTitle: String? = null,
    val originalAuthor: String? = null,
    val note: String? = null
)

/**
 * 选集目录树节点
 *
 * 支持多层树形结构（如 卷 -> 体裁 -> 组诗 -> 篇目）。
 * 既可包含子节点目录（subNodes），也可直接包含本节点下的直属篇目条目（entries）。
 *
 * @property nodeId 节点标识（如 "vol_1", "genre_5_gu"）
 * @property name 节点显示名称（如 "卷一 五言古诗", "感遇三十八首"）
 * @property sortOrder 同一层级内的排序序号
 * @property nodeType 节点类型（如 "VOLUME", "GENRE", "SECTION", "GROUP"）
 * @property subNodes 子目录节点列表
 * @property entries 直属本节点的篇目列表
 */
data class AnthologyCatalogNode(
    val nodeId: String,
    val name: String,
    val sortOrder: Int,
    val nodeType: String = "VOLUME",
    val subNodes: List<AnthologyCatalogNode> = emptyList(),
    val entries: List<AnthologyEntry> = emptyList()
)

/**
 * 选集完整书籍模型
 *
 * 选集实体与作品实体完全解耦。选集只维护自身的目录树、版本编排和条目引用。
 *
 * @property id 选集唯一标识（如 "tang_poetry_300"）
 * @property title 选集标准书名（如 "唐诗三百首"）
 * @property editor 选集编者（如 "〔清〕蘅塘退士（孙洙）辑"）
 * @property edition 依据底本/版本说明（如 "乾隆二十八年（1763）蘅塘退士编选本"）
 * @property description 选集简介
 * @property rootNodes 顶级目录节点列表（有分卷/分类时使用）
 * @property directEntries 书籍直属篇目列表（无分卷简单选集，或书前总序/直属篇目使用）
 */
data class AnthologyBook(
    val id: String,
    val title: String,
    val editor: String = "",
    val edition: String = "",
    val description: String = "",
    val rootNodes: List<AnthologyCatalogNode> = emptyList(),
    val directEntries: List<AnthologyEntry> = emptyList()
)

/**
 * 展平后的连续选集条目
 *
 * @property bookOrderIndex 全书严格连续递增的原书编排序号（1-based）
 * @property entry 原始条目
 * @property path 所属各级目录名称路径（如 ["卷一 五言古诗", "感遇三十八首"]）
 */
data class FlattenedAnthologyItem(
    val bookOrderIndex: Int,
    val entry: AnthologyEntry,
    val path: List<String>
)

/**
 * 目录树排序元素，用于同一父级下 entries 与 subNodes 的混合排序
 */
private sealed class CatalogSortItem {
    abstract val sortOrder: Int
    abstract val identifier: String

    data class EntrySortItem(val entry: AnthologyEntry) : CatalogSortItem() {
        override val sortOrder: Int get() = entry.sortOrder
        override val identifier: String get() = entry.entryId
    }

    data class NodeSortItem(val node: AnthologyCatalogNode) : CatalogSortItem() {
        override val sortOrder: Int get() = node.sortOrder
        override val identifier: String get() = node.nodeId
    }
}

/**
 * 目录树混合排序比较器：
 * 1. 严格依据 sortOrder 升序排列
 * 2. sortOrder 重复时，篇目 (Entry) 优先于子目录 (Node)
 * 3. 仍相同时，依据唯一标识符字典序升序确定，保证确定性与跨平台一致性
 */
private val catalogSortItemComparator = Comparator<CatalogSortItem> { a, b ->
    val orderCmp = a.sortOrder.compareTo(b.sortOrder)
    if (orderCmp != 0) return@Comparator orderCmp

    val typeA = if (a is CatalogSortItem.EntrySortItem) 0 else 1
    val typeB = if (b is CatalogSortItem.EntrySortItem) 0 else 1
    val typeCmp = typeA.compareTo(typeB)
    if (typeCmp != 0) return@Comparator typeCmp

    a.identifier.compareTo(b.identifier)
}

/**
 * 递归展平选集目录树，还原原书编排顺序
 *
 * 核心规则：
 * 1. 同一父级下的直属篇目与子目录参与同一套排序。
 * 2. 遇到篇目时立即加入结果序列；遇到子目录时递归遍历展开。
 * 3. 全书 bookOrderIndex 从 1 开始连续自增。
 */
fun AnthologyBook.flattenEntries(): List<FlattenedAnthologyItem> {
    val result = mutableListOf<FlattenedAnthologyItem>()
    var currentSequence = 1

    fun traverseNode(node: AnthologyCatalogNode, currentPath: List<String>) {
        val nodePath = currentPath + node.name
        val items = mutableListOf<CatalogSortItem>()
        node.entries.forEach { items.add(CatalogSortItem.EntrySortItem(it)) }
        node.subNodes.forEach { items.add(CatalogSortItem.NodeSortItem(it)) }
        items.sortWith(catalogSortItemComparator)

        for (item in items) {
            when (item) {
                is CatalogSortItem.EntrySortItem -> {
                    result.add(
                        FlattenedAnthologyItem(
                            bookOrderIndex = currentSequence++,
                            entry = item.entry,
                            path = nodePath
                        )
                    )
                }
                is CatalogSortItem.NodeSortItem -> {
                    traverseNode(item.node, nodePath)
                }
            }
        }
    }

    // 顶级条目与顶级目录节点混合排序
    val rootItems = mutableListOf<CatalogSortItem>()
    directEntries.forEach { rootItems.add(CatalogSortItem.EntrySortItem(it)) }
    rootNodes.forEach { rootItems.add(CatalogSortItem.NodeSortItem(it)) }
    rootItems.sortWith(catalogSortItemComparator)

    for (rootItem in rootItems) {
        when (rootItem) {
            is CatalogSortItem.EntrySortItem -> {
                result.add(
                    FlattenedAnthologyItem(
                        bookOrderIndex = currentSequence++,
                        entry = rootItem.entry,
                        path = emptyList()
                    )
                )
            }
            is CatalogSortItem.NodeSortItem -> {
                traverseNode(rootItem.node, emptyList())
            }
        }
    }

    return result
}
