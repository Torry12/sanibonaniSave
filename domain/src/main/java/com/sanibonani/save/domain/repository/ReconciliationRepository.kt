package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.ReconciliationRecord
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing financial reconciliation records.
 */
interface ReconciliationRepository {
    /**
     * Observes reconciliation history for a specific group.
     */
    fun observeReconciliations(groupId: String): Flow<List<ReconciliationRecord>>

    /**
     * Records a new reconciliation result.
     */
    suspend fun recordReconciliation(record: ReconciliationRecord): Result<ReconciliationRecord>
}
