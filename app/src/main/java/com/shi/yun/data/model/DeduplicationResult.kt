package com.shi.yun.data.model

/**
 * 作品匹配置信度与结果状态枚举
 */
enum class MatchConfidence {
    /** 确认同一作品（标题、作者、正文骨架指纹高度一致） */
    CONFIRMED_MATCH,

    /** 疑似同一作品（标题作者一致，正文存在少量异文或分段差异） */
    SUSPECTED_MATCH,

    /** 异文/异名/别题匹配（如早发白帝城/下江陵，正文骨架一致） */
    VARIANT_MATCH,

    /** 明确不匹配（有充分证据确认非同一作品，如词牌相同但作者与正文截然不同） */
    NOT_MATCH,

    /** 无法确定（关键信息缺失严重，如题名无题、无作者且正文极其简短） */
    UNCERTAIN
}

/**
 * 作品去重判定结果实体
 *
 * 明确关联参与比对判定的双方 workId，并记录比对依据、判定原因与正文指纹。
 *
 * @property sourceWorkId 比对源作品 ID
 * @property candidateWorkId 候选比对作品 ID
 * @property confidence 判定状态受控枚举
 * @property reason 判定的具体事实与原因说明
 * @property fingerprintA 源作品归一化文本指纹
 * @property fingerprintB 候选作品归一化文本指纹
 */
data class DeduplicationResult(
    val sourceWorkId: String,
    val candidateWorkId: String,
    val confidence: MatchConfidence,
    val reason: String,
    val fingerprintA: String = "",
    val fingerprintB: String = ""
)
