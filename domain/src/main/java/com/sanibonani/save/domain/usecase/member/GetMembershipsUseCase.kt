package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.member.MemberRepository
import javax.inject.Inject

/**
 * The member rows one account holds across every group, read once.
 */
class GetMembershipsUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(userId: String): Result<List<Member>> =
        memberRepository.getMemberships(userId)
}
