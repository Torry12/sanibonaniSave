package com.sanibonani.save.domain.validation

import com.sanibonani.save.domain.model.Money
import java.util.Calendar

/**
 * Centralized validation logic to avoid redundant checks across screens/ViewModels.
 *
 * Identity, banking and email rules live in [InputValidator]; this object is the
 * convenience surface callers use and must not restate a rule with its own regex.
 */
object ValidationUtils {

    // ──────────────────────────────────────────────────────────────────────────
    // EMAIL VALIDATION
    // ──────────────────────────────────────────────────────────────────────────
    fun isValidEmail(email: String): Boolean = InputValidator.isValidEmail(email)

    // ──────────────────────────────────────────────────────────────────────────
    // PASSWORD VALIDATION
    // ──────────────────────────────────────────────────────────────────────────
    fun isValidPassword(password: String): Boolean = InputValidator.isValidPassword(password)

    // ──────────────────────────────────────────────────────────────────────────
    // MEMBER VALIDATION
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Validates South African ID number: 13 digits, date of birth and Luhn checksum.
     * Delegates to [InputValidator] so registration and KYC can never disagree.
     */
    fun isValidSAIdNumber(idNumber: String): Boolean = InputValidator.isValidSAIdNumber(idNumber)

    fun isValidPhoneNumber(phoneNumber: String): Boolean = InputValidator.isValidPhone(phoneNumber)

    fun isValidBankAccount(accountNumber: String): Boolean =
        InputValidator.validateAccountNumber(accountNumber, "").isValid()

    fun isValidBranchCode(branchCode: String): Boolean = InputValidator.isValidBranchCode(branchCode)

    fun isValidName(name: String): Boolean = InputValidator.isValidName(name)

    fun isValidAmount(amount: Money): Boolean = amount.isPositive

    fun isValidBankingDetails(accountNumber: String, branchCode: String): Boolean {
        return isValidBankAccount(accountNumber) && isValidBranchCode(branchCode)
    }

    fun isValidPersonalDetails(
        firstName: String,
        lastName: String,
        idNumber: String,
        phoneNumber: String
    ): Boolean {
        return isValidName(firstName) &&
            isValidName(lastName) &&
            isValidSAIdNumber(idNumber) &&
            isValidPhoneNumber(phoneNumber)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PAYMENT VALIDATION
    // ──────────────────────────────────────────────────────────────────────────
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
        val currentMonth = calendar.get(Calendar.MONTH) + 1 // Calendar months are 0-based
        
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

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Error(val message: String) : ValidationResult()

    fun isValid(): Boolean = this is Valid
    fun getErrorMessage(): String? = (this as? Error)?.message
}
