package com.sanibonani.save.domain.usecase.member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.domain.utils.safeRunCatching
import javax.inject.Inject

/**
 * Lifts a suspension, mirroring [SuspendMemberUseCase]: the member goes back to ACTIVE through the
 * blessed status path, then the behaviour track drops its suspension marker.
 */
class UnsuspendMemberUseCase @Inject constructor(
    private val updateMemberStatusUseCase: UpdateMemberStatusUseCase,
    private val behaviorTrackingRepository: BehaviorTrackingRepository
) {
    suspend operator fun invoke(memberId: String): Result<Unit> = safeRunCatching {
        updateMemberStatusUseCase(memberId, MemberStatus.ACTIVE).getOrThrow()
        behaviorTrackingRepository.unsuspendMember(memberId).getOrThrow()
    }
}
