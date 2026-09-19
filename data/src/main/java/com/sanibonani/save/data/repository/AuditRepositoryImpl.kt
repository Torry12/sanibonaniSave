package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.GroupAuditReport
import com.sanibonani.save.domain.model.MemberAuditReport
import com.sanibonani.save.domain.model.PlatformAuditReport
import com.sanibonani.save.domain.repository.AuditRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditRepositoryImpl @Inject constructor(
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>
) : AuditRepository {
    private val supabase: SupabaseClient get() = supabaseProvider.get()

    override suspend fun getMemberAuditReport(memberId: String): Result<MemberAuditReport> = runCatching {
        supabase.postgrest.rpc(
            function = "generate_member_audit_report_v1",
            parameters = mapOf("p_member_id" to memberId)
        ).decodeSingle<MemberAuditReport>()
    }

    override suspend fun getGroupAuditReport(groupId: String): Result<GroupAuditReport> = runCatching {
        supabase.postgrest.rpc(
            function = "generate_group_audit_report_v1",
            parameters = mapOf("p_group_id" to groupId)
        ).decodeSingle<GroupAuditReport>()
    }

    override suspend fun getPlatformAuditReport(): Result<PlatformAuditReport> = runCatching {
        supabase.postgrest.rpc(
            function = "generate_platform_audit_report_v1"
        ).decodeSingle<PlatformAuditReport>()
    }
}
