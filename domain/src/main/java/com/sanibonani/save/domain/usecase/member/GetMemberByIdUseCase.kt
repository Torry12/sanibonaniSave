package com.sanibonani.save.domain.usecase.member
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.member.MemberRepository
import javax.inject.Inject

/**
 * Reads one member record by its primary key.
 */
class GetMemberByIdUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(memberId: String): Result<Member> =
        memberRepository.getMemberById(memberId)
}
