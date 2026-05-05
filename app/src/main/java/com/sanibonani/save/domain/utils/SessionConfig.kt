package com.sanibonani.save.domain.utils

import com.sanibonani.save.BuildConfig

/**
 * Configuration for session timeouts in the app.
 * Handles different session lengths for different operations.
 */
object SessionConfig {

    /**
     * Session timeout for password reset via email: 3 minutes (180 seconds)
     * This is a security measure to limit the time a password reset link is valid
     */
    const val PASSWORD_RESET_SESSION_TIMEOUT_SECONDS = 180 // 3 minutes

    /**
     * Session timeout for standard authentication: 24 hours
     */
    const val STANDARD_SESSION_TIMEOUT_SECONDS = 86400 // 24 hours

    /**
     * Session timeout for biometric quick login: 1 hour
     */
    const val BIOMETRIC_SESSION_TIMEOUT_SECONDS = 3600 // 1 hour

    /**
     * Session inactivity timeout: 15 minutes
     * User will be logged out if no activity for this duration
     */
    const val INACTIVITY_TIMEOUT_SECONDS = 900 // 15 minutes

    /**
     * Get appropriate session timeout based on operation type
     */
    fun getSessionTimeout(operationType: String): Long = when (operationType) {
        "password_reset" -> PASSWORD_RESET_SESSION_TIMEOUT_SECONDS
        "biometric" -> BIOMETRIC_SESSION_TIMEOUT_SECONDS
        else -> STANDARD_SESSION_TIMEOUT_SECONDS
    }.toLong()
}

