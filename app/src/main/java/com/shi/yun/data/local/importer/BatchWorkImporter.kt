package com.shi.yun.data.local.importer

import androidx.room.withTransaction
import com.shi.yun.data.local.ShiYunDatabase
import com.shi.yun.data.local.entity.AuthorAliasEntity
import com.shi.yun.data.local.entity.AuthorEntity
import com.shi.yun.data.local.entity.CipaiEntity
import com.shi.yun.data.local.entity.ComponentType
import com.shi.yun.data.local.entity.ContentAttributionEntity
import com.shi.yun.data.local.entity.ContentComponentEntity
import com.shi.yun.data.local.entity.DataBatchEntity
import com.shi.yun.data.local.entity.DynastyEntity
import com.shi.yun.data.local.entity.ExternalSourceLinkEntity
import com.shi.yun.data.local.entity.FormEntity
import com.shi.yun.data.local.entity.GenreEntity
import com.shi.yun.data.local.entity.QupaiEntity
import com.shi.yun.data.local.entity.ReleaseWorkEntryEntity
import com.shi.yun.data.local.entity.TagEntity
import com.shi.yun.data.local.entity.WorkAuthorEntity
import com.shi.yun.data.local.entity.WorkEntity
import com.shi.yun.data.local.entity.WorkSourceLinkEntity
import com.shi.yun.data.local.entity.WorkVersionClassificationEntity
import com.shi.yun.data.local.entity.WorkVersionContentEntity
import com.shi.yun.data.local.entity.WorkVersionEntity
import com.shi.yun.data.local.entity.WorkVersionTagEntity
import com.shi.yun.data.local.importer.model.AuthorImportItem
import com.shi.yun.data.local.importer.model.BatchImportResult
import com.shi.yun.data.local.importer.model.PrecheckDisposition
import com.shi.yun.data.local.importer.model.SourceImportInfo
import com.shi.yun.data.local.importer.model.WorkImportItem
import com.shi.yun.data.local.importer.model.WorkImportResultItem
import com.shi.yun.data.model.AnnotationItem
import com.shi.yun.data.model.LayoutType
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * 批量作品导入核心服务 (BatchWorkImporter)
 *
 * 负责执行阶段 3 步骤 5.2 规范要求的工业级数据批量写入：
 * 1. 严格协调 36 张表中的核心多表结构：
 *    - 数据批次 (data_batches)
 *    - 作品与版本 (works, work_versions)
 *    - 作者与别名 (authors, author_aliases, work_authors)
 *    - 五维内容组件 (content_components, work_version_contents, content_attributions)
 *    - 维度分类与标签 (work_version_classifications, work_version_tags)
 *    - 来源追溯与链接 (external_source_links, work_source_links)
 *    - 正式发布包映射 (release_work_entries)（可选）
 * 2. 事务与失败防护：
 *    - 导入前强制预检，仅写入 READY_FOR_IMPORT 条目；
 *    - 核心写入操作处于 Room 原子事务 (database.withTransaction) 保护下；
 *    - 任意异常导致事务完整回滚 (0 脏数据)，并在事务外将 data_batches 标记为 FAILED；
 * 3. 学术严谨性要求：
 *    - 作者生平独立保存在 authors 表，绝不混入作品内容组件；
 *    - 正确识别并复用已有作者及别名，不盲目新增重复作者；
 *    - 报告真实统计结果与各作品导入明细。
 */
