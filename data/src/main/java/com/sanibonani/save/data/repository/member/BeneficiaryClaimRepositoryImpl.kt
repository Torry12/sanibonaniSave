package com.sanibonani.save.data.repository.member

import com.sanibonani.save.data.datasource.LocalClaimDataSource
import com.sanibonani.save.data.datasource.RemoteClaimDataSource
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

class BeneficiaryClaimRepositoryImpl @Inject constructor(
    private val remoteSource: RemoteClaimDataSource,
    private val localSource: LocalClaimDataSource,
    private val json: Json,
    private val pendingOpDao: com.sanibonani.save.data.local.PendingOperationDao
) : BaseRepository("BeneficiaryClaimRepository"), BeneficiaryClaimRepository {

    override suspend fun submitClaim(
        claim: BeneficiaryPayoutClaim,
        idempotencyKey: String?
    ): Result<BeneficiaryPayoutClaim> {
        val stableClaim = claim.copy(
            id = claim.id ?: idempotencyKey ?: java.util.UUID.randomUUID().toString()
        )
        return safeApiCall(
            operationType = "beneficiary_claim",
            groupId = stableClaim.groupId,
            payload = json.encodeToString(stableClaim),
            dao = pendingOpDao,
            idempotencyKey = idempotencyKey
        ) {
            val saved = if (idempotencyKey != null) {
                remoteSource.getClaimById(stableClaim.id!!).getOrElse {
                    remoteSource.submitClaim(stableClaim).getOrThrow()
                }
            } else {
                remoteSource.submitClaim(stableClaim).getOrThrow()
            }
            localSource.saveClaim(saved)
            saved
        }
    }

    override fun observeClaimsForMember(memberId: String, groupId: String): Flow<Result<List<BeneficiaryPayoutClaim>>> = observeAndSync(
        dbFlow = localSource.observeClaimsForMember(memberId, groupId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getClaimsForMember(memberId, groupId).getOrThrow() },
        cacheSync = { localSource.syncClaimsForMember(memberId, groupId, it) }
    )

    override fun observeClaimsForGroup(groupId: String): Flow<Result<List<BeneficiaryPayoutClaim>>> = observeAndSync(
        dbFlow = localSource.observeClaimsForGroup(groupId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getClaimsForGroup(groupId).getOrThrow() },
        cacheSync = { localSource.syncClaimsForGroup(groupId, it) }
    )

    override suspend fun updateClaimStatus(
        claimId: String,
        status: BeneficiaryClaimStatus,
        reviewedBy: String?,
        adminNotes: String?,
        rejectionReason: String?
    ): Result<Unit> = authoritativeCall("updateClaimStatus") {
        remoteSource.updateClaimStatus(claimId, status, reviewedBy, adminNotes, rejectionReason).getOrThrow()
        localSource.saveClaim(remoteSource.getClaimById(claimId).getOrThrow())
    }

    override suspend fun verifyClaimDocument(
        claimId: String,
        docKey: String,
        status: DocumentStatus
    ): Result<Unit> = authoritativeCall("verifyClaimDocument") {
        remoteSource.updateClaimDocumentStatus(claimId, docKey, status).getOrThrow()
        localSource.saveClaim(remoteSource.getClaimById(claimId).getOrThrow())
    }

    override suspend fun payClaimAtomic(claimId: String, adminId: String, notes: String?): Result<Unit> =
        authoritativeCall("payClaimAtomic") {
            remoteSource.payClaimAtomic(claimId, adminId, notes).getOrThrow()
            localSource.saveClaim(remoteSource.getClaimById(claimId).getOrThrow())
        }

    override fun observeEscalatedClaims(): Flow<Result<List<BeneficiaryPayoutClaim>>> = observeAndSync(
        dbFlow = localSource.observeEscalatedClaims(),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getEscalatedClaims().getOrThrow() },
        cacheSync = { localSource.saveClaims(it) }
    )

    override suspend fun getEscalatedClaimsSync(): Result<List<BeneficiaryPayoutClaim>> = remoteSource.getEscalatedClaims()

    override suspend fun getClaimById(claimId: String): Result<BeneficiaryPayoutClaim> = safeRunCatching {
        val remote = remoteSource.getClaimById(claimId).getOrThrow()
        localSource.saveClaim(remote)
        remote
    }

    companion object {
        fun buildPayClaimRpcParams(
            claimId: String,
            adminId: String,
            fallbackAdminId: String?,
            notes: String?
        ): JsonObject = buildJsonObject {
            put("p_claim_id", claimId)
            notes?.let { put("p_notes", it) }

            val resolvedAdminId = when {
                adminId.isNotBlank() && adminId != "PLATFORM_ADMIN" -> adminId
                !fallbackAdminId.isNullOrBlank() -> fallbackAdminId
                else -> "00000000-0000-0000-0000-000000000000"
            }
            put("p_admin_id", resolvedAdminId)
        }
    }
}
