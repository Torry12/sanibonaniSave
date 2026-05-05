package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.BuildConfig

object PlatformAdminAuthPolicy {
    val EMAIL: String = BuildConfig.PLATFORM_ADMIN_EMAIL
    val ASSUME_ALL_AUTH_USERS_ARE_PLATFORM_ADMIN: Boolean = BuildConfig.ASSUME_ALL_AUTH_USERS_ARE_PLATFORM_ADMIN

    fun isPlatformAdminEmail(email: String?): Boolean {
        return email?.trim()?.lowercase() == EMAIL.lowercase()
    }
}
