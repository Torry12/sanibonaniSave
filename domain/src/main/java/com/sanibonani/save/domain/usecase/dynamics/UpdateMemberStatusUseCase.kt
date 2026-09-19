package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.MemberRepository
import javax.inject.Inject

class UpdateMemberStatusUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(memberId: String, newStatus: MemberStatus): Result<Unit> = runCatching {
        if (newStatus == MemberStatus.ACTIVE) {
            val member = memberRepository.getMemberById(memberId).getOrThrow()
            if (member.document1Status != DocumentStatus.VERIFIED || member.contractStatus != DocumentStatus.VERIFIED) {
                throw Exception("Verification required")
            }
        }
        memberRepository.updateMemberStatus(memberId, newStatus).getOrThrow()
    }
}
