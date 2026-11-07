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

    /**
     * Reads one member's contributions from the server and writes what it answered to the cache.
     *
     * [observeMemberContributions] answers from Room first and sends the refresh verdict behind those
     * rows, so its first emission only proves the cache spoke. Anything that certifies money off this
     * book - the probation cycle's joining-fee rule - needs a read that cannot answer without the
     * server, and a failure here means the book is unread rather than empty.
     */
    suspend fun syncMemberContributions(memberId: String, groupId: String): Result<List<Contribution>>

    /**
     * The group-wide twin of [syncMemberContributions], for the reads that certify money for a whole
     * group rather than one member: a ROSCA rotation derives every pot from this book, so a cached
     * row that the server never confirmed would release a payout the group has not paid for.
     */
    suspend fun syncGroupContributions(groupId: String): Result<List<Contribution>>

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
