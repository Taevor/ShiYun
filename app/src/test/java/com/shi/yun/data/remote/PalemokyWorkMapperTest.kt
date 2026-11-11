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

class PalemokyWorkMapperTest {

    private fun loadFixtureText(path: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: throw IllegalStateException("找不到测试夹具: $path")
        return InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun testMapDuFuLvShi() {
        val jsonText = loadFixtureText("fixtures/palemoky_dufu_lvshi.json")
        val work = PalemokyWorkMapper.mapToWork(jsonText)

        assertEquals("palemoky_1024", work.id)
        assertEquals("春望", work.title)
        assertEquals("杜甫", work.author)
        assertEquals("唐", work.dynasty)
        assertEquals(Genre.POEM, work.genre)
        assertTrue(work.content.contains("国破山河在，城春草木深。"))
        assertEquals(4, work.content.lines().size)

        // 验证诗泉不提供辅助内容
        assertNull(work.auxiliary)

        // 验证来源凭证
        assertEquals(1, work.sourceTraces.size)
        val trace = work.sourceTraces.first()
        assertEquals(SourceOrigin.POETRY_PALEMOKY, trace.source)
        assertEquals("1024", trace.externalWorkId)
        assertEquals("https://poetry.palemoky.com/api/poems/1024", trace.externalUrl)

        val contentAttr = trace.contentAttribution
        assertNotNull(contentAttr)
        assertEquals(ContentProvenance.ORIGINAL_ANCIENT, contentAttr?.provenance)
        assertEquals(LegalLicense.UNSPECIFIED, contentAttr?.license)
        assertEquals(ReviewStatus.UNVERIFIED_DRAFT, contentAttr?.reviewStatus)

        // 验证辅助内容凭证均为 null
        assertNull(trace.translationAttribution)
        assertNull(trace.annotationAttribution)
        assertNull(trace.appreciationAttribution)
        assertNull(trace.rubyPinyinAttribution)
    }

    @Test
    fun testMapUnknownTypeFallbackToPoem() {
        val jsonText = loadFixtureText("fixtures/palemoky_unknown_type.json")
        val work = PalemokyWorkMapper.mapToWork(jsonText)

        assertEquals("palemoky_9999", work.id)
        assertEquals("短歌行", work.title)
        assertEquals("曹操", work.author)
        assertEquals("魏晋", work.dynasty)
        assertEquals(Genre.POEM, work.genre)
        assertTrue(work.content.contains("对酒当歌，人生几何！"))
    }

    @Test
    fun testMissingIdOrTitleThrows() {
        try {
            PalemokyWorkMapper.mapToWork("""{"title":"测试"}""")
            fail("缺失 id 必须抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("id") == true)
        }

        try {
            PalemokyWorkMapper.mapToWork("""{"id":123}""")
            fail("缺失 title 必须抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("title") == true)
        }
    }
}
