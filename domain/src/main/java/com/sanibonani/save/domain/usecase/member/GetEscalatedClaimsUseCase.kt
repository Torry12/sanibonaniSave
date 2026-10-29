package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import javax.inject.Inject

/**
 * Claims the group has escalated to the platform for a reviewer's decision.
 */
class GetEscalatedClaimsUseCase @Inject constructor(
    private val beneficiaryClaimRepository: BeneficiaryClaimRepository
) {
    suspend operator fun invoke(): Result<List<BeneficiaryPayoutClaim>> =
        beneficiaryClaimRepository.getEscalatedClaimsSync()
}
