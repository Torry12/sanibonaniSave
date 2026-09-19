package com.sanibonani.save.domain.usecase.equity

import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.service.ComplianceService
import com.sanibonani.save.domain.service.KycService
import javax.inject.Inject

class RegisterMemberUseCase @Inject constructor(
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val complianceService: ComplianceService,
    private val kycService: KycService
) {
    suspend operator fun invoke(member: Member, transactionId: String? = null): Result<Member> = runCatching {
        kycService.evaluateMemberIdentity(member).getOrThrow()
        val group = groupRepository.getGroupById(member.groupId).getOrThrow()
        complianceService.validateGroupCapacity(group, 1).getOrThrow()
        memberRepository.registerMember(member, transactionId).getOrThrow()
    }
}
