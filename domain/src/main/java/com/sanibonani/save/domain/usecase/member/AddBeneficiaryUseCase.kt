package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import javax.inject.Inject

/**
 * Stores a beneficiary row.
 *
 * `beneficiaries.group_id` is a NOT NULL uuid with a foreign key to groups, so an empty key is not
 * an omission the server fills in - PostgREST rejects it and the row never lands. Refusing here
 * keeps that contract next to the write instead of at each screen that performs one.
 */
class AddBeneficiaryUseCase @Inject constructor(
    private val beneficiaryRepository: BeneficiaryRepository
) {
    suspend operator fun invoke(beneficiary: Beneficiary): Result<Beneficiary> {
        if (beneficiary.groupId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "No group is loaded for this member, so the beneficiary cannot be saved. Sync and try again."
                )
            )
        }
        return beneficiaryRepository.addBeneficiary(beneficiary)
    }
}
