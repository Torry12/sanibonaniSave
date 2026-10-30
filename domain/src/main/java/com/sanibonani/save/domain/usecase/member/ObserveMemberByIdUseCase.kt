package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.member.MemberRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * One member row as the cache and the server see it, re-emitted on every change.
 */
class ObserveMemberByIdUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    operator fun invoke(memberId: String): Flow<Result<Member?>> =
        memberRepository.observeMemberById(memberId)
}
