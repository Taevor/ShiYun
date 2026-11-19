package com.shi.yun.data.local.importer.pipeline

import java.security.MessageDigest

/**
 * 原始记录归档条目
 */
data class ArchivedRawRecord(
    val sourceCode: String,
    val externalWorkId: String,
    val rawFilePath: String,
    val fileHashSha256: String,
    val rawJsonPayload: String
)

/**
 * 原始文件归档条目
 */
data class ArchivedFileEntry(
    val sourceCode: String,
    val filePath: String,
    val fileHashSha256: String,
    val recordCount: Int
)

/**
 * 完整原始数据归档与多源追溯索引 (RawRecordArchive)
 *
 * 负责在原始数据读取与解析阶段建立文件级 SHA-256 哈希归档和记录级索引映射：
 * 1. 确保多来源合并后，未被采用来源的完整原始记录仍能通过 (sourceCode, externalWorkId) 或文件路径准确定位与恢复；
 * 2. 避免在导入模型或数据库实体中冗余复制整包庞大 JSON，优先通过哈希与精准索引保障 100% 原始数据可追溯；
 * 3. 统计并区分归档率、记录定位率与索引非空率。
 */
class RawRecordArchive {

    private val files = mutableMapOf<String, ArchivedFileEntry>()
    private val recordsByKey = mutableMapOf<String, ArchivedRawRecord>()

    /**
     * 归档输入文件并计算 SHA-256
     */
    fun archiveFile(sourceCode: String, filePath: String, rawContent: String, recordCount: Int): String {
        val hash = calculateSha256(rawContent)
        val entry = ArchivedFileEntry(
            sourceCode = sourceCode,
            filePath = filePath,
            fileHashSha256 = hash,
            recordCount = recordCount
        )
        files["$sourceCode@$filePath"] = entry
        return hash
    }

    /**
     * 注册单条原始记录
     */
    fun archiveRecord(
        sourceCode: String,
        externalWorkId: String?,
        rawFilePath: String?,
        fileHashSha256: String?,
        rawJsonPayload: String?
    ) {
        if (externalWorkId.isNullOrBlank() || rawJsonPayload.isNullOrBlank()) return
        val key = makeKey(sourceCode, externalWorkId)
        recordsByKey[key] = ArchivedRawRecord(
            sourceCode = sourceCode,
            externalWorkId = externalWorkId,
            rawFilePath = rawFilePath ?: "unknown_path",
            fileHashSha256 = fileHashSha256 ?: "",
            rawJsonPayload = rawJsonPayload
        )
    }

    /**
     * 根据来源代码与外部作品编号检索完整原始记录（支持未采用来源还原）
     */
    fun findRawRecord(sourceCode: String, externalWorkId: String): ArchivedRawRecord? {
        return recordsByKey[makeKey(sourceCode, externalWorkId)]
    }

    /**
     * 检查某条引用记录是否存在于归档中
     */
    fun hasRecord(sourceCode: String, externalWorkId: String): Boolean {
        return recordsByKey.containsKey(makeKey(sourceCode, externalWorkId))
    }

    fun getArchivedFile(sourceCode: String, filePath: String): ArchivedFileEntry? {
        return files["$sourceCode@$filePath"]
    }

    fun getAllArchivedFiles(): List<ArchivedFileEntry> = files.values.toList()

    fun totalArchivedRecords(): Int = recordsByKey.size

    fun totalArchivedFiles(): Int = files.size

    private fun makeKey(sourceCode: String, externalWorkId: String): String =
        "${sourceCode.uppercase()}:${externalWorkId.trim()}"

    companion object {
        fun calculateSha256(content: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}
