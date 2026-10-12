package com.sanibonani.save.domain.repository.disbursement
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.model.DocumentStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

interface PayoutRepository {
    suspend fun requestPayout(payout: PayoutRequest): Result<String>
    suspend fun getPendingPayouts(): Result<List<PayoutRequest>>
    suspend fun getPayoutById(payoutId: String): Result<PayoutRequest>

    /**
     * Checks if a member/group is eligible for a payout without creating a request.
     */
    suspend fun validatePayoutEligibility(
        groupId: String,
        memberId: String?,
        amount: Money,
        type: com.sanibonani.save.domain.model.PayoutType
    ): Result<Unit>

    suspend fun updatePayoutStatus(
        payoutId: String,
        status: PayoutStatus,
        payoutReference: String? = null,
        metadata: JsonObject? = null
    ): Result<Unit>
    suspend fun completePayoutAtomic(payoutId: String, adminId: String, payoutReference: String? = null): Result<Unit>

    /** Verifies a specific document on a payout request. */
    suspend fun verifyPayoutDocument(
        payoutId: String,
        status: DocumentStatus
    ): Result<Unit>

    fun observePayouts(groupId: String): Flow<Result<List<PayoutRequest>>>
    fun observePendingPayouts(): Flow<Result<List<PayoutRequest>>>
    fun observePayoutsForMember(memberId: String): Flow<Result<List<PayoutRequest>>>

    /**
     * Executes a ROSCA payout with loan offsets atomically.
     */
    suspend fun processRoscaPayout(
        groupId: String,
        memberId: String,
        totalPot: Money,
        loanDeductions: JsonArray,
        payoutAmount: Money,
        payoutData: JsonObject
    ): Result<String>
}
