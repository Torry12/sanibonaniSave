package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteClaimDataSource
import com.sanibonani.save.data.repository.member.BeneficiaryClaimRepositoryImpl
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
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class SupabaseClaimDataSourceImpl @Inject constructor(
    private val client: SupabaseClient
) : RemoteClaimDataSource {

    private val PRIMARY_TABLE = "beneficiary_payout_claims"
    private val CLAIM_COLUMNS_PRIMARY = "id,group_id,member_id,beneficiary_id,beneficiary_name,face_photo_url,face_photo_status,beneficiary_id_url,beneficiary_id_status,death_certificate_url,death_certificate_status,member_id_url,member_id_status,cause_of_death,date_of_death,claim_amount,bank_name,account_no,branch_code,account_holder,notes,status,reviewed_by,reviewed_at,admin_notes,rejection_reason,created_at"

    override suspend fun submitClaim(claim: BeneficiaryPayoutClaim): Result<BeneficiaryPayoutClaim> = safeRunCatching {
        client.postgrest[PRIMARY_TABLE].upsert(claim) {
            onConflict = "id"
            select(columns = Columns.raw(CLAIM_COLUMNS_PRIMARY))
        }.decodeSingle<BeneficiaryPayoutClaim>()
    }

    override suspend fun getClaimsForMember(memberId: String, groupId: String): Result<List<BeneficiaryPayoutClaim>> = safeRunCatching {
        client.postgrest[PRIMARY_TABLE].select(columns = Columns.raw(CLAIM_COLUMNS_PRIMARY)) {
            filter {
                eq("member_id", memberId)
                eq("group_id", groupId)
            }
        }.decodeList<BeneficiaryPayoutClaim>()
    }

    override suspend fun getClaimsForGroup(groupId: String): Result<List<BeneficiaryPayoutClaim>> = safeRunCatching {
        client.postgrest[PRIMARY_TABLE].select(columns = Columns.raw(CLAIM_COLUMNS_PRIMARY)) {
            filter { eq("group_id", groupId) }
        }.decodeList<BeneficiaryPayoutClaim>()
    }

    override suspend fun getClaimById(id: String): Result<BeneficiaryPayoutClaim> = safeRunCatching {
        client.postgrest[PRIMARY_TABLE].select(columns = Columns.raw(CLAIM_COLUMNS_PRIMARY)) {
            filter { eq("id", id) }
        }.decodeSingle<BeneficiaryPayoutClaim>()
    }

    override suspend fun updateClaimStatus(claimId: String, status: BeneficiaryClaimStatus, reviewedBy: String?, adminNotes: String?, rejectionReason: String?): Result<Unit> = safeRunCatching {
        client.postgrest[PRIMARY_TABLE].update(buildJsonObject {
            put("status", status.name.lowercase())
            reviewedBy?.let { put("reviewed_by", it) }
            adminNotes?.let { put("admin_notes", it) }
            rejectionReason?.let { put("rejection_reason", it) }
            put("reviewed_at", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }) { filter { eq("id", claimId) } }
    }

    override suspend fun updateClaimDocumentStatus(claimId: String, docKey: String, status: DocumentStatus): Result<Unit> = safeRunCatching {
        val column = "${docKey}_status"
        client.postgrest[PRIMARY_TABLE].update(buildJsonObject {
            put(column, status.name.lowercase())
        }) { filter { eq("id", claimId) } }
    }

    override suspend fun payClaimAtomic(claimId: String, adminId: String, notes: String?): Result<Unit> = safeRunCatching {
        val rpcParams = BeneficiaryClaimRepositoryImpl.buildPayClaimRpcParams(
            claimId = claimId,
            adminId = adminId,
            fallbackAdminId = null, // No fallback in direct UI calls usually
            notes = notes
        )
        client.postgrest.rpc("pay_burial_claim_v1", rpcParams)
    }

    override suspend fun getEscalatedClaims(): Result<List<BeneficiaryPayoutClaim>> = safeRunCatching {
        val statuses = listOf(BeneficiaryClaimStatus.ESCALATED.name.lowercase(), BeneficiaryClaimStatus.APPROVED.name.lowercase())
        client.postgrest[PRIMARY_TABLE].select(columns = Columns.raw(CLAIM_COLUMNS_PRIMARY)) {
            filter { or { statuses.forEach { eq("status", it) } } }
        }.decodeList<BeneficiaryPayoutClaim>()
    }
}
