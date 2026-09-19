package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.GroupSettings
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.repository.GroupRepository
import javax.inject.Inject

class UpdateGroupSettingsUseCase @Inject constructor(
    private val groupRepo: GroupRepository
) {
    suspend operator fun invoke(groupId: String, settings: GroupSettings): Result<Unit> = runCatching {
        val updates = mapOf(
            "joining_fee" to (settings.joiningFee.toDoubleOrNull() ?: 0.0),
            "monthly_contribution" to (settings.monthlyContribution.toDoubleOrNull() ?: 0.0),
            "bank_name" to settings.bankName,
            "account_number" to settings.accountNumber,
            "branch_code" to settings.branchCode
        )
        groupRepo.updateGroupSettings(groupId, updates).getOrThrow()
    }
}
