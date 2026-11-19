package com.shi.yun.data.local.importer.detector

import com.shi.yun.data.local.dao.CategoryDao
import com.shi.yun.data.local.dao.WorkDao
import com.shi.yun.data.local.entity.AuthorEntity
import com.shi.yun.data.local.entity.ComponentType
import com.shi.yun.data.local.entity.ContentComponentEntity
import com.shi.yun.data.local.importer.model.PrecheckDisposition
import com.shi.yun.data.local.importer.model.WorkImportItem

/**
 * 重复判定决策明细 (DuplicateDecision)
 */
data class DuplicateDecision(
    val disposition: PrecheckDisposition,
    val reason: String,
    val matchedWorkId: String? = null,
    val matchedVersionId: String? = null
)

/**
 * 导入作品查重与冲突判定器 (WorkDuplicateDetector)
 *
 * 遵循严格的古籍学术严谨性与数据保护规则：
 * 1. 同一来源、同一外部作品编号：作为强匹配候选进行深层比对；
 * 2. 标题和作者高度一致：只能作为疑似重复候选，进一步比对正文；
 * 3. 同名但作者不同：严格判定为独立作品，绝不误判为重复；
 * 4. 同名同作者且正文一致，但辅助内容（译文/注释/背景/赏析）有新增或补充：判定为待确认 (NEEDS_CONFIRMATION)，避免遗漏新考证成果；
 * 5. 同一作品不同校本（异文）：严格保留差异，标记为待确认 (NEEDS_CONFIRMATION)，绝不擅自覆盖原版本；
 * 6. 无法确定是否同一作品：标记为待确认 (NEEDS_CONFIRMATION)；
 * 7. 作者比对支持标准名与别名库 (author_aliases) 双向等价识别；
 * 8. 纯只读检索，绝对不执行合并、迁移、更新或覆盖写入。
 */
