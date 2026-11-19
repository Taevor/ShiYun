package com.shi.yun.data.util

import com.shi.yun.data.model.AnnotationItem
import com.shi.yun.data.model.AttributionInfo
import com.shi.yun.data.model.AttributionStatus
import com.shi.yun.data.model.AuxiliaryContent
import com.shi.yun.data.model.ContentAttribution
import com.shi.yun.data.model.ContentProvenance
import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.LayoutType
import com.shi.yun.data.model.LegalLicense
import com.shi.yun.data.model.LicenseType
import com.shi.yun.data.model.ReviewStatus
import com.shi.yun.data.model.SourceInfo
import com.shi.yun.data.model.SourceOrigin
import com.shi.yun.data.model.Work
import com.shi.yun.data.model.WorkSourceTrace
import org.json.JSONObject

/**
 * 诗词作品双向 JSON 序列化器
 *
 * 核心职责：
 * 1. 将 Work 实体完整序列化为规范 JSON 字符串及 JSONObject，完整保留新增 sourceTraces 多来源追溯凭证；
 * 2. 将 JSON 文本（包括旧版 600 首规范与带 sourceTraces 的新版规范）反序列化为 Work 实体；
 * 3. 内置轻量纯 Kotlin JSON 解析引擎，无需依赖第三方库或 JVM Stub，保证在 Android 设备与纯 JVM 单元测试下行为一致。
 */
object WorkJsonSerializer {

    // =========================================================================
    // 序列化接口
    // =========================================================================

    fun serialize(work: Work): String {
        val sb = StringBuilder()
        sb.append("{\n")
        val fields = mutableListOf<String>()

        fields.add("  \"id\": ${escapeString(work.id)}")
        fields.add("  \"title\": ${escapeString(work.title)}")
        fields.add("  \"author\": ${escapeString(work.author)}")
        fields.add("  \"dynasty\": ${escapeString(work.dynasty)}")
        fields.add("  \"genre\": ${escapeString(work.genre.code)}")
        fields.add("  \"content\": ${escapeString(work.content)}")
        fields.add("  \"excerpt\": ${escapeString(work.excerpt)}")

        if (work.cipai != null) fields.add("  \"cipai\": ${escapeString(work.cipai)}")
        if (work.subtitle != null) fields.add("  \"subtitle\": ${escapeString(work.subtitle)}")
        if (work.layout != null) fields.add("  \"layout\": ${escapeString(work.layout.code)}")
        if (work.preface != null) fields.add("  \"preface\": ${escapeString(work.preface)}")

        if (work.source.isNotEmpty()) fields.add("  \"source\": ${escapeString(work.source)}")
        if (work.sourceId.isNotEmpty()) fields.add("  \"sourceId\": ${escapeString(work.sourceId)}")
        if (work.licenseNote.isNotEmpty()) fields.add("  \"licenseNote\": ${escapeString(work.licenseNote)}")

        if (work.textSource != null) {
            fields.add("  \"textSource\": ${serializeSourceInfo(work.textSource)}")
        }
        if (work.attribution != null) {
            fields.add("  \"attribution\": ${serializeAttributionInfo(work.attribution)}")
        }
        if (work.auxiliary != null) {
            fields.add("  \"auxiliary\": ${serializeAuxiliaryContent(work.auxiliary)}")
        }

        if (work.sourceTraces.isNotEmpty()) {
            fields.add("  \"sourceTraces\": ${serializeSourceTraces(work.sourceTraces)}")
        }

        sb.append(fields.joinToString(",\n"))
        sb.append("\n}")
        return sb.toString()
    }

    fun serializeToJsonObject(work: Work): JSONObject {
        return JSONObject(serialize(work))
    }

    // =========================================================================
    // 反序列化接口
    // =========================================================================

    fun deserialize(jsonString: String): Work {
        val map = parseJsonObject(jsonString)
        return deserialize(map)
    }

