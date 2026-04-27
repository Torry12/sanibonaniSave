package com.sanibonani.save.domain.utils

object PlatformAdminAuthPolicy {
    const val EMAIL: String = "torryymsimango@gmail.com"
    const val PASSWORD: String = "torry123M"
    private const val PASSWORD_ALIAS: String = "ttor123M"

    fun isPlatformAdminEmail(email: String?): Boolean {
        return email?.trim()?.lowercase() == EMAIL
    }

    fun normalizeSignInPassword(email: String, password: String): String {
        return if (isPlatformAdminEmail(email) && (password == PASSWORD || password == PASSWORD_ALIAS)) {
            PASSWORD
        } else {
            password
        }
    }
}