class BatchWorkImporter(
    private val database: ShiYunDatabase,
    private val prechecker: WorkImportPrechecker = WorkImportPrechecker(database.workDao(), database.categoryDao())
) {
    private val workDao = database.workDao()
    private val categoryDao = database.categoryDao()
    private val releaseDao = database.releaseDao()

    internal var onAfterBatchCreated: (suspend (String) -> Unit)? = null
    internal var onBeforeTransactionCommit: (suspend () -> Unit)? = null

    /**
     * 执行整批作品的校验、查重与批量原子入库
     *
     * @param batchCode 批次代码 (如 "BATCH_TANG_001")
     * @param batchSource 批次数据源描述 (如 "全唐诗库")
     * @param items 待导入作品列表
     * @param releaseId 可选挂载的正式发布包 ID
     * @return 批量导入执行结果汇总
     */
    suspend fun importBatch(
        batchCode: String,
        batchSource: String,
        items: List<WorkImportItem>,
        releaseId: String? = null
    ): BatchImportResult {
        val now = System.currentTimeMillis()
        val batchId = "batch_" + UUID.randomUUID().toString().replace("-", "").take(16)

        // 1. 导入前综合预检
        val precheckSummary = prechecker.precheckBatch(items)

        // 2. 预先在事务外写入批次记录（初始状态严格为 IMPORTING，保证导入全过程有据可查）
        val batchEntity = DataBatchEntity(
            batchId = batchId,
            batchCode = batchCode,
            batchSource = batchSource,
            itemCount = items.size,
            status = "IMPORTING",
            createdAtEpochMs = now
        )
        releaseDao.insertOrUpdateBatch(batchEntity)
        onAfterBatchCreated?.invoke(batchId)

        // 3. 构建初始明细条目与待写入索引集合
        val resultItems = ArrayList<WorkImportResultItem>(items.size)
        val readyIndices = mutableListOf<Int>()

        for ((index, item) in items.withIndex()) {
            val report = precheckSummary.items[index]
            when (report.disposition) {
                PrecheckDisposition.CAN_SKIP -> {
                    resultItems.add(
                        WorkImportResultItem(
                            index = index,
                            title = item.title,
                            author = report.author,
                            disposition = PrecheckDisposition.CAN_SKIP,
                            isSuccess = true,
                            workId = report.matchedWorkId,
                            workVersionId = report.matchedVersionId,
                            message = report.reason
                        )
                    )
                }
                PrecheckDisposition.NEEDS_CONFIRMATION -> {
                    resultItems.add(
                        WorkImportResultItem(
                            index = index,
                            title = item.title,
                            author = report.author,
                            disposition = PrecheckDisposition.NEEDS_CONFIRMATION,
                            isSuccess = false,
                            workId = report.matchedWorkId,
                            workVersionId = report.matchedVersionId,
                            message = report.reason
                        )
                    )
                }
                PrecheckDisposition.VALIDATION_FAILED -> {
                    resultItems.add(
                        WorkImportResultItem(
                            index = index,
                            title = item.title,
                            author = report.author,
                            disposition = PrecheckDisposition.VALIDATION_FAILED,
                            isSuccess = false,
                            message = report.reason
                        )
                    )
                }
                PrecheckDisposition.READY_FOR_IMPORT -> {
                    readyIndices.add(index)
                }
            }
        }

        // 4. 若无任何需要写入的作品，更新批次最终状态并返回
        if (readyIndices.isEmpty()) {
            val finalStatus = when {
                items.isEmpty() -> "IMPORTED"
                precheckSummary.failedCount == items.size -> "FAILED"
                precheckSummary.skipCount == items.size -> "IMPORTED"
                else -> "PARTIAL"
            }
            releaseDao.updateBatchStatus(batchId, finalStatus)

            val isSuccess = finalStatus == "IMPORTED"
            val errorMsg = when (finalStatus) {
                "FAILED" -> "批次内所有作品均校验失败，未执行任何入库"
                "PARTIAL" -> "批次未产生新导入作品（部分条目存疑或校验失败）"
                else -> null
            }

            return BatchImportResult(
                batchId = batchId,
                batchCode = batchCode,
                totalInputCount = items.size,
                importedCount = 0,
                skippedCount = precheckSummary.skipCount,
                needsConfirmationCount = precheckSummary.needsConfirmationCount,
                failedCount = precheckSummary.failedCount,
                isSuccessful = isSuccess,
                errorMessage = errorMsg,
                batchStatus = finalStatus,
                items = resultItems.sortedBy { it.index }
            )
        }

        // 5. 在 Room 原子事务中写入所有 READY_FOR_IMPORT 作品
        try {
            val importedItems = database.withTransaction {
                val successfulWrites = mutableListOf<WorkImportResultItem>()
                for (index in readyIndices) {
                    val item = items[index]
                    val (wId, wvId) = writeWorkItem(item, releaseId, now)
                    successfulWrites.add(
                        WorkImportResultItem(
                            index = index,
                            title = item.title,
                            author = item.author?.name?.trim() ?: "",
                            disposition = PrecheckDisposition.READY_FOR_IMPORT,
                            isSuccess = true,
                            workId = wId,
                            workVersionId = wvId,
                            message = "导入成功"
                        )
                    )
                }
                onBeforeTransactionCommit?.invoke()
                successfulWrites
            }

            resultItems.addAll(importedItems)

            // 根据预检分歧及实际结果确定最终批次状态
            val finalStatus = if (precheckSummary.needsConfirmationCount > 0 || precheckSummary.failedCount > 0) {
                "PARTIAL"
            } else {
                "IMPORTED"
            }
            releaseDao.updateBatchStatus(batchId, finalStatus)

            return BatchImportResult(
                batchId = batchId,
                batchCode = batchCode,
                totalInputCount = items.size,
                importedCount = importedItems.size,
                skippedCount = precheckSummary.skipCount,
                needsConfirmationCount = precheckSummary.needsConfirmationCount,
                failedCount = precheckSummary.failedCount,
                isSuccessful = true,
                batchStatus = finalStatus,
                items = resultItems.sortedBy { it.index }
            )
        } catch (e: Throwable) {
            // 事务回滚！所有作品数据撤销（0 脏数据）
            // 在事务外将 data_batches 标记为 FAILED
            try {
                releaseDao.updateBatchStatus(batchId, "FAILED")
            } catch (_: Throwable) {}

            return BatchImportResult(
                batchId = batchId,
                batchCode = batchCode,
                totalInputCount = items.size,
                importedCount = 0,
                skippedCount = precheckSummary.skipCount,
                needsConfirmationCount = precheckSummary.needsConfirmationCount,
                failedCount = precheckSummary.failedCount,
                isSuccessful = false,
                errorMessage = "批次导入事务执行失败，已全部安全回滚: ${e.message}",
                batchStatus = "FAILED",
                items = resultItems.sortedBy { it.index }
            )
        }
    }

    /**
     * 单篇作品完整多表级联写入逻辑
     */
    private suspend fun writeWorkItem(
        item: WorkImportItem,
        releaseId: String?,
        timestamp: Long
    ): Pair<String, String> {
        val workId = "work_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val workVersionId = "wv_" + UUID.randomUUID().toString().replace("-", "").take(16)

        // 1. 写入作品核心表 (works)
        val work = WorkEntity(
            workId = workId,
            standardTitle = item.title.trim(),
            identityStatus = "NORMAL",
            sortOrder = 0,
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp
        )
        workDao.insertWork(work)

        // 2. 写入作品版本表 (work_versions)
        val resolvedLayout = LayoutType.fromCodeOrNull(item.layoutType)?.code ?: LayoutType.FIVE_JUEJU.code
        val version = WorkVersionEntity(
            workVersionId = workVersionId,
            workId = workId,
            versionCode = 1,
            editionName = "底本",
            subtitle = item.subtitle?.trim(),
            preface = item.preface?.trim(),
            layoutType = resolvedLayout,
            excerpt = item.excerpt?.trim(),
            createdAtEpochMs = timestamp
        )
        workDao.insertWorkVersion(version)

        // 3. 写入作者与别名关联 (authors, author_aliases, work_authors)
        writeAuthors(item, workVersionId)

        // 4. 写入五维内容组件与装配 (content_components, work_version_contents, content_attributions)
        writeComponents(item, workVersionId, timestamp)

        // 5. 写入分类与标签 (work_version_classifications, work_version_tags)
        writeClassificationsAndTags(item, workVersionId, timestamp)

        // 6. 写入作品外部来源与溯源 (external_source_links, work_source_links)
        writeWorkSource(item, workId)

        // 7. 若提供了 releaseId，挂载到发布包 (release_work_entries)
        if (releaseId != null) {
            releaseDao.insertReleaseWorkEntries(
                listOf(
                    ReleaseWorkEntryEntity(
                        releaseId = releaseId,
                        workId = workId,
                        workVersionId = workVersionId
                    )
                )
            )
        }

        return Pair(workId, workVersionId)
    }

    /**
     * 作者入库处理：支持多作者、别名自动补全，作者生平档案独立入库
     */
    private suspend fun writeAuthors(item: WorkImportItem, workVersionId: String) {
        val authorList = mutableListOf<Pair<AuthorImportItem, Boolean>>()
        if (item.author != null) {
            authorList.add(Pair(item.author, true))
        }
        for (coAuthor in item.coAuthors) {
            authorList.add(Pair(coAuthor, false))
        }

        if (authorList.isEmpty()) {
            // 未知作者：按照规范不创建统一的虚假“佚名”档案，不向 work_authors 写入虚假关联，允许作品和版本作者为空
            return
        }

        for ((index, pair) in authorList.withIndex()) {
            val (authorItem, isPrimary) = pair
            val name = authorItem.name.trim()
            val existing = categoryDao.findAuthorByNameOrAlias(name)
            val authorId = if (existing != null) {
                insertMissingAliases(existing.authorId, authorItem.aliases)
                existing.authorId
            } else {
                val newId = "author_" + UUID.randomUUID().toString().replace("-", "").take(16)
                val resolvedDynastyId = ensureDynastyExists(authorItem.dynasty)
                var bioLinkId: String? = null
                if (!authorItem.biographySourceUrl.isNullOrBlank()) {
                    val linkId = "esl_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    val esl = ExternalSourceLinkEntity(
                        linkId = linkId,
                        sourceCode = "AUTHOR_BIO",
                        externalItemId = authorItem.externalAuthorId ?: newId,
                        itemUrl = authorItem.biographySourceUrl
                    )
                    categoryDao.insertExternalSourceLinks(listOf(esl))
                    bioLinkId = linkId
                }
                categoryDao.insertAuthors(
                    listOf(
                        AuthorEntity(
                            authorId = newId,
                            standardName = name,
                            dynastyId = resolvedDynastyId,
                            biography = authorItem.biography,
                            biographySource = authorItem.biographySource,
                            biographySourceLinkId = bioLinkId,
                            sortOrder = 0
                        )
                    )
                )
                insertMissingAliases(newId, authorItem.aliases)
                newId
            }

            workDao.insertWorkAuthors(
                listOf(
                    WorkAuthorEntity(
                        workVersionId = workVersionId,
                        authorId = authorId,
                        authorRole = if (isPrimary) "PRIMARY" else "CO_AUTHOR",
                        authorOrder = index + 1,
                        determinationStatus = "CONFIRMED"
                    )
                )
            )
        }
    }

    /**
     * 写入五维受控内容组件
     */
    private suspend fun writeComponents(
        item: WorkImportItem,
        workVersionId: String,
        timestamp: Long
    ) {
        // 1. 正文 (CONTENT) - 必填
        writeSingleComponent(
            workVersionId = workVersionId,
            type = ComponentType.CONTENT,
            content = item.content,
            rawFormat = "PLAIN_TEXT",
            sourceInfo = item.contentSource,
            timestamp = timestamp
        )

        // 2. 译文 (TRANSLATION)
        if (!item.translation.isNullOrBlank()) {
            writeSingleComponent(
                workVersionId = workVersionId,
                type = ComponentType.TRANSLATION,
                content = item.translation,
                rawFormat = "PLAIN_TEXT",
                sourceInfo = item.translationSource,
                timestamp = timestamp
            )
        }

        // 3. 注释 (ANNOTATION)
        val (annotContent, annotFormat) = if (!item.annotations.isNullOrBlank()) {
            Pair(item.annotations, "PLAIN_TEXT")
        } else if (!item.annotationItems.isNullOrEmpty()) {
            Pair(serializeAnnotationItems(item.annotationItems), "STRUCTURED_JSON")
        } else {
            Pair(null, null)
        }
        if (annotContent != null && annotFormat != null) {
            writeSingleComponent(
                workVersionId = workVersionId,
                type = ComponentType.ANNOTATION,
                content = annotContent,
                rawFormat = annotFormat,
                sourceInfo = item.annotationSource,
                timestamp = timestamp
            )
        }

        // 4. 创作背景 (BACKGROUND)
        if (!item.background.isNullOrBlank()) {
            writeSingleComponent(
                workVersionId = workVersionId,
                type = ComponentType.BACKGROUND,
                content = item.background,
                rawFormat = "PLAIN_TEXT",
                sourceInfo = item.backgroundSource,
                timestamp = timestamp
            )
        }

        // 5. 赏析 (APPRECIATION)
        if (!item.appreciation.isNullOrBlank()) {
            writeSingleComponent(
                workVersionId = workVersionId,
                type = ComponentType.APPRECIATION,
                content = item.appreciation,
                rawFormat = "PLAIN_TEXT",
                sourceInfo = item.appreciationSource,
                timestamp = timestamp
            )
        }
    }

    private suspend fun writeSingleComponent(
        workVersionId: String,
        type: ComponentType,
        content: String,
        rawFormat: String,
        sourceInfo: SourceImportInfo?,
        timestamp: Long
    ) {
        val compId = "cc_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val comp = ContentComponentEntity(
            componentId = compId,
            componentType = type.code,
            contentPayload = content,
            rawFormat = rawFormat,
            originType = "HUMAN_CURATED",
            contentHash = computeSha256(content),
            createdAtEpochMs = timestamp
        )
        workDao.insertComponent(comp)

        val versionContent = WorkVersionContentEntity(
            workVersionId = workVersionId,
            componentType = type.code,
            componentId = compId,
            reviewStatus = "VERIFIED",
            reviewedAtEpochMs = timestamp
        )
        workDao.insertWorkVersionContents(listOf(versionContent))

        if (sourceInfo != null) {
            val attrId = "attr_" + UUID.randomUUID().toString().replace("-", "").take(16)
            val attribution = ContentAttributionEntity(
                attributionId = attrId,
                componentId = compId,
                externalSourceLinkId = null,
                curatorName = sourceInfo.curatorName ?: sourceInfo.sourceName,
                licenseType = sourceInfo.licenseType ?: "PUBLIC_DOMAIN",
                licenseNote = sourceInfo.licenseNote
            )
            workDao.insertContentAttribution(attribution)
        }
    }

    /**
     * 写入分类维度与标签
     */
    private suspend fun writeClassificationsAndTags(
        item: WorkImportItem,
        workVersionId: String,
        timestamp: Long
    ) {
        val classifications = mutableListOf<WorkVersionClassificationEntity>()

        // 朝代
        val dynastyId = ensureDynastyExists(item.dynasty)
        if (dynastyId != null) {
            classifications.add(
                WorkVersionClassificationEntity(
                    workVersionId = workVersionId,
                    dimensionType = "DYNASTY",
                    classificationId = dynastyId,
                    determinationStatus = "CONFIRMED",
                    reviewStatus = "VERIFIED",
                    updatedAtEpochMs = timestamp
                )
            )
        }

        // 体裁
        val genreId = ensureGenreExists(item.genre)
        if (genreId != null) {
            classifications.add(
                WorkVersionClassificationEntity(
                    workVersionId = workVersionId,
                    dimensionType = "GENRE",
                    classificationId = genreId,
                    determinationStatus = "CONFIRMED",
                    reviewStatus = "VERIFIED",
                    updatedAtEpochMs = timestamp
                )
            )
        }

        // 形式
        val formId = ensureFormExists(item.form, genreId)
        if (formId != null) {
            classifications.add(
                WorkVersionClassificationEntity(
                    workVersionId = workVersionId,
                    dimensionType = "FORM",
                    classificationId = formId,
                    determinationStatus = "CONFIRMED",
                    reviewStatus = "VERIFIED",
                    updatedAtEpochMs = timestamp
                )
            )
        }

        // 词牌
        val cipaiId = ensureCipaiExists(item.cipai)
        if (cipaiId != null) {
            classifications.add(
                WorkVersionClassificationEntity(
                    workVersionId = workVersionId,
                    dimensionType = "CIPAI",
                    classificationId = cipaiId,
                    determinationStatus = "CONFIRMED",
                    reviewStatus = "VERIFIED",
                    updatedAtEpochMs = timestamp
                )
            )
        }

        // 曲牌
        val qupaiId = ensureQupaiExists(item.qupai)
        if (qupaiId != null) {
            classifications.add(
                WorkVersionClassificationEntity(
                    workVersionId = workVersionId,
                    dimensionType = "QUPAI",
                    classificationId = qupaiId,
                    determinationStatus = "CONFIRMED",
                    reviewStatus = "VERIFIED",
                    updatedAtEpochMs = timestamp
                )
            )
        }

        if (classifications.isNotEmpty()) {
            workDao.insertWorkVersionClassifications(classifications)
        }

        // 专题标签
        if (item.tags.isNotEmpty()) {
            val versionTags = item.tags.map { tag ->
                val tagId = ensureTagExists(tag)
                WorkVersionTagEntity(
                    workVersionId = workVersionId,
                    tagId = tagId,
                    reviewStatus = "VERIFIED"
                )
            }
            workDao.insertWorkVersionTags(versionTags)
        }
    }

    /**
     * 写入作品外部数据源追溯链接
     */
    private suspend fun writeWorkSource(item: WorkImportItem, workId: String) {
        val sourceCode = item.externalSourceCode?.trim()
        val extId = item.externalWorkId?.trim()

        if (!sourceCode.isNullOrEmpty() && !extId.isNullOrEmpty()) {
            var esl = categoryDao.getExternalSourceLinkByCodeAndItem(sourceCode, extId)
            if (esl == null) {
                val linkId = "esl_" + UUID.randomUUID().toString().replace("-", "").take(16)
                esl = ExternalSourceLinkEntity(
                    linkId = linkId,
                    sourceCode = sourceCode,
                    externalItemId = extId,
                    itemUrl = item.externalUrl,
                    licenseDesc = item.workSource?.licenseType ?: "PUBLIC_DOMAIN"
                )
                categoryDao.insertExternalSourceLinks(listOf(esl))
            }

            val wslId = "wsl_" + UUID.randomUUID().toString().replace("-", "").take(16)
            workDao.insertWorkSourceLink(
                WorkSourceLinkEntity(
                    linkId = wslId,
                    workId = workId,
                    externalSourceLinkId = esl.linkId,
                    isPrimary = 1
                )
            )
        }
    }

    /**
     * 补充缺失的作者别名
     */
    private suspend fun insertMissingAliases(authorId: String, aliases: List<String>) {
        if (aliases.isEmpty()) return
        val existingAliases = categoryDao.getAliasesByAuthorId(authorId).map { it.aliasName }
        val newAliases = aliases.filter { it.isNotBlank() && !existingAliases.contains(it.trim()) }
        if (newAliases.isNotEmpty()) {
            val entities = newAliases.map { alias ->
                AuthorAliasEntity(
                    aliasId = "aa_" + UUID.randomUUID().toString().replace("-", "").take(16),
                    authorId = authorId,
                    aliasName = alias.trim(),
                    aliasType = "HAO"
                )
            }
            categoryDao.insertAuthorAliases(entities)
        }
    }

    private suspend fun ensureDynastyExists(dynastyName: String?): String? {
        if (dynastyName.isNullOrBlank()) return null
        val clean = dynastyName.trim()
        val existing = categoryDao.getDynastyById(clean)
            ?: categoryDao.getAllDynasties().firstOrNull { it.standardName == clean }
        if (existing != null) return existing.dynastyId

        categoryDao.insertDynasties(listOf(DynastyEntity(dynastyId = clean, standardName = clean, sortOrder = 0)))
        return clean
    }

    private suspend fun ensureGenreExists(genreName: String?): String? {
        if (genreName.isNullOrBlank()) return null
        val clean = genreName.trim()
        val existing = categoryDao.getGenreById(clean)
            ?: categoryDao.getAllGenres().firstOrNull { it.standardName == clean }
        if (existing != null) return existing.genreId

        categoryDao.insertGenres(listOf(GenreEntity(genreId = clean, standardName = clean, sortOrder = 0)))
        return clean
    }

    private suspend fun ensureFormExists(formName: String?, genreId: String?): String? {
        if (formName.isNullOrBlank()) return null
        val clean = formName.trim()
        val existing = categoryDao.getFormById(clean)
        if (existing != null) return existing.formId

        val resolvedGenreId = genreId ?: ensureGenreExists("poem") ?: "poem"
        categoryDao.insertForms(listOf(FormEntity(formId = clean, standardName = clean, genreId = resolvedGenreId, sortOrder = 0)))
        return clean
    }

    private suspend fun ensureCipaiExists(cipaiName: String?): String? {
        if (cipaiName.isNullOrBlank()) return null
        val clean = cipaiName.trim()
        val existing = categoryDao.getCipaiById(clean)
            ?: categoryDao.getAllCipais().firstOrNull { it.standardName == clean }
        if (existing != null) return existing.cipaiId

        categoryDao.insertCipais(listOf(CipaiEntity(cipaiId = clean, standardName = clean, sortOrder = 0)))
        return clean
    }

    private suspend fun ensureQupaiExists(qupaiName: String?): String? {
        if (qupaiName.isNullOrBlank()) return null
        val clean = qupaiName.trim()
        val existing = categoryDao.getQupaiById(clean)
            ?: categoryDao.getAllQupais().firstOrNull { it.standardName == clean }
        if (existing != null) return existing.qupaiId

        categoryDao.insertQupais(listOf(QupaiEntity(qupaiId = clean, standardName = clean, qupaiCategory = "NORTHERN", sortOrder = 0)))
        return clean
    }

    private suspend fun ensureTagExists(tagName: String): String {
        val clean = tagName.trim()
        val existing = categoryDao.getTagsByCategory("THEME").firstOrNull { it.standardName == clean }
        if (existing != null) return existing.tagId

        val tagId = "tag_" + clean
        categoryDao.insertTags(listOf(TagEntity(tagId = tagId, standardName = clean, tagCategory = "THEME", sortOrder = 0)))
        return tagId
    }

    private fun serializeAnnotationItems(items: List<AnnotationItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("index", item.index)
            obj.put("quote", item.quote)
            obj.put("note", item.note)
            if (item.phonetic != null) obj.put("phonetic", item.phonetic)
            if (item.targetLine != null) obj.put("targetLine", item.targetLine)
            array.put(obj)
        }
        return array.toString()
    }

    private fun computeSha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
