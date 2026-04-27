package com.sanibonani.save.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class MemberRepositoryUploadRoutingTest {

    @Test
    fun `profile photo uploads route to avatars bucket`() {
        assertEquals(PROFILE_PHOTOS_BUCKET, memberUploadBucketForIndex(PROFILE_PHOTO_INDEX))
        assertEquals(
            "members/member-1/profile_123.jpg",
            memberUploadPathForIndex(
                memberId = "member-1",
                documentIndex = PROFILE_PHOTO_INDEX,
                ext = "jpg",
                timestamp = 123L
            )
        )
    }

    @Test
    fun `fixed slot documents route to documents bucket`() {
        assertEquals(MEMBER_DOCUMENTS_BUCKET, memberUploadBucketForIndex(3))
        assertEquals(
            "members/member-1/doc_3_123.pdf",
            memberUploadPathForIndex(
                memberId = "member-1",
                documentIndex = 3,
                ext = "pdf",
                timestamp = 123L
            )
        )
    }
}
