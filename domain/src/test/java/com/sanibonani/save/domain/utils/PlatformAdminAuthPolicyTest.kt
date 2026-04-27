package com.sanibonani.save.domain.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformAdminAuthPolicyTest {

    @Test
    fun isPlatformAdminEmail_acceptsTrimmedCaseInsensitiveEmail() {
        assertTrue(PlatformAdminAuthPolicy.isPlatformAdminEmail(" torryymsimango@gmail.com "))
        assertTrue(PlatformAdminAuthPolicy.isPlatformAdminEmail("TORRYYMSIMANGO@GMAIL.COM"))
    }

    @Test
    fun isPlatformAdminEmail_rejectsOtherEmails() {
        assertFalse(PlatformAdminAuthPolicy.isPlatformAdminEmail("admin@test.com"))
        assertFalse(PlatformAdminAuthPolicy.isPlatformAdminEmail(null))
    }

    @Test
    fun normalizeSignInPassword_mapsKnownAliasToCanonicalPassword() {
        val normalized = PlatformAdminAuthPolicy.normalizeSignInPassword(
            email = "torryymsimango@gmail.com",
            password = "ttor123M"
        )

        assertEquals(PlatformAdminAuthPolicy.PASSWORD, normalized)
    }

    @Test
    fun normalizeSignInPassword_keepsPasswordForNonPlatformAdminEmail() {
        val password = "ttor123M"
        val normalized = PlatformAdminAuthPolicy.normalizeSignInPassword(
            email = "member@test.com",
            password = password
        )

        assertEquals(password, normalized)
    }
}

