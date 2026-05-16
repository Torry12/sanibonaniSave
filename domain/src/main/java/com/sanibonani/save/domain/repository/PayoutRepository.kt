package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing disbursements from the platform account to group accounts.
 */
interface PayoutRepository {
    suspend fun requestPayout(payout: PayoutRequest): Result<String>
    suspend fun getPendingPayouts(): Result<List<PayoutRequest>>
    suspend fun getPayoutById(payoutId: String): Result<PayoutRequest>
    suspend fun updatePayoutStatus(payoutId: String, status: PayoutStatus, yocoPayoutId: String? = null): Result<Unit>
    
    /**
     * Atomically completes a payout: updates status AND decrements group balance AND logs to ledger.
     */
    suspend fun completePayoutAtomic(
        payoutId: String,
        adminId: String,
        yocoPayoutId: String? = null
    ): Result<Unit>

    fun observePayouts(groupId: String): Flow<Result<List<PayoutRequest>>>
}
