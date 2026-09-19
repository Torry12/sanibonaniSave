package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteAuditDataSource
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.service.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuditDataSourceImpl @Inject constructor(
    private val client: SupabaseClient
) : RemoteAuditDataSource {

    override suspend fun generateMemberAudit(memberId: String): Result<AuditResult<MemberAuditReport>> = runCatching {
        val response = client.postgrest.rpc(
            function = "generate_member_audit_report_v1",
            parameters = buildJsonObject { put("p_member_id", memberId) }
        )
        AuditResult(data = response.decodeSingle<MemberAuditReport>(), rawJson = response.data.toString())
    }

    override suspend fun generateGroupAudit(groupId: String): Result<AuditResult<GroupAuditReport>> = runCatching {
        val response = client.postgrest.rpc(
            function = "generate_group_audit_report_v1",
            parameters = buildJsonObject { put("p_group_id", groupId) }
        )
        AuditResult(data = response.decodeSingle<GroupAuditReport>(), rawJson = response.data.toString())
    }

    override suspend fun generatePlatformAudit(): Result<AuditResult<PlatformAuditReport>> = runCatching {
        val response = client.postgrest.rpc(function = "generate_platform_audit_report_v1", parameters = buildJsonObject {})
        AuditResult(data = response.decodeSingle<PlatformAuditReport>(), rawJson = response.data.toString())
    }
}
