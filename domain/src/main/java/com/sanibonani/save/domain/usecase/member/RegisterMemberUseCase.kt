package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.validation.ValidationUtils
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Orchestrates the process of a user joining a group.
 * Includes validation, capacity checks, duplicate checks, and initial membership setup.
 */
class RegisterMemberUseCase @Inject constructor(
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val supabaseRepository: SupabaseRepository? = null
) {
    private val tag = "RegisterMemberUseCase"

    suspend operator fun invoke(member: Member, transactionId: String? = null): Result<Member> =
        safeRunCatching(tag) {
            require(member.groupId.isNotBlank()) { "Group ID required for registration." }

            val fullNameTrim = member.fullName.trim()
            if (fullNameTrim.isNotBlank()) {
                require(fullNameTrim.length >= 3) { "Full Name must be at least 3 characters." }
            }

            val emailTrim = member.email?.trim() ?: ""
            if (emailTrim.isNotBlank()) {
                require(ValidationUtils.isValidEmail(emailTrim)) { "Invalid email address format." }
            }

            AppLogger.d(tag, "Starting member registration for group ${member.groupId}")

            // 1. Resolve authenticated user ID
            val currentUserId = member.userId?.takeIf { it.isNotBlank() } ?: supabaseRepository?.currentUserId

            // 2. Fetch group to validate state and capacity
            val group = groupRepository.getGroupById(member.groupId).getOrThrow()

            if (!group.registrationPaid) {
                error("This group is not yet active. Please contact the administrator.")
            }

            // 3. Prevent duplicate enrollment in the same group
            if (!currentUserId.isNullOrBlank()) {
                val existingMembership = safeRunCatching { memberRepository.getMemberByUserId(currentUserId, member.groupId).getOrNull() }.getOrNull()
                if (existingMembership != null) {
                    error("You are already enrolled as a member in this group.")
                }
            }

            // 4. Capacity check: enforce maxMembers limit when > 0
            if (group.maxMembers > 0 && group.currentMembers >= group.maxMembers) {
                error("This group has reached its maximum capacity of ${group.maxMembers} members.")
            }

            // 5. SA ID validation if provided, with the same rule KYC applies later.
            val idNum = member.idNumber?.trim()
            if (!idNum.isNullOrBlank() && !ValidationUtils.isValidSAIdNumber(idNum)) {
                error("Invalid South African ID Number format. Must be 13 digits with a valid date of birth and checksum.")
            }

            // 6. Calculate initial status
            val initialStatus = when {
                group.joiningFee.isPositive && transactionId.isNullOrBlank() -> MemberStatus.PENDING_PAYMENT
                group.probationMonths > 0 -> MemberStatus.PROBATION
                else -> MemberStatus.ACTIVE
            }

            val nowStr = LocalDateTime.now().toString()
            val probationEndStr = if (group.probationMonths > 0) {
                LocalDateTime.now().plusMonths(group.probationMonths.toLong()).toString()
            } else null

            val memberToRegister = member.copy(
                fullName = if (fullNameTrim.isNotBlank()) fullNameTrim else member.fullName,
                email = emailTrim,
                phone = member.phone.trim(),
                userId = currentUserId,
                joinedAt = member.joinedAt ?: nowStr,
                probationEndAt = member.probationEndAt ?: probationEndStr,
                status = initialStatus,
                memberKey = member.memberKey ?: UUID.randomUUID().toString()
            )

            val registeredMember = memberRepository.registerMember(memberToRegister, transactionId).getOrThrow()

            AppLogger.d(tag, "Successfully registered member: ${registeredMember.id} with status $initialStatus")
            registeredMember
        }
}
