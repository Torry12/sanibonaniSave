package com.sanibonani.save.domain.usecase.member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.member.MemberRepository
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching
/**
 * Encapsulates the logic for updating a member's status.
 *
 * There is deliberately no transition table here. The one this class used to carry
 * (`ALLOWED_TRANSITIONS` / `validateTransition`) had no caller in the whole tree, while eight places
 * write a status and five of them go straight to [MemberRepository]: activation, KYC approval, the
 * probation sweep, contribution strategies and the forced-loan settlement. Enforcing it on the three
 * routes that came through here would have guarded a third of the writes and refused a sanction that
 * already ships - the settlement demotes an ACTIVE member to PROBATION, which the table forbade.
 * Whatever legality a status change has is decided by the server and by the flow that owns it.
 */
class UpdateMemberStatusUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(memberId: String, newStatus: MemberStatus): Result<Unit> =
        safeRunCatching {
            memberRepository.updateMemberStatus(memberId, newStatus).getOrThrow()
        }
}