class WorkDuplicateDetector(
    private val workDao: WorkDao,
    private val categoryDao: CategoryDao? = null
) {

    /**
     * 对单篇待导入作品执行查重判定
     */
    suspend fun detectDuplicate(item: WorkImportItem): DuplicateDecision {
        val normInputContent = normalizeClassicalText(item.content)

        // ==================== 规则 1：基于外部来源的强匹配判定 ====================
        val extSource = item.externalSourceCode?.trim()
        val extId = item.externalWorkId?.trim()
        if (!extSource.isNullOrEmpty() && !extId.isNullOrEmpty()) {
            val strongCandidates = workDao.getWorksByExternalSource(extSource, extId)
            if (strongCandidates.isNotEmpty()) {
                val matchedWork = strongCandidates.first()
                val versions = workDao.getVersionsByWorkId(matchedWork.workId)
                val activeVersion = versions.firstOrNull()

                // 提取现有版本的各组件
                val existingComponents = activeVersion?.let { v ->
                    workDao.getComponentsByVersionId(v.workVersionId)
                } ?: emptyList()

                val existingContent = existingComponents
                    .firstOrNull { it.componentType == ComponentType.CONTENT.code }
                    ?.contentPayload ?: ""

                val normExistingContent = normalizeClassicalText(existingContent)

                return if (normInputContent != normExistingContent) {
                    // 存在异文，严禁覆盖
                    DuplicateDecision(
                        disposition = PrecheckDisposition.NEEDS_CONFIRMATION,
                        reason = "外部数据源 [${extSource}:${extId}] 已关联作品 [${matchedWork.workId}]，但待导入正文存在异文差异，需人工复核是否作为新校本",
                        matchedWorkId = matchedWork.workId,
                        matchedVersionId = activeVersion?.workVersionId
                    )
                } else if (hasAuxiliaryDifferences(item, existingComponents)) {
                    // 正文一致，但辅助内容有新增或修改
                    DuplicateDecision(
                        disposition = PrecheckDisposition.NEEDS_CONFIRMATION,
                        reason = "外部数据源 [${extSource}:${extId}] 已关联作品 [${matchedWork.workId}]，正文一致但辅助内容（译文/注释/背景/赏析）有新增或补充，需人工确认",
                        matchedWorkId = matchedWork.workId,
                        matchedVersionId = activeVersion?.workVersionId
                    )
                } else {
                    // 正文与辅助内容完全一致
                    DuplicateDecision(
                        disposition = PrecheckDisposition.CAN_SKIP,
                        reason = "外部数据源 [${extSource}:${extId}] 已存在于库中作品 [${matchedWork.workId}]，正文及辅助内容完全一致，可安全跳过",
                        matchedWorkId = matchedWork.workId,
                        matchedVersionId = activeVersion?.workVersionId
                    )
                }
            }
        }

        // ==================== 规则 2：基于标题和作者的疑似重复判定 ====================
        val titleCandidates = workDao.getWorksByTitle(item.title.trim())
        if (titleCandidates.isNotEmpty()) {
            for (candidate in titleCandidates) {
                val versions = workDao.getVersionsByWorkId(candidate.workId)
                val activeVersion = versions.firstOrNull() ?: continue
                val existingAuthors = workDao.getAuthorsByVersionId(activeVersion.workVersionId)

                // 规则 3：同名但作者不同，明确为独立作品（如李白与杜甫皆有《塞下曲》）
                val isSameAuthor = isAuthorEquivalent(item, existingAuthors)
                if (!isSameAuthor) {
                    continue // 继续比对其他同名候选，不判定为重复
                }

                // 标题与作者高度一致，比对正文文本骨架及组件
                val existingComponents = workDao.getComponentsByVersionId(activeVersion.workVersionId)
                val existingContent = existingComponents
                    .firstOrNull { it.componentType == ComponentType.CONTENT.code }
                    ?.contentPayload ?: ""

                val normExistingContent = normalizeClassicalText(existingContent)

                return if (normInputContent != normExistingContent) {
                    // 规则 4：同一作品不同校本（异文），严格保留差异，标记为待确认，绝不擅自覆盖
                    DuplicateDecision(
                        disposition = PrecheckDisposition.NEEDS_CONFIRMATION,
                        reason = "库中已存在同名且作者一致的作品 [${candidate.workId}]，但正文存在异文分歧，需人工确认版本关系",
                        matchedWorkId = candidate.workId,
                        matchedVersionId = activeVersion.workVersionId
                    )
                } else if (hasAuxiliaryDifferences(item, existingComponents)) {
                    // 正文一致，但辅助内容有新增或修改
                    DuplicateDecision(
                        disposition = PrecheckDisposition.NEEDS_CONFIRMATION,
                        reason = "库中已存在相同标题、作者且正文一致的作品 [${candidate.workId}]，但辅助内容（译文/注释/背景/赏析）存在新增或补充，需人工确认",
                        matchedWorkId = candidate.workId,
                        matchedVersionId = activeVersion.workVersionId
                    )
                } else {
                    // 标题、作者、正文及辅助内容完全一致
                    DuplicateDecision(
                        disposition = PrecheckDisposition.CAN_SKIP,
                        reason = "库中已存在相同标题、作者且正文完全一致的作品 [${candidate.workId}]，可安全跳过",
                        matchedWorkId = candidate.workId,
                        matchedVersionId = activeVersion.workVersionId
                    )
                }
            }
        }

        // ==================== 规则 5：无任何强匹配或同名同作者疑似冲突 ====================
        return DuplicateDecision(
            disposition = PrecheckDisposition.READY_FOR_IMPORT,
            reason = "经多维比对未发现重复冲突，符合新增作品条件"
        )
    }

    /**
     * 判断待导入作者与库中现有版本作者是否等价（支持别名匹配）
     */
    private suspend fun isAuthorEquivalent(
        item: WorkImportItem,
        existingAuthors: List<AuthorEntity>
    ): Boolean {
        val inputName = item.author?.name?.trim().orEmpty()
        val existingNames = existingAuthors.map { it.standardName }
        val existingAuthorIds = existingAuthors.map { it.authorId }

        if (inputName.isBlank() && existingNames.isEmpty()) return true
        if (inputName.isBlank() || existingNames.isEmpty()) return false

        // 1. 直接清洗姓名比对
        val cleanInput = cleanName(inputName)
        for (name in existingNames) {
            val cleanExisting = cleanName(name)
            if (cleanInput == cleanExisting || cleanInput.contains(cleanExisting) || cleanExisting.contains(cleanInput)) {
                return true
            }
        }

        // 2. 待导入数据中自带的别名与已有作者姓名比对
        val inputAliases = item.author?.aliases.orEmpty()
        for (alias in inputAliases) {
            val cleanAlias = cleanName(alias)
            for (name in existingNames) {
                val cleanExisting = cleanName(name)
                if (cleanAlias == cleanExisting || cleanAlias.contains(cleanExisting) || cleanExisting.contains(cleanAlias)) {
                    return true
                }
            }
        }

        // 3. 借助 CategoryDao 查询库中作者及别名库 (author_aliases)
        if (categoryDao != null) {
            // (a) 查询 inputName 对应作者是否与已有作者之一匹配
            val matchedByInputName = categoryDao.findAuthorByNameOrAlias(inputName)
            if (matchedByInputName != null && existingAuthorIds.contains(matchedByInputName.authorId)) {
                return true
            }

            // (b) 检查待导入别名是否与已有作者之一匹配
            for (alias in inputAliases) {
                val matchedByAlias = categoryDao.findAuthorByNameOrAlias(alias.trim())
                if (matchedByAlias != null && existingAuthorIds.contains(matchedByAlias.authorId)) {
                    return true
                }
            }

            // (c) 检查库中已有作者的所有别名是否包含输入姓名
            for (authorId in existingAuthorIds) {
                val aliasesInDb = categoryDao.getAliasesByAuthorId(authorId)
                if (aliasesInDb.any { cleanName(it.aliasName) == cleanInput }) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * 检查待导入作品与库中版本相比，辅助内容（译文、注释、背景、赏析）是否存在新增或变更
     */
    private fun hasAuxiliaryDifferences(
        item: WorkImportItem,
        existingComponents: List<ContentComponentEntity>
    ): Boolean {
        val existingByType = existingComponents.associateBy { it.componentType }

        // 1. 译文
        val inputTranslation = item.translation?.trim().orEmpty()
        val existTranslation = existingByType[ComponentType.TRANSLATION.code]?.contentPayload?.trim().orEmpty()
        if (inputTranslation.isNotEmpty() && inputTranslation != existTranslation) return true

        // 2. 注释
        val inputAnnotations = item.annotations?.trim().orEmpty()
        val existAnnotations = existingByType[ComponentType.ANNOTATION.code]?.contentPayload?.trim().orEmpty()
        if (inputAnnotations.isNotEmpty() && inputAnnotations != existAnnotations) return true

        // 3. 创作背景
        val inputBackground = item.background?.trim().orEmpty()
        val existBackground = existingByType[ComponentType.BACKGROUND.code]?.contentPayload?.trim().orEmpty()
        if (inputBackground.isNotEmpty() && inputBackground != existBackground) return true

        // 4. 赏析
        val inputAppreciation = item.appreciation?.trim().orEmpty()
        val existAppreciation = existingByType[ComponentType.APPRECIATION.code]?.contentPayload?.trim().orEmpty()
        if (inputAppreciation.isNotEmpty() && inputAppreciation != existAppreciation) return true

        return false
    }

    private fun cleanName(name: String): String {
        return name.replace(Regex("[\\[\\]〔〕()（）\\s·]+"), "").trim()
    }

    /**
     * 古籍正文文本骨架归一化（去除换行、多余空格及标点符号，仅比对汉字字形骨架）
     */
    fun normalizeClassicalText(text: String): String {
        return text.replace(Regex("[\\s\\p{Punct}，。！？；：“”‘’（）《》、·—…]+"), "").trim()
    }
}
