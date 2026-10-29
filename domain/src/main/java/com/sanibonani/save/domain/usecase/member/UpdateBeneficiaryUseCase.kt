package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import javax.inject.Inject

/**
 * Writes an edited beneficiary back over its existing row, identity included.
 */
class UpdateBeneficiaryUseCase @Inject constructor(
    private val beneficiaryRepository: BeneficiaryRepository
) {
    suspend operator fun invoke(beneficiary: Beneficiary): Result<Beneficiary> =
        beneficiaryRepository.updateBeneficiary(beneficiary)
}
