package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import javax.inject.Inject

/**
 * Accepts or rejects one supporting document on a burial claim.
 *
 * [docKey] names the document field on the claim (for example `face_photo`), so a reviewer can
 * rule on a single attachment without resending the whole claim.
 */
class VerifyBurialClaimDocumentUseCase @Inject constructor(
    private val claimRepository: BeneficiaryClaimRepository
) {
    suspend operator fun invoke(claimId: String, docKey: String, approve: Boolean): Result<Unit> =
        claimRepository.verifyClaimDocument(
            claimId = claimId,
            docKey = docKey,
            status = if (approve) DocumentStatus.VERIFIED else DocumentStatus.REJECTED
        )
}
