package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteReconciliationDataSource
import com.sanibonani.save.domain.model.ConsolidationReport
import com.sanibonani.save.domain.model.ReconciliationRecord
import com.sanibonani.save.domain.model.ReconciliationStatus
import com.sanibonani.save.domain.model.SettlementRecord
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class SupabaseReconciliationDataSourceImpl @Inject constructor(
    private val client: SupabaseClient
) : RemoteReconciliationDataSource {

    override suspend fun getReconciliations(groupId: String): Result<List<ReconciliationRecord>> = safeRunCatching {
        client.postgrest["reconciliations"].select {
            filter { eq("group_id", groupId) }
        }.decodeList<ReconciliationRecord>()
    }

    override suspend fun performReconciliation(groupId: String): Result<ReconciliationRecord> = safeRunCatching {
        // Map to v3 for gold-standard consistency
        val res = client.postgrest.rpc(
            function = "recalculate_all_group_financials_v3",
            parameters = buildJsonObject { put("p_group_id", groupId) }
        ).decodeAs<ConsolidationReport>()

        res.reconciliationRecord ?: throw Exception("Reconciliation record missing from report")
    }

    override suspend fun resolveDiscrepancy(reconciliationId: String, notes: String): Result<Unit> = safeRunCatching {
        client.postgrest["reconciliations"].update(buildJsonObject {
            put("status", ReconciliationStatus.RESOLVED.name.lowercase())
            put("notes", notes)
        }) {
            filter { eq("id", reconciliationId) }
        }
    }

    override suspend fun getSettlements(): Result<List<SettlementRecord>> = safeRunCatching {
        client.postgrest["settlements"].select().decodeList<SettlementRecord>()
    }

    override suspend fun getSettlementByTransactionId(transactionId: String): Result<SettlementRecord?> = safeRunCatching {
        client.postgrest["settlements"].select {
            filter { eq("transaction_id", transactionId) }
        }.decodeSingleOrNull<SettlementRecord>()
    }

    override suspend fun updateSettlement(record: SettlementRecord): Result<Unit> = safeRunCatching {
        client.postgrest["settlements"].upsert(record) {
            onConflict = "id"
        }
    }
}
