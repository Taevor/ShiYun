package com.shi.yun.data.util

import com.shi.yun.data.model.DeduplicationResult
import com.shi.yun.data.model.MatchConfidence
import com.shi.yun.data.model.Work
import kotlin.math.max
import kotlin.math.min

/**
 * 跨数据源作品去重与实体匹配判定器
 *
 * 遵循阶段一五种匹配状态判定规范：
 * 1. CONFIRMED_MATCH: 标题、作者及正文骨架完全一致；
 * 2. SUSPECTED_MATCH: 标题作者一致，正文存在细微异文或分段差异（相似度 >= 85%）；
 * 3. VARIANT_MATCH: 题名存在别题/异体字，但正文高度一致；
 * 4. NOT_MATCH: 充分证据确认不是同一作品（如同词牌不同作者、正文截然不同）；
 * 5. UNCERTAIN: 关键元数据缺失严重或正文过短无法确认。
 */
object WorkDeduplicator {

    private val PUNCT_REGEX = Regex("[\\s\\p{P}\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF]+")

    fun computeContentFingerprint(content: String): String {
        return content.replace(PUNCT_REGEX, "").trim()
    }

    private fun normalizeString(s: String): String {
        return s.replace(PUNCT_REGEX, "").trim()
    }

    fun match(workA: Work, workB: Work): DeduplicationResult {
        val idA = workA.id
        val idB = workB.id

        if (idA.isNotEmpty() && idA == idB) {
            val fp = computeContentFingerprint(workA.content)
            return DeduplicationResult(
                sourceWorkId = idA,
                candidateWorkId = idB,
                confidence = MatchConfidence.CONFIRMED_MATCH,
                reason = "作品 ID 完全相同",
                fingerprintA = fp,
                fingerprintB = fp
            )
        }

        val fpA = computeContentFingerprint(workA.content)
        val fpB = computeContentFingerprint(workB.content)

        val normTitleA = normalizeString(workA.title)
        val normTitleB = normalizeString(workB.title)

        val normAuthorA = normalizeString(workA.author)
        val normAuthorB = normalizeString(workB.author)

        val similarity = calculateSimilarity(fpA, fpB)

        // 场景 1: 作者均明确存在，但作者明确不同
        if (normAuthorA.isNotEmpty() && normAuthorB.isNotEmpty() && normAuthorA != normAuthorB) {
            return if (similarity >= 0.85) {
                // 正文高度一致但署名作者不同（古籍文献归属争议）
                DeduplicationResult(
                    sourceWorkId = idA,
                    candidateWorkId = idB,
                    confidence = MatchConfidence.SUSPECTED_MATCH,
                    reason = "正文指纹高度一致 (${(similarity * 100).toInt()}%)，但署名作者存在分歧 ($normAuthorA vs $normAuthorB)",
                    fingerprintA = fpA,
                    fingerprintB = fpB
                )
            } else {
                // 作者不同且正文不一致（如词牌相同不同人所作） -> 明确 NOT_MATCH
                DeduplicationResult(
                    sourceWorkId = idA,
                    candidateWorkId = idB,
                    confidence = MatchConfidence.NOT_MATCH,
                    reason = "作者明确不同 ($normAuthorA vs $normAuthorB) 且正文指纹不一致，确认为不同作品",
                    fingerprintA = fpA,
                    fingerprintB = fpB
                )
            }
        }

        // 场景 2: 题名完全一致（或归一化后一致）
        val titleMatches = normTitleA.isNotEmpty() && normTitleA == normTitleB

        if (titleMatches) {
            val authorsMatch = (normAuthorA.isEmpty() && normAuthorB.isEmpty()) || (normAuthorA == normAuthorB)
            if (authorsMatch) {
                val isSampledLongContent = max(fpA.length, fpB.length) > MAX_LCS_CONTENT_LENGTH
                return when {
                    similarity >= 0.98 -> {
                        if (isSampledLongContent) {
                            // 长篇文本仅凭采样相似度不得直接判定为 CONFIRMED_MATCH，保守判定为 SUSPECTED_MATCH，防止采样误判强行合并
                            DeduplicationResult(
                                sourceWorkId = idA,
                                candidateWorkId = idB,
                                confidence = MatchConfidence.SUSPECTED_MATCH,
                                reason = "标题与作者一致，长篇文本采样指纹高度一致 (${(similarity * 100).toInt()}%)，待全量深度校核",
                                fingerprintA = fpA,
                                fingerprintB = fpB
                            )
                        } else {
                            DeduplicationResult(
                                sourceWorkId = idA,
                                candidateWorkId = idB,
                                confidence = MatchConfidence.CONFIRMED_MATCH,
                                reason = "标题、作者与正文指纹完全一致",
                                fingerprintA = fpA,
                                fingerprintB = fpB
                            )
                        }
                    }
                    similarity >= 0.80 -> {
                        DeduplicationResult(
                            sourceWorkId = idA,
                            candidateWorkId = idB,
                            confidence = MatchConfidence.SUSPECTED_MATCH,
                            reason = "标题与作者一致，正文存在版本异文 (${(similarity * 100).toInt()}%)",
                            fingerprintA = fpA,
                            fingerprintB = fpB
                        )
                    }
                    else -> {
                        // 标题相同但正文差异过大
                        val isGenericTitle = normTitleA in setOf("无题", "杂诗", "偶成", "感遇", "感怀", "古诗", "赠别")
                        if (isGenericTitle && (normAuthorA.isEmpty() || normAuthorB.isEmpty())) {
                            DeduplicationResult(
                                sourceWorkId = idA,
                                candidateWorkId = idB,
                                confidence = MatchConfidence.UNCERTAIN,
                                reason = "标题为常见泛称题名且缺少作者，正文不一致，无法判定",
                                fingerprintA = fpA,
                                fingerprintB = fpB
                            )
                        } else {
                            DeduplicationResult(
                                sourceWorkId = idA,
                                candidateWorkId = idB,
                                confidence = MatchConfidence.NOT_MATCH,
                                reason = "同题名但正文内容截然不同 (相似度: ${(similarity * 100).toInt()}%)",
                                fingerprintA = fpA,
                                fingerprintB = fpB
                            )
                        }
                    }
                }
            } else {
                // 其中一方作者缺失
                return if (similarity >= 0.85) {
                    DeduplicationResult(
                        sourceWorkId = idA,
                        candidateWorkId = idB,
                        confidence = MatchConfidence.SUSPECTED_MATCH,
                        reason = "标题与正文指纹一致，但其中一方作者信息缺失",
                        fingerprintA = fpA,
                        fingerprintB = fpB
                    )
                } else {
                    DeduplicationResult(
                        sourceWorkId = idA,
                        candidateWorkId = idB,
                        confidence = MatchConfidence.NOT_MATCH,
                        reason = "同题名但正文不吻合且作者信息缺失",
                        fingerprintA = fpA,
                        fingerprintB = fpB
                    )
                }
            }
        }

        // 场景 3: 题名不一致
        if (similarity >= 0.85) {
            // 题名不同（别题/异名，如《早发白帝城》/《下江陵》），但正文指纹高度一致
            return DeduplicationResult(
                sourceWorkId = idA,
                candidateWorkId = idB,
                confidence = MatchConfidence.VARIANT_MATCH,
                reason = "题名存在别题/异名分歧 (${workA.title} vs ${workB.title})，但正文骨架高度一致 (${(similarity * 100).toInt()}%)",
                fingerprintA = fpA,
                fingerprintB = fpB
            )
        }

        // 场景 4: 关键信息严重缺失且正文过短
        if ((normTitleA.isEmpty() || normTitleB.isEmpty() || normTitleA == "无题") &&
            (normAuthorA.isEmpty() || normAuthorB.isEmpty()) &&
            min(fpA.length, fpB.length) < 15
        ) {
            return DeduplicationResult(
                sourceWorkId = idA,
                candidateWorkId = idB,
                confidence = MatchConfidence.UNCERTAIN,
                reason = "作品标题或作者缺失严重且正文篇幅过短，证据不足以判定",
                fingerprintA = fpA,
                fingerprintB = fpB
            )
        }

        // 场景 5: 默认明确不匹配
        return DeduplicationResult(
            sourceWorkId = idA,
            candidateWorkId = idB,
            confidence = MatchConfidence.NOT_MATCH,
            reason = "题名与正文均不一致，无任何同一性证据",
            fingerprintA = fpA,
            fingerprintB = fpB
        )
    }

