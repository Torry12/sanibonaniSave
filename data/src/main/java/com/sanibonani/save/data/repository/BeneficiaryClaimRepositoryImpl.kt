package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.repository.BeneficiaryClaimRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import com.sanibonani.save.data.utils.logAndGetMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val PRIMARY_TABLE = "beneficiary_payout_claims"
private const val LEGACY_TABLE = "burial_claims"

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

    private inline fun <T> withClaimsTable(block: (String) -> T): T {
        return try {
            block(PRIMARY_TABLE)
        } catch (e: Exception) {
            if (isMissingPrimaryTableError(e)) {
                block(LEGACY_TABLE)
            } else {
                throw IllegalStateException(e.logAndGetMessage(tag))
            }
        }
    }

    private fun isMissingPrimaryTableError(error: Throwable): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return (
            message.contains("could not find the table") ||
                message.contains("does not exist")
            ) && message.contains(PRIMARY_TABLE)
    }

    override suspend fun submitClaim(claim: BeneficiaryPayoutClaim): Result<BeneficiaryPayoutClaim> =
        retryWithExponentialBackoff {
            runCatching {
                val saved = withClaimsTable { table ->
                    supabase.postgrest[table].upsert(claim) {
                        onConflict = "id"
                        select()
                    }.decodeSingle<BeneficiaryPayoutClaim>()
                }
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
            withClaimsTable { table ->
                supabase.postgrest[table]
                    .select(columns = Columns.raw(CLAIM_COLUMNS)) {
                        filter {
                            eq("member_id", memberId)
                            eq("group_id", groupId)
                        }
                    }.decodeList<BeneficiaryPayoutClaim>()
            }
        },
        cacheSync = { list -> db.beneficiaryClaimDao().syncForMember(memberId, groupId, list) }
    )

    override fun observeClaimsForGroup(groupId: String): Flow<Result<List<BeneficiaryPayoutClaim>>> =
        observeAndSync(
            dbFlow = db.beneficiaryClaimDao().observeClaimsForGroup(groupId),
            mapper = { it.toModel() },
            toEntity = { it.toEntity() },
            networkFetch = {
                withClaimsTable { table ->
                    supabase.postgrest[table]
                        .select(columns = Columns.raw(CLAIM_COLUMNS)) {
                            filter { eq("group_id", groupId) }
                        }.decodeList<BeneficiaryPayoutClaim>()
                }
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
            withClaimsTable { table ->
                supabase.postgrest[table].update(buildJsonObject {
                    put("status", status.name.lowercase())
                    reviewedBy?.let { put("reviewed_by", it) }
                    adminNotes?.let { put("admin_notes", it) }
                    rejectionReason?.let { put("rejection_reason", it) }
                    put("reviewed_at", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                }) {
                    filter { eq("id", claimId) }
                }
            }
            // Refresh local cache
            withClaimsTable { table ->
                supabase.postgrest[table]
                    .select(columns = Columns.raw(CLAIM_COLUMNS)) {
                        filter { eq("id", claimId) }
                    }.decodeSingleOrNull<BeneficiaryPayoutClaim>()?.let {
                        db.beneficiaryClaimDao().upsertClaim(it.toEntity())
                    }
                }
            Unit
        }
    }

    override suspend fun payClaimAtomic(
        claimId: String,
        adminId: String,
        notes: String?
    ): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            val rpcParams = buildPayClaimRpcParams(
                claimId = claimId,
                adminId = adminId,
                fallbackAdminId = supabase.auth.currentUserOrNull()?.id,
                notes = notes
            )
            supabase.postgrest.rpc("pay_burial_claim_v1", rpcParams)

            // Refresh local cache for this claim
            getClaimById(claimId).onSuccess { updated ->
                db.beneficiaryClaimDao().upsertClaim(updated.toEntity())
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
                withClaimsTable { table ->
                    supabase.postgrest[table]
                        .select(columns = Columns.raw(CLAIM_COLUMNS)) {
                            filter { eq("status", "escalated") }
                        }.decodeList<BeneficiaryPayoutClaim>()
                }
            },
            cacheSync = { list ->
                // Only upsert escalated ones – don't wipe other statuses
                db.beneficiaryClaimDao().upsertClaims(list)
            }
        )

    override suspend fun getClaimById(claimId: String): Result<BeneficiaryPayoutClaim> = retryWithExponentialBackoff {
        runCatching {
            val claim = withClaimsTable { table ->
                supabase.postgrest[table]
                    .select(columns = Columns.raw(CLAIM_COLUMNS)) {
                        filter { eq("id", claimId) }
                    }.decodeSingleOrNull<BeneficiaryPayoutClaim>()
            } ?: throw Exception("Claim not found: $claimId")
            
            db.beneficiaryClaimDao().upsertClaim(claim.toEntity())
            claim
        }
    }

    companion object {
        private val UUID_REGEX = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )

        internal fun buildPayClaimRpcParams(
            claimId: String,
            adminId: String,
            fallbackAdminId: String?,
            notes: String?
        ): JsonObject = buildJsonObject {
            put("p_claim_id", claimId)
            val safeAdminId = adminId.takeIf { it.isValidUuid() }
                ?: fallbackAdminId?.takeIf { it.isValidUuid() }
            safeAdminId?.let { put("p_admin_id", it) }
            notes?.let { put("p_notes", it) }
        }

        private fun String.isValidUuid(): Boolean = UUID_REGEX.matches(trim())
    }
}

