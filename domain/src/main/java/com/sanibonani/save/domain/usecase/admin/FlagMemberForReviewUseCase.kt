package com.sanibonani.save.domain.usecase.admin
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import javax.inject.Inject

/**
 * Marks a member as needing an admin review. This annotates the behaviour track only; the member's
 * own status is left alone, which is what separates it from SuspendMemberUseCase.
 */
class FlagMemberForReviewUseCase @Inject constructor(
    private val behaviorTrackingRepository: BehaviorTrackingRepository
) {
    suspend operator fun invoke(memberId: String, reason: String): Result<Unit> =
        behaviorTrackingRepository.flagMemberForReview(memberId = memberId, reason = reason)
}
