package com.sanibonani.save.domain.validation

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Platform fee fields are published prices, and zero is a price an admin can legitimately choose:
 * waiving the payout fee or the late penalty. A blank or mistyped field is not a price at all, so it
 * has to be rejected rather than quietly saved as whatever the app fell back to.
 */
class InputValidatorFeeTest {

    @Test
    fun `a waived fee of zero is a valid published amount`() {
        assertTrue(InputValidator.validateNonNegativeAmount("0", "Payout fee") is ValidationResult.Valid)
        assertTrue(InputValidator.validateNonNegativeAmount("0.00", "Payout fee") is ValidationResult.Valid)
    }

    @Test
    fun `a published fee keeps its amount`() {
        assertTrue(InputValidator.validateNonNegativeAmount("700.00", "Registration fee") is ValidationResult.Valid)
        assertTrue(InputValidator.validateNonNegativeAmount("7.5", "Late fee percent") is ValidationResult.Valid)
    }

    @Test
    fun `a cleared field is rejected instead of being saved as an invented amount`() {
        val blank = InputValidator.validateNonNegativeAmount("   ", "Monthly member fee")
        assertTrue(blank is ValidationResult.Error)
        assertTrue((blank as ValidationResult.Error).message.contains("required"))
    }

    @Test
    fun `a mistyped fee is rejected instead of being saved as an invented amount`() {
        for (raw in listOf("1O.00", "R100", "100.000", "-50.00", "1.2.3")) {
            val result = InputValidator.validateNonNegativeAmount(raw, "Monthly member fee")
            assertTrue("expected '$raw' to be rejected, got $result", result is ValidationResult.Error)
        }
    }
}
