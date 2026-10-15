package com.sanibonani.save.data.repository.treasury
import com.sanibonani.save.data.datasource.LocalReconciliationDataSource
import com.sanibonani.save.data.datasource.RemoteReconciliationDataSource
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.ReconciliationRecord
import com.sanibonani.save.domain.model.ReconciliationStatus
import com.sanibonani.save.domain.model.SettlementRecord
import com.sanibonani.save.domain.repository.treasury.ReconciliationRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import com.sanibonani.save.domain.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class ReconciliationRepositoryImpl @Inject constructor(
    private val remoteSource: RemoteReconciliationDataSource,
    private val localSource: LocalReconciliationDataSource,
    private val dbProvider: Provider<SanibonaniDatabase>
) : BaseRepository("ReconciliationRepository"), ReconciliationRepository {

    private val db: SanibonaniDatabase get() = dbProvider.get()

    override fun observeReconciliations(groupId: String): Flow<Result<List<ReconciliationRecord>>> = observeAndSync(
        dbFlow = localSource.observeReconciliations(groupId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getReconciliations(groupId).getOrThrow() },
        cacheSync = { list ->
            localSource.clearReconciliations(groupId)
            localSource.saveReconciliations(list)
        }
    )

    override suspend fun performReconciliation(groupId: String): Result<ReconciliationRecord> = safeRunCatching {
        try {
            val record = remoteSource.performReconciliation(groupId).getOrThrow()
            localSource.saveReconciliations(listOf(record))
            record
        } catch (e: CancellationException) {
            throw e // A cancelled sync must never be recorded as a local reconciliation
        } catch (e: Exception) {
            val localRecord = performLocalReconciliation(groupId)
            localSource.saveReconciliations(listOf(localRecord))
            localRecord
        }
    }

    private suspend fun performLocalReconciliation(groupId: String): ReconciliationRecord {
        val ledgerEntities = db.ledgerDao().observeLedger(groupId).firstOrNull() ?: emptyList()
        val ledgerSum = ledgerEntities.fold(Money.ZERO) { acc, entry -> acc + entry.amount }
        val groupEntity = db.groupDao().getGroupById(groupId)
        val actualBalance = groupEntity?.balance ?: Money.ZERO
        val discrepancy = actualBalance - ledgerSum
        val isBalanced = discrepancy.abs() < Money("0.01")
        val status = if (isBalanced) ReconciliationStatus.BALANCED else ReconciliationStatus.DISCREPANCY

        return ReconciliationRecord(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            periodStart = System.currentTimeMillis() - (365L * 24 * 3600 * 1000),
            periodEnd = System.currentTimeMillis(),
            status = status,
            ledgerBalance = ledgerSum,
            actualBalance = actualBalance,
            discrepancy = discrepancy,
            notes = if (isBalanced) "Local offline audit: Ledger sum matches recorded group balance."
                    else "Local offline audit: Discrepancy of ${discrepancy.format()} detected between ledger entries and group balance.",
            performedBy = "LOCAL_RECONCILIATION_ENGINE",
            createdAt = Instant.now().toString()
        )
    }

    override suspend fun resolveDiscrepancy(reconciliationId: String, notes: String): Result<Unit> = retryWithExponentialBackoff {
        // A resolution is only real once the system of record accepts it. Previously a
        // remote failure was swallowed and the local record was marked RESOLVED anyway,
        // so an admin could close a financial discrepancy in the app while the ledger of
        // record still showed it open. The local status is therefore left untouched and
        // the failure is surfaced.
        try {
            remoteSource.resolveDiscrepancy(reconciliationId, notes).getOrThrow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(
                "ReconciliationRepository",
                "Could not resolve discrepancy $reconciliationId on the server; local status left unchanged.",
                e
            )
            throw e
        }
        localSource.updateReconciliationStatus(reconciliationId, ReconciliationStatus.RESOLVED, notes)
    }

    override fun observeSettlements(): Flow<Result<List<SettlementRecord>>> = observeAndSync(
        dbFlow = localSource.observeSettlements(),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getSettlements().getOrThrow() },
        cacheSync = { list ->
            localSource.clearAllSettlements()
            localSource.saveSettlements(list)
        }
    )

    override suspend fun getSettlementByTransactionId(transactionId: String): Result<SettlementRecord?> = safeRunCatching {
        localSource.getSettlementByTransactionId(transactionId)
            ?: remoteSource.getSettlementByTransactionId(transactionId).getOrThrow()
    }

    override suspend fun updateSettlement(record: SettlementRecord): Result<Unit> = retryWithExponentialBackoff {
        try {
            remoteSource.updateSettlement(record).getOrThrow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep the local write so the offline UI reflects the admin's action, but do
            // not let the failure disappear: an unconfirmed settlement must be visible to
            // the caller. Local state self-corrects on the next getSettlements() sync.
            AppLogger.w(
                "ReconciliationRepository",
                "Settlement ${record.id} was not confirmed by the server; cached locally only.",
                e
            )
            localSource.saveSettlements(listOf(record))
            throw e
        }
        localSource.saveSettlements(listOf(record))
    }
}
