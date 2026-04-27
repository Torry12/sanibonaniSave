package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPublicGroupsUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    operator fun invoke(): Flow<Result<List<Group>>> {
        return groupRepository.getPublicGroups()
    }
}
