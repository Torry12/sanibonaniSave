package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.repository.group.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Streams one group as its cached row changes, so a detail screen follows the offline-first
 * cache the same way a list does.
 */
class ObserveGroupDetailUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    operator fun invoke(groupId: String): Flow<Result<Group?>> =
        groupRepository.observeGroup(groupId)
}
