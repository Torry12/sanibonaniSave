package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * Sends one message to every group on the platform.
 *
 * There is no preview step, so this is the call that puts text in front of every member at once.
 */
class BroadcastPlatformMessageUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(message: String): Result<Unit> =
        platformRepository.broadcastPlatformMessage(message)
}
