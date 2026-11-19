package com.shi.yun.data.local.importer.validator

import com.shi.yun.data.local.importer.model.ValidationIssue
import com.shi.yun.data.local.importer.model.ValidationResult
import com.shi.yun.data.local.importer.model.WorkImportItem
import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.LayoutType
import org.json.JSONArray

/**
 * 作品导入前置校验器 (WorkImportValidator)
 *
 * 在纯内存中对待导入作品进行业务级完整性检查，无需访问或写入数据库：
 * 1. 核心必填字段检查（标题非空、正文非空且格式合理）；
 * 2. 辅助内容可选性保证（译文、注释、背景、赏析允许为空，缺失绝不判定为无效）；
 * 3. 分类合法性检查（未知朝代/体裁允许为空，绝不自动猜测）；
 * 4. 严格隔离检查：防止作者生平介绍 (biography) 错误混入正文、背景或赏析组件；
 * 5. 结构化注释解析检查：验证 JSON 注释数组语法，拦截损坏数据；
 * 6. 细粒度来源信息合规性校验。
 */
object WorkImportValidator {

    fun validate(item: WorkImportItem): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        // 1. 标题校验
        if (item.title.isBlank()) {
            if (!item.cipai.isNullOrBlank()) {
                // 仅含词牌无独立题名且无法提取正文首句的宋词/词作，作为非致命问题记录，保留进入待确认 (NEEDS_CONFIRMATION) 状态
                issues.add(ValidationIssue("title", "作品无独立标题且无法可靠提取正文首句（仅含词牌 [${item.cipai}]），需人工确认标题或核准为无题", isFatal = false))
            } else {
                issues.add(ValidationIssue("title", "作品标题不能为空白", isFatal = true))
            }
        }

        // 2. 正文校验（仅要求正文非空，不设字数下限以支持单字名作）
        val trimmedContent = item.content.trim()
        if (trimmedContent.isEmpty()) {
            issues.add(ValidationIssue("content", "作品正文不能为空白", isFatal = true))
        }

        // 3. 作者信息校验（允许为空，若存在则姓名必填）
        item.author?.let { author ->
            if (author.name.isBlank()) {
                issues.add(ValidationIssue("author.name", "主作者姓名不能为空白", isFatal = true))
            }
        }
        item.coAuthors.forEachIndexed { idx, coAuthor ->
            if (coAuthor.name.isBlank()) {
                issues.add(ValidationIssue("coAuthors[$idx].name", "合著者姓名不能为空白", isFatal = true))
            }
            if (item.author != null && coAuthor.name.trim() == item.author.name.trim()) {
                issues.add(ValidationIssue("coAuthors[$idx]", "合著者名单中出现了与主作者相同的姓名: '${coAuthor.name}'", isFatal = false))
            }
        }

        // 4. 分类维度校验（未知允许为空，存在时核验已知规范，不自动猜测）
        item.genre?.takeIf { it.isNotBlank() }?.let { genreStr ->
            val fromCode = Genre.fromCodeOrNull(genreStr)
            val fromName = Genre.entries.find { it.displayName == genreStr.trim() }
            if (fromCode == null && fromName == null) {
                issues.add(ValidationIssue("genre", "未知的体裁分类: '$genreStr'，需人工确认为新体裁或标准编码", isFatal = false))
            }
        }
        item.layoutType?.takeIf { it.isNotBlank() }?.let { layoutStr ->
            if (LayoutType.fromCodeOrNull(layoutStr) == null) {
                issues.add(ValidationIssue("layoutType", "未知的文学排版标识: '$layoutStr'", isFatal = false))
            }
        }

