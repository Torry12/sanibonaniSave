package com.sanibonani.save.domain.usecase.member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.domain.utils.safeRunCatching
import javax.inject.Inject

/**
 * Suspends a member.
 *
 * The member record is the authority for status, so it is written first, through
 * [UpdateMemberStatusUseCase]; the behaviour track is then annotated with the reason, because the
 * admin's behavior screen renders the suspension from that row. A member whose status the server
 * refused to change never gets a suspended-looking track.
 */
class SuspendMemberUseCase @Inject constructor(
    private val updateMemberStatusUseCase: UpdateMemberStatusUseCase,
    private val behaviorTrackingRepository: BehaviorTrackingRepository
) {
    suspend operator fun invoke(memberId: String, reason: String): Result<Unit> = safeRunCatching {
        updateMemberStatusUseCase(memberId, MemberStatus.SUSPENDED).getOrThrow()
        behaviorTrackingRepository.suspendMember(memberId, reason).getOrThrow()
    }
}
