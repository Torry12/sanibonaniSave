package com.sanibonani.save.data.repository.treasury
import com.sanibonani.save.data.datasource.LocalLedgerDataSource
import com.sanibonani.save.data.datasource.RemoteLedgerDataSource
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LedgerRepositoryImpl @Inject constructor(
    private val remoteSource: RemoteLedgerDataSource,
    private val localSource: LocalLedgerDataSource,
    private val localGroupSource: com.sanibonani.save.data.datasource.LocalGroupDataSource
) : BaseRepository("LedgerRepository"), LedgerRepository {

    override fun observeGroupLedger(groupId: String): Flow<Result<List<LedgerEntry>>> = observeAndSync(
        dbFlow = localSource.observeGroupLedger(groupId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getGroupLedger(groupId).getOrThrow() },
        cacheSync = { localSource.syncGroupLedger(groupId, it) }
    )

    override suspend fun getGroupLedger(groupId: String): Result<List<LedgerEntry>> = remoteSource.getGroupLedger(groupId)

    override fun observeMemberLedger(memberId: String): Flow<Result<List<LedgerEntry>>> = observeAndSync(
        dbFlow = localSource.observeMemberLedger(memberId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getMemberLedger(memberId).getOrThrow() },
        cacheSync = { localSource.saveLedgerEntries(it) }
    )

    override suspend fun getMemberLedger(memberId: String): Result<List<LedgerEntry>> = remoteSource.getMemberLedger(memberId)

    override fun observePlatformLedger(): Flow<Result<List<LedgerEntry>>> = observeAndSync(
        dbFlow = localSource.observePlatformLedger(),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getPlatformLedger().getOrThrow() },
        cacheSync = { localSource.saveLedgerEntries(it) }
    )

    override suspend fun getPlatformLedger(): Result<List<LedgerEntry>> = remoteSource.getPlatformLedger()

    override suspend fun logPlatformEvent(entry: LedgerEntry): Result<Unit> = retryWithExponentialBackoff {
        remoteSource.logPlatformEvent(entry).getOrThrow()
    }

    override suspend fun consolidateGroupLedger(groupId: String): Result<ConsolidationReport> = retryWithExponentialBackoff {
        val report = remoteSource.consolidateGroupLedger(groupId).getOrThrow()
        localSource.saveReconciliation(report)
        localGroupSource.getGroupById(groupId)?.let { group ->
            localGroupSource.saveGroup(group.copy(balance = report.finalBalance))
        }
        report
    }

    override suspend fun createCheckpoint(groupId: String): Result<String> = remoteSource.createCheckpoint(groupId)

    override fun observeConsolidatedAuditTrail(groupId: String): Flow<Result<List<LedgerEntry>>> = observeAndSync(
        dbFlow = localSource.observeConsolidatedLedger(groupId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getConsolidatedAuditTrail(groupId).getOrThrow() },
        cacheSync = { localSource.saveLedgerEntries(it) }
    )
}
