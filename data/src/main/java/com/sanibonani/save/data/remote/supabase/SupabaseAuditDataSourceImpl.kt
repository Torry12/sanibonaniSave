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

@Singleton
class SupabaseAuditDataSourceImpl @Inject constructor(
    protocol: DataConnectionProtocol
) : BaseSupabaseDataSource(protocol), RemoteAuditDataSource {

    private val tag = "SupabaseAudit"

    override suspend fun generateMemberAudit(memberId: String): Result<AuditResult<MemberAuditReport>> = runCatching {
        try {
            val response = client.postgrest.rpc(
                function = "generate_member_audit_report_v1",
                parameters = buildJsonObject { put("p_member_id", memberId) }
            )
            val rawJson = response.data.toString()
            AppLogger.d(tag, "Member Audit Raw JSON: $rawJson")
            val data = response.decodeAs<MemberAuditReport>()
            AuditResult(data = data, rawJson = rawJson)
        } catch (e: Exception) {
            AppLogger.e(tag, "Member Audit Failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun generateGroupAudit(groupId: String): Result<AuditResult<GroupAuditReport>> = runCatching {
        try {
            val response = client.postgrest.rpc(
                function = "generate_group_audit_report_v1",
                parameters = buildJsonObject { put("p_group_id", groupId) }
            )
            val rawJson = response.data.toString()
            AppLogger.d(tag, "Group Audit Raw JSON: $rawJson")
            val data = response.decodeAs<GroupAuditReport>()
            AuditResult(data = data, rawJson = rawJson)
        } catch (e: Exception) {
            AppLogger.e(tag, "Group Audit Failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun generatePlatformAudit(): Result<AuditResult<PlatformAuditReport>> = runCatching {
        try {
            val response = client.postgrest.rpc(
                function = "generate_platform_audit_report_v1",
                parameters = buildJsonObject {}
            )
            val rawJson = response.data.toString()
            AppLogger.d(tag, "Platform Audit Raw JSON: $rawJson")
            val data = response.decodeAs<PlatformAuditReport>()
            AuditResult(data = data, rawJson = rawJson)
        } catch (e: Exception) {
            AppLogger.e(tag, "Platform Audit Failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun getLatestPlatformAuditReport(): Result<PlatformAuditReport?> = runCatching {
        client.postgrest["platform_audit_reports"].select {
            order("generated_at", order = Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull<PlatformAuditReport>()
    }


    override suspend fun auditAndFixGroupBalance(groupId: String, fix: Boolean): Result<GroupAuditResult> = runCatching {
        client.postgrest.rpc(
            function = "audit_and_fix_group_balance_v1",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                put("p_fix", fix)
            }
        ).decodeAs<GroupAuditResult>()
    }

    override suspend fun getAuditLogs(limit: Int): Result<List<AuditLog>> = runCatching {
        client.postgrest["audit_logs"].select {
            order("created_at", order = Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<AuditLog>()
    }

    override suspend fun getAuditLogsForMember(memberId: String, limit: Int): Result<List<AuditLog>> = runCatching {
        client.postgrest["audit_logs"].select {
            filter { eq("target_member_id", memberId) }
            order("created_at", order = Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<AuditLog>()
    }

    override suspend fun getAuditLogsForGroup(groupId: String, limit: Int): Result<List<AuditLog>> = runCatching {
        client.postgrest["audit_logs"].select {
            filter { eq("target_group_id", groupId) }
            order("created_at", order = Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<AuditLog>()
    }

    override suspend fun insertAuditLog(log: AuditLog): Result<Unit> = runCatching {
        client.postgrest["audit_logs"].insert(log)
        Unit
    }
}




