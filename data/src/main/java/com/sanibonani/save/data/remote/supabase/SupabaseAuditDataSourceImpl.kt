package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteAuditDataSource
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.service.*
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class SupabaseAuditDataSourceImpl @Inject constructor(
    protocol: DataConnectionProtocol
) : BaseSupabaseDataSource(protocol), RemoteAuditDataSource {

    private val tag = "SupabaseAudit"

    override suspend fun generateMemberAudit(memberId: String): Result<AuditResult<MemberAuditReport>> = safeRunCatching("$tag.member") {
        val response = client.postgrest.rpc(
            function = "generate_member_audit_report_v1",
            parameters = buildJsonObject { put("p_member_id", memberId) }
        )
        val rawJson = response.data.toString()
        AppLogger.d(tag, "Member audit report received (${rawJson.length} chars)")
        // The RPC answers SQL NULL for an id that does not exist; decoding that into the
        // non-nullable model throws a serializer error the admin cannot act on.
        val report = response.decodeAs<MemberAuditReport?>()
            ?: error("No member has the id $memberId, so no audit report could be produced.")
        AuditResult(data = report, rawJson = rawJson)
    }

    override suspend fun generateGroupAudit(groupId: String): Result<AuditResult<GroupAuditReport>> = safeRunCatching("$tag.group") {
        val response = client.postgrest.rpc(
            function = "generate_group_audit_report_v1",
            parameters = buildJsonObject { put("p_group_id", groupId) }
        )
        val rawJson = response.data.toString()
        AppLogger.d(tag, "Group audit report received (${rawJson.length} chars)")
        val report = response.decodeAs<GroupAuditReport?>()
            ?: error("No group has the id $groupId, so no audit report could be produced.")
        AuditResult(data = report, rawJson = rawJson)
    }

    override suspend fun generatePlatformAudit(): Result<AuditResult<PlatformAuditReport>> = safeRunCatching("$tag.platform") {
        val response = client.postgrest.rpc(
            function = "generate_platform_audit_report_v1",
            parameters = buildJsonObject {}
        )
        val rawJson = response.data.toString()
        AppLogger.d(tag, "Platform audit report received (${rawJson.length} chars)")
        AuditResult(data = response.decodeAs<PlatformAuditReport>(), rawJson = rawJson)
    }

    override suspend fun getLatestPlatformAuditReport(): Result<PlatformAuditReport?> = safeRunCatching {
        client.postgrest["platform_audit_reports"].select {
            order("generated_at", order = Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<PlatformAuditReport>()
    }


    override suspend fun auditAndFixGroupBalance(groupId: String, fix: Boolean): Result<GroupAuditResult> = safeRunCatching {
        client.postgrest.rpc(
            function = "audit_and_fix_group_balance_v1",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                put("p_fix", fix)
            }
        ).decodeAs<GroupAuditResult>()
    }

    override suspend fun getAuditLogs(limit: Int): Result<List<AuditLog>> =
        loadAuditLogs(limit = limit)

    override suspend fun getAuditLogsForMember(memberId: String, limit: Int): Result<List<AuditLog>> =
        loadAuditLogs("target_member_id", memberId, limit)

    override suspend fun getAuditLogsForGroup(groupId: String, limit: Int): Result<List<AuditLog>> =
        loadAuditLogs("target_group_id", groupId, limit)

    private suspend fun loadAuditLogs(
        targetColumn: String? = null,
        targetId: String? = null,
        limit: Int
    ): Result<List<AuditLog>> = safeRunCatching {
        client.postgrest["audit_logs"].select {
            if (targetColumn != null && targetId != null) {
                filter { eq(targetColumn, targetId) }
            }
            order("created_at", order = Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<AuditLog>()
    }

    override suspend fun insertAuditLog(log: AuditLog): Result<Unit> = safeRunCatching {
        client.postgrest["audit_logs"].insert(log)
        Unit
    }
}


