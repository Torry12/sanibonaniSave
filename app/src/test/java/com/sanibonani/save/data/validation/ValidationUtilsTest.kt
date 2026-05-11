package com.sanibonani.save.data.validation

import com.sanibonani.save.domain.validation.ValidationUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive tests for validation utilities.
 * Tests all input validators: ID, phone, email, bank account, branch code, etc.
 */
class ValidationUtilsTest {

    // ══════════════════════════════════════════════════════════════════════════
    // ID NUMBER VALIDATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `validateSAIdNumber - valid ID returns true`() {
        val validId = "8001015800089" // 13-digit valid format
        assertTrue("Valid SA ID should pass", ValidationUtils.isValidSAIdNumber(validId))
    }

    @Test
    fun `validateSAIdNumber - too short ID returns false`() {
        val shortId = "800101580008" // 12 digits
        assertFalse("ID with 12 digits should fail", ValidationUtils.isValidSAIdNumber(shortId))
    }

    @Test
    fun `validateSAIdNumber - too long ID returns false`() {
        val longId = "80010158000812" // 14 digits
        assertFalse("ID with 14 digits should fail", ValidationUtils.isValidSAIdNumber(longId))
    }

    @Test
    fun `validateSAIdNumber - non-numeric ID returns false`() {
        val nonNumericId = "800101580008A"
        assertFalse("Non-numeric ID should fail", ValidationUtils.isValidSAIdNumber(nonNumericId))
    }

    @Test
    fun `validateSAIdNumber - empty ID returns false`() {
        assertFalse("Empty ID should fail", ValidationUtils.isValidSAIdNumber(""))
    }

    @Test
    fun `validateSAIdNumber - spaces in ID returns false`() {
        val spacedId = "8001 0158 0008 1"
        assertFalse("ID with spaces should fail after cleaning",
            ValidationUtils.isValidSAIdNumber(spacedId.replace(" ", "")))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHONE NUMBER VALIDATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `validatePhoneNumber - valid SA phone returns true`() {
        val validPhone = "0715555555" // Starts with 07, 10 digits
        assertTrue("Valid SA phone should pass", ValidationUtils.isValidPhoneNumber(validPhone))
    }

    @Test
    fun `validatePhoneNumber - valid 06x number returns true`() {
        val validPhone = "0615555555" // Starts with 06
        assertTrue("Valid 06x phone should pass", ValidationUtils.isValidPhoneNumber(validPhone))
    }

    @Test
    fun `validatePhoneNumber - too short phone returns false`() {
        val shortPhone = "071555555" // 9 digits
        assertFalse("9-digit phone should fail", ValidationUtils.isValidPhoneNumber(shortPhone))
    }

    @Test
    fun `validatePhoneNumber - too long phone returns false`() {
        val longPhone = "07155555555" // 11 digits
        assertFalse("11-digit phone should fail", ValidationUtils.isValidPhoneNumber(longPhone))
    }

    @Test
    fun `validatePhoneNumber - non-07-or-06 prefix fails`() {
        val invalidPrefix = "0825555555" // Starts with 08
        assertFalse("08xx prefix should fail", ValidationUtils.isValidPhoneNumber(invalidPrefix))
    }

    @Test
    fun `validatePhoneNumber - non-numeric fails`() {
        val nonNumeric = "071AAA5555"
        assertFalse("Non-numeric phone should fail", ValidationUtils.isValidPhoneNumber(nonNumeric))
    }

    @Test
    fun `validatePhoneNumber - empty phone returns false`() {
        assertFalse("Empty phone should fail", ValidationUtils.isValidPhoneNumber(""))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EMAIL VALIDATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `validateEmail - valid email returns true`() {
        val validEmail = "user@example.com"
        assertTrue("Valid email should pass", ValidationUtils.isValidEmail(validEmail))
    }

    @Test
    fun `validateEmail - email with plus returns true`() {
        val emailWithPlus = "user+tag@example.com"
        assertTrue("Email with plus sign should pass", ValidationUtils.isValidEmail(emailWithPlus))
    }

    @Test
    fun `validateEmail - email with numbers returns true`() {
        val emailWithNumbers = "user123@example456.co.za"
        assertTrue("Email with numbers should pass", ValidationUtils.isValidEmail(emailWithNumbers))
    }

    @Test
    fun `validateEmail - no @ symbol fails`() {
        val noAt = "userexample.com"
        assertFalse("Email without @ should fail", ValidationUtils.isValidEmail(noAt))
    }

    @Test
    fun `validateEmail - no domain fails`() {
        val noDomain = "user@"
        assertFalse("Email without domain should fail", ValidationUtils.isValidEmail(noDomain))
    }

    @Test
    fun `validateEmail - no local part fails`() {
        val noLocal = "@example.com"
        assertFalse("Email without local part should fail", ValidationUtils.isValidEmail(noLocal))
    }

    @Test
    fun `validateEmail - space in email fails`() {
        val withSpace = "user @example.com"
        assertFalse("Email with space should fail", ValidationUtils.isValidEmail(withSpace))
    }

    @Test
    fun `validateEmail - empty email returns false`() {
        assertFalse("Empty email should fail", ValidationUtils.isValidEmail(""))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BANK ACCOUNT NUMBER VALIDATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `validateBankAccount - 10-digit account returns true`() {
        val account = "1234567890"
        assertTrue("10-digit account should pass", ValidationUtils.isValidBankAccount(account))
    }

    @Test
    fun `validateBankAccount - 13-digit account returns true`() {
        val account = "1234567890123"
        assertTrue("13-digit account should pass", ValidationUtils.isValidBankAccount(account))
    }

    @Test
    fun `validateBankAccount - 7-digit account returns true`() {
        val account = "1234567"
        assertTrue("7-digit account should pass", ValidationUtils.isValidBankAccount(account))
    }

    @Test
    fun `validateBankAccount - 6-digit account returns false`() {
        val account = "123456"
        assertFalse("6-digit account should fail", ValidationUtils.isValidBankAccount(account))
    }

    @Test
    fun `validateBankAccount - 14-digit account returns false`() {
        val account = "12345678901234"
        assertFalse("14-digit account should fail", ValidationUtils.isValidBankAccount(account))
    }

    @Test
    fun `validateBankAccount - non-numeric fails`() {
        val account = "123456789A"
        assertFalse("Non-numeric account should fail", ValidationUtils.isValidBankAccount(account))
    }

    @Test
    fun `validateBankAccount - empty account returns false`() {
        assertFalse("Empty account should fail", ValidationUtils.isValidBankAccount(""))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BRANCH CODE VALIDATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `validateBranchCode - 6-digit code returns true`() {
        val code = "123456"
        assertTrue("6-digit branch code should pass", ValidationUtils.isValidBranchCode(code))
    }

    @Test
    fun `validateBranchCode - 5-digit code returns false`() {
        val code = "12345"
        assertFalse("5-digit branch code should fail", ValidationUtils.isValidBranchCode(code))
    }

    @Test
    fun `validateBranchCode - 7-digit code returns false`() {
        val code = "1234567"
        assertFalse("7-digit branch code should fail", ValidationUtils.isValidBranchCode(code))
    }

    @Test
    fun `validateBranchCode - non-numeric fails`() {
        val code = "12345A"
        assertFalse("Non-numeric branch code should fail", ValidationUtils.isValidBranchCode(code))
    }

    @Test
    fun `validateBranchCode - empty code returns false`() {
        assertFalse("Empty branch code should fail", ValidationUtils.isValidBranchCode(""))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NAME FIELD VALIDATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `validateName - valid name returns true`() {
        val name = "John Doe"
        assertTrue("Valid name should pass", ValidationUtils.isValidName(name))
    }

    @Test
    fun `validateName - single name returns true`() {
        val name = "Madonna"
        assertTrue("Single name should pass", ValidationUtils.isValidName(name))
    }

    @Test
    fun `validateName - hyphenated name returns true`() {
        val name = "Mary-Jane"
        assertTrue("Hyphenated name should pass", ValidationUtils.isValidName(name))
    }

    @Test
    fun `validateName - apostrophe name returns true`() {
        val name = "O'Brien"
        assertTrue("Name with apostrophe should pass", ValidationUtils.isValidName(name))
    }

    @Test
    fun `validateName - too short returns false`() {
        val name = "A"
        assertFalse("Single character name should fail", ValidationUtils.isValidName(name))
    }

    @Test
    fun `validateName - with numbers fails`() {
        val name = "John123"
        assertFalse("Name with numbers should fail", ValidationUtils.isValidName(name))
    }

    @Test
    fun `validateName - with special chars fails`() {
        val name = "John@Doe"
        assertFalse("Name with @ should fail", ValidationUtils.isValidName(name))
    }

    @Test
    fun `validateName - empty returns false`() {
        assertFalse("Empty name should fail", ValidationUtils.isValidName(""))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PASSWORD VALIDATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `validatePassword - strong password returns true`() {
        val password = "SecurePass123!"
        assertTrue("Strong password should pass", ValidationUtils.isValidPassword(password))
    }

    @Test
    fun `validatePassword - too short returns false`() {
        val password = "Pass123!"
        assertFalse("Password < 10 chars should fail", ValidationUtils.isValidPassword(password))
    }

    @Test
    fun `validatePassword - no uppercase fails`() {
        val password = "securepass123!"
        assertFalse("No uppercase should fail", ValidationUtils.isValidPassword(password))
    }

    @Test
    fun `validatePassword - no lowercase fails`() {
        val password = "SECUREPASS123!"
        assertFalse("No lowercase should fail", ValidationUtils.isValidPassword(password))
    }

    @Test
    fun `validatePassword - no number fails`() {
        val password = "SecurePass!"
        assertFalse("No number should fail", ValidationUtils.isValidPassword(password))
    }

    @Test
    fun `validatePassword - no special char fails`() {
        val password = "SecurePass123"
        assertFalse("No special char should fail", ValidationUtils.isValidPassword(password))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AMOUNT VALIDATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `validateAmount - positive amount returns true`() {
        assertTrue("Positive amount should pass", ValidationUtils.isValidAmount(100.0))
    }

    @Test
    fun `validateAmount - zero amount fails`() {
        assertFalse("Zero amount should fail", ValidationUtils.isValidAmount(0.0))
    }

    @Test
    fun `validateAmount - negative amount fails`() {
        assertFalse("Negative amount should fail", ValidationUtils.isValidAmount(-100.0))
    }

    @Test
    fun `validateAmount - fractional amount returns true`() {
        assertTrue("Fractional amount should pass", ValidationUtils.isValidAmount(150.99))
    }

    @Test
    fun `validateAmount - very large amount returns true`() {
        assertTrue("Large amount should pass", ValidationUtils.isValidAmount(9999999.99))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMPOSITE VALIDATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `validateBankingDetails - all valid fields returns true`() {
        val isValid = ValidationUtils.isValidBankingDetails(
            accountNumber = "1234567890",
            branchCode = "123456"
        )
        assertTrue("Valid banking details should pass", isValid)
    }

    @Test
    fun `validateBankingDetails - invalid account returns false`() {
        val isValid = ValidationUtils.isValidBankingDetails(
            accountNumber = "123456", // Too short
            branchCode = "123456"
        )
        assertFalse("Invalid account should fail", isValid)
    }

    @Test
    fun `validateBankingDetails - invalid branch returns false`() {
        val isValid = ValidationUtils.isValidBankingDetails(
            accountNumber = "1234567890",
            branchCode = "12345" // Too short
        )
        assertFalse("Invalid branch should fail", isValid)
    }

    @Test
    fun `validatePersonalDetails - all valid returns true`() {
        val isValid = ValidationUtils.isValidPersonalDetails(
            firstName = "John",
            lastName = "Doe",
            idNumber = "8001015800089",
            phoneNumber = "0715555555"
        )
        assertTrue("Valid personal details should pass", isValid)
    }

    @Test
    fun `validatePersonalDetails - invalid ID fails`() {
        val isValid = ValidationUtils.isValidPersonalDetails(
            firstName = "John",
            lastName = "Doe",
            idNumber = "123", // Invalid
            phoneNumber = "0715555555"
        )
        assertFalse("Invalid ID should fail", isValid)
    }

    @Test
    fun `validatePersonalDetails - invalid phone fails`() {
        val isValid = ValidationUtils.isValidPersonalDetails(
            firstName = "John",
            lastName = "Doe",
            idNumber = "8001015800089",
            phoneNumber = "123" // Invalid
        )
        assertFalse("Invalid phone should fail", isValid)
    }
}

