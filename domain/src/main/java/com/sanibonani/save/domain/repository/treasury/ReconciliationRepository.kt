package com.sanibonani.save.domain.repository.treasury
import com.sanibonani.save.domain.model.ReconciliationRecord
import com.sanibonani.save.domain.model.SettlementRecord
import kotlinx.coroutines.flow.Flow

/**
 * Repository for high-integrity financial reconciliation and external provider settlement tracking.
 */
interface ReconciliationRepository {
    // ── Reconciliations ───────────────────────────────────────────────────────
    /**
     * Observes reconciliation history for a specific group.
     */
    fun observeReconciliations(groupId: String): Flow<Result<List<ReconciliationRecord>>>

    /**
     * Triggers a server-side reconciliation process.
     */
    suspend fun performReconciliation(groupId: String): Result<ReconciliationRecord>

    /**
     * Resolves a detected discrepancy in the ledger via a corrective adjustment entry.
     */
    suspend fun resolveDiscrepancy(reconciliationId: String, notes: String): Result<Unit>

    // ── Settlements ───────────────────────────────────────────────────────────
    /**
     * Observes external transaction settlements.
     */
    fun observeSettlements(): Flow<Result<List<SettlementRecord>>>

    /**
     * Fetches a settlement record by its transaction ID.
     */
    suspend fun getSettlementByTransactionId(transactionId: String): Result<SettlementRecord?>

    /**
     * Updates an external settlement status.
     */
    suspend fun updateSettlement(record: SettlementRecord): Result<Unit>
}
