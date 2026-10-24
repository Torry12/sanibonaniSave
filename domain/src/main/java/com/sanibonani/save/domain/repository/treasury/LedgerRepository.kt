package com.sanibonani.save.domain.repository.treasury
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.ConsolidationReport
import kotlinx.coroutines.flow.Flow

interface LedgerRepository {
    // ── Ledger ────────────────────────────────────────────────────────────────
    fun observeGroupLedger(groupId: String): Flow<Result<List<LedgerEntry>>>
    suspend fun getGroupLedger(groupId: String): Result<List<LedgerEntry>>
    fun observeMemberLedger(memberId: String): Flow<Result<List<LedgerEntry>>>
    suspend fun getMemberLedger(memberId: String): Result<List<LedgerEntry>>
    fun observePlatformLedger(): Flow<Result<List<LedgerEntry>>>
    suspend fun getPlatformLedger(): Result<List<LedgerEntry>>
    suspend fun logPlatformEvent(entry: LedgerEntry): Result<Unit>

    /**
     * Reconstructs missing ledger entries from sub-ledgers (Contributions, Repayments, Payouts, Claims).
     * This is a high-integrity accounting procedure that repairs the group's audit trail.
     */
    suspend fun consolidateGroupLedger(groupId: String): Result<ConsolidationReport>

    /**
     * Observes the consolidated audit trail (Group + relevant Platform entries).
     */
    fun observeConsolidatedAuditTrail(groupId: String): Flow<Result<List<LedgerEntry>>>
}
