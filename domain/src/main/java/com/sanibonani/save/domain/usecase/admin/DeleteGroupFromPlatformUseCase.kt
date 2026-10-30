package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * Removes a group from the platform. Destructive and outside the admin's own group scope, so it
 * stays behind an explicit confirmation rather than a swipe.
 */
class DeleteGroupFromPlatformUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(groupId: String): Result<Unit> =
        platformRepository.deleteGroupFromPlatform(groupId)
}
