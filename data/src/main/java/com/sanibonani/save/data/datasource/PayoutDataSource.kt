package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.DocumentStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

interface RemotePayoutDataSource {
    suspend fun requestPayout(payout: PayoutRequest): Result<PayoutRequest>
    suspend fun getPayouts(groupId: String): Result<List<PayoutRequest>>
    suspend fun getPendingPayouts(): Result<List<PayoutRequest>>
    suspend fun getPayoutsForMember(memberId: String): Result<List<PayoutRequest>>
    suspend fun getPayoutById(id: String): Result<PayoutRequest>
    suspend fun updatePayoutStatus(
        payoutId: String,
        status: PayoutStatus,
        reference: String? = null,
        processedBy: String? = null,
        processedAt: String? = null,
        metadata: JsonObject? = null
    ): Result<Unit>
    suspend fun completePayoutAtomic(payoutId: String, adminId: String, reference: String?): Result<Unit>
    suspend fun updatePayoutDocumentStatus(payoutId: String, status: DocumentStatus): Result<Unit>
    suspend fun processRoscaPayout(groupId: String, memberId: String, totalPot: Money, loanDeductions: JsonArray, payoutAmount: Money, payoutData: JsonObject): Result<String>
}

interface LocalPayoutDataSource {
    fun observePayouts(groupId: String): Flow<List<PayoutRequest>>
    fun observePayoutsForMember(memberId: String): Flow<List<PayoutRequest>>
    fun observePendingPayouts(): Flow<List<PayoutRequest>>
    suspend fun savePayouts(payouts: List<PayoutRequest>)
    suspend fun savePayout(payout: PayoutRequest)
    suspend fun syncPayouts(groupId: String, payouts: List<PayoutRequest>)
    suspend fun clearAll()
}
