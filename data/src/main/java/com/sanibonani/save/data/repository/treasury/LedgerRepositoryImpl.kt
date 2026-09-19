package com.sanibonani.save.data.repository.treasury

import com.sanibonani.save.data.local.*
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.repository.mappers.*
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LedgerRepositoryImpl @Inject constructor(
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>,
    private val dbProvider: javax.inject.Provider<SanibonaniDatabase>,
) : BaseRepository("LedgerRepository"), LedgerRepository {
    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    override fun observeGroupLedger(groupId: String): Flow<Result<List<LedgerEntry>>> = observeAndSync(
        dbFlow = db.ledgerDao().observeLedger(groupId),
        mapper = { it.toModel() },
        toEntity = { it.toLedgerEntity() },
        networkFetch = {
            supabase.postgrest["group_ledger"].select(columns = Columns.raw("*")) {
                filter { eq("group_id", groupId) }
            }.decodeList<LedgerEntry>()
        },
        cacheSync = { list -> db.ledgerDao().syncLedger(groupId, list) }
    )

    override suspend fun getGroupLedger(groupId: String): Result<List<LedgerEntry>> = runCatching {
        supabase.postgrest["group_ledger"].select(columns = Columns.raw("*")) {
            filter { eq("group_id", groupId) }
        }.decodeList<LedgerEntry>()
    }

    override fun observeMemberLedger(memberId: String): Flow<Result<List<LedgerEntry>>> = observeAndSync(
        dbFlow = db.ledgerDao().observeMemberLedger(memberId),
        mapper = { it.toModel() },
        toEntity = { it.toLedgerEntity() },
        networkFetch = {
            supabase.postgrest["group_ledger"].select(columns = Columns.raw("*")) {
                filter { eq("member_id", memberId) }
            }.decodeList<LedgerEntry>()
        },
        cacheSync = { list -> db.ledgerDao().upsertLedger(list) }
    )

    override suspend fun getMemberLedger(memberId: String): Result<List<LedgerEntry>> = runCatching {
        supabase.postgrest["group_ledger"].select(columns = Columns.raw("*")) {
            filter { eq("member_id", memberId) }
        }.decodeList<LedgerEntry>()
    }

    override fun observePlatformLedger(): Flow<Result<List<LedgerEntry>>> = observeAndSync(
        dbFlow = db.ledgerDao().observePlatformLedger(),
        mapper = { it.toModel() },
        toEntity = { it.toLedgerEntity() },
        networkFetch = {
            supabase.postgrest["platform_ledger"].select {
                order("created_at", order = Order.DESCENDING)
            }.decodeList<LedgerEntry>().map { it.copy(isPlatform = true) }
        },
        cacheSync = { list -> db.ledgerDao().upsertLedger(list) }
    )

    override suspend fun getPlatformLedger(): Result<List<LedgerEntry>> = runCatching {
        supabase.postgrest["platform_ledger"].select {
            order("created_at", order = Order.DESCENDING)
        }.decodeList<LedgerEntry>().map { it.copy(isPlatform = true) }
    }

    override suspend fun logPlatformEvent(entry: LedgerEntry): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            AppLogger.d(tag, "🪵 Logging platform event: ${entry.description}")
            val rpcParams = buildJsonObject {
                put("p_amount", entry.amount.amount.toPlainString())
                put("p_description", entry.description)
                put("p_category", entry.category)
                put("p_transaction_id", entry.transactionId)
                put("p_group_id", entry.groupId)
            }
            supabase.postgrest.rpc("record_platform_revenue_v1", rpcParams).decodeAs<JsonElement>()
            Unit
        }
    }

    override suspend fun consolidateGroupLedger(groupId: String): Result<ConsolidationReport> = retryWithExponentialBackoff {
        runCatching {
            AppLogger.i(tag, "🔄 Triggering deep ledger consolidation for group: $groupId")
            val report = supabase.postgrest.rpc(
                function = "recalculate_all_group_financials_v3",
                parameters = buildJsonObject { put("p_group_id", groupId) }
            ).decodeAs<ConsolidationReport>()

            // Update local reconciliation record and refresh group metadata (balance)
            db.ledgerDao().upsertReconciliations(listOf(report.reconciliationReport.toEntity()))

            // This ensures the local group balance is authoritatively synced with the reconstruction result
            db.groupDao().getGroupById(groupId)?.let { local ->
                db.groupDao().upsertGroup(local.copy(balance = report.finalBalance))
            }

            report
        }
    }

    override suspend fun createCheckpoint(groupId: String): Result<String> = runCatching {
        supabase.postgrest.rpc(
            function = "create_ledger_checkpoint_v1",
            parameters = buildJsonObject { put("p_group_id", groupId) }
        ).decodeAs<String>()
    }

    override fun observeConsolidatedAuditTrail(groupId: String): Flow<Result<List<LedgerEntry>>> = observeAndSync(
        dbFlow = db.ledgerDao().observeConsolidatedLedger(groupId),
        mapper = { it.toModel() },
        toEntity = { it.toLedgerEntity() },
        networkFetch = {
            supabase.postgrest["vw_consolidated_audit_trail"].select {
                filter { eq("group_id", groupId) }
                order("created_at", order = Order.DESCENDING)
            }.decodeList<LedgerEntry>()
        },
        cacheSync = { entities ->
            // For now, we mix them in the same table in Room
            db.ledgerDao().upsertLedger(entities)
        }
    )
}
