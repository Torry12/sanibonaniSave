package com.sanibonani.save.domain.service

/**
 * Service for financial reconciliation logic.
 */
interface ReconciliationService {
    suspend fun reconcile(groupId: String): Result<Unit>
}

