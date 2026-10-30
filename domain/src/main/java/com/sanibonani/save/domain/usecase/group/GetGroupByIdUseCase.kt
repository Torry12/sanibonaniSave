package com.sanibonani.save.domain.usecase.group
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.group.GroupRepository
import javax.inject.Inject

/**
 * Reads one group record by its primary key.
 */
class GetGroupByIdUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String): Result<Group> =
        groupRepository.getGroupById(groupId)
}
