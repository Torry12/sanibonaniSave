package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.BuildConfig

object PlatformAdminAuthPolicy {
    val EMAIL: String = BuildConfig.PLATFORM_ADMIN_EMAIL

    fun isPlatformAdminEmail(email: String?): Boolean {
        return email?.trim()?.lowercase() == EMAIL.lowercase()
    }
}
