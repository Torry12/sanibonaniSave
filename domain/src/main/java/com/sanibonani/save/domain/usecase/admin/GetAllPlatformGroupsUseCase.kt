package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * Every group on the platform, used for the distribution charts rather than for any one group's
 * money. Read-through, so a group created elsewhere appears without a cache refresh.
 */
class GetAllPlatformGroupsUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(): Result<List<Group>> = platformRepository.getAllGroups()
}
