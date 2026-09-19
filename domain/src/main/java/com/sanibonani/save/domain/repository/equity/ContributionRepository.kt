package com.sanibonani.save.domain.repository.equity

import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray

/**
 * Authoritative repository for all fund inflows (Equity).
 * Manages both individual contribution records and higher-level payment transactions.
 */
interface ContributionRepository {
    // ── Individual Contributions (Equity Logs) ────────────────────────────────
    suspend fun recordContribution(contribution: Contribution): Result<Unit>
    fun observeMemberContributions(memberId: String, groupId: String): Flow<Result<List<Contribution>>>
    fun observeGroupContributions(groupId: String): Flow<Result<List<Contribution>>>

    // ── Payment Transactions (Receipts) ───────────────────────────────────────
    suspend fun recordPayment(payment: Payment): Result<String>
    fun observePayments(groupId: String): Flow<Result<List<Payment>>>

    /**
     * Executes a multi-allocation payment atomically on the server.
     * Maps to process_complex_payment_v4 in Postgres.
     * @param allocations JsonArray of {type, amount, loan_id?, due_date}
     */
    suspend fun processComplexPayment(
        groupId: String,
        memberId: String,
        totalAmount: Money,
        txId: String,
        allocations: JsonArray,
        method: PaymentMethod,
        paymentType: PaymentType = PaymentType.CONTRIBUTION,
        destination: PaymentDestination = PaymentDestination.GROUP
    ): Result<Money>

    // ── Compatibility & Helpers ──────────────────────────────────────────────
    suspend fun getTotalContributed(memberId: String, groupId: String): Money
}
