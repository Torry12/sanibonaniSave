package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.repository.group.GroupRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Reads the public-group list from the local cache only, so a cold screen has rows to draw
 * without waiting on the network.
 */
class WarmPublicGroupsCacheUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke() {
        groupRepository.getPublicGroupsFromCache().first()
    }
}
