package com.sanibonani.save.data.local.room

import com.sanibonani.save.data.datasource.LocalLedgerDataSource
import com.sanibonani.save.data.local.LedgerDao
import com.sanibonani.save.data.local.toLedgerEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.ConsolidationReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLedgerDataSourceImpl @Inject constructor(
    private val ledgerDao: LedgerDao
) : LocalLedgerDataSource {

    override fun observeGroupLedger(groupId: String): Flow<List<LedgerEntry>> =
        ledgerDao.observeLedger(groupId).map { list -> list.map { it.toModel() } }

    override fun observeMemberLedger(memberId: String): Flow<List<LedgerEntry>> =
        ledgerDao.observeMemberLedger(memberId).map { list -> list.map { it.toModel() } }

    override fun observePlatformLedger(): Flow<List<LedgerEntry>> =
        ledgerDao.observePlatformLedger().map { list -> list.map { it.toModel() } }

    override fun observeConsolidatedLedger(groupId: String): Flow<List<LedgerEntry>> =
        ledgerDao.observeConsolidatedLedger(groupId).map { list -> list.map { it.toModel() } }

    override suspend fun saveLedgerEntries(entries: List<LedgerEntry>) {
        ledgerDao.upsertLedger(entries.map { it.toLedgerEntity() })
    }

    override suspend fun syncGroupLedger(groupId: String, entries: List<LedgerEntry>) {
        ledgerDao.syncLedger(groupId, entries.map { it.toLedgerEntity() })
    }

    override suspend fun saveReconciliation(report: ConsolidationReport) {
        report.reconciliationRecord?.let {
            ledgerDao.upsertReconciliations(listOf(it.toEntity()))
        }
    }

    override suspend fun clearAll() {
        ledgerDao.clearAll()
        ledgerDao.clearAllSettlements()
    }
}
