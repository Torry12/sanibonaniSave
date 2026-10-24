package com.sanibonani.save.domain.service

import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for retrieving the appropriate bank transaction parser based on format.
 */
@Singleton
class BankParserFactory @Inject constructor(
    private val json: Json
) {
    fun getParser(format: String): BankTransactionParser {
        return when (format.lowercase()) {
            "json" -> StandardJsonBankParser(json)
            "csv" -> CsvBankParser()
            else -> throw IllegalArgumentException("Unsupported bank statement format: $format")
        }
    }
}
