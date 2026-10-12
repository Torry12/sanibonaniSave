package com.sanibonani.save.data.service

import com.sanibonani.save.domain.model.ReconciliationRecord
import com.sanibonani.save.domain.repository.treasury.ReconciliationRepository
import com.sanibonani.save.domain.service.ReconciliationService
import javax.inject.Inject

class ReconciliationServiceImpl @Inject constructor(
    private val reconciliationRepository: ReconciliationRepository
) : ReconciliationService {

    override suspend fun auditGroupLedger(groupId: String): Result<ReconciliationRecord> {
        return reconciliationRepository.performReconciliation(groupId)
    }

    override suspend fun checkSettlementStatus(transactionId: String): Result<Boolean> {
        // High-integrity check: validates if external payment has been settled into our ledger
        return reconciliationRepository.getSettlementByTransactionId(transactionId).map { it != null }
    }

    override suspend fun resolveDiscrepancy(reconciliationId: String, correctionNotes: String): Result<Unit> {
        return reconciliationRepository.resolveDiscrepancy(reconciliationId, correctionNotes)
    }
}



