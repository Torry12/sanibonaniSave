package com.sanibonani.save.data.repository.member

import com.sanibonani.save.data.datasource.LocalClaimDataSource
import com.sanibonani.save.data.datasource.RemoteClaimDataSource
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import javax.inject.Inject

class BeneficiaryClaimRepositoryImpl @Inject constructor(
    private val remoteSource: RemoteClaimDataSource,
    private val localSource: LocalClaimDataSource,
    private val json: Json,
    private val pendingOpDao: com.sanibonani.save.data.local.PendingOperationDao
) : BaseRepository("BeneficiaryClaimRepository"), BeneficiaryClaimRepository {

    override suspend fun submitClaim(claim: BeneficiaryPayoutClaim): Result<BeneficiaryPayoutClaim> =
        safeApiCall(
            operationType = "beneficiary_claim",
            groupId = claim.groupId,
            payload = json.encodeToString(claim),
            dao = pendingOpDao
        ) {
            val saved = remoteSource.submitClaim(claim).getOrThrow()
            localSource.saveClaim(saved)
            saved
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

    override suspend fun updateClaimStatus(claimId: String, status: BeneficiaryClaimStatus, reviewedBy: String?, adminNotes: String?, rejectionReason: String?): Result<Unit> = runCatching {
        remoteSource.updateClaimStatus(claimId, status, reviewedBy, adminNotes, rejectionReason).getOrThrow()
        val updated = remoteSource.getClaimById(claimId).getOrThrow()
        localSource.saveClaim(updated)
    }

    override suspend fun verifyClaimDocument(claimId: String, docKey: String, status: DocumentStatus): Result<Unit> = runCatching {
        remoteSource.updateClaimDocumentStatus(claimId, docKey, status).getOrThrow()
        val updated = remoteSource.getClaimById(claimId).getOrThrow()
        localSource.saveClaim(updated)
    }

    override suspend fun payClaimAtomic(claimId: String, adminId: String, notes: String?): Result<Unit> = runCatching {
        remoteSource.payClaimAtomic(claimId, adminId, notes).getOrThrow()
        val updated = remoteSource.getClaimById(claimId).getOrThrow()
        localSource.saveClaim(updated)
    }

    override fun observeEscalatedClaims(): Flow<Result<List<BeneficiaryPayoutClaim>>> = observeAndSync(
        dbFlow = localSource.observeEscalatedClaims(),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getEscalatedClaims().getOrThrow() },
        cacheSync = { localSource.saveClaims(it) }
    )

    override suspend fun getEscalatedClaimsSync(): Result<List<BeneficiaryPayoutClaim>> = remoteSource.getEscalatedClaims()

    override suspend fun getClaimById(claimId: String): Result<BeneficiaryPayoutClaim> = runCatching {
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
