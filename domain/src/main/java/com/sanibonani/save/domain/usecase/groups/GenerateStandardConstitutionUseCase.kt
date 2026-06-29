package com.sanibonani.save.domain.usecase.groups

import com.sanibonani.save.domain.repository.ExportRepository
import com.sanibonani.save.domain.repository.GroupRepository
import javax.inject.Inject

class GenerateStandardConstitutionUseCase @Inject constructor(
    private val groupRepo: GroupRepository,
    private val exportRepo: ExportRepository
) {
    suspend operator fun invoke(groupId: String): Result<Unit> {
        val group = groupRepo.getGroupById(groupId).getOrNull()
            ?: return Result.failure(Exception("Group not found"))

        return exportRepo.exportGroupConstitution(group).fold(
            onSuccess = { file ->
                groupRepo.uploadConstitution(groupId, file.readBytes(), "Constitution.pdf")
            },
            onFailure = { Result.failure(it) }
        )
    }
}
