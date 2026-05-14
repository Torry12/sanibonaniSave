package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import javax.inject.Inject

/**
 * Orchestrates the process of a user joining a group.
 * Includes validation and initial membership setup.
 */
class RegisterMemberUseCase @Inject constructor(
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(member: Member, transactionId: String? = null): Result<Member> =
        runCatching {
            require(member.groupId.isNotBlank()) { "Group ID required for registration." }

            // Fetch group to validate state and get settings
            val group = groupRepository.getGroupById(member.groupId).getOrThrow()

            if (!group.registrationPaid) {
                error("This group is not yet active. Please contact the administrator.")
            }

            // Capacity check: enforce maxMembers limit when > 0
            if (group.maxMembers > 0 && group.currentMembers >= group.maxMembers) {
                error("This group has reached its maximum capacity of ${group.maxMembers} members.")
            }

            // Note: persistence-level duplicate checks are enforced by the repository
            memberRepository.registerMember(member, transactionId).getOrThrow()
        }
}