    fun deserialize(map: Map<String, Any?>): Work {
        val id = requireString(map, "id")
        val title = requireString(map, "title")
        val author = optString(map, "author", "")
        val dynasty = optString(map, "dynasty", "")
        val content = requireString(map, "content")
        val excerpt = optString(map, "excerpt", "")

        val genreStr = requireString(map, "genre")
        val genre = Genre.fromCode(genreStr)

        val layoutStr = optNullableString(map, "layout")
        val layout = layoutStr?.let { LayoutType.fromCode(it) }

        val cipai = optNullableString(map, "cipai")
        val subtitle = optNullableString(map, "subtitle")
        val preface = optNullableString(map, "preface")

        val source = optString(map, "source", "")
        val sourceId = optString(map, "sourceId", "")
        val licenseNote = optString(map, "licenseNote", "")

        val textSource = parseSourceInfoFromMap(optMap(map, "textSource"))
            ?: if (source.isNotEmpty()) SourceInfo(sourceName = source, licenseNote = licenseNote) else null

        val attribution = parseAttributionFromMap(optMap(map, "attribution"))
        val auxiliary = parseAuxiliaryFromMap(optMap(map, "auxiliary"))
        val sourceTraces = parseSourceTracesFromList(optList(map, "sourceTraces"))

        return Work(
            id = id,
            title = title,
            author = author,
            dynasty = dynasty,
            genre = genre,
            content = content,
            excerpt = excerpt,
            cipai = cipai,
            subtitle = subtitle,
            layout = layout,
            preface = preface,
            textSource = textSource,
            attribution = attribution,
            auxiliary = auxiliary,
            source = source,
            sourceId = sourceId,
            licenseNote = licenseNote,
            sourceTraces = sourceTraces
        )
    }

    fun deserialize(jsonObject: JSONObject): Work {
        return deserialize(jsonObject.toString())
    }

    // =========================================================================
    // 内部结构序列化辅助函数
    // =========================================================================

    private fun serializeSourceInfo(info: SourceInfo): String {
        val fields = mutableListOf<String>()
        fields.add("\"sourceName\": ${escapeString(info.sourceName)}")
        fields.add("\"licenseType\": ${escapeString(info.licenseType.code)}")
        if (info.referenceUrl != null) fields.add("\"referenceUrl\": ${escapeString(info.referenceUrl)}")
        fields.add("\"licenseNote\": ${escapeString(info.licenseNote)}")
        return "{" + fields.joinToString(",") + "}"
    }

    private fun serializeAttributionInfo(attr: AttributionInfo): String {
        val fields = mutableListOf<String>()
        fields.add("\"status\": ${escapeString(attr.status.name)}")
        val candidates = attr.candidateAuthors.joinToString(",") { escapeString(it) }
        fields.add("\"candidateAuthors\": [$candidates]")
        fields.add("\"note\": ${escapeString(attr.note)}")
        val sources = attr.academicSources.joinToString(",") { escapeString(it) }
        fields.add("\"academicSources\": [$sources]")
        return "{" + fields.joinToString(",") + "}"
    }

    private fun serializeAuxiliaryContent(aux: AuxiliaryContent): String {
        val fields = mutableListOf<String>()
        if (aux.translation != null) {
            fields.add("\"translation\": ${escapeString(aux.translation)}")
            if (aux.translationSource != null) {
                fields.add("\"translationSource\": ${serializeSourceInfo(aux.translationSource)}")
            }
        }
        if (!aux.annotations.isNullOrEmpty()) {
            val items = aux.annotations.joinToString(",") { ann ->
                val af = mutableListOf<String>()
                af.add("\"index\": ${ann.index}")
                af.add("\"quote\": ${escapeString(ann.quote)}")
                af.add("\"note\": ${escapeString(ann.note)}")
                if (ann.phonetic != null) af.add("\"phonetic\": ${escapeString(ann.phonetic)}")
                if (ann.targetLine != null) af.add("\"targetLine\": ${ann.targetLine}")
                "{" + af.joinToString(",") + "}"
            }
            fields.add("\"annotations\": [$items]")
            if (aux.annotationSource != null) {
                fields.add("\"annotationSource\": ${serializeSourceInfo(aux.annotationSource)}")
            }
        }
        if (aux.appreciation != null) {
            fields.add("\"appreciation\": ${escapeString(aux.appreciation)}")
            if (aux.appreciationSource != null) {
                fields.add("\"appreciationSource\": ${serializeSourceInfo(aux.appreciationSource)}")
            }
        }
        if (aux.background != null) {
            fields.add("\"background\": ${escapeString(aux.background)}")
            if (aux.backgroundSource != null) {
                fields.add("\"backgroundSource\": ${serializeSourceInfo(aux.backgroundSource)}")
            }
        }
        return "{" + fields.joinToString(",") + "}"
    }

