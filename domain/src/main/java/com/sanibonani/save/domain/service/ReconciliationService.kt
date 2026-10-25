package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.ReconciliationRecord

/**
 * Service for high-integrity ledger balancing and reconciliation audits.
 * Enforces immutability and ensures physical bank/gateway balances align with virtual ledgers.
 */
interface ReconciliationService {
    /**
     * Performs a deep audit of the group's financial history.
     * Compares the sum of all [LedgerEntry] records against the current [Group.balance].
     */
    suspend fun auditGroupLedger(groupId: String): Result<ReconciliationRecord>

    /**
     * Verifies if a specific transaction has been settled by the external provider.
     */
    suspend fun checkSettlementStatus(transactionId: String): Result<Boolean>

    /**
     * Resolves a detected discrepancy in the ledger via a corrective adjustment entry.
     */
    suspend fun resolveDiscrepancy(reconciliationId: String, correctionNotes: String): Result<Unit>
}

