package com.sanibonani.save.domain.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformAdminAuthPolicyTest {

    @Test
    fun isPlatformAdminEmail_acceptsTrimmedCaseInsensitiveEmail() {
        assertTrue(PlatformAdminAuthPolicy.isPlatformAdminEmail(" torrymsimango@gmail.com "))
        assertTrue(PlatformAdminAuthPolicy.isPlatformAdminEmail("TORRYMSIMANGO@GMAIL.COM"))
    }

    @Test
    fun isPlatformAdminEmail_rejectsOtherEmails() {
        assertFalse(PlatformAdminAuthPolicy.isPlatformAdminEmail("admin@test.com"))
        assertFalse(PlatformAdminAuthPolicy.isPlatformAdminEmail(null))
    }

    @Test
    fun email_policy_accepts_exact_configured_admin_email_only() {
        assertTrue(PlatformAdminAuthPolicy.isPlatformAdminEmail(PlatformAdminAuthPolicy.EMAIL))
    }

    @Test
    fun email_policy_does_not_elevate_non_admin_email() {
        assertFalse(PlatformAdminAuthPolicy.isPlatformAdminEmail("member@test.com"))
    }
}

