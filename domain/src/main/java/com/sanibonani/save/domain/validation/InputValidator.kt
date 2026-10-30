package com.sanibonani.save.domain.validation

import com.sanibonani.save.domain.config.BankAccountValidation
import com.sanibonani.save.domain.config.MemberValidation
import com.sanibonani.save.domain.utils.parseMoneyAmountOrNull
import java.util.Calendar

/**
 * Input validation utilities for member data, group settings, and payment info.
 * Centralizes all validation logic to prevent garbage data in Supabase.
 */

object InputValidator {
    
    // ── Member Name Validation ─────────────────────────────────────────────
    fun isValidName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.length < MemberValidation.NAME_MIN_LENGTH) return false
        if (name.length > MemberValidation.NAME_MAX_LENGTH) return false
        // Allow letters, spaces, hyphens, and apostrophes
        return name.matches(Regex("^[a-zA-Z\\s'-]+$"))
    }
    
    fun validateName(name: String): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult.Error("Name is required")
            name.length < MemberValidation.NAME_MIN_LENGTH -> 
                ValidationResult.Error("Name must be at least ${MemberValidation.NAME_MIN_LENGTH} characters")
            name.length > MemberValidation.NAME_MAX_LENGTH -> 
                ValidationResult.Error("Name must not exceed ${MemberValidation.NAME_MAX_LENGTH} characters")
            !isValidName(name) -> 
                ValidationResult.Error("Name contains invalid characters (use letters, spaces, hyphens, apostrophes only)")
            else -> ValidationResult.Valid
        }
    }

    // ── SA ID Number Validation ────────────────────────────────────────────
    /**
     * The single SA ID rule for the whole platform: 13 digits, a real date of birth in the
     * first six, and a correct Luhn check digit. Registration, group creation and KYC must
     * all agree, otherwise a member can be persisted here and rejected further down the line.
     */
    fun isValidSAIdNumber(idNumber: String): Boolean {
        val cleaned = idNumber.trim()
        if (cleaned.length != MemberValidation.SA_ID_NUMBER_LENGTH) return false
        if (!cleaned.matches(Regex(MemberValidation.SA_ID_REGEX))) return false

        val month = cleaned.substring(2, 4).toIntOrNull() ?: return false
        val day = cleaned.substring(4, 6).toIntOrNull() ?: return false
        if (month !in 1..12 || day !in 1..31) return false

        // Verify Luhn checksum (13th digit)
        return verifySAIdChecksum(cleaned)
    }
    
    private fun verifySAIdChecksum(idNumber: String): Boolean {
        var sum = 0
        var isOdd = true
        
        // Process first 12 digits, alternate multiply by 2
        for (i in 11 downTo 0) {
            var digit = idNumber[i].toString().toInt()
            if (isOdd) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            isOdd = !isOdd
        }
        
        // Calculate check digit
        val checkDigit = (10 - (sum % 10)) % 10
        return checkDigit == idNumber[12].toString().toInt()
    }
    
    fun validateSAIdNumber(idNumber: String): ValidationResult {
        val cleaned = idNumber.trim()
        return when {
            cleaned.isBlank() -> ValidationResult.Error("ID number is required")
            cleaned.length != MemberValidation.SA_ID_NUMBER_LENGTH -> 
                ValidationResult.Error("SA ID must be exactly ${MemberValidation.SA_ID_NUMBER_LENGTH} digits")
            !cleaned.matches(Regex(MemberValidation.SA_ID_REGEX)) -> 
                ValidationResult.Error("SA ID must contain only digits")
            !isValidDateOfBirthSegment(cleaned) ->
                ValidationResult.Error("SA ID must start with a valid date of birth (YYMMDD)")
            !verifySAIdChecksum(cleaned) -> 
                ValidationResult.Error("Invalid SA ID number (checksum verification failed)")
            else -> ValidationResult.Valid
        }
    }

    private fun isValidDateOfBirthSegment(idNumber: String): Boolean {
        val month = idNumber.substring(2, 4).toIntOrNull() ?: return false
        val day = idNumber.substring(4, 6).toIntOrNull() ?: return false
        return month in 1..12 && day in 1..31
    }

    // ── Phone Number Validation ────────────────────────────────────────────
    // Members are contacted by OTP and WhatsApp, so only SA mobile ranges are accepted.
    // Landlines and 086/087 service numbers are rejected at every capture point.
    fun normalizePhone(phone: String): String =
        phone.trim().replace(Regex("[\\s()-]"), "")

    fun isValidPhone(phone: String): Boolean {
        val cleaned = normalizePhone(phone)
        if (cleaned.isBlank()) return false
        return cleaned.matches(Regex(MemberValidation.SA_MOBILE_REGEX))
    }
    
    fun validatePhone(phone: String): ValidationResult {
        return when {
            phone.isBlank() -> ValidationResult.Error("Phone number is required")
            !isValidPhone(phone) ->
                ValidationResult.Error("Enter a South African mobile number (e.g. 0821234567 or +27821234567)")
            else -> ValidationResult.Valid
        }
    }

    // ── Password Validation ────────────────────────────────────────────────
    fun isValidPassword(password: String): Boolean {
        if (password.length < MemberValidation.PASSWORD_MIN_LENGTH) return false
        if (!password.any { it.isUpperCase() }) return false
        if (!password.any { it.isLowerCase() }) return false
        if (!password.any { it.isDigit() }) return false
        if (!password.any { !it.isLetterOrDigit() }) return false
        return true
    }

    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult.Error("Password is required")
            password.length < MemberValidation.PASSWORD_MIN_LENGTH ->
                ValidationResult.Error("Password must be at least ${MemberValidation.PASSWORD_MIN_LENGTH} characters")
            !password.any { it.isUpperCase() } ->
                ValidationResult.Error("Password must contain an uppercase letter")
            !password.any { it.isLowerCase() } ->
                ValidationResult.Error("Password must contain a lowercase letter")
            !password.any { it.isDigit() } ->
                ValidationResult.Error("Password must contain a number")
            !password.any { !it.isLetterOrDigit() } ->
                ValidationResult.Error("Password must contain a special character")
            else -> ValidationResult.Valid
        }
    }

    // ── Email Validation ───────────────────────────────────────────────────
    fun isValidEmail(email: String): Boolean {
        if (email.isBlank()) return false
        return email.matches(Regex(MemberValidation.EMAIL_REGEX))
    }
    
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult.Error("Email is required")
            !isValidEmail(email) -> ValidationResult.Error("Invalid email format")
            else -> ValidationResult.Valid
        }
    }

    // ── Bank Account Validation ────────────────────────────────────────────
    fun isValidAccountNumber(accountNumber: String, bankName: String): Boolean {
        if (accountNumber.isBlank()) return false
        val allowedLength = BankAccountValidation.ACCOUNT_NUMBER_LENGTHS[bankName]
            ?: BankAccountValidation.DEFAULT_ACCOUNT_LENGTH
        return accountNumber.length in allowedLength
    }
    
    fun validateAccountNumber(accountNumber: String, bankName: String): ValidationResult {
        return when {
            accountNumber.isBlank() -> ValidationResult.Error("Account number is required")
            !accountNumber.matches(Regex("^[0-9]+$")) -> 
                ValidationResult.Error("Account number must contain only digits")
            !isValidAccountNumber(accountNumber, bankName) -> {
                val allowedLength = BankAccountValidation.ACCOUNT_NUMBER_LENGTHS[bankName] 
                    ?: BankAccountValidation.DEFAULT_ACCOUNT_LENGTH
                ValidationResult.Error("$bankName account numbers must be ${allowedLength.first}-${allowedLength.last} digits")
            }
            else -> ValidationResult.Valid
        }
    }

    // ── Branch Code Validation ─────────────────────────────────────────────
    // A blank branch code is not accepted anywhere payouts are addressed, so it must not
    // validate here either.
    fun isValidBranchCode(branchCode: String): Boolean {
        if (branchCode.isBlank()) return false
        return branchCode.length == BankAccountValidation.BRANCH_CODE_LENGTH &&
                branchCode.matches(Regex(BankAccountValidation.BRANCH_CODE_REGEX))
    }
    
    fun validateBranchCode(branchCode: String): ValidationResult {
        return when {
            branchCode.isBlank() -> ValidationResult.Error("Branch code is required")
            !branchCode.matches(Regex("^[0-9]+$")) -> 
                ValidationResult.Error("Branch code must contain only digits")
            branchCode.length != BankAccountValidation.BRANCH_CODE_LENGTH -> 
                ValidationResult.Error("Branch code must be exactly ${BankAccountValidation.BRANCH_CODE_LENGTH} digits")
            else -> ValidationResult.Valid
        }
    }

    // ── Group Name Validation ──────────────────────────────────────────────
    fun validateGroupName(name: String): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult.Error("Group name is required")
            name.length < 3 -> ValidationResult.Error("Group name must be at least 3 characters")
            name.length > 100 -> ValidationResult.Error("Group name must not exceed 100 characters")
            else -> ValidationResult.Valid
        }
    }

    // ── Monetary Amount Validation ─────────────────────────────────────────
    fun validateMonetaryAmount(amount: String, fieldName: String = "Amount"): ValidationResult {
        val parsed = amount.parseMoneyAmountOrNull()
        return when {
            amount.isBlank() -> ValidationResult.Error("$fieldName is required")
            parsed == null -> ValidationResult.Error("$fieldName must be a valid currency amount")
            parsed <= java.math.BigDecimal.ZERO ->
                ValidationResult.Error("$fieldName must be greater than 0")
            else -> ValidationResult.Valid
        }
    }

    /**
     * The rule for published prices, where zero is a decision rather than a mistake: a platform fee
     * can be waived, but a blank or mistyped field is not a waiver.
     */
    fun validateNonNegativeAmount(amount: String, fieldName: String = "Amount"): ValidationResult {
        val parsed = amount.parseMoneyAmountOrNull()
        return when {
            amount.isBlank() -> ValidationResult.Error("$fieldName is required")
            parsed == null -> ValidationResult.Error("$fieldName must be a valid currency amount")
            else -> ValidationResult.Valid
        }
    }

    // ── Card Payment Validation ────────────────────────────────────────────
    fun validateCardNumber(cardNumber: String): ValidationResult {
        val cleaned = cardNumber.filter { it.isDigit() }
        return when {
            cleaned.isBlank() -> ValidationResult.Error("Card number is required")
            cleaned.length < 13 || cleaned.length > 19 -> ValidationResult.Error("Invalid card number (13-19 digits)")
            else -> ValidationResult.Valid
        }
    }

    fun validateCardExpiry(expiry: String): ValidationResult {
        val digits = expiry.filter { it.isDigit() }
        if (digits.length < 4) {
            return ValidationResult.Error("Invalid expiry date (MM/YY)")
        }

        val month = digits.substring(0, 2).toIntOrNull() ?: 0
        if (month !in 1..12) {
            return ValidationResult.Error("Invalid month (01-12)")
        }

        val year = digits.substring(2, 4).toIntOrNull() ?: 0

        val calendar = Calendar.getInstance()
        val currentYearShort = calendar.get(Calendar.YEAR) % 100
        // Calendar months are 0-based
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        if (year < currentYearShort || (year == currentYearShort && month < currentMonth)) {
            return ValidationResult.Error("Card has expired")
        }

        return ValidationResult.Valid
    }

    fun validateCardCVV(cvv: String): ValidationResult {
        val cleaned = cvv.filter { it.isDigit() }
        return when {
            cleaned.isBlank() -> ValidationResult.Error("CVV is required")
            cleaned.length !in 3..4 -> ValidationResult.Error("Invalid CVV (3-4 digits)")
            else -> ValidationResult.Valid
        }
    }

    fun validatePaymentFields(cardNumber: String, expiry: String, cvv: String): ValidationResult {
        val cardValidation = validateCardNumber(cardNumber)
        if (cardValidation !is ValidationResult.Valid) return cardValidation

        val expiryValidation = validateCardExpiry(expiry)
        if (expiryValidation !is ValidationResult.Valid) return expiryValidation

        return validateCardCVV(cvv)
    }
}