    private fun serializeSourceTraces(traces: List<WorkSourceTrace>): String {
        val items = traces.joinToString(",\n") { trace ->
            val fields = mutableListOf<String>()
            fields.add("\"source\": ${escapeString(trace.source.name)}")
            fields.add("\"externalWorkId\": ${escapeString(trace.externalWorkId)}")
            if (trace.externalUrl != null) fields.add("\"externalUrl\": ${escapeString(trace.externalUrl)}")
            if (trace.externalTitle != null) fields.add("\"externalTitle\": ${escapeString(trace.externalTitle)}")
            if (trace.externalAuthor != null) fields.add("\"externalAuthor\": ${escapeString(trace.externalAuthor)}")
            if (trace.externalDynasty != null) fields.add("\"externalDynasty\": ${escapeString(trace.externalDynasty)}")
            fields.add("\"fetchedAtEpochMs\": ${trace.fetchedAtEpochMs}")

            if (trace.contentAttribution != null) {
                fields.add("\"contentAttribution\": ${serializeContentAttribution(trace.contentAttribution)}")
            }
            if (trace.translationAttribution != null) {
                fields.add("\"translationAttribution\": ${serializeContentAttribution(trace.translationAttribution)}")
            }
            if (trace.annotationAttribution != null) {
                fields.add("\"annotationAttribution\": ${serializeContentAttribution(trace.annotationAttribution)}")
            }
            if (trace.appreciationAttribution != null) {
                fields.add("\"appreciationAttribution\": ${serializeContentAttribution(trace.appreciationAttribution)}")
            }
            if (trace.rubyPinyinAttribution != null) {
                fields.add("\"rubyPinyinAttribution\": ${serializeContentAttribution(trace.rubyPinyinAttribution)}")
            }
            "{" + fields.joinToString(",") + "}"
        }
        return "[\n$items\n  ]"
    }

    private fun serializeContentAttribution(attr: ContentAttribution): String {
        val fields = mutableListOf<String>()
        fields.add("\"origin\": ${escapeString(attr.origin.name)}")
        if (attr.externalId != null) fields.add("\"externalId\": ${escapeString(attr.externalId)}")
        fields.add("\"provenance\": ${escapeString(attr.provenance.name)}")
        fields.add("\"license\": ${escapeString(attr.license.name)}")
        fields.add("\"reviewStatus\": ${escapeString(attr.reviewStatus.name)}")
        if (attr.licenseNote != null) fields.add("\"licenseNote\": ${escapeString(attr.licenseNote)}")
        fields.add("\"fetchedAtEpochMs\": ${attr.fetchedAtEpochMs}")
        return "{" + fields.joinToString(",") + "}"
    }

    // =========================================================================
    // 内部结构反序列化辅助函数
    // =========================================================================

    private fun parseSourceInfoFromMap(map: Map<String, Any?>?): SourceInfo? {
        if (map == null) return null
        val sourceName = optString(map, "sourceName", "")
        if (sourceName.isBlank()) return null
        val licenseCode = optString(map, "licenseType", "UNKNOWN")
        val licenseType = LicenseType.fromCodeOrNull(licenseCode) ?: LicenseType.UNKNOWN
        val referenceUrl = optNullableString(map, "referenceUrl")
        val licenseNote = optString(map, "licenseNote", "待核实")
        return SourceInfo(sourceName, licenseType, referenceUrl, licenseNote)
    }

