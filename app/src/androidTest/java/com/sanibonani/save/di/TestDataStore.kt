package com.sanibonani.save.di

import com.sanibonani.save.domain.model.Money
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
    val mockPolls = mutableMapOf<String, JsonObject>()
    val mockPollOptions = mutableMapOf<String, JsonObject>()
    val mockPollVotes = mutableMapOf<String, JsonObject>()

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
        mockPolls.clear()
        mockPollOptions.clear()
        mockPollVotes.clear()
        dbInstance?.clearAllTables()
    }

    fun getMoney(json: JsonElement?): Money {
        if (json == null) return Money.ZERO
        if (json is JsonPrimitive) return Money(json.content)
        if (json is JsonObject) {
            val amt = json["amount"]?.jsonPrimitive?.content ?: "0.00"
            return Money(amt)
        }
        return Money.ZERO
    }

    fun buildMoney(amount: Money) = JsonPrimitive(amount.amount.toPlainString())

    fun buildMoney(amount: Double) = JsonPrimitive(Money(amount).amount.toPlainString())
}
