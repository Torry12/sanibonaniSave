package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteLedgerDataSource
import com.sanibonani.save.domain.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class SupabaseLedgerDataSourceImpl @Inject constructor(
    private val client: SupabaseClient,
    private val payloadFactory: com.sanibonani.save.data.repository.mappers.DataPayloadFactory
) : RemoteLedgerDataSource {

    override suspend fun getGroupLedger(groupId: String): Result<List<LedgerEntry>> = safeRunCatching {
        client.postgrest["group_ledger"].select { filter { eq("group_id", groupId) } }.decodeList<LedgerEntry>()
    }

    override suspend fun getMemberLedger(memberId: String): Result<List<LedgerEntry>> = safeRunCatching {
        client.postgrest["group_ledger"].select { filter { eq("member_id", memberId) } }.decodeList<LedgerEntry>()
    }

    override suspend fun getPlatformLedger(): Result<List<LedgerEntry>> = safeRunCatching {
        client.postgrest["platform_ledger"].select { order("created_at", order = Order.DESCENDING) }.decodeList<LedgerEntry>().map { it.copy(isPlatform = true) }
    }

    override suspend fun getConsolidatedAuditTrail(groupId: String): Result<List<LedgerEntry>> = safeRunCatching {
        client.postgrest["vw_consolidated_audit_trail"].select {
            filter { eq("group_id", groupId) }
            order("created_at", order = Order.DESCENDING)
        }.decodeList<LedgerEntry>()
    }

    override suspend fun logPlatformEvent(entry: LedgerEntry): Result<Unit> = safeRunCatching {
        val payload = payloadFactory.createLedgerPayload(entry)
        client.postgrest.rpc("record_platform_revenue_v2", payload)
    }

    override suspend fun consolidateGroupLedger(groupId: String): Result<ConsolidationReport> = safeRunCatching {
        client.postgrest.rpc("recalculate_all_group_financials_v3", buildJsonObject { put("p_group_id", groupId) }).decodeAs<ConsolidationReport>()
    }
}
