package com.sanibonani.save.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card rules were restated inside ValidationUtils while the file's own contract says
 * InputValidator is the single source of truth for payment checks. These tests pin the rules
 * where they now live and prove the proxy still answers identically, so a caller that keeps
 * using ValidationUtils cannot silently drift from the brain.
 */
class InputValidatorCardTest {

    @Test
    fun `a card number is judged on its digits only`() {
        assertTrue(InputValidator.validateCardNumber("4111 1111 1111 1111") is ValidationResult.Valid)
        assertTrue(InputValidator.validateCardNumber("4111111111111111") is ValidationResult.Valid)
        assertTrue(InputValidator.validateCardNumber("") is ValidationResult.Error)
    }

    @Test
    fun `a card number outside 13 to 19 digits is refused`() {
        assertTrue(InputValidator.validateCardNumber("411111111111") is ValidationResult.Error)
        assertTrue(InputValidator.validateCardNumber("41111111111111111111") is ValidationResult.Error)
    }

    @Test
    fun `an expiry is refused when malformed, out of range or past`() {
        assertTrue(InputValidator.validateCardExpiry("12/99") is ValidationResult.Valid)
        assertTrue(InputValidator.validateCardExpiry("12/20") is ValidationResult.Error)
        assertTrue(InputValidator.validateCardExpiry("13/99") is ValidationResult.Error)
        assertTrue(InputValidator.validateCardExpiry("1/9") is ValidationResult.Error)
    }

    @Test
    fun `a CVV must be three or four digits`() {
        assertTrue(InputValidator.validateCardCVV("123") is ValidationResult.Valid)
        assertTrue(InputValidator.validateCardCVV("1234") is ValidationResult.Valid)
        assertTrue(InputValidator.validateCardCVV("12") is ValidationResult.Error)
        assertTrue(InputValidator.validateCardCVV("12345") is ValidationResult.Error)
    }

    @Test
    fun `the composite check reports the first failing field`() {
        val badCard = InputValidator.validatePaymentFields("", "12/99", "123")
        assertTrue(badCard is ValidationResult.Error)
        assertEquals("Card number is required", (badCard as ValidationResult.Error).message)

        val badCvv = InputValidator.validatePaymentFields("4111111111111111", "12/99", "")
        assertTrue(badCvv is ValidationResult.Error)
        assertEquals("CVV is required", (badCvv as ValidationResult.Error).message)

        assertTrue(
            InputValidator.validatePaymentFields("4111 1111 1111 1111", "12/99", "123")
                is ValidationResult.Valid
        )
    }

    @Test
    fun `the proxy answers exactly what the brain answers`() {
        val cardNumbers = listOf("", "4242", "4111111111111111", "41111111111111111111")
        val expiries = listOf("", "12/99", "12/20", "13/99")
        val cvvs = listOf("", "12", "123", "12345")

        cardNumbers.forEach {
            assertEquals(ValidationUtils.validateCardNumber(it), InputValidator.validateCardNumber(it))
        }
        expiries.forEach {
            assertEquals(ValidationUtils.validateCardExpiry(it), InputValidator.validateCardExpiry(it))
        }
        cvvs.forEach {
            assertEquals(ValidationUtils.validateCardCVV(it), InputValidator.validateCardCVV(it))
        }
        assertEquals(
            ValidationUtils.validatePaymentFields("4111111111111111", "12/99", "12"),
            InputValidator.validatePaymentFields("4111111111111111", "12/99", "12")
        )
    }
}
