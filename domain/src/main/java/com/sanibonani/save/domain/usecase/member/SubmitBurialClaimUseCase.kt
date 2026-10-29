package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import javax.inject.Inject

/**
 * Files a burial payout claim for a member. [idempotencyKey] lets a resubmit of the same attempt
 * be recognised by the server instead of filing a second claim.
 */
class SubmitBurialClaimUseCase @Inject constructor(
    private val beneficiaryClaimRepository: BeneficiaryClaimRepository
) {
    suspend operator fun invoke(
        claim: BeneficiaryPayoutClaim,
        idempotencyKey: String? = null
    ): Result<BeneficiaryPayoutClaim> =
        beneficiaryClaimRepository.submitClaim(claim, idempotencyKey)
}
