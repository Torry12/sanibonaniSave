package com.sanibonani.save.domain.repository.equity
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformFee

interface GroupFinancialRepository {
    /** Atomically updates group balance by adding the specified amount (can be negative). */
    suspend fun incrementGroupBalance(
        groupId: String,
        amount: Money,
        memberId: String? = null
    ): Result<Money>

    /**
     * Atomically records a disbursement (outflow).
     * Decrements group balance and records a ledger entry.
     */
    suspend fun recordDisbursement(
        groupId: String,
        amount: Money,
        description: String,
        category: String,
        transactionId: String? = null,
        memberId: String? = null
    ): Result<Money>

    suspend fun payPlatformFee(
        groupId: String,
        amount: Money,
        feeType: String,
        txId: String? = null
    ): Result<Unit>

    /** Fetches all platform fees for a group that are not fully paid. */
    suspend fun getOutstandingPlatformFees(groupId: String): Result<List<PlatformFee>>

    /**
     * Deep-audits the group balance by summing all physical ledger entries.
     * Returns the actual sum found in the immutable group_ledger.
     */
    suspend fun getVerifiedBalance(groupId: String): Result<Money>

    suspend fun repairGroupData(groupId: String): Result<Unit>
}
