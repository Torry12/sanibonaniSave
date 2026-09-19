package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.GroupAuditReport
import com.sanibonani.save.domain.model.MemberAuditReport
import com.sanibonani.save.domain.model.PlatformAuditReport
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Strategy Pattern for generating different types of forensic audit reports.
 */
interface AuditReportStrategy<T> {
    suspend fun generate(id: String? = null): Result<T>
}

class MemberAuditStrategy(private val supabase: SupabaseClient) : AuditReportStrategy<MemberAuditReport> {
    override suspend fun generate(id: String?): Result<MemberAuditReport> = runCatching {
        requireNotNull(id) { "Member ID is required for audit" }
        supabase.postgrest.rpc(
            function = "generate_member_audit_report_v1",
            parameters = buildJsonObject { put("p_member_id", id) }
        ).decodeSingle<MemberAuditReport>()
    }
}

class GroupAuditStrategy(private val supabase: SupabaseClient) : AuditReportStrategy<GroupAuditReport> {
    override suspend fun generate(id: String?): Result<GroupAuditReport> = runCatching {
        requireNotNull(id) { "Group ID is required for audit" }
        supabase.postgrest.rpc(
            function = "generate_group_audit_report_v1",
            parameters = buildJsonObject { put("p_group_id", id) }
        ).decodeSingle<GroupAuditReport>()
    }
}

class PlatformAuditStrategy(private val supabase: SupabaseClient) : AuditReportStrategy<PlatformAuditReport> {
    override suspend fun generate(id: String?): Result<PlatformAuditReport> = runCatching {
        supabase.postgrest.rpc(
            function = "generate_platform_audit_report_v1",
            parameters = buildJsonObject { }
        ).decodeSingle<PlatformAuditReport>()
    }
}
