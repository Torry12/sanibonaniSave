package com.sanibonani.save.data.utils

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.NotifEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberPortalUiUtilsTest {

    @Test
    fun `filterNotificationsForMember keeps matching and broadcast notifications sorted newest first`() {
        val notifications = listOf(
            AppNotification(id = "1", memberId = "member-a", message = "mine", triggerEvent = NotifEvent.CUSTOM),
            AppNotification(id = "3", memberId = null, message = "broadcast", triggerEvent = NotifEvent.PAYMENT_DUE),
            AppNotification(id = "2", memberId = "member-b", message = "other", triggerEvent = NotifEvent.CUSTOM)
        )

        val filtered = filterNotificationsForMember(notifications, memberId = "member-a")

        assertEquals(listOf("3", "1"), filtered.mapNotNull { it.id })
    }

    @Test
    fun `partitionMemberNotifications separates direct messages from system notifications`() {
        val notifications = listOf(
            AppNotification(id = "1", message = "custom", triggerEvent = NotifEvent.CUSTOM),
            AppNotification(id = "2", message = "member", triggerEvent = NotifEvent.MEMBER_MESSAGE),
            AppNotification(id = "3", message = "system", triggerEvent = NotifEvent.PAYMENT_CONFIRMED)
        )

        val (messages, systemNotifications) = partitionMemberNotifications(notifications)

        assertEquals(listOf("1", "2"), messages.mapNotNull { it.id })
        assertEquals(listOf("3"), systemNotifications.mapNotNull { it.id })
    }

    @Test
    fun `applyUploadedMemberDocument marks fixed document slot as pending`() {
        val updated = Member(
            id = "member-1",
            document2Type = "application/pdf",
            document2Status = DocumentStatus.VERIFIED
        ).applyUploadedMemberDocument(
            documentIndex = 2,
            uploadedUrl = "https://files.example/proof.pdf",
            documentType = null
        )

        assertEquals("https://files.example/proof.pdf", updated.document2Url)
        assertEquals("application/pdf", updated.document2Type)
        assertEquals(DocumentStatus.PENDING, updated.document2Status)
    }

    @Test
    fun `mergeUploadedMemberDocument preserves fresher backend slot data when available`() {
        val merged = Member(
            id = "member-1",
            document4Url = "https://cdn.example/marriage.pdf",
            document4Type = "application/pdf",
            document4Status = DocumentStatus.VERIFIED
        ).mergeUploadedMemberDocument(
            documentIndex = 4,
            uploadedUrl = "https://tmp.example/upload.pdf",
            documentType = "image/png"
        )

        assertEquals("https://cdn.example/marriage.pdf", merged.document4Url)
        assertEquals("application/pdf", merged.document4Type)
        assertEquals(DocumentStatus.VERIFIED, merged.document4Status)
    }

    @Test
    fun `mergeUploadedMemberDocument prefers fresh profile photo url`() {
        val merged = Member(
            id = "member-1",
            profilePhotoUrl = "https://old.example/photo.jpg"
        ).mergeUploadedMemberDocument(
            documentIndex = 0,
            uploadedUrl = "https://new.example/photo.jpg",
            documentType = null
        )

        assertEquals("https://new.example/photo.jpg", merged.profilePhotoUrl)
    }

    @Test
    fun `memberDocumentLabel and profile refresh helpers cover unknown indexes`() {
        assertEquals("Constitution", memberDocumentLabel(5))
        assertEquals("Document 9", memberDocumentLabel(9))
        assertTrue(shouldRefreshProfileImageVersion(0))
        assertTrue(!shouldRefreshProfileImageVersion(3))
    }

    @Test
    fun `resolveFreshProfilePhotoUrl trims blank values`() {
        assertEquals(
            "https://fresh.example/photo.jpg",
            resolveFreshProfilePhotoUrl("   https://old.example/photo.jpg   ", " https://fresh.example/photo.jpg ")
        )
        assertNull(resolveFreshProfilePhotoUrl("   ", null))
    }

    @Test
    fun `requiresSupabaseAuthHeaders only matches authenticated storage urls`() {
        assertTrue(
            requiresSupabaseAuthHeaders(
                "https://project.supabase.co/storage/v1/object/authenticated/documents/members/m1/doc_1.pdf"
            )
        )
        assertTrue(!requiresSupabaseAuthHeaders("https://project.supabase.co/storage/v1/object/public/avatars/members/m1/profile.jpg"))
        assertTrue(!requiresSupabaseAuthHeaders("https://cdn.example.com/photo.jpg"))
    }

    @Test
    fun `sanitizeMemberDocumentUploadError hides infrastructure setup details`() {
        val sanitized = sanitizeMemberDocumentUploadError(
            "File storage is not configured on the server. Please create the required Supabase Storage buckets and apply the storage policies."
        )

        assertEquals("Document upload is temporarily unavailable. Please try again later.", sanitized)
    }

    @Test
    fun `sanitizeMemberDocumentUploadError keeps regular validation feedback`() {
        val original = "This file is too large to upload. Please choose a smaller file and try again."
        assertEquals(original, sanitizeMemberDocumentUploadError(original))
    }
}

