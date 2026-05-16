package com.sanibonani.save.domain.validation

import com.sanibonani.save.domain.utils.isPositiveMoneyAmount
import com.sanibonani.save.domain.utils.parseMoneyAmountOrNull
import java.math.BigDecimal
import java.util.Calendar

/**
 * Centralized validation logic to avoid redundant checks across screens/ViewModels
 */
object ValidationUtils {

    private val strictSaPhoneRegex = "^0(6|7)[0-9]{8}$".toRegex()
    // SA bank account numbers vary by bank; accept the common 7–13 digit range.
    private val bankAccountRegex = "^[0-9]{7,13}$".toRegex()
    private val branchCodeRegex = "^[0-9]{6}$".toRegex()
    private val minimumMonthlyContribution = BigDecimal("10.00")

    // ──────────────────────────────────────────────────────────────────────────
    // EMAIL VALIDATION
    // ──────────────────────────────────────────────────────────────────────────
    fun isValidEmail(email: String): Boolean {
        if (email.isBlank()) return false
        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return email.matches(emailRegex)
    }

    fun validateEmailField(email: String, isOptional: Boolean = false): ValidationResult {
        if (isOptional && email.isBlank()) return ValidationResult.Valid
        return when {
            email.isBlank() -> ValidationResult.Error("Email is required")
            !isValidEmail(email) -> ValidationResult.Error("Invalid email format")
            else -> ValidationResult.Valid
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PASSWORD VALIDATION
    // ──────────────────────────────────────────────────────────────────────────
    fun isValidPassword(password: String): Boolean {
        if (password.isBlank()) return false
        if (password.length < 10) return false
        if (!password.any { it.isUpperCase() }) return false
        if (!password.any { it.isLowerCase() }) return false
        if (!password.any { it.isDigit() }) return false
        if (!password.any { !it.isLetterOrDigit() }) return false
        return true
    }

    fun validatePasswordField(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult.Error("Password is required")
            password.length < 10 -> ValidationResult.Error("Password must be at least 10 characters")
            !password.any { it.isUpperCase() } -> ValidationResult.Error("Password must include at least one uppercase letter")
            !password.any { it.isLowerCase() } -> ValidationResult.Error("Password must include at least one lowercase letter")
            !password.any { it.isDigit() } -> ValidationResult.Error("Password must include at least one number")
            !password.any { !it.isLetterOrDigit() } -> ValidationResult.Error("Password must include at least one special character")
            else -> ValidationResult.Valid
        }
    }

    fun validatePasswordMatch(password: String, confirmPassword: String): ValidationResult {
        return when {
            password != confirmPassword -> ValidationResult.Error("Passwords do not match")
            else -> ValidationResult.Valid
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MEMBER VALIDATION
    // ──────────────────────────────────────────────────────────────────────────
    fun validateMemberFields(
        fullName: String,
        idNumber: String,
        phone: String,
        email: String,
        street: String,
        suburb: String,
        city: String,
        province: String
    ): ValidationResult {
        return when {
            fullName.isBlank() -> ValidationResult.Error("Full name is required")
            fullName.trim().length < 2 -> ValidationResult.Error("Full name must be at least 2 characters")
            idNumber.isBlank() -> ValidationResult.Error("ID number is required")
            !isValidSAID(idNumber) -> ValidationResult.Error("Invalid South African ID number")
            phone.isBlank() -> ValidationResult.Error("Phone number is required")
            !phone.matches("^0[1-9][0-9]{8}$".toRegex()) -> ValidationResult.Error("Invalid phone number format (e.g. 0712345678)")
            email.isNotBlank() && !isValidEmail(email) -> ValidationResult.Error("Invalid email format")
            street.isBlank() -> ValidationResult.Error("Street address is required")
            suburb.isBlank() -> ValidationResult.Error("Suburb is required")
            city.isBlank() -> ValidationResult.Error("City is required")
            province.isBlank() -> ValidationResult.Error("Province is required")
            else -> ValidationResult.Valid
        }
    }

    /**
     * Validates South African ID number using Luhn Algorithm
     */
    fun isValidSAID(idNumber: String): Boolean {
        val cleanId = idNumber.trim()
        if (cleanId.length != 13 || !cleanId.all { it.isDigit() }) return false

        // Basic date validation (YYMMDD)
        val month = cleanId.substring(2, 4).toInt()
        val day = cleanId.substring(4, 6).toInt()
        if (month !in 1..12 || day !in 1..31) return false

        // Luhn Algorithm
        var sum = 0
        for (i in 0 until 12) {
            var digit = cleanId[i] - '0'
            if (i % 2 == 1) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        val checkDigit = (10 - (sum % 10)) % 10
        return checkDigit == (cleanId[12] - '0')
    }

    // Compatibility helpers used by legacy and test validation callsites.
    fun isValidSAIdNumber(idNumber: String): Boolean = isValidSAID(idNumber)

    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleaned = phoneNumber.trim()
        return cleaned.matches(strictSaPhoneRegex)
    }

    fun isValidBankAccount(accountNumber: String): Boolean = accountNumber.matches(bankAccountRegex)

    fun isValidBranchCode(branchCode: String): Boolean = branchCode.matches(branchCodeRegex)

    fun normalizePhoneNumber(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return when {
            digitsOnly.startsWith("27") && digitsOnly.length == 11 -> digitsOnly
            digitsOnly.startsWith("0") && digitsOnly.length == 10 -> "27" + digitsOnly.substring(1)
            digitsOnly.length == 9 -> "27" + digitsOnly
            else -> digitsOnly
        }
    }

    fun isValidName(name: String): Boolean = InputValidator.isValidName(name)

    fun isValidAmount(amount: Double): Boolean = amount.isPositiveMoneyAmount()

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
    // GROUP VALIDATION
    // ──────────────────────────────────────────────────────────────────────────
    fun validateGroupStep1(name: String, email: String, phone: String): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult.Error("Group name is required")
            name.length < 3 -> ValidationResult.Error("Group name must be at least 3 characters")
            email.isBlank() -> ValidationResult.Error("Group email is required")
            !isValidEmail(email) -> ValidationResult.Error("Invalid email format")
            phone.isBlank() -> ValidationResult.Error("WhatsApp number is required")
            !phone.matches("^0[1-9][0-9]{8}$".toRegex()) -> ValidationResult.Error("Invalid phone number format")
            else -> ValidationResult.Valid
        }
    }

    fun validateGroupStep2(province: String, city: String): ValidationResult {
        return when {
            province.isBlank() -> ValidationResult.Error("Province is required")
            city.isBlank() -> ValidationResult.Error("City / Town is required")
            else -> ValidationResult.Valid
        }
    }

    fun validateGroupStep3(joining: String, contribution: String, maxMembers: String): ValidationResult {
        val joiningFee = joining.parseMoneyAmountOrNull()
        val monthlyContrib = contribution.parseMoneyAmountOrNull()
        val maxMem = maxMembers.toIntOrNull()

        return when {
            joiningFee == null -> ValidationResult.Error("Joining fee must be a valid amount")
            monthlyContrib == null || monthlyContrib < minimumMonthlyContribution -> ValidationResult.Error("Monthly contribution must be at least R10")
            maxMem == null || maxMem < 2 -> ValidationResult.Error("Maximum members must be at least 2")
            else -> ValidationResult.Valid
        }
    }

    fun validateGroupStep4(bankName: String, accountNumber: String, branchCode: String): ValidationResult {
        return when {
            bankName.isBlank() -> ValidationResult.Error("Bank name is required")
            accountNumber.length !in 7..11 || !accountNumber.all { it.isDigit() } -> 
                ValidationResult.Error("Account number must be 7–11 digits (SA PASA standard)")
            branchCode.length != 6 || !branchCode.all { it.isDigit() } -> 
                ValidationResult.Error("Branch code must be 6 digits")
            else -> ValidationResult.Valid
        }
    }

    fun validateGroupStep6(fullName: String, email: String, password: String, isLoggedIn: Boolean, idNumber: String = ""): ValidationResult {
        return when {
            fullName.isBlank() -> ValidationResult.Error("Admin name is required")
            fullName.trim().length < 3 -> ValidationResult.Error("Admin name must be at least 3 characters")
            idNumber.isBlank() -> ValidationResult.Error("SA ID Number is required")
            !isValidSAID(idNumber) -> ValidationResult.Error("Invalid SA ID Number. Please enter a valid 13-digit ID.")
            !isLoggedIn && email.isBlank() -> ValidationResult.Error("Admin email is required")
            !isLoggedIn && !isValidEmail(email) -> ValidationResult.Error("Invalid email format")
            !isLoggedIn && password.length < 10 -> ValidationResult.Error("Password must be at least 10 characters")
            else -> ValidationResult.Valid
        }
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
