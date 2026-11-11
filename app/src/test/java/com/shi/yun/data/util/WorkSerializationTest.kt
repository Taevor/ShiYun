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
import com.shi.yun.data.repository.LocalJsonWorkRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkSerializationTest {

    /**
     * 验收要求 3 & 4：验证工程原有 600 首生产作品仍能完整读取，
     * 且旧版 JSON 不含 sourceTraces 时 100% 平滑兼容，默认解析为空列表。
     */
    @Test
    fun testLegacy600WorksFullLoading() {
        val assetsFile = File("src/main/assets/data/works.json")
        assertTrue("必须能定位到工程 assets/data/works.json", assetsFile.exists())

        val jsonText = assetsFile.readText(Charsets.UTF_8)
        val rawList = WorkJsonSerializer.parseJson(jsonText) as? List<*>
        assertNotNull("作品 JSON 根结构必须为数组", rawList)
        assertEquals(600, rawList?.size)

        var tangCount = 0
        var songCount = 0

        for (i in 0 until 600) {
            @Suppress("UNCHECKED_CAST")
            val itemMap = rawList?.get(i) as Map<String, Any?>

            // 1. 直接由原始 600 首 JSON 对象 Map 反序列化
            val workFromMap = WorkJsonSerializer.deserialize(itemMap)

            // 2. 序列化为规范 JSON 字符串，并通过 LocalJsonWorkRepository.parseWorkFromJson 反序列化验证真实调用链
            val singleWorkJson = WorkJsonSerializer.serialize(workFromMap)
            val work = LocalJsonWorkRepository.parseWorkFromJson(singleWorkJson)

            assertTrue("作品 ID 必须非空", work.id.isNotBlank())
            assertTrue("作品标题必须非空", work.title.isNotBlank())
            assertTrue("作品正文必须非空", work.content.isNotBlank())

            // 验证旧版 600 首作品未含 sourceTraces 时，严格默认为空列表，完全向后兼容
            assertNotNull(work.sourceTraces)
            assertTrue(work.sourceTraces.isEmpty())

            if (work.id.startsWith("tang_")) tangCount++
            if (work.id.startsWith("song_")) songCount++
        }

        // 验证作品 ID 与数量完整性：唐诗 320 首，宋词 280 首
        assertEquals(320, tangCount)
        assertEquals(280, songCount)
    }

    /**
     * 验收要求 5：验证新 JSON 的多来源追溯及五个内容维度凭证可以完整往返（Round-trip）
     */
    @Test
    fun testFullRoundTripWithMultiSourceTracesAndFiveAttributions() {
        val originalWork = Work(
            id = "work_roundtrip_001",
            title = "水调歌头·明月几时有",
            author = "苏轼",
            dynasty = "宋",
            genre = Genre.CI,
            content = "明月几时有？把酒问青天。\n不知天上宫阙，今夕是何年。",
            excerpt = "明月几时有？把酒问青天。",
            cipai = "水调歌头",
            subtitle = "明月几时有",
            layout = LayoutType.CI_DOUBLE,
            preface = "丙辰中秋，欢饮达旦，大醉，作此篇，兼怀子由。",
            textSource = SourceInfo(
                sourceName = "《东坡乐府》乾隆刊本",
                licenseType = LicenseType.PUBLIC_DOMAIN,
                referenceUrl = "https://example.com/dongpo",
                licenseNote = "公有领域底本"
            ),
            attribution = AttributionInfo(
                status = AttributionStatus.CONFIRMED,
                candidateAuthors = emptyList(),
                note = "确认为苏轼元丰年间词作",
                academicSources = listOf("《四库全书总目提要》")
            ),
            auxiliary = AuxiliaryContent(
                translation = "明月从什么时候开始有的呢？我端起酒杯遥问苍天。",
                translationSource = SourceInfo("现代名家译本", LicenseType.CC_BY_SA_4_0),
                annotations = listOf(
                    AnnotationItem(index = 1, quote = "把酒", note = "端着酒杯。", phonetic = "bǎ jiǔ", targetLine = 1)
                ),
                annotationSource = SourceInfo("古典词鉴赏辞典", LicenseType.UNKNOWN),
                appreciation = "此词为中秋望月咏怀之千古绝唱。",
                appreciationSource = SourceInfo("唐宋词选评", LicenseType.AUTHORIZED)
            ),
            source = "local_canonical",
            sourceId = "song_000001",
            licenseNote = "诗云经典库核准收录",
            sourceTraces = listOf(
                WorkSourceTrace(
                    source = SourceOrigin.LOCAL_CANONICAL,
                    externalWorkId = "song_000001",
                    externalUrl = "https://shiyun.app/works/song_000001",
                    externalTitle = "水调歌头",
                    externalAuthor = "苏轼",
                    externalDynasty = "宋",
                    contentAttribution = ContentAttribution(
                        origin = SourceOrigin.LOCAL_CANONICAL,
                        externalId = "local_001",
                        provenance = ContentProvenance.ORIGINAL_ANCIENT,
                        license = LegalLicense.PUBLIC_DOMAIN,
                        reviewStatus = ReviewStatus.VERIFIED,
                        licenseNote = "经诗云古籍校勘审定"
                    )
                ),
                WorkSourceTrace(
                    source = SourceOrigin.SHI_CI_CN,
                    externalWorkId = "shici_shuidiaogetou_sushi",
                    externalUrl = "https://shi-ci.cn/api/poems/shici_shuidiaogetou_sushi",
                    externalTitle = "水调歌头·明月几时有",
                    externalAuthor = "苏轼",
                    contentAttribution = ContentAttribution(
                        origin = SourceOrigin.SHI_CI_CN,
                        provenance = ContentProvenance.ORIGINAL_ANCIENT,
                        license = LegalLicense.PUBLIC_DOMAIN,
                        reviewStatus = ReviewStatus.UNVERIFIED_DRAFT
                    ),
                    translationAttribution = ContentAttribution(
                        origin = SourceOrigin.SHI_CI_CN,
                        externalId = "trans_001",
                        provenance = ContentProvenance.HUMAN_CURATED,
                        license = LegalLicense.CC_BY_NC_4_0,
                        reviewStatus = ReviewStatus.PENDING_REVIEW
                    ),
                    annotationAttribution = ContentAttribution(
                        origin = SourceOrigin.SHI_CI_CN,
                        provenance = ContentProvenance.UNKNOWN_PROVENANCE,
                        license = LegalLicense.UNSPECIFIED,
                        reviewStatus = ReviewStatus.UNVERIFIED_DRAFT
                    ),
                    appreciationAttribution = ContentAttribution(
                        origin = SourceOrigin.SHI_CI_CN,
                        provenance = ContentProvenance.AI_GENERATED,
                        license = LegalLicense.PROPRIETARY_RESERVED,
                        reviewStatus = ReviewStatus.UNVERIFIED_DRAFT
                    ),
                    rubyPinyinAttribution = ContentAttribution(
                        origin = SourceOrigin.SHI_CI_CN,
                        provenance = ContentProvenance.UNKNOWN_PROVENANCE,
                        license = LegalLicense.UNSPECIFIED,
                        reviewStatus = ReviewStatus.VERIFIED
                    )
                )
            )
        )

        // 1. 序列化为规范 JSON
        val serializedJson = WorkJsonSerializer.serialize(originalWork)
        assertTrue(serializedJson.contains("\"sourceTraces\":"))
        assertTrue(serializedJson.contains("shici_shuidiaogetou_sushi"))

        // 2. 反序列化
        val deserializedWork = WorkJsonSerializer.deserialize(serializedJson)

        // 3. 验证基础字段
        assertEquals(originalWork.id, deserializedWork.id)
        assertEquals(originalWork.title, deserializedWork.title)
        assertEquals(originalWork.author, deserializedWork.author)
        assertEquals(originalWork.dynasty, deserializedWork.dynasty)
        assertEquals(originalWork.genre, deserializedWork.genre)
        assertEquals(originalWork.content, deserializedWork.content)
        assertEquals(originalWork.excerpt, deserializedWork.excerpt)
        assertEquals(originalWork.cipai, deserializedWork.cipai)
        assertEquals(originalWork.subtitle, deserializedWork.subtitle)
        assertEquals(originalWork.layout, deserializedWork.layout)
        assertEquals(originalWork.preface, deserializedWork.preface)

        // 4. 验证底本、归属考订与辅助内容
        assertEquals(originalWork.textSource?.sourceName, deserializedWork.textSource?.sourceName)
        assertEquals(originalWork.attribution?.status, deserializedWork.attribution?.status)
        assertEquals(originalWork.auxiliary?.translation, deserializedWork.auxiliary?.translation)
        assertEquals(originalWork.auxiliary?.annotations?.size, deserializedWork.auxiliary?.annotations?.size)

        // 5. 验证多来源追溯与 5 维凭证完整往返
        assertEquals(2, deserializedWork.sourceTraces.size)

        val localTrace = deserializedWork.sourceTraces[0]
        assertEquals(SourceOrigin.LOCAL_CANONICAL, localTrace.source)
        assertEquals("song_000001", localTrace.externalWorkId)
        assertEquals(ReviewStatus.VERIFIED, localTrace.contentAttribution?.reviewStatus)

        val shiCiTrace = deserializedWork.sourceTraces[1]
        assertEquals(SourceOrigin.SHI_CI_CN, shiCiTrace.source)
        assertEquals("shici_shuidiaogetou_sushi", shiCiTrace.externalWorkId)
        assertEquals(ContentProvenance.ORIGINAL_ANCIENT, shiCiTrace.contentAttribution?.provenance)
        assertEquals(LegalLicense.PUBLIC_DOMAIN, shiCiTrace.contentAttribution?.license)

        assertEquals(ContentProvenance.HUMAN_CURATED, shiCiTrace.translationAttribution?.provenance)
        assertEquals(LegalLicense.CC_BY_NC_4_0, shiCiTrace.translationAttribution?.license)
        assertEquals(ReviewStatus.PENDING_REVIEW, shiCiTrace.translationAttribution?.reviewStatus)

        assertEquals(ContentProvenance.UNKNOWN_PROVENANCE, shiCiTrace.annotationAttribution?.provenance)
        assertEquals(LegalLicense.UNSPECIFIED, shiCiTrace.annotationAttribution?.license)

        assertEquals(ContentProvenance.AI_GENERATED, shiCiTrace.appreciationAttribution?.provenance)
        assertEquals(LegalLicense.PROPRIETARY_RESERVED, shiCiTrace.appreciationAttribution?.license)

        assertEquals(ContentProvenance.UNKNOWN_PROVENANCE, shiCiTrace.rubyPinyinAttribution?.provenance)
        assertEquals(ReviewStatus.VERIFIED, shiCiTrace.rubyPinyinAttribution?.reviewStatus)
    }

    /**
     * 边界与容错测试：非法转义、缺少引号、截断 JSON 能够安全抛出异常，绝不静默生成错误 Work
     */
    @Test
    fun testCorruptedJsonFailsSafely() {
        // 缺少引号
        try {
            WorkJsonSerializer.deserialize("{id: test_id, title: 无题}")
            org.junit.Assert.fail("缺少合法格式必须抛出 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message != null)
        }

        // 截断 JSON
        try {
            WorkJsonSerializer.deserialize("{\"id\": \"test_1\", \"title\": \"截断测试\"")
            org.junit.Assert.fail("截断 JSON 必须抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message != null)
        }

        // 缺失必填字段 id
        try {
            WorkJsonSerializer.deserialize("{\"title\": \"有标题无ID\", \"content\": \"正文\", \"genre\": \"poem\"}")
            org.junit.Assert.fail("缺失必填 id 必须抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("id") == true)
        }
    }

    /**
     * 中文特殊字符、罕见 Unicode 生僻字与标点完整往返
     */
    @Test
    fun testSpecialUnicodeAndChinesePunctuationRoundTrip() {
        val workWithUnicode = Work(
            id = "unicode_001",
            title = "㪫㪳䶮“双引号”与‘单引号’测试",
            author = "𠮷野家\\李白",
            dynasty = "唐·天宝",
            genre = Genre.POEM,
            content = "第一句：春眠不觉晓，\n第二句：处处闻啼鸟。\n生僻字：𠮷、䶮、㪫。\n特殊标点：——……【】『』「」",
            excerpt = "春眠不觉晓，处处闻啼鸟。"
        )

        val json = WorkJsonSerializer.serialize(workWithUnicode)
        val deserialized = WorkJsonSerializer.deserialize(json)

        assertEquals(workWithUnicode.id, deserialized.id)
        assertEquals(workWithUnicode.title, deserialized.title)
        assertEquals(workWithUnicode.author, deserialized.author)
        assertEquals(workWithUnicode.content, deserialized.content)
    }
}
