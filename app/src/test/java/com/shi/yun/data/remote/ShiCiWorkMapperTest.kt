package com.shi.yun.data.remote

import com.shi.yun.data.model.ContentProvenance
import com.shi.yun.data.model.Genre
import com.shi.yun.data.model.LegalLicense
import com.shi.yun.data.model.ReviewStatus
import com.shi.yun.data.model.SourceOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.InputStreamReader

class ShiCiWorkMapperTest {

    private fun loadFixtureText(path: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: throw IllegalStateException("找不到测试夹具: $path")
        return InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun testMapBaijuyiCaoWithFullAuxiliary() {
        val jsonText = loadFixtureText("fixtures/shici_baijuyi_cao.json")
        val work = ShiCiWorkMapper.mapToWork(jsonText)

        // 验证基本字段
        assertEquals("shici_fu-de-gu-yuan-cao-song-bie", work.id)
        assertEquals("赋得古原草送别", work.title)
        assertEquals("白居易", work.author)
        assertEquals("唐", work.dynasty)
        assertEquals(Genre.POEM, work.genre)
        assertTrue(work.content.contains("野火烧不尽，春风吹又生。"))
        assertEquals(4, work.content.lines().size)

        // 验证辅助内容映射与强一致性
        val aux = work.auxiliary
        assertNotNull(aux)
        assertNotNull(aux?.translation)
        assertNotNull(aux?.translationSource)
        assertEquals(3, aux?.annotations?.size)
        assertEquals("赋得", aux?.annotations?.get(0)?.quote)
        assertEquals("按指定的题目赋诗。", aux?.annotations?.get(0)?.note)
        assertNotNull(aux?.appreciation)

        // 验证来源凭证独立性与审慎性
        assertEquals(1, work.sourceTraces.size)
        val trace = work.sourceTraces.first()
        assertEquals(SourceOrigin.SHI_CI_CN, trace.source)
        assertEquals("fu-de-gu-yuan-cao-song-bie", trace.externalWorkId)

        // 正文凭证：古典原作，网络接口转录版本未核实整理版权 -> UNSPECIFIED
        val contentAttr = trace.contentAttribution
        assertNotNull(contentAttr)
        assertEquals(ContentProvenance.ORIGINAL_ANCIENT, contentAttr?.provenance)
        assertEquals(LegalLicense.UNSPECIFIED, contentAttr?.license)
        assertEquals(ReviewStatus.UNVERIFIED_DRAFT, contentAttr?.reviewStatus)

        // 译文凭证：来源不明/未指定许可，不推定公有领域
        val transAttr = trace.translationAttribution
        assertNotNull(transAttr)
        assertEquals(ContentProvenance.UNKNOWN_PROVENANCE, transAttr?.provenance)
        assertEquals(LegalLicense.UNSPECIFIED, transAttr?.license)
        assertEquals(ReviewStatus.UNVERIFIED_DRAFT, transAttr?.reviewStatus)

        // 注释凭证：来源不明/未指定许可
        val annoAttr = trace.annotationAttribution
        assertNotNull(annoAttr)
        assertEquals(ContentProvenance.UNKNOWN_PROVENANCE, annoAttr?.provenance)
        assertEquals(LegalLicense.UNSPECIFIED, annoAttr?.license)

        // 赏析凭证：来源不明/未指定许可
        val apprAttr = trace.appreciationAttribution
        assertNotNull(apprAttr)
        assertEquals(ContentProvenance.UNKNOWN_PROVENANCE, apprAttr?.provenance)
        assertEquals(LegalLicense.UNSPECIFIED, apprAttr?.license)

        // 拼音凭证：来源未确认/未指定许可
        val rubyAttr = trace.rubyPinyinAttribution
        assertNotNull(rubyAttr)
        assertEquals(ContentProvenance.UNKNOWN_PROVENANCE, rubyAttr?.provenance)
        assertEquals(LegalLicense.UNSPECIFIED, rubyAttr?.license)
    }

    @Test
    fun testMapMinimalPoemWithoutAuxiliary() {
        val jsonText = loadFixtureText("fixtures/shici_minimal_poem.json")
        val work = ShiCiWorkMapper.mapToWork(jsonText)

        assertEquals("shici_minimal-test-poem", work.id)
        assertEquals("无题绝句", work.title)
        assertEquals("", work.author)
        assertEquals("", work.dynasty)
        assertEquals("行到水穷处，坐看云起时。", work.content)
        assertNull(work.auxiliary)

        val trace = work.sourceTraces.first()
        assertNotNull(trace.contentAttribution)
        assertNull(trace.translationAttribution)
        assertNull(trace.annotationAttribution)
        assertNull(trace.appreciationAttribution)
        assertNull(trace.rubyPinyinAttribution)
    }

    @Test
    fun testMissingRequiredFieldsThrows() {
        try {
            ShiCiWorkMapper.mapToWork("""{"title":"测试"}""")
            fail("缺失 slug 必须抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("slug") == true)
        }

        try {
            ShiCiWorkMapper.mapToWork("""{"slug":"test-slug"}""")
            fail("缺失 title 必须抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("title") == true)
        }
    }
}
