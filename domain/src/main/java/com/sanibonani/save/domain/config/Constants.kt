package com.sanibonani.save.domain.config

/**
 * Central constants for the SanibonaniSave platform: identity/bank validation shapes
 * and upload limits.
 */

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

    // SA mobile prefixes only: 060-069, 071-074, 076-079 and 081-084, national or +27 form.
    // 086/087 are service numbers and 01-05 are landlines; neither can receive an OTP.
    const val SA_MOBILE_REGEX = "^(?:\\+27|0)(?:6[0-9]|7[1-4]|7[6-9]|8[1-4])[0-9]{7}$"

    const val PASSWORD_MIN_LENGTH = 10

    const val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
}

// ── Bank Account Validation ────────────────────────────────────────────────
object BankAccountValidation {
    // Standard SA bank account number lengths (aligned with DB constraints)
    val ACCOUNT_NUMBER_LENGTHS = mapOf(
        "ABSA"          to (7..13),
        "FNB"           to (7..13),
        "Nedbank"       to (7..13),
        "Capitec"       to (7..13),
        "African Bank"  to (7..13),
        "Standard Bank" to (7..13),
        "Postbank"      to (7..13),
        "TymeBank"      to (7..13),
        "Discovery"     to (7..13),
        "Bank Zero"     to (7..13)
    )
    val DEFAULT_ACCOUNT_LENGTH = 7..13
    
    // Branch code is exactly 6 digits in SA
    const val BRANCH_CODE_LENGTH = 6
    const val BRANCH_CODE_REGEX = "^[0-9]{6}$"
}

// ── File Upload Limits ─────────────────────────────────────────────────────
object FileUploadLimits {
    const val MAX_FILE_SIZE_BYTES = 3 * 1024 * 1024 // 3MB
}