    private const val MAX_LCS_CONTENT_LENGTH = 1500

    /**
     * 计算两段归一化文本的最长公共子序列（LCS）相似度 (0.0 ~ 1.0)
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val maxLen = max(s1.length, s2.length)
        val minLen = min(s1.length, s2.length)
        if (minLen.toDouble() / maxLen < 0.4) {
            return minLen.toDouble() / maxLen
        }

        // 超长篇幅安全保护：避免长篇古文（>1500字）直接进行高复杂度矩阵比对
        if (maxLen > MAX_LCS_CONTENT_LENGTH) {
            return calculateLongContentSimilarity(s1, s2)
        }

        // 动态规划求解 LCS 长度（滚动数组空间优化）
        val dp = IntArray(s2.length + 1)
        for (i in 1..s1.length) {
            var prev = 0
            for (j in 1..s2.length) {
                val temp = dp[j]
                if (s1[i - 1] == s2[j - 1]) {
                    dp[j] = prev + 1
                } else {
                    dp[j] = max(dp[j], dp[j - 1])
                }
                prev = temp
            }
        }
        val lcs = dp[s2.length]
        return (2.0 * lcs) / (s1.length + s2.length)
    }

    /**
     * 长篇文本安全采样指纹比对：采样前、中、后核心分段指纹进行估算
     */
    private fun calculateLongContentSimilarity(s1: String, s2: String): Double {
        val sampleLen = 300
        val p1Head = s1.take(sampleLen)
        val p1Mid = s1.substring(s1.length / 2 - sampleLen / 2, s1.length / 2 + sampleLen / 2)
        val p1Tail = s1.takeLast(sampleLen)

        val p2Head = s2.take(sampleLen)
        val p2Mid = s2.substring(s2.length / 2 - sampleLen / 2, s2.length / 2 + sampleLen / 2)
        val p2Tail = s2.takeLast(sampleLen)

        val simHead = calculateSimilarity(p1Head, p2Head)
        val simMid = calculateSimilarity(p1Mid, p2Mid)
        val simTail = calculateSimilarity(p1Tail, p2Tail)

        return (simHead * 0.35 + simMid * 0.30 + simTail * 0.35)
    }
}
