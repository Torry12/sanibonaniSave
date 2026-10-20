package com.sanibonani.save.domain.repository.equity
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Authoritative repository for all fund inflows (Equity).
 * Manages both individual contribution records and higher-level payment transactions.
 */
interface ContributionRepository {
    // ── Individual Contributions (Equity Logs) ────────────────────────────────
    suspend fun recordContribution(contribution: Contribution, idempotencyKey: String? = null): Result<Unit>
    fun observeMemberContributions(memberId: String, groupId: String): Flow<Result<List<Contribution>>>
    fun observeGroupContributions(groupId: String): Flow<Result<List<Contribution>>>

    // ── Payment Transactions (Receipts) ───────────────────────────────────────
    suspend fun recordPayment(payment: Payment): Result<String>

    /**
     * Executes a multi-allocation payment atomically on the server.
     * Maps to process_complex_payment_v4 in Postgres.
     * @param allocations List of [PaymentAllocation]
     */
    suspend fun processComplexPayment(
        groupId: String,
        memberId: String,
        totalAmount: Money,
        txId: String,
        allocations: List<PaymentAllocation>,
        method: PaymentMethod,
        paymentType: PaymentType = PaymentType.CONTRIBUTION,
        destination: PaymentDestination = PaymentDestination.GROUP
    ): Result<Money>
}
