package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.GroupRepository
import javax.inject.Inject

/**
 * Encapsulates the logic for updating group settings.
 * Validates business rules for actuarial and configuration changes.
 */
class UpdateGroupSettingsUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(group: Group): Result<Unit> {
        // Validation logic
        if (group.goalAmount <= 0) {
            return Result.failure(Exception("Goal amount must be positive"))
        }
        if (group.periodMonths <= 0) {
            return Result.failure(Exception("Period months must be positive"))
        }
        if (group.maxMembers <= 0) {
            return Result.failure(Exception("Max members must be at least 1"))
        }

        return groupRepository.updateGroup(group)
    }
}
