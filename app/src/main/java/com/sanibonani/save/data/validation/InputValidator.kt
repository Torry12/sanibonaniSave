package com.sanibonani.save.data.validation

import com.sanibonani.save.data.BankAccountValidation
import com.sanibonani.save.data.MemberValidation

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
    fun isValidSAIdNumber(idNumber: String): Boolean {
        if (idNumber.length != MemberValidation.SA_ID_NUMBER_LENGTH) return false
        if (!idNumber.matches(Regex(MemberValidation.SA_ID_REGEX))) return false
        
        // Verify Luhn checksum (13th digit)
        return verifySAIdChecksum(idNumber)
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
        return when {
            idNumber.isBlank() -> ValidationResult.Error("ID number is required")
            idNumber.length != MemberValidation.SA_ID_NUMBER_LENGTH -> 
                ValidationResult.Error("SA ID must be exactly ${MemberValidation.SA_ID_NUMBER_LENGTH} digits")
            !idNumber.matches(Regex(MemberValidation.SA_ID_REGEX)) -> 
                ValidationResult.Error("SA ID must contain only digits")
            !verifySAIdChecksum(idNumber) -> 
                ValidationResult.Error("Invalid SA ID number (checksum verification failed)")
            else -> ValidationResult.Valid
        }
    }

    // ── Phone Number Validation ────────────────────────────────────────────
    fun isValidPhone(phone: String): Boolean {
        if (phone.isBlank()) return false
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        return cleaned.length in MemberValidation.PHONE_MIN_LENGTH..MemberValidation.PHONE_MAX_LENGTH
    }
    
    fun validatePhone(phone: String): ValidationResult {
        return when {
            phone.isBlank() -> ValidationResult.Error("Phone number is required")
            !isValidPhone(phone) -> 
                ValidationResult.Error("Phone must be ${MemberValidation.PHONE_MIN_LENGTH}-${MemberValidation.PHONE_MAX_LENGTH} digits (format: +27XXXXXXXXX or 0XXXXXXXXX)")
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
        return accountNumber.length in (allowedLength ?: (8..20))
    }
    
    fun validateAccountNumber(accountNumber: String, bankName: String): ValidationResult {
        return when {
            accountNumber.isBlank() -> ValidationResult.Error("Account number is required")
            !accountNumber.matches(Regex("^[0-9]+$")) -> 
                ValidationResult.Error("Account number must contain only digits")
            !isValidAccountNumber(accountNumber, bankName) -> {
                val allowedLength = BankAccountValidation.ACCOUNT_NUMBER_LENGTHS[bankName] ?: (8..20)
                ValidationResult.Error("$bankName account numbers must be ${allowedLength.first}-${allowedLength.last} digits")
            }
            else -> ValidationResult.Valid
        }
    }

    // ── Branch Code Validation ─────────────────────────────────────────────
    fun isValidBranchCode(branchCode: String): Boolean {
        if (branchCode.isBlank()) return true  // Optional
        return branchCode.length == BankAccountValidation.BRANCH_CODE_LENGTH &&
                branchCode.matches(Regex(BankAccountValidation.BRANCH_CODE_REGEX))
    }
    
    fun validateBranchCode(branchCode: String): ValidationResult {
        return when {
            branchCode.isBlank() -> ValidationResult.Valid  // Optional field
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
        return when {
            amount.isBlank() -> ValidationResult.Error("$fieldName is required")
            !amount.matches(Regex("^[0-9]+(\\.[0-9]{1,2})?$")) -> 
                ValidationResult.Error("$fieldName must be a valid currency amount")
            amount.toDoubleOrNull()?.let { it <= 0 } == true -> 
                ValidationResult.Error("$fieldName must be greater than 0")
            else -> ValidationResult.Valid
        }
    }
}

