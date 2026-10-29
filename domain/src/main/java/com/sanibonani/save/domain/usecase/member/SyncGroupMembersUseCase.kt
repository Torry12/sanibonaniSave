package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.member.MemberRepository
import javax.inject.Inject

/**
 * Pulls a group's roster from the server into the local cache.
 *
 * This is a write to the cache, not a read: callers that only want to display members use the
 * Room-backed stream, and reach for this when the admin deliberately asks for current truth.
 */
class SyncGroupMembersUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(groupId: String): Result<List<Member>> =
        memberRepository.syncGroupMembers(groupId)
}
