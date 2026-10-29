package com.sanibonani.save.domain.usecase.admin
import com.sanibonani.save.domain.model.MemberBehaviorTrack
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import javax.inject.Inject

/**
 * Reads the single behavior track row backing the admin's behavior-insights screen.
 */
class GetMemberBehaviorTrackUseCase @Inject constructor(
    private val behaviorTrackingRepository: BehaviorTrackingRepository
) {
    suspend operator fun invoke(memberId: String): Result<MemberBehaviorTrack?> =
        behaviorTrackingRepository.getMemberBehavior(memberId)
}
