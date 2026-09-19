package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.ReconciliationRecord
import com.sanibonani.save.domain.model.ReconciliationStatus
import com.sanibonani.save.domain.model.SettlementRecord
import com.sanibonani.save.domain.repository.ReconciliationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReconciliationRepositoryImpl @Inject constructor(
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>,
    private val dbProvider: javax.inject.Provider<SanibonaniDatabase>
) : BaseRepository("ReconciliationRepository"), ReconciliationRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    override fun observeReconciliations(groupId: String): Flow<Result<List<ReconciliationRecord>>> = observeAndSync(
        dbFlow = db.ledgerDao().observeReconciliations(groupId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["reconciliations"].select {
                filter { eq("group_id", groupId) }
            }.decodeList<ReconciliationRecord>()
        },
        cacheSync = { list ->
            db.ledgerDao().clearReconciliations(groupId)
            db.ledgerDao().upsertReconciliations(list)
        }
    )

    override suspend fun performReconciliation(groupId: String): Result<ReconciliationRecord> = runCatching {
        val record = supabase.postgrest.rpc("perform_group_reconciliation_v1", buildJsonObject {
            put("p_group_id", groupId)
        }).decodeSingle<ReconciliationRecord>()

        db.ledgerDao().upsertReconciliations(listOf(record.toEntity()))
        record
    }

    override suspend fun resolveDiscrepancy(reconciliationId: String, notes: String): Result<Unit> = runCatching {
        supabase.postgrest["reconciliations"].update(mapOf(
            "status" to ReconciliationStatus.RESOLVED.name.lowercase(),
            "notes" to notes
        )) {
            filter { eq("id", reconciliationId) }
        }
        Unit
    }

    override fun observeSettlements(): Flow<Result<List<SettlementRecord>>> = observeAndSync(
        dbFlow = db.ledgerDao().observeSettlements(),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["settlements"].select().decodeList<SettlementRecord>()
        },
        cacheSync = { list ->
            db.ledgerDao().clearAllSettlements()
            db.ledgerDao().upsertSettlements(list)
        }
    )

    override suspend fun getSettlementByTransactionId(transactionId: String): Result<SettlementRecord?> = runCatching {
        db.ledgerDao().getSettlementByTransactionId(transactionId)?.toModel()
            ?: supabase.postgrest["settlements"].select {
                filter { eq("transaction_id", transactionId) }
            }.decodeSingleOrNull<SettlementRecord>()
    }

    override suspend fun updateSettlement(record: SettlementRecord): Result<Unit> = runCatching {
        supabase.postgrest["settlements"].upsert(record)
        db.ledgerDao().upsertSettlements(listOf(record.toEntity()))
        Unit
    }
}
