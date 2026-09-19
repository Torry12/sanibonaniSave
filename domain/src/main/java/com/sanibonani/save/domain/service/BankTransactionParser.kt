package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.BankTransaction
import com.sanibonani.save.domain.model.BankTransactionStatus
import com.sanibonani.save.domain.model.Money
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Strategy interface for parsing raw bank data into domain models.
 */
interface BankTransactionParser {
    fun parse(rawData: String, groupId: String): List<BankTransaction>
}

class StandardJsonBankParser(private val json: Json) : BankTransactionParser {
    override fun parse(rawData: String, groupId: String): List<BankTransaction> {
        val element = json.parseToJsonElement(rawData)
        return element.jsonArray.map { item ->
            val obj = item.jsonObject
            BankTransaction(
                id = obj["id"]?.jsonPrimitive?.content,
                groupId = groupId,
                bankReference = obj["bank_reference"]?.jsonPrimitive?.content ?: "",
                amount = Money(obj["amount"]?.jsonPrimitive?.content ?: "0.00"),
                transactionDate = obj["transaction_date"]?.jsonPrimitive?.content ?: "",
                description = obj["description"]?.jsonPrimitive?.content,
                status = BankTransactionStatus.UNRECONCILED
            )
        }
    }
}

class CsvBankParser : BankTransactionParser {
    override fun parse(rawData: String, groupId: String): List<BankTransaction> {
        // Stub for future CSV parsing logic
        return emptyList()
    }
}
