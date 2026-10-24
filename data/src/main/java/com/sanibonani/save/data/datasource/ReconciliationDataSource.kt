package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.ReconciliationRecord
import com.sanibonani.save.domain.model.ReconciliationStatus
import com.sanibonani.save.domain.model.SettlementRecord
import kotlinx.coroutines.flow.Flow

interface RemoteReconciliationDataSource {
    suspend fun getReconciliations(groupId: String): Result<List<ReconciliationRecord>>
    suspend fun performReconciliation(groupId: String): Result<ReconciliationRecord>
    suspend fun resolveDiscrepancy(reconciliationId: String, notes: String): Result<Unit>
    suspend fun getSettlements(): Result<List<SettlementRecord>>
    suspend fun getSettlementByTransactionId(transactionId: String): Result<SettlementRecord?>
    suspend fun updateSettlement(record: SettlementRecord): Result<Unit>
}

interface LocalReconciliationDataSource {
    fun observeReconciliations(groupId: String): Flow<List<ReconciliationRecord>>
    fun observeSettlements(): Flow<List<SettlementRecord>>
    suspend fun saveReconciliations(reconciliations: List<ReconciliationRecord>)
    suspend fun clearReconciliations(groupId: String)
    suspend fun updateReconciliationStatus(id: String, status: ReconciliationStatus, notes: String)
    suspend fun getSettlementByTransactionId(transactionId: String): SettlementRecord?
    suspend fun saveSettlements(settlements: List<SettlementRecord>)
    suspend fun clearAllSettlements()
}
