package com.sanibonani.save.domain.repository.disbursement
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.model.DocumentStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

interface PayoutRepository {
    suspend fun requestPayout(payout: PayoutRequest, idempotencyKey: String? = null): Result<String>
    suspend fun getPendingPayouts(): Result<List<PayoutRequest>>
    suspend fun getPayoutById(payoutId: String): Result<PayoutRequest>

    // [actorId] is persisted as the processor: the dual-control gate needs to know who moved a
    // request into processing before it can block that same admin from completing it.
    suspend fun updatePayoutStatus(
        payoutId: String,
        status: PayoutStatus,
        payoutReference: String? = null,
        metadata: JsonObject? = null,
        actorId: String? = null
    ): Result<Unit>
    suspend fun completePayoutAtomic(payoutId: String, adminId: String, payoutReference: String? = null): Result<Unit>

    /** Verifies a specific document on a payout request. */
    suspend fun verifyPayoutDocument(
        payoutId: String,
        status: DocumentStatus
    ): Result<Unit>

    fun observePayouts(groupId: String): Flow<Result<List<PayoutRequest>>>
    fun observePayoutsForMember(memberId: String): Flow<Result<List<PayoutRequest>>>

    /**
     * Executes a ROSCA payout with loan offsets atomically.
     *
     * [period] is the rotation period (yyyy-MM) this payout settles. The server claims it before it
     * offsets a single loan, so a re-run of a period cannot repay the same loans twice. The returned
     * payout id is null when the database created no payouts row - a pot the offsets fully absorbed,
     * or a period it had already executed.
     */
    suspend fun processRoscaPayout(
        groupId: String,
        memberId: String,
        totalPot: Money,
        loanDeductions: JsonArray,
        payoutAmount: Money,
        payoutData: JsonObject,
        period: String
    ): Result<String?>
}