    private fun parseAttributionFromMap(map: Map<String, Any?>?): AttributionInfo? {
        if (map == null) return null
        val statusCode = optString(map, "status", "")
        if (statusCode.isBlank()) return null
        val status = AttributionStatus.fromCode(statusCode)
        val rawCandidates = optList(map, "candidateAuthors")
        val candidateAuthors = rawCandidates?.mapNotNull { it?.toString() } ?: emptyList()
        val note = optString(map, "note", "")
        val rawSources = optList(map, "academicSources")
        val academicSources = rawSources?.mapNotNull { it?.toString() } ?: emptyList()
        return AttributionInfo(status, candidateAuthors, note, academicSources)
    }

    private fun parseAuxiliaryFromMap(map: Map<String, Any?>?): AuxiliaryContent? {
        if (map == null) return null
        val translation = optNullableString(map, "translation")
        val translationSource = parseSourceInfoFromMap(optMap(map, "translationSource"))

        val annotationsRaw = optList(map, "annotations")
        val annotations = annotationsRaw?.mapIndexedNotNull { i, item ->
            if (item is Map<*, *>) {
                val idx = (item["index"] as? Number)?.toInt() ?: (i + 1)
                val quote = item["quote"]?.toString() ?: ""
                val note = item["note"]?.toString() ?: ""
                val phonetic = item["phonetic"]?.toString()
                val targetLine = (item["targetLine"] as? Number)?.toInt()
                if (quote.isNotBlank() && note.isNotBlank()) {
                    AnnotationItem(idx, quote, note, phonetic, targetLine)
                } else null
            } else null
        }
        val annotationSource = parseSourceInfoFromMap(optMap(map, "annotationSource"))

        val appreciation = optNullableString(map, "appreciation")
        val appreciationSource = parseSourceInfoFromMap(optMap(map, "appreciationSource"))

        val background = optNullableString(map, "background")
        val backgroundSource = parseSourceInfoFromMap(optMap(map, "backgroundSource"))

        if (translation == null && annotations.isNullOrEmpty() && appreciation == null && background == null) {
            return null
        }

        return AuxiliaryContent(
            translation = translation,
            translationSource = translationSource,
            annotations = annotations,
            annotationSource = annotationSource,
            appreciation = appreciation,
            appreciationSource = appreciationSource,
            background = background,
            backgroundSource = backgroundSource
        )
    }

    fun parseSourceTracesFromList(list: List<Any?>?): List<WorkSourceTrace> {
        if (list == null) return emptyList()
        return list.mapNotNull { item ->
            if (item is Map<*, *>) {
                val sourceStr = item["source"]?.toString() ?: ""
                val source = SourceOrigin.entries.firstOrNull { it.name.equals(sourceStr, ignoreCase = true) }
                    ?: SourceOrigin.USER_CUSTOM
                val externalWorkId = item["externalWorkId"]?.toString() ?: ""
                val externalUrl = item["externalUrl"]?.toString()
                val externalTitle = item["externalTitle"]?.toString()
                val externalAuthor = item["externalAuthor"]?.toString()
                val externalDynasty = item["externalDynasty"]?.toString()
                val fetchedAt = (item["fetchedAtEpochMs"] as? Number)?.toLong() ?: System.currentTimeMillis()

                @Suppress("UNCHECKED_CAST")
                val contentAttr = parseContentAttributionFromMap(item["contentAttribution"] as? Map<String, Any?>)
                @Suppress("UNCHECKED_CAST")
                val transAttr = parseContentAttributionFromMap(item["translationAttribution"] as? Map<String, Any?>)
                @Suppress("UNCHECKED_CAST")
                val annoAttr = parseContentAttributionFromMap(item["annotationAttribution"] as? Map<String, Any?>)
                @Suppress("UNCHECKED_CAST")
                val apprAttr = parseContentAttributionFromMap(item["appreciationAttribution"] as? Map<String, Any?>)
                @Suppress("UNCHECKED_CAST")
                val rubyAttr = parseContentAttributionFromMap(item["rubyPinyinAttribution"] as? Map<String, Any?>)

                WorkSourceTrace(
                    source = source,
                    externalWorkId = externalWorkId,
                    externalUrl = externalUrl,
                    externalTitle = externalTitle,
                    externalAuthor = externalAuthor,
                    externalDynasty = externalDynasty,
                    fetchedAtEpochMs = fetchedAt,
                    contentAttribution = contentAttr,
                    translationAttribution = transAttr,
                    annotationAttribution = annoAttr,
                    appreciationAttribution = apprAttr,
                    rubyPinyinAttribution = rubyAttr
                )
            } else null
        }
    }

