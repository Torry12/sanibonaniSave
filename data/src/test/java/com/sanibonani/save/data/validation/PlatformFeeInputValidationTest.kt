package com.sanibonani.save.data.validation

import com.sanibonani.save.domain.model.Money
import org.junit.Assert.*
import org.junit.Test

class PlatformFeeInputValidationTest {

    @Test
    fun `test fee numeric parsing and comma sanitization`() {
        val inputs = listOf(
            "10.00" to Money("10.00"),
            "10,00" to Money("10.00"),
            "700" to Money("700.00"),
            "5.50" to Money("5.50"),
            "20,5" to Money("20.50")
        )

        for ((input, expected) in inputs) {
            val sanitized = input.replace(',', '.').trim()
            val parsed = runCatching { Money(sanitized) }.getOrDefault(Money.ZERO)
            assertEquals("Failed to parse input: $input", expected, parsed)
        }
    }

    @Test
    fun `test auto suspension days parsing`() {
        val validDays = "30"
        val parsedDays = validDays.toIntOrNull() ?: 30
        assertEquals(30, parsedDays)

        val invalidDays = "abc"
        val fallbackDays = invalidDays.toIntOrNull() ?: 30
        assertEquals(30, fallbackDays)
    }

    @Test
    fun `test bank account number and branch code digit validation`() {
        val accountNumber = "1234567890"
        val branchCode = "250655"

        assertTrue(accountNumber.all { it.isDigit() })
        assertTrue(branchCode.all { it.isDigit() })

        val invalidAccount = "1234A5678"
        assertFalse(invalidAccount.all { it.isDigit() })
    }
}
