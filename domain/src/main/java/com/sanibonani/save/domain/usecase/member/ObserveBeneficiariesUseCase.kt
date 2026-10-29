package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * A member's beneficiaries, re-emitted on every change.
 */
class ObserveBeneficiariesUseCase @Inject constructor(
    private val beneficiaryRepository: BeneficiaryRepository
) {
    operator fun invoke(memberId: String): Flow<Result<List<Beneficiary>>> =
        beneficiaryRepository.observeBeneficiaries(memberId)
}
