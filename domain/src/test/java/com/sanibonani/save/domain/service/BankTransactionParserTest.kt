package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.BankTransactionStatus
import com.sanibonani.save.domain.model.Money
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BankTransactionParserTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonParser = StandardJsonBankParser(json)
    private val csvParser = CsvBankParser()

    @Test
    fun `parse valid json bank transactions successfully`() {
        val rawData = """
            [
                {
                    "id": "tx_1",
                    "amount": "R 1,500.50",
                    "bank_reference": "REF_001",
                    "transaction_date": "2026-10-01",
                    "description": "Deposit Test"
                }
            ]
        """.trimIndent()

        val result = jsonParser.parse(rawData, "group_1")
        assertEquals(1, result.size)
        assertEquals("tx_1", result[0].id)
        assertEquals("group_1", result[0].groupId)
        assertEquals("REF_001", result[0].bankReference)
        assertEquals(Money(1500.50), result[0].amount)
        assertEquals("2026-10-01", result[0].transactionDate)
        assertEquals("Deposit Test", result[0].description)
        assertEquals(BankTransactionStatus.UNRECONCILED, result[0].status)
    }

    @Test
    fun `parse valid csv bank statements successfully`() {
        val rawData = """
            Date,Reference,Amount,Description
            2026-10-02,REF_002,"R 2000.00",Salary Deposit
        """.trimIndent()

        val result = csvParser.parse(rawData, "group_1")
        assertEquals(1, result.size)
        assertEquals("group_1", result[0].groupId)
        assertEquals("REF_002", result[0].bankReference)
        assertEquals(Money(2000.00), result[0].amount)
        assertEquals("2026-10-02", result[0].transactionDate)
        assertEquals("Salary Deposit", result[0].description)
        assertEquals(BankTransactionStatus.UNRECONCILED, result[0].status)
    }

    @Test
    fun `parse blank or malformed data returns empty list`() {
        assertTrue(jsonParser.parse("", "group_1").isEmpty())
        assertTrue(csvParser.parse("   ", "group_1").isEmpty())
    }
}
