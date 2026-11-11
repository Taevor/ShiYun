package com.shi.yun.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkSourceTraceTest {

    @Test
    fun testWorkDefaultSourceTracesIsEmpty() {
        val work = Work(
            id = "tang_000001",
            title = "感遇·其一",
            author = "张九龄",
            dynasty = "唐",
            genre = Genre.POEM,
            content = "兰叶春葳蕤，桂华秋皎洁。",
            excerpt = "兰叶春葳蕤，桂华秋皎洁。"
        )
        assertNotNull(work.sourceTraces)
        assertTrue(work.sourceTraces.isEmpty())
    }

    @Test
    fun testIndependentFiveDimensionAttribution() {
        val contentAttr = ContentAttribution(
            origin = SourceOrigin.LOCAL_CANONICAL,
            provenance = ContentProvenance.ORIGINAL_ANCIENT,
            license = LegalLicense.PUBLIC_DOMAIN,
            reviewStatus = ReviewStatus.VERIFIED
        )
        val translationAttr = ContentAttribution(
            origin = SourceOrigin.SHI_CI_CN,
            externalId = "trans_123",
            provenance = ContentProvenance.HUMAN_CURATED,
            license = LegalLicense.CC_BY_NC_4_0,
            reviewStatus = ReviewStatus.PENDING_REVIEW
        )
        val annotationAttr = ContentAttribution(
            origin = SourceOrigin.SHI_CI_CN,
            externalId = "anno_456",
            provenance = ContentProvenance.HUMAN_CURATED,
            license = LegalLicense.UNSPECIFIED,
            reviewStatus = ReviewStatus.UNVERIFIED_DRAFT
        )
        val appreciationAttr = ContentAttribution(
            origin = SourceOrigin.SHI_CI_CN,
            externalId = "appr_789",
            provenance = ContentProvenance.AI_GENERATED,
            license = LegalLicense.PROPRIETARY_RESERVED,
            reviewStatus = ReviewStatus.UNVERIFIED_DRAFT
        )
        val rubyAttr = ContentAttribution(
            origin = SourceOrigin.SHI_CI_CN,
            provenance = ContentProvenance.HUMAN_CURATED,
            license = LegalLicense.UNSPECIFIED,
            reviewStatus = ReviewStatus.VERIFIED
        )

        val trace = WorkSourceTrace(
            source = SourceOrigin.SHI_CI_CN,
            externalWorkId = "shici_slug_001",
            externalTitle = "感遇·其一",
            contentAttribution = contentAttr,
            translationAttribution = translationAttr,
            annotationAttribution = annotationAttr,
            appreciationAttribution = appreciationAttr,
            rubyPinyinAttribution = rubyAttr
        )

        assertEquals(ContentProvenance.ORIGINAL_ANCIENT, trace.contentAttribution?.provenance)
        assertEquals(LegalLicense.CC_BY_NC_4_0, trace.translationAttribution?.license)
        assertEquals(ReviewStatus.UNVERIFIED_DRAFT, trace.annotationAttribution?.reviewStatus)
        assertEquals(ContentProvenance.AI_GENERATED, trace.appreciationAttribution?.provenance)
        assertEquals(LegalLicense.UNSPECIFIED, trace.rubyPinyinAttribution?.license)
    }
}
