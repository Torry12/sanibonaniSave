package com.sanibonani.save.domain.usecase.group
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.group.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
class GetPublicGroupsUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    operator fun invoke(): Flow<Result<List<Group>>> {
        return groupRepository.getPublicGroups()
    }
}
