package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import javax.inject.Inject

/**
 * Removes one beneficiary row by its own keys.
 *
 * A row that never reached the server has no key to delete by, and the member/group context may be
 * missing on a device that has not synced yet - both are refused rather than sent as a delete that
 * would silently match nothing.
 */
class RemoveBeneficiaryUseCase @Inject constructor(
    private val beneficiaryRepository: BeneficiaryRepository
) {
    suspend operator fun invoke(
        beneficiary: Beneficiary,
        member: Member?,
        group: Group?
    ): Result<Unit> {
        val id = beneficiary.id
            ?: return Result.failure(
                IllegalStateException("This beneficiary is not saved on the server yet. Sync and try again.")
            )

        val memberId = beneficiary.memberId.ifBlank { member?.id.orEmpty() }
        val groupId = beneficiary.groupId.ifBlank {
            member?.groupId?.ifBlank { null } ?: group?.id.orEmpty()
        }
        if (memberId.isBlank() || groupId.isBlank()) {
            return Result.failure(
                IllegalStateException("No member or group is loaded, so the beneficiary cannot be removed. Sync and try again.")
            )
        }

        return beneficiaryRepository.deleteBeneficiary(groupId, memberId, id)
    }
}
