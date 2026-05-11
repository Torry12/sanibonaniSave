package com.sanibonani.save.domain.utils

/**
 * Centralized auth identifier normalization/validation so app and data layers
 * follow the same protocol rules.
 */
object AuthIdentityUtils {
    private val emailRegex = Regex(
        pattern = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
        option = RegexOption.IGNORE_CASE
    )

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun isPlausibleEmail(email: String): Boolean = emailRegex.matches(normalizeEmail(email))
}

