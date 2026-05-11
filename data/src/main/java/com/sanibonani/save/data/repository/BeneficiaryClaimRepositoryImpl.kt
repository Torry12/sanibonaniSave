package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.repository.BeneficiaryClaimRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TABLE = "burial_claims"

private val CLAIM_COLUMNS = listOf(
    "id", "group_id", "member_id", "beneficiary_id", "beneficiary_name",
    "cause_of_death", "date_of_death", "claim_amount", "bank_name",
    "account_no", "branch_code", "account_holder", "notes", "status",
    "reviewed_by", "reviewed_at", "admin_notes", "rejection_reason", "created_at"
).joinToString(",")

@Singleton
class BeneficiaryClaimRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase
) : BaseRepository("BeneficiaryClaimRepository"), BeneficiaryClaimRepository {

    override suspend fun submitClaim(claim: BeneficiaryPayoutClaim): Result<BeneficiaryPayoutClaim> =
        retryWithExponentialBackoff {
            runCatching {
                val saved = supabase.postgrest[TABLE].upsert(claim) {
                    onConflict = "id"
                    select()
                }.decodeSingle<BeneficiaryPayoutClaim>()
                db.beneficiaryClaimDao().upsertClaim(saved.toEntity())
                saved
            }
        }

    override fun observeClaimsForMember(
        memberId: String,
        groupId: String
    ): Flow<Result<List<BeneficiaryPayoutClaim>>> = observeAndSync(
        dbFlow = db.beneficiaryClaimDao().observeClaimsForMember(memberId, groupId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest[TABLE]
                .select(columns = Columns.raw(CLAIM_COLUMNS)) {
                    filter {
                        eq("member_id", memberId)
                        eq("group_id", groupId)
                    }
                }.decodeList<BeneficiaryPayoutClaim>()
        },
        cacheSync = { list -> db.beneficiaryClaimDao().syncForMember(memberId, groupId, list) }
    )

    override fun observeClaimsForGroup(groupId: String): Flow<Result<List<BeneficiaryPayoutClaim>>> =
        observeAndSync(
            dbFlow = db.beneficiaryClaimDao().observeClaimsForGroup(groupId),
            mapper = { it.toModel() },
            toEntity = { it.toEntity() },
            networkFetch = {
                supabase.postgrest[TABLE]
                    .select(columns = Columns.raw(CLAIM_COLUMNS)) {
                        filter { eq("group_id", groupId) }
                    }.decodeList<BeneficiaryPayoutClaim>()
            },
            cacheSync = { list -> db.beneficiaryClaimDao().syncForGroup(groupId, list) }
        )

    override suspend fun updateClaimStatus(
        claimId: String,
        status: BeneficiaryClaimStatus,
        reviewedBy: String?,
        adminNotes: String?,
        rejectionReason: String?
    ): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest[TABLE].update(buildJsonObject {
                put("status", status.name.lowercase())
                reviewedBy?.let { put("reviewed_by", it) }
                adminNotes?.let { put("admin_notes", it) }
                rejectionReason?.let { put("rejection_reason", it) }
                put("reviewed_at", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            }) {
                filter { eq("id", claimId) }
            }
            // Refresh local cache
            supabase.postgrest[TABLE]
                .select(columns = Columns.raw(CLAIM_COLUMNS)) {
                    filter { eq("id", claimId) }
                }.decodeSingleOrNull<BeneficiaryPayoutClaim>()?.let {
                    db.beneficiaryClaimDao().upsertClaim(it.toEntity())
                }
            Unit
        }
    }

    override fun observeEscalatedClaims(): Flow<Result<List<BeneficiaryPayoutClaim>>> =
        observeAndSync(
            dbFlow = db.beneficiaryClaimDao().observeEscalatedClaims(),
            mapper = { it.toModel() },
            toEntity = { it.toEntity() },
            networkFetch = {
                supabase.postgrest[TABLE]
                    .select(columns = Columns.raw(CLAIM_COLUMNS)) {
                        filter { eq("status", "escalated") }
                    }.decodeList<BeneficiaryPayoutClaim>()
            },
            cacheSync = { list ->
                // Only upsert escalated ones – don't wipe other statuses
                db.beneficiaryClaimDao().upsertClaims(list)
            }
        )
}

