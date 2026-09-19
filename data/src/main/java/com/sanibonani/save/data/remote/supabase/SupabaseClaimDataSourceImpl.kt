package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteClaimDataSource
import com.sanibonani.save.domain.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClaimDataSourceImpl @Inject constructor(
    private val client: SupabaseClient
) : RemoteClaimDataSource {

    private val PRIMARY_TABLE = "beneficiary_payout_claims"
    private val CLAIM_COLUMNS_PRIMARY = "id,group_id,member_id,beneficiary_id,beneficiary_name,face_photo_url,beneficiary_id_url,death_certificate_url,member_id_url,cause_of_death,date_of_death,claim_amount,bank_name,account_no,branch_code,account_holder,notes,status,reviewed_by,reviewed_at,admin_notes,rejection_reason,created_at"

    override suspend fun submitClaim(claim: BeneficiaryPayoutClaim): Result<BeneficiaryPayoutClaim> = runCatching {
        client.postgrest[PRIMARY_TABLE].upsert(claim) {
            onConflict = "id"
            select(columns = Columns.raw(CLAIM_COLUMNS_PRIMARY))
        }.decodeSingle<BeneficiaryPayoutClaim>()
    }

    override suspend fun getClaimsForMember(memberId: String, groupId: String): Result<List<BeneficiaryPayoutClaim>> = runCatching {
        client.postgrest[PRIMARY_TABLE].select(columns = Columns.raw(CLAIM_COLUMNS_PRIMARY)) {
            filter {
                eq("member_id", memberId)
                eq("group_id", groupId)
            }
        }.decodeList<BeneficiaryPayoutClaim>()
    }

    override suspend fun getClaimsForGroup(groupId: String): Result<List<BeneficiaryPayoutClaim>> = runCatching {
        client.postgrest[PRIMARY_TABLE].select(columns = Columns.raw(CLAIM_COLUMNS_PRIMARY)) {
            filter { eq("group_id", groupId) }
        }.decodeList<BeneficiaryPayoutClaim>()
    }

    override suspend fun getClaimById(id: String): Result<BeneficiaryPayoutClaim> = runCatching {
        client.postgrest[PRIMARY_TABLE].select(columns = Columns.raw(CLAIM_COLUMNS_PRIMARY)) {
            filter { eq("id", id) }
        }.decodeSingle<BeneficiaryPayoutClaim>()
    }

    override suspend fun updateClaimStatus(claimId: String, status: BeneficiaryClaimStatus, reviewedBy: String?, adminNotes: String?, rejectionReason: String?): Result<Unit> = runCatching {
        client.postgrest[PRIMARY_TABLE].update(buildJsonObject {
            put("status", status.name.lowercase())
            reviewedBy?.let { put("reviewed_by", it) }
            adminNotes?.let { put("admin_notes", it) }
            rejectionReason?.let { put("rejection_reason", it) }
            put("reviewed_at", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }) { filter { eq("id", claimId) } }
    }

    override suspend fun payClaimAtomic(claimId: String, adminId: String, notes: String?): Result<Unit> = runCatching {
        client.postgrest.rpc("pay_burial_claim_v1", buildJsonObject {
            put("p_claim_id", claimId)
            put("p_admin_id", adminId)
            notes?.let { put("p_notes", it) }
        })
    }

    override suspend fun getEscalatedClaims(): Result<List<BeneficiaryPayoutClaim>> = runCatching {
        client.postgrest[PRIMARY_TABLE].select(columns = Columns.raw(CLAIM_COLUMNS_PRIMARY)) {
            filter { eq("status", "escalated") }
        }.decodeList<BeneficiaryPayoutClaim>()
    }
}
