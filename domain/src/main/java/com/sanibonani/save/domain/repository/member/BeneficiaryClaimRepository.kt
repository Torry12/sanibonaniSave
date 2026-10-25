package com.sanibonani.save.domain.repository.member

import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.model.DocumentStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing burial society beneficiary payout claims.
 * Three-tier flow: Member submits → Admin reviews & escalates → Platform Admin approves/rejects.
 */
interface BeneficiaryClaimRepository {

    /** Member: submit a new payout claim for a specific beneficiary. */
    suspend fun submitClaim(claim: BeneficiaryPayoutClaim, idempotencyKey: String? = null): Result<BeneficiaryPayoutClaim>

    /** Member: observe all claims for a specific member in a group. */
    fun observeClaimsForMember(memberId: String, groupId: String): Flow<Result<List<BeneficiaryPayoutClaim>>>

    /** Admin: observe all claims submitted by members of a group. */
    fun observeClaimsForGroup(groupId: String): Flow<Result<List<BeneficiaryPayoutClaim>>>

    /** Admin/Platform: update the status of a claim (review, escalate, approve, reject). */
    suspend fun updateClaimStatus(
        claimId: String,
        status: BeneficiaryClaimStatus,
        reviewedBy: String? = null,
        adminNotes: String? = null,
        rejectionReason: String? = null
    ): Result<Unit>

    /** Verifies a specific document on a claim. docKey matches model property (e.g. 'face_photo') */
    suspend fun verifyClaimDocument(
        claimId: String,
        docKey: String,
        status: DocumentStatus
    ): Result<Unit>

    /**
     * Atomically pays a burial claim: updates status to PAID AND decrements group balance AND logs to ledger.
     */
    suspend fun payClaimAtomic(
        claimId: String,
        adminId: String,
        notes: String? = null
    ): Result<Unit>

    /** Platform Admin: observe all claims that have been escalated for platform review. */
    fun observeEscalatedClaims(): Flow<Result<List<BeneficiaryPayoutClaim>>>

    /** Platform Admin: fetch all escalated claims synchronously. */
    suspend fun getEscalatedClaimsSync(): Result<List<BeneficiaryPayoutClaim>>

    /** Get a specific claim by its ID. */
    suspend fun getClaimById(claimId: String): Result<BeneficiaryPayoutClaim>
}
