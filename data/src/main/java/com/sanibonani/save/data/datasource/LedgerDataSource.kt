package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.ConsolidationReport
import kotlinx.coroutines.flow.Flow

interface RemoteLedgerDataSource {
    suspend fun getGroupLedger(groupId: String): Result<List<LedgerEntry>>
    suspend fun getMemberLedger(memberId: String): Result<List<LedgerEntry>>
    suspend fun getPlatformLedger(): Result<List<LedgerEntry>>
    suspend fun getConsolidatedAuditTrail(groupId: String): Result<List<LedgerEntry>>
    suspend fun logPlatformEvent(entry: LedgerEntry): Result<Unit>
    suspend fun consolidateGroupLedger(groupId: String): Result<ConsolidationReport>
    suspend fun createCheckpoint(groupId: String): Result<String>
}

interface LocalLedgerDataSource {
    fun observeGroupLedger(groupId: String): Flow<List<LedgerEntry>>
    fun observeMemberLedger(memberId: String): Flow<List<LedgerEntry>>
    fun observePlatformLedger(): Flow<List<LedgerEntry>>
    fun observeConsolidatedLedger(groupId: String): Flow<List<LedgerEntry>>
    suspend fun saveLedgerEntries(entries: List<LedgerEntry>)
    suspend fun syncGroupLedger(groupId: String, entries: List<LedgerEntry>)
    suspend fun saveReconciliation(report: ConsolidationReport)
    suspend fun clearAll()
}