    private fun parseContentAttributionFromMap(map: Map<String, Any?>?): ContentAttribution? {
        if (map == null) return null
        val originStr = optString(map, "origin", "")
        val origin = SourceOrigin.entries.firstOrNull { it.name.equals(originStr, ignoreCase = true) }
            ?: SourceOrigin.USER_CUSTOM
        val externalId = optNullableString(map, "externalId")

        val provStr = optString(map, "provenance", "")
        val provenance = ContentProvenance.entries.firstOrNull { it.name.equals(provStr, ignoreCase = true) }
            ?: ContentProvenance.UNKNOWN_PROVENANCE

        val licStr = optString(map, "license", "")
        val license = LegalLicense.entries.firstOrNull { it.name.equals(licStr, ignoreCase = true) }
            ?: LegalLicense.UNSPECIFIED

        val revStr = optString(map, "reviewStatus", "")
        val reviewStatus = ReviewStatus.entries.firstOrNull { it.name.equals(revStr, ignoreCase = true) }
            ?: ReviewStatus.UNVERIFIED_DRAFT

        val licenseNote = optNullableString(map, "licenseNote")
        val fetchedAt = (map["fetchedAtEpochMs"] as? Number)?.toLong() ?: System.currentTimeMillis()

        return ContentAttribution(
            origin = origin,
            externalId = externalId,
            provenance = provenance,
            license = license,
            reviewStatus = reviewStatus,
            licenseNote = licenseNote,
            fetchedAtEpochMs = fetchedAt
        )
    }

    // =========================================================================
    // 轻量纯 Kotlin JSON 解析与字符串工具
    // =========================================================================

