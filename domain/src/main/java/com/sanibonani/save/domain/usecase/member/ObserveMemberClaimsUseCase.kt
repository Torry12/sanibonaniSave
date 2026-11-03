package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The claims one member filed in one group, re-emitted on every cache or server change.
 */
class ObserveMemberClaimsUseCase @Inject constructor(
    private val beneficiaryClaimRepository: BeneficiaryClaimRepository
) {
    operator fun invoke(memberId: String, groupId: String): Flow<Result<List<BeneficiaryPayoutClaim>>> =
        beneficiaryClaimRepository.observeClaimsForMember(memberId, groupId)
}
