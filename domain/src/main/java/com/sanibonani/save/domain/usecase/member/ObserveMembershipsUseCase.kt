package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.member.MemberRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Every group the signed-in user belongs to, one member row per group.
 */
class ObserveMembershipsUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    operator fun invoke(userId: String): Flow<Result<List<Member>>> =
        memberRepository.observeMemberships(userId)
}
