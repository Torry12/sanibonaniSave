package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing burial society beneficiary payout claims.
 * Three-tier flow: Member submits → Admin reviews & escalates → Platform Admin approves/rejects.
 */
interface BeneficiaryClaimRepository {

    /** Member: submit a new payout claim for a specific beneficiary. */
    suspend fun submitClaim(claim: BeneficiaryPayoutClaim): Result<BeneficiaryPayoutClaim>

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

    /** Platform Admin: observe all claims that have been escalated for platform review. */
    fun observeEscalatedClaims(): Flow<Result<List<BeneficiaryPayoutClaim>>>
}

