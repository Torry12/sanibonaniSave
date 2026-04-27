package com.sanibonani.save.data

/**
 * Central constants for the SanibonaniSave platform.
 * Eliminates magic numbers and provides single source of truth for fees, defaults, and limits.
 */

// ── Platform Fees ──────────────────────────────────────────────────────────
// PlatformFees moved to com.sanibonani.save.domain.model.Models.kt to break circular dependency

// ── Group Defaults ─────────────────────────────────────────────────────────
object GroupDefaults {
    const val PROBATION_MONTHS = 3
    const val PAYMENT_DUE_DAY = 28
    const val MAX_MEMBERS = 50
    const val AUTO_SUSPEND_AFTER_DAYS = 2
    const val LATE_FEE_GRACE_DAYS = 5
}

// ── Member Validation ──────────────────────────────────────────────────────
object MemberValidation {
    // SA ID number: 13 digits, format YYMMDDGGGGGCC where:
    // YYMMDD = birthdate, GGGGG = gender (5 digits), C = citizenship, C = digit root
    const val SA_ID_NUMBER_LENGTH = 13
    const val SA_ID_REGEX = "^[0-9]{13}$"
    
    const val NAME_MIN_LENGTH = 2
    const val NAME_MAX_LENGTH = 100
    
    const val PHONE_MIN_LENGTH = 10
    const val PHONE_MAX_LENGTH = 15
    
    const val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
}

// ── Bank Account Validation ────────────────────────────────────────────────
object BankAccountValidation {
    // Account number formats per major SA banks
    val ACCOUNT_NUMBER_LENGTHS = mapOf(
        "ABSA"          to (10..16),
        "FNB"           to (10..20),
        "Nedbank"       to (10..16),
        "Capitec"       to (8..20),
        "African Bank"  to (10..16),
        "Standard Bank" to (10..20),
        "Postbank"      to (10..20),
        "TymeBank"      to (10..20)
    )
    
    // Branch code is typically 6 digits
    const val BRANCH_CODE_LENGTH = 6
    const val BRANCH_CODE_REGEX = "^[0-9]{6}$"
}

// ── Actuarial Defaults ────────────────────────────────────────────────────
object ActuarialDefaults {
    const val DEFAULT_SAFETY_LOADING_PCT = 35.0      // 35% loading above pure premium
    const val DEFAULT_MORTALITY_RATE_PCT = 0.82      // 0.82% annual mortality (SA typical)
    const val RESERVE_ADEQUACY_MONTHS = 6            // 6 months emergency reserve
    const val ANNUAL_DISCOUNT_RATE_PCT = 8.5         // 8.5% discount rate for APV
    
    // Risk score weighting
    const val RESERVE_ADEQUACY_WEIGHT = 0.3
    const val CONTRIBUTION_SUFFICIENCY_WEIGHT = 0.4
    const val PAYMENT_RATE_WEIGHT = 0.3
}

// ── Notification Channels ──────────────────────────────────────────────────
object NotificationDefaults {
    const val DEFAULT_CHANNEL = "both"  // WhatsApp + Email by default
}

// ── Database ───────────────────────────────────────────────────────────────
object DatabaseConfig {
    const val DATABASE_NAME = "sanibonani.db"
    const val SCHEMA_VERSION = 5
    
    // Stale cache threshold: 24 hours
    const val STALE_CACHE_THRESHOLD_MS = 24 * 60 * 60 * 1000L
}

// ── API Pagination ────────────────────────────────────────────────────────
object PaginationDefaults {
    const val PAGE_SIZE = 100
    const val INITIAL_LOAD_SIZE = 100
}

// ── Timeout (milliseconds) ────────────────────────────────────────────────
object TimeoutDefaults {
    const val SUPABASE_QUERY_TIMEOUT_MS = 10_000L      // 10 seconds
    const val SIGN_IN_RETRY_DELAY_MS = 400L            // 400ms between retries
    const val SIGN_IN_RETRY_ATTEMPTS = 3
}

// ── File Upload Limits ─────────────────────────────────────────────────────
object FileUploadLimits {
    const val MAX_FILE_SIZE_BYTES = 3 * 1024 * 1024 // 3MB
}