    private fun escapeString(s: String): String {
        val sb = StringBuilder(s.length + 4)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        val hex = c.code.toString(16).padStart(4, '0')
                        sb.append("\\u$hex")
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    fun parseJsonObject(json: String): Map<String, Any?> {
        val v = parseJson(json.trim())
        if (v is Map<*, *>) {
            return v as Map<String, Any?>
        }
        throw IllegalArgumentException("Expected JSON object starting with '{'")
    }

    fun parseJson(json: String): Any? {
        val p = JsonParser(json)
        return p.parse()
    }

    private class JsonParser(private val src: String) {
        private var idx = 0

        fun parse(): Any? {
            skipWs()
            val res = parseValue()
            skipWs()
            return res
        }

        private fun skipWs() {
            while (idx < src.length) {
                when (src[idx]) {
                    ' ', '\t', '\r', '\n' -> idx++
                    '/' -> {
                        if (idx + 1 < src.length && src[idx + 1] == '/') {
                            idx += 2
                            while (idx < src.length && src[idx] != '\n' && src[idx] != '\r') idx++
                        } else break
                    }
                    else -> break
                }
            }
        }

        private fun parseValue(): Any? {
            skipWs()
            if (idx >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val c = src[idx]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"', '\'' -> parseString(c)
                't' -> {
                    if (src.startsWith("true", idx)) { idx += 4; true } else throw IllegalArgumentException("Unexpected token at $idx")
                }
                'f' -> {
                    if (src.startsWith("false", idx)) { idx += 5; false } else throw IllegalArgumentException("Unexpected token at $idx")
                }
                'n' -> {
                    if (src.startsWith("null", idx)) { idx += 4; null } else throw IllegalArgumentException("Unexpected token at $idx")
                }
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected char '$c' at $idx")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            idx++ // skip '{'
            val map = LinkedHashMap<String, Any?>()
            while (true) {
                skipWs()
                if (idx >= src.length) throw IllegalArgumentException("Unterminated object")
                if (src[idx] == '}') {
                    idx++
                    break
                }
                val quote = src[idx]
                val key = if (quote == '"' || quote == '\'') {
                    parseString(quote)
                } else {
                    val s = idx
                    while (idx < src.length && src[idx] != ':' && !src[idx].isWhitespace()) idx++
                    src.substring(s, idx)
                }
                skipWs()
                if (idx >= src.length || src[idx] != ':') throw IllegalArgumentException("Expected ':' at $idx")
                idx++ // skip ':'
                val value = parseValue()
                map[key] = value
                skipWs()
                if (idx < src.length && src[idx] == ',') {
                    idx++
                } else if (idx < src.length && src[idx] == '}') {
                    idx++
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or '}' at $idx")
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            idx++ // skip '['
            val list = mutableListOf<Any?>()
            while (true) {
                skipWs()
                if (idx >= src.length) throw IllegalArgumentException("Unterminated array")
                if (src[idx] == ']') {
                    idx++
                    break
                }
                list.add(parseValue())
                skipWs()
                if (idx < src.length && src[idx] == ',') {
                    idx++
                } else if (idx < src.length && src[idx] == ']') {
                    idx++
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or ']' at $idx")
                }
            }
            return list
        }

        private fun parseString(quote: Char): String {
            idx++ // skip quote
            val sb = StringBuilder()
            while (idx < src.length) {
                val c = src[idx++]
                if (c == quote) return sb.toString()
                if (c == '\\') {
                    if (idx >= src.length) throw IllegalArgumentException("Unterminated escape")
                    when (val esc = src[idx++]) {
                        '"' -> sb.append('"')
                        '\'' -> sb.append('\'')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (idx + 4 > src.length) throw IllegalArgumentException("Invalid unicode escape")
                            val hex = src.substring(idx, idx + 4)
                            idx += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseNumber(): Number {
            val s = idx
            if (src[idx] == '-') idx++
            while (idx < src.length && src[idx].isDigit()) idx++
            var isDouble = false
            if (idx < src.length && src[idx] == '.') {
                isDouble = true
                idx++
                while (idx < src.length && src[idx].isDigit()) idx++
            }
            if (idx < src.length && (src[idx] == 'e' || src[idx] == 'E')) {
                isDouble = true
                idx++
                if (idx < src.length && (src[idx] == '+' || src[idx] == '-')) idx++
                while (idx < src.length && src[idx].isDigit()) idx++
            }
            val numStr = src.substring(s, idx)
            return if (isDouble) {
                numStr.toDouble()
            } else {
                val l = numStr.toLong()
                if (l in Int.MIN_VALUE..Int.MAX_VALUE) l.toInt() else l
            }
        }
    }

    private fun requireString(map: Map<String, Any?>, key: String): String {
        val v = map[key] ?: throw IllegalArgumentException("缺少必填字段: '$key'")
        val s = v.toString().trim()
        if (s.isEmpty()) throw IllegalArgumentException("字段 '$key' 不能为空白")
        return s
    }

    private fun optString(map: Map<String, Any?>, key: String, fallback: String): String {
        val v = map[key] ?: return fallback
        return v.toString().trim()
    }

    private fun optNullableString(map: Map<String, Any?>, key: String): String? {
        val v = map[key] ?: return null
        val s = v.toString().trim()
        return if (s.isEmpty()) null else s
    }

    @Suppress("UNCHECKED_CAST")
    private fun optMap(map: Map<String, Any?>, key: String): Map<String, Any?>? {
        return map[key] as? Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun optList(map: Map<String, Any?>, key: String): List<Any?>? {
        return map[key] as? List<Any?>
    }
}
