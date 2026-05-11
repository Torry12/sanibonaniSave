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
    suspend operator fun invoke(member: Member, transactionId: String? = null): Result<Member> {
        return try {
            if (member.groupId.isBlank()) {
                return Result.failure(Exception("Group ID required for registration"))
            }

            // Fetch group to validate state and get settings
            val group = groupRepository.getGroupById(member.groupId).getOrThrow()
            
            if (!group.registrationPaid) {
                return Result.failure(Exception("This group is not yet active. Please contact the administrator."))
            }

            // Check if user is already a member (handled in repository, but we could add more logic here if needed)
            // For SRP, the repository handles the persistence-level unique checks, 
            // while the use case handles business rule validation.

            val result = memberRepository.registerMember(member, transactionId)
            
            // If successful registration and it's a new member (not just a status update from payment)
            // incrementing the member count is handled inside registerMember in the repository
            // but arguably that side effect should be here in the UseCase.
            // However, the repository currently handles it to ensure consistency.

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
