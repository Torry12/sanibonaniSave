package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.member.MemberRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The members attached to one group, streaming from the local cache as the server writes rows.
 *
 * A caller that needs current truth pairs this with [SyncGroupMembersUseCase] rather than reading
 * once, because the flow keeps emitting after the first value.
 */
class ObserveGroupMembersUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    operator fun invoke(groupId: String): Flow<Result<List<Member>>> =
        memberRepository.getGroupMembers(groupId)
}
