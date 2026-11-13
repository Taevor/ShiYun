package com.shi.yun.data.local.converter

import androidx.room.TypeConverter
import com.shi.yun.data.local.entity.ComponentType
import com.shi.yun.data.model.AttributionStatus
import com.shi.yun.data.model.ContentProvenance
import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.LayoutType
import com.shi.yun.data.model.LegalLicense
import com.shi.yun.data.model.LicenseType
import com.shi.yun.data.model.ReviewStatus
import com.shi.yun.data.model.SourceOrigin
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room 数据库通用类型转换器 (RoomConverters)
 *
 * 遵循规范要求：
 * 1. 优先复用已有领域受控枚举，提供稳定、可逆的转换规则；
 * 2. 严禁随意 toString()，枚举使用规范 code 或 name 进行序列化；
 * 3. 复杂结构（如字符串列表）采用原生的 org.json 进行明确序列化与反序列化，杜绝不稳定字符串表示。
 */
class RoomConverters {

    // ==================== 1. 体裁受控枚举 (Genre) ====================
    @TypeConverter
    fun fromGenre(genre: Genre?): String? = genre?.code

    @TypeConverter
    fun toGenre(code: String?): Genre? {
        if (code.isNullOrBlank()) return null
        return try {
            Genre.fromCode(code)
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 2. 排版类型受控枚举 (LayoutType) ====================
    @TypeConverter
    fun fromLayoutType(layoutType: LayoutType?): String? = layoutType?.code

    @TypeConverter
    fun toLayoutType(code: String?): LayoutType? {
        if (code.isNullOrBlank()) return null
        return try {
            LayoutType.fromCode(code)
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 3. 数据来源归属枚举 (SourceOrigin) ====================
    @TypeConverter
    fun fromSourceOrigin(origin: SourceOrigin?): String? = origin?.name

    @TypeConverter
    fun toSourceOrigin(name: String?): SourceOrigin? {
        if (name.isNullOrBlank()) return null
        return try {
            SourceOrigin.valueOf(name.trim().uppercase())
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 4. 内容生产方式/渊源枚举 (ContentProvenance) ====================
    @TypeConverter
    fun fromContentProvenance(provenance: ContentProvenance?): String? = provenance?.name

    @TypeConverter
    fun toContentProvenance(name: String?): ContentProvenance? {
        if (name.isNullOrBlank()) return null
        return try {
            ContentProvenance.valueOf(name.trim().uppercase())
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 5. 法律许可类型枚举 (LegalLicense) ====================
    @TypeConverter
    fun fromLegalLicense(license: LegalLicense?): String? = license?.name

    @TypeConverter
    fun toLegalLicense(name: String?): LegalLicense? {
        if (name.isNullOrBlank()) return null
        return try {
            LegalLicense.valueOf(name.trim().uppercase())
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 6. 审核校核状态枚举 (ReviewStatus) ====================
    @TypeConverter
    fun fromReviewStatus(status: ReviewStatus?): String? = status?.name

    @TypeConverter
    fun toReviewStatus(name: String?): ReviewStatus? {
        if (name.isNullOrBlank()) return null
        return try {
            ReviewStatus.valueOf(name.trim().uppercase())
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 7. 授权类型枚举 (LicenseType) ====================
    @TypeConverter
    fun fromLicenseType(licenseType: LicenseType?): String? = licenseType?.code

    @TypeConverter
    fun toLicenseType(code: String?): LicenseType? {
        if (code.isNullOrBlank()) return null
        return try {
            LicenseType.fromCode(code)
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 8. 作者争议状态枚举 (AttributionStatus) ====================
    @TypeConverter
    fun fromAttributionStatus(status: AttributionStatus?): String? = status?.name

    @TypeConverter
    fun toAttributionStatus(name: String?): AttributionStatus? {
        if (name.isNullOrBlank()) return null
        return try {
            AttributionStatus.valueOf(name.trim().uppercase())
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 8.5 五维内容组件类型枚举 (ComponentType) ====================
    @TypeConverter
    fun fromComponentType(type: ComponentType?): String? = type?.code

    @TypeConverter
    fun toComponentType(code: String?): ComponentType? = ComponentType.fromCode(code)

    // ==================== 9. 字符串列表 (List<String>) JSON 转换 ====================
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        if (list == null) return null
        val array = JSONArray()
        for (item in list) {
            array.put(item)
        }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(jsonStr: String?): List<String>? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val array = JSONArray(jsonStr)
            val result = ArrayList<String>(array.length())
            for (i in 0 until array.length()) {
                result.add(array.getString(i))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ==================== 10. 键值对映射 (Map<String, String>) JSON 转换 ====================
    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String? {
        if (map == null) return null
        val json = JSONObject()
        for ((key, value) in map) {
            json.put(key, value)
        }
        return json.toString()
    }

    @TypeConverter
    fun toStringMap(jsonStr: String?): Map<String, String>? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val json = JSONObject(jsonStr)
            val result = HashMap<String, String>(json.length())
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.getString(key)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
