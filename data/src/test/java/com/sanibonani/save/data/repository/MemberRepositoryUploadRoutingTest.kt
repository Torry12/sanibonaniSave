package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.repository.system.DefaultStorageNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

class MemberRepositoryUploadRoutingTest {

    private val namingStrategy = DefaultStorageNamingStrategy()

    @Test
    fun `profile photo uploads route to avatars bucket`() {
        assertEquals(DefaultStorageNamingStrategy.AVATARS_BUCKET, namingStrategy.getBucketForMemberDoc(DefaultStorageNamingStrategy.PROFILE_PHOTO_INDEX))
        assertEquals(
            "members/member-1/profile_123.jpg",
            namingStrategy.getMemberDocPath(
                memberId = "member-1",
                index = DefaultStorageNamingStrategy.PROFILE_PHOTO_INDEX,
                extension = "jpg",
                timestamp = 123L
            )
        )
    }

    @Test
    fun `fixed slot documents route to documents bucket`() {
        assertEquals(DefaultStorageNamingStrategy.DOCUMENTS_BUCKET, namingStrategy.getBucketForMemberDoc(3))
        assertEquals(
            "members/member-1/doc_3_123.pdf",
            namingStrategy.getMemberDocPath(
                memberId = "member-1",
                index = 3,
                extension = "pdf",
                timestamp = 123L
            )
        )
    }
}
