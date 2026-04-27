package com.sanibonani.save.viewmodel

import com.sanibonani.save.data.utils.resolveFreshProfilePhotoUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemberViewModelProfilePhotoUrlTest {

    @Test
    fun `resolveFreshProfilePhotoUrl prefers uploaded when present`() {
        val resolved = resolveFreshProfilePhotoUrl(
            existingUrl = "https://old.example/photo.jpg",
            uploadedUrl = "https://new.example/photo.jpg"
        )

        assertEquals("https://new.example/photo.jpg", resolved)
    }

    @Test
    fun `resolveFreshProfilePhotoUrl falls back to existing when uploaded is blank`() {
        val resolved = resolveFreshProfilePhotoUrl(
            existingUrl = "https://old.example/photo.jpg",
            uploadedUrl = "   "
        )

        assertEquals("https://old.example/photo.jpg", resolved)
    }

    @Test
    fun `resolveFreshProfilePhotoUrl returns null when both are empty`() {
        val resolved = resolveFreshProfilePhotoUrl(
            existingUrl = null,
            uploadedUrl = ""
        )

        assertNull(resolved)
    }

    @Test
    fun `resolveFreshProfilePhotoUrl returns null when existing is blank spaces and uploaded is blank`() {
        val resolved = resolveFreshProfilePhotoUrl(
            existingUrl = "   ",
            uploadedUrl = "   "
        )

        assertNull(resolved)
    }
}

