package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.BankTransaction
import com.sanibonani.save.domain.model.BankTransactionStatus
import com.sanibonani.save.domain.model.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Strategy interface for parsing raw bank data into domain models.
 */
interface BankTransactionParser {
    fun parse(rawData: String, groupId: String): List<BankTransaction>
}

class StandardJsonBankParser(private val json: Json) : BankTransactionParser {
    override fun parse(rawData: String, groupId: String): List<BankTransaction> {
        val element = safeRunCatching { json.parseToJsonElement(rawData) }.getOrNull() ?: return emptyList()
        val array = safeRunCatching { element.jsonArray }.getOrNull() ?: return emptyList()
        return array.mapIndexedNotNull { index, item ->
            safeRunCatching {
                val obj = item.jsonObject
                val rawAmount = obj["amount"]?.jsonPrimitive?.content ?: "0.00"
                val cleanAmount = rawAmount.replace(Regex("[R\\s,]"), "")
                val rawRef = obj["bank_reference"]?.jsonPrimitive?.content ?: ""
                val safeRef = if (rawRef.isBlank()) "REF_${groupId}_${System.currentTimeMillis()}_$index" else rawRef

                BankTransaction(
                    id = obj["id"]?.jsonPrimitive?.content,
                    groupId = groupId,
                    bankReference = safeRef,
                    amount = Money(cleanAmount),
                    transactionDate = obj["transaction_date"]?.jsonPrimitive?.content ?: LocalDate.now().toString(),
                    description = obj["description"]?.jsonPrimitive?.content,
                    status = BankTransactionStatus.UNRECONCILED
                )
            }.getOrNull()
        }
    }
}

class CsvBankParser : BankTransactionParser {
    override fun parse(rawData: String, groupId: String): List<BankTransaction> {
        val lines = rawData.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        // Assumption: CSV Format is Date,Reference,Amount,Description
        return lines.drop(1).mapIndexedNotNull { index, line ->
            val parts = line.split(",").map { it.trim().removeSurrounding("\"") }
            if (parts.size < 3) return@mapIndexedNotNull null

            safeRunCatching {
                // Clean the string (remove ZAR symbol, spaces, and commas) to prevent BigDecimal exception
                val rawAmount = parts[2].replace(Regex("[R\\s,]"), "")
                val rawRef = parts[1]
                val safeRef = if (rawRef.isBlank()) "REF_${groupId}_${System.currentTimeMillis()}_$index" else rawRef

                BankTransaction(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    bankReference = safeRef,
                    amount = Money(rawAmount),
                    transactionDate = parts[0],
                    description = parts.getOrNull(3),
                    status = BankTransactionStatus.UNRECONCILED
                )
            }.getOrNull()
        }
    }
}
