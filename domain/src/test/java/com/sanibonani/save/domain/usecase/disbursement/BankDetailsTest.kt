package com.sanibonani.save.domain.usecase.disbursement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One rule decides whether money can be addressed to an account. The admin payout, the member
 * withdrawal and the ROSCA rotation all spend from the same treasury, so they must agree on what
 * counts as a usable bank detail instead of each checking a different subset.
 */
class BankDetailsTest {

    @Test
    fun `valid details are trimmed before they are stored`() {
        val result = validateBankDetails(" FNB ", " 123456789 ", " 250655 ")

        val details = (result as BankDetailsResult.Valid).details
        assertEquals("FNB", details.bankName)
        assertEquals("123456789", details.accountNo)
        assertEquals("250655", details.branchCode)
    }

    @Test
    fun `a blank bank name is reported as the bank-name field`() {
        val result = validateBankDetails("   ", "123456789", "250655")

        val invalid = result as BankDetailsResult.Invalid
        assertEquals(BankDetailsField.BANK_NAME, invalid.field)
    }

    @Test
    fun `a blank account number is reported as the account-number field`() {
        val result = validateBankDetails("FNB", "  ", "250655")

        val invalid = result as BankDetailsResult.Invalid
        assertEquals(BankDetailsField.ACCOUNT_NUMBER, invalid.field)
    }

    @Test
    fun `an account number with letters is rejected`() {
        val result = validateBankDetails("FNB", "12AB456789", "250655")

        val invalid = result as BankDetailsResult.Invalid
        assertEquals(BankDetailsField.ACCOUNT_NUMBER, invalid.field)
        assertTrue(invalid.reason.contains("digits"))
    }

    @Test
    fun `an account number of the wrong length for the bank is rejected`() {
        val result = validateBankDetails("FNB", "12345", "250655")

        val invalid = result as BankDetailsResult.Invalid
        assertEquals(BankDetailsField.ACCOUNT_NUMBER, invalid.field)
    }

    @Test
    fun `a blank branch code is reported as the branch-code field`() {
        val result = validateBankDetails("FNB", "123456789", " ")

        val invalid = result as BankDetailsResult.Invalid
        assertEquals(BankDetailsField.BRANCH_CODE, invalid.field)
    }

    @Test
    fun `a branch code of the wrong length is rejected`() {
        val result = validateBankDetails("FNB", "123456789", "2506")

        val invalid = result as BankDetailsResult.Invalid
        assertEquals(BankDetailsField.BRANCH_CODE, invalid.field)
        assertTrue(invalid.reason.contains("Branch code"))
    }
}
