package com.sanibonani.save.domain.usecase

import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.NotificationPref
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * Encapsulates the logic for creating a new group.
 * Ensures the creator is automatically added as an active member of the group
 * with all prerequisite information captured and stored.
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
    ): Result<String> = runCatching {
        AppLogger.d(tag, "📍 Starting group creation: ${group.name}")

        val normalizedAdminEmail = adminEmail?.trim()
        val normalizedAdminPassword = adminPassword?.trim()
        val isNewAccount = !normalizedAdminEmail.isNullOrBlank() && !normalizedAdminPassword.isNullOrBlank()

        val userId: String? = if (isNewAccount) {
            AppLogger.d(tag, "📝 Creating new admin account: $normalizedAdminEmail")
            val id = supabaseRepository.signUp(normalizedAdminEmail, normalizedAdminPassword, mapOf(
                "full_name" to (adminFullName ?: "Group Admin"),
                // A user only becomes group admin after successful group creation.
                "role" to "member"
            )).getOrThrow()
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
        // Users are now allowed to manage multiple groups
        val groupToCreate = group.copy(adminUserId = userId)
        val groupId = groupRepository.createGroup(groupToCreate).getOrThrow()
        AppLogger.d(tag, "✅ Group created with ID: $groupId")

        // It's crucial to update the user's custom claims *before* they perform actions
        // that rely on those claims (like adding a member).
        supabaseRepository.updateUserRole(userId, UserRole.GROUP_ADMIN, groupId)
        AppLogger.d(tag, "Updated user claims to include new group admin role for $groupId")

        // Register the admin as the first member with all captured information
        AppLogger.d(tag, "👥 Registering creator as admin member with full details...")
        val nowStr = LocalDateTime.now().toString()
        val probationEndDate = LocalDateTime.now().plusMonths(group.probationMonths.toLong()).toString()

        val adminMember = Member(
            groupId = groupId,
            userId = userId,
            fullName = adminFullName ?: "Group Admin",
            idNumber = adminIdNumber?.takeIf { it.matches(Regex("^[0-9]{13}$")) }, // Validate 13-digit SA ID
            email = normalizedAdminEmail?.takeIf { it.isNotBlank() } ?: supabaseRepository.currentSessionEmail ?: "",
            phone = adminPhone ?: "",
            province = group.province,
            city = group.city,
            suburb = adminSuburb ?: group.township,
            street = adminStreet ?: "",
            notificationPref = adminNotificationPref,
            status = MemberStatus.PENDING_PAYMENT, // Use PENDING_PAYMENT until registration fee is paid
            memberKey = UUID.randomUUID().toString(),
            joinedAt = nowStr,
            probationEndAt = probationEndDate,
            totalContributions = 0,
            totalPaid = 0.0,
            beneficiaryCount = 0,
            beneficiaryOver65Count = 0
        )

        memberRepository.registerMember(adminMember).getOrThrow()
        AppLogger.d(tag, "✅ Admin member registered successfully with all details")
        AppLogger.d(tag, "   - Name: ${adminMember.fullName}")
        AppLogger.d(tag, "   - Email: ${adminMember.email}")
        AppLogger.d(tag, "   - Phone: ${adminMember.phone}")
        AppLogger.d(tag, "   - ID Number: ${if (adminMember.idNumber != null) "✓ Captured" else "Not provided"}")
        AppLogger.d(tag, "   - Location: ${adminMember.city}, ${adminMember.province}")
        AppLogger.d(tag, "   - Notification Pref: ${adminMember.notificationPref}")

        AppLogger.d(tag, "🎉 Group creation completed successfully: $groupId")
        groupId
    }.onFailure { e ->
        AppLogger.e(tag, "❌ Group creation failed: ${e.message}", e)
    }
}
