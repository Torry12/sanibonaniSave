package com.sanibonani.save.domain.usecase.admin
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import javax.inject.Inject

/**
 * Removes the pending-review annotation from a member's behaviour track.
 */
class UnflagMemberUseCase @Inject constructor(
    private val behaviorTrackingRepository: BehaviorTrackingRepository
) {
    suspend operator fun invoke(memberId: String): Result<Unit> =
        behaviorTrackingRepository.unflagMember(memberId)
}
