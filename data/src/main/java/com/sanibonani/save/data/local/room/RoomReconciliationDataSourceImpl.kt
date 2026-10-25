package com.sanibonani.save.data.local.room

import com.sanibonani.save.data.datasource.LocalReconciliationDataSource
import com.sanibonani.save.data.local.LedgerDao
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.ReconciliationRecord
import com.sanibonani.save.domain.model.ReconciliationStatus
import com.sanibonani.save.domain.model.SettlementRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomReconciliationDataSourceImpl @Inject constructor(
    private val ledgerDao: LedgerDao
) : LocalReconciliationDataSource {

    override fun observeReconciliations(groupId: String): Flow<List<ReconciliationRecord>> =
        ledgerDao.observeReconciliations(groupId).map { list -> list.map { it.toModel() } }

    override fun observeSettlements(): Flow<List<SettlementRecord>> =
        ledgerDao.observeSettlements().map { list -> list.map { it.toModel() } }

    override suspend fun saveReconciliations(reconciliations: List<ReconciliationRecord>) {
        ledgerDao.upsertReconciliations(reconciliations.map { it.toEntity() })
    }

    override suspend fun clearReconciliations(groupId: String) {
        ledgerDao.clearReconciliations(groupId)
    }

    override suspend fun updateReconciliationStatus(id: String, status: ReconciliationStatus, notes: String) {
        ledgerDao.updateReconciliationStatus(id, status, notes)
    }

    override suspend fun getSettlementByTransactionId(transactionId: String): SettlementRecord? =
        ledgerDao.getSettlementByTransactionId(transactionId)?.toModel()

    override suspend fun saveSettlements(settlements: List<SettlementRecord>) {
        ledgerDao.upsertSettlements(settlements.map { it.toEntity() })
    }

    override suspend fun clearAllSettlements() {
        ledgerDao.clearAllSettlements()
    }
}
