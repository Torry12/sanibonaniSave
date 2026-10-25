package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.NotificationPref
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.validation.ValidationUtils
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Encapsulates the logic for creating a new group.
 * Ensures the creator is automatically added as an active member of the group
 * with all prerequisite information captured and stored atomically.
 */
class CreateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val supabaseRepository: SupabaseRepository
) {
    private val tag = "CreateGroupUseCase"

    suspend operator fun invoke(
        group: Group,
        adminEmail: String? = null,
        adminPassword: String? = null,
        adminFullName: String? = null,
        adminPhone: String? = null,
        adminIdNumber: String? = null,
        adminStreet: String? = null,
        adminSuburb: String? = null,
        adminNotificationPref: NotificationPref = NotificationPref.BOTH
    ): Result<String> {
        // Tracks the group id committed to the DB so a later failure can be rolled back.
        var committedGroupId: String? = null

        return safeRunCatching {
            AppLogger.d(tag, "📍 Starting group creation & member onboarding: ${group.name}")
            val normalizedAdminEmail = adminEmail?.trim()
            val normalizedAdminPassword = adminPassword?.trim()
            val isNewAccount = !normalizedAdminEmail.isNullOrBlank() && !normalizedAdminPassword.isNullOrBlank()

            val userId: String? = if (isNewAccount) {
                AppLogger.d(tag, "📝 Creating new admin account: $normalizedAdminEmail")
                val id = supabaseRepository.signUp(
                    normalizedAdminEmail,
                    normalizedAdminPassword,
                    mapOf(
                        "full_name" to (adminFullName?.takeIf { it.isNotBlank() } ?: "Group Admin"),
                        "role" to "member"
                    )
                ).getOrThrow()
                AppLogger.d(tag, "✅ Admin account created: $id")
                id
            } else {
                AppLogger.d(tag, "🔑 Using existing user account")
                val id = supabaseRepository.currentUserId
                if (id != null) {
                    val currentRole = supabaseRepository.getUserRole()
                    AppLogger.d(tag, "👤 Current user role: $currentRole")
                }
                id
            }

            require(userId != null) {
                "A registered user account is required to create a group. Please sign in or provide account details."
            }

            AppLogger.d(tag, "💾 Creating group in database...")
            val groupToCreate = group.copy(adminUserId = userId)
            val groupId = groupRepository.createGroup(groupToCreate).getOrThrow()
            AppLogger.d(tag, "✅ Group created with ID: $groupId")

            // The row is committed from here on, so a later failure must roll it back.
            committedGroupId = groupId

            // Look up existing member profile if details were not explicitly provided
            val existingProfileMember = if (adminFullName.isNullOrBlank() || adminEmail.isNullOrBlank() || adminPhone.isNullOrBlank()) {
                safeRunCatching {
                    memberRepository.getMemberships(userId).getOrNull()?.firstOrNull {
                        it.fullName.isNotBlank() && it.fullName != "Group Admin"
                    }
                }.getOrNull()
            } else null

            val resolvedEmail = normalizedAdminEmail?.takeIf { it.isNotBlank() }
                ?: supabaseRepository.currentSessionEmail
                ?: existingProfileMember?.email
                ?: ""

            val resolvedFullName = adminFullName?.takeIf { it.isNotBlank() }
                ?: existingProfileMember?.fullName
                ?: "Group Admin"

            val resolvedPhone = adminPhone?.takeIf { it.isNotBlank() }
                ?: existingProfileMember?.phone
                ?: ""

            val resolvedIdNumber = adminIdNumber?.takeIf { ValidationUtils.isValidSAIdNumber(it) }
                ?: existingProfileMember?.idNumber

            val resolvedStreet = adminStreet?.takeIf { it.isNotBlank() }
                ?: existingProfileMember?.street
                ?: ""

            val resolvedSuburb = adminSuburb?.takeIf { it.isNotBlank() }
                ?: existingProfileMember?.suburb
                ?: group.township

            // Onboard the creator as the first member of the group.
            //
            // The database trigger `tr_ensure_admin_is_member` already inserts a placeholder
            // row for (group_id, user_id) when the group row lands. A plain INSERT here used
            // to collide with UNIQUE(group_id, user_id) and surface as "This record already
            // exists" — the group *was* created, but the user saw an error and each retry
            // minted another orphaned group. An upsert either creates the rich row or
            // upgrades the placeholder, which makes this step idempotent.
            AppLogger.d(tag, "👥 Onboarding creator as group member...")
            val nowStr = LocalDateTime.now().toString()
            val probationEndDate = LocalDateTime.now().plusMonths(group.probationMonths.toLong()).toString()

            val adminMember = Member(
                groupId = groupId,
                userId = userId,
                fullName = resolvedFullName,
                idNumber = resolvedIdNumber,
                email = resolvedEmail,
                phone = resolvedPhone,
                province = group.province,
                city = group.city,
                suburb = resolvedSuburb,
                street = resolvedStreet,
                notificationPref = adminNotificationPref,
                status = MemberStatus.PENDING_PAYMENT,
                memberKey = UUID.randomUUID().toString(),
                joinedAt = nowStr,
                probationEndAt = probationEndDate,
                totalContributions = 0,
                totalPaid = Money.ZERO,
                beneficiaryCount = 0,
                beneficiaryOver65Count = 0
            )

            val memberRes = memberRepository.upsertMember(adminMember)

            if (memberRes.isFailure) {
                AppLogger.e(tag, "❌ Failed to register admin member for group $groupId.", memberRes.exceptionOrNull())

                // Roll the group back so the user does not accumulate unusable, member-less
                // groups on every retry. The account itself is left in place so a retry can
                // reuse the same session instead of forcing another sign-up.
                committedGroupId = null
                val rollback = groupRepository.deleteGroup(groupId)
                if (rollback.isFailure) {
                    AppLogger.e(
                        tag,
                        "Could not roll back partially created group $groupId: ${rollback.exceptionOrNull()?.message}",
                        rollback.exceptionOrNull()
                    )
                } else {
                    AppLogger.w(tag, "Rolled back partially created group $groupId after member onboarding failure.")
                }

                throw memberRes.exceptionOrNull()
                    ?: IllegalStateException("Failed to register admin member during group creation.")
            }

            AppLogger.d(tag, "✅ Admin creator onboarded as member successfully: $groupId")

            // Elevate only once the membership exists. A failed upsert rolls the group back, and
            // a role pointing at a deleted group would lock the account out of every destination.
            val roleUpdate = supabaseRepository.updateUserRole(userId, UserRole.GROUP_ADMIN, groupId)
            if (roleUpdate.isFailure) {
                // Role elevation failure is non-fatal for group creation, but it must not be
                // silent: without the claim the admin loses access to the group they just made.
                AppLogger.w(
                    tag,
                    "Failed to elevate $userId to GROUP_ADMIN for $groupId: ${roleUpdate.exceptionOrNull()?.message}"
                )
            } else {
                AppLogger.d(tag, "Updated user claims to include new group admin role for $groupId")
            }

            groupId
        }.onFailure { e ->
            if (committedGroupId != null) {
                AppLogger.e(
                    tag,
                    "❌ Group creation & onboarding failed. Group $committedGroupId could not be completed and was rolled back: ${e.message}",
                    e
                )
            } else {
                AppLogger.e(tag, "❌ Group creation & onboarding failed: ${e.message}", e)
            }
        }
    }
}
