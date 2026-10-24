package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Orchestrates the activation side-effects for a newly created group.
 * Handles admin member status transition and group metadata updates.
 *
 * Enforces business rule: A group can ONLY be active if the group registration fee is not outstanding.
 */
class ActivateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(groupId: String, txId: String? = null): Result<Unit> = safeRunCatching {
        // 1. Fetch group
        val group = groupRepository.getGroupById(groupId).getOrThrow()

        // 2. Enforce registration fee requirement: A group can ONLY be active if registration fee is not outstanding
        if (group.isRegistrationFeeOutstanding && txId.isNullOrBlank()) {
            throw IllegalStateException("Group cannot be activated while registration fee is outstanding. Please complete registration payment.")
        }

        // 3. Mark Group as Registered/Paid
        groupRepository.updateFeeStatus(groupId, AdminFeeState.PAID).getOrThrow()
        groupRepository.updateGroupSettings(groupId, mapOf("registration_paid" to true)).getOrThrow()

        // 4. Update Admin Member status to activate onboarding
        group.adminUserId?.let { adminUserId ->
            val adminMember = memberRepository.getMemberByUserId(adminUserId, groupId).getOrNull()
                ?: memberRepository.syncGroupMembers(groupId).getOrNull()?.find { it.userId == adminUserId }

            if (adminMember != null) {
                val adminMemberId = adminMember.id
                if (!adminMemberId.isNullOrBlank()) {
                    val nextStatus = if (group.probationMonths > 0) MemberStatus.PROBATION else MemberStatus.ACTIVE
                    memberRepository.updateMemberStatus(adminMemberId, nextStatus).getOrThrow()
                }
            }
        }
    }
}
