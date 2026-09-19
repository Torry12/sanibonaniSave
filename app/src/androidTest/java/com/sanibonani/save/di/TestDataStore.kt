package com.sanibonani.save.di

import com.sanibonani.save.data.local.SanibonaniDatabase
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

object TestDataStore {
    var dbInstance: SanibonaniDatabase? = null
    val mockGroups = mutableMapOf<String, JsonObject>()
    val mockMembers = mutableMapOf<String, JsonObject>()
    val mockContributions = mutableMapOf<String, JsonObject>()
    val mockBeneficiaries = mutableMapOf<String, JsonObject>()
    val mockClaims = mutableMapOf<String, JsonObject>()
    val mockPayouts = mutableMapOf<String, JsonObject>()
    val mockLoans = mutableMapOf<String, JsonObject>()
    val mockLoanRepayments = mutableMapOf<String, JsonObject>()
    val mockProfiles = mutableMapOf<String, JsonObject>()

    fun reset() {
        mockGroups.clear()
        mockMembers.clear()
        mockContributions.clear()
        mockBeneficiaries.clear()
        mockClaims.clear()
        mockPayouts.clear()
        mockLoans.clear()
        mockLoanRepayments.clear()
        mockProfiles.clear()
        dbInstance?.clearAllTables()
    }

    fun getAmount(json: JsonElement?): Double {
        if (json == null) return 0.0
        if (json is JsonPrimitive) return json.content.toDoubleOrNull() ?: 0.0
        if (json is JsonObject) {
            val amt = json["amount"]?.jsonPrimitive?.content ?: "0"
            return amt.toDoubleOrNull() ?: 0.0
        }
        return 0.0
    }

    fun buildMoney(amount: Double) = JsonPrimitive(String.format("%.2f", amount))
}
