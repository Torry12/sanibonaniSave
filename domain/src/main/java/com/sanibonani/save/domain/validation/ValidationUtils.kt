package com.sanibonani.save.domain.validation

import com.sanibonani.save.domain.model.Money

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
    fun validateCardNumber(cardNumber: String): ValidationResult =
        InputValidator.validateCardNumber(cardNumber)

    fun validateCardExpiry(expiry: String): ValidationResult =
        InputValidator.validateCardExpiry(expiry)

    fun validateCardCVV(cvv: String): ValidationResult =
        InputValidator.validateCardCVV(cvv)

    fun validatePaymentFields(cardNumber: String, expiry: String, cvv: String): ValidationResult =
        InputValidator.validatePaymentFields(cardNumber, expiry, cvv)
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Error(val message: String) : ValidationResult()

    fun isValid(): Boolean = this is Valid
    fun getErrorMessage(): String? = (this as? Error)?.message
}