        // 5. 作者生平隔离校验（严防作者介绍混入作品内容）
        val authorBio = item.author?.biography?.trim()
        if (!authorBio.isNullOrEmpty()) {
            if (item.content.trim() == authorBio) {
                issues.add(ValidationIssue("content", "作品正文与作者生平介绍完全一致，作者生平介绍被错误填入了正文组件", isFatal = true))
            }
            if (item.background?.trim() == authorBio) {
                issues.add(ValidationIssue("background", "创作背景与作者生平介绍完全一致，作者生平介绍被错误填入了创作背景组件", isFatal = true))
            }
            if (item.appreciation?.trim() == authorBio) {
                issues.add(ValidationIssue("appreciation", "文学赏析与作者生平介绍完全一致，作者生平介绍被错误填入了赏析组件", isFatal = true))
            }
        }

        // 6. 结构化注释解析校验
        item.annotations?.trim()?.takeIf { it.isNotEmpty() }?.let { annotText ->
            if (item.rawFormat.equals("STRUCTURED_JSON", ignoreCase = true) || (annotText.startsWith("[") && annotText.endsWith("]"))) {
                try {
                    val array = JSONArray(annotText)
                    if (array.length() == 0) {
                        issues.add(ValidationIssue("annotations", "注释 JSON 数组为空", isFatal = false))
                    }
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i)
                        if (obj == null) {
                            issues.add(ValidationIssue("annotations[$i]", "注释条目必须为 JSON 对象", isFatal = true))
                        } else {
                            val quote = obj.optString("quote", "").trim()
                            val note = obj.optString("note", "").trim()
                            if (quote.isEmpty()) {
                                issues.add(ValidationIssue("annotations[$i].quote", "注释条目的原文字词 (quote) 不能为空", isFatal = true))
                            }
                            if (note.isEmpty()) {
                                issues.add(ValidationIssue("annotations[$i].note", "注释条目的解释内容 (note) 不能为空", isFatal = true))
                            }
                        }
                    }
                } catch (e: Exception) {
                    issues.add(ValidationIssue("annotations", "注释数据格式损坏，无法作为 JSON 解析: ${e.message}", isFatal = true))
                }
            }
        }
        item.annotationItems?.forEachIndexed { idx, annotItem ->
            if (annotItem.quote.isBlank()) {
                issues.add(ValidationIssue("annotationItems[$idx].quote", "注释对象条目的 quote 不能为空白", isFatal = true))
            }
            if (annotItem.note.isBlank()) {
                issues.add(ValidationIssue("annotationItems[$idx].note", "注释对象条目的 note 不能为空白", isFatal = true))
            }
        }

        // 7. 外部来源索引格式校验
        if (item.externalSourceCode.isNullOrBlank() && !item.externalWorkId.isNullOrBlank()) {
            issues.add(ValidationIssue("externalSourceCode", "提供了外部作品编号 (externalWorkId)，但未指定外部数据源代码 (externalSourceCode)", isFatal = false))
        }
        item.externalUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { url ->
            if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
                issues.add(ValidationIssue("externalUrl", "外部来源链接格式异常（非标准 http/https 协议）: '$url'", isFatal = false))
            }
        }

        // 8. 来源名称非空校验（若提供了来源对象，其 sourceName 必填）
        item.contentSource?.let { validateSourceName("contentSource", it, issues) }
        item.translationSource?.let { validateSourceName("translationSource", it, issues) }
        item.annotationSource?.let { validateSourceName("annotationSource", it, issues) }
        item.backgroundSource?.let { validateSourceName("backgroundSource", it, issues) }
        item.appreciationSource?.let { validateSourceName("appreciationSource", it, issues) }
        item.workSource?.let { validateSourceName("workSource", it, issues) }

        val hasFatal = issues.any { it.isFatal }
        return ValidationResult(isValid = !hasFatal, issues = issues)
    }

    private fun validateSourceName(field: String, source: com.shi.yun.data.local.importer.model.SourceImportInfo, issues: MutableList<ValidationIssue>) {
        if (source.sourceName.isBlank()) {
            issues.add(ValidationIssue("$field.sourceName", "来源对象中的来源名称 (sourceName) 不能为空白", isFatal = true))
        }
    }
}
