package com.sanibonani.save.domain.usecase.equity

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.service.ComplianceService
import com.sanibonani.save.domain.validation.ValidationUtils
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CreateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val supabaseRepository: SupabaseRepository,
    private val complianceService: ComplianceService
) {
    suspend operator fun invoke(
        group: Group,
        adminEmail: String? = null,
        adminPassword: String? = null,
        adminFullName: String? = null,
        adminPhone: String? = null,
        adminIdNumber: String? = null
    ): Result<String> = runCatching {
        val userId = if (!adminEmail.isNullOrBlank() && !adminPassword.isNullOrBlank()) {
            supabaseRepository.signUp(adminEmail, adminPassword, mapOf("full_name" to (adminFullName ?: "Admin"), "role" to "group_admin")).getOrThrow()
        } else supabaseRepository.currentUserId ?: throw Exception("Auth required")

        complianceService.validateGroupCapacity(group, 1).getOrThrow()
        val groupId = groupRepository.createGroup(group.copy(adminUserId = userId)).getOrThrow()
        supabaseRepository.updateUserRole(userId, UserRole.GROUP_ADMIN, groupId)

        val adminMember = Member(
            groupId = groupId,
            userId = userId,
            fullName = adminFullName ?: "Admin",
            idNumber = adminIdNumber,
            email = adminEmail ?: supabaseRepository.currentSessionEmail ?: "",
            phone = adminPhone ?: "",
            status = MemberStatus.PENDING_PAYMENT,
            joinedAt = Instant.now().toString()
        )
        memberRepository.registerMember(adminMember).getOrThrow()
        groupId
    }
}
