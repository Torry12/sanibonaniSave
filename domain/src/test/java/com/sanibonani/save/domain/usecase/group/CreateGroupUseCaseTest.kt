package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CreateGroupUseCaseTest {

    private lateinit var createGroupUseCase: CreateGroupUseCase
    private val groupRepository: GroupRepository = mockk()
    private val memberRepository: MemberRepository = mockk()
    private val supabaseRepository: SupabaseRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        createGroupUseCase = CreateGroupUseCase(groupRepository, memberRepository, supabaseRepository)
    }

    @Test
    fun `invoke with new account should sign up user and create group with admin as member`() = runBlocking {
        // Given
        val group = Group(name = "Test Group", province = "Gauteng", city = "Johannesburg", township = "Soweto", probationMonths = 3)
        val adminEmail = "admin@test.com"
        val adminPassword = "password"
        val adminFullName = "Admin Name"
        val adminPhone = "0821234567"
        val adminIdNumber = "8501015009086"
        val userId = "user-123"
        val groupId = "group-456"

        coEvery { supabaseRepository.signUp(adminEmail, adminPassword, any()) } returns Result.success(userId)
        coEvery { groupRepository.createGroup(any()) } returns Result.success(groupId)
        coEvery { supabaseRepository.updateUserRole(userId, UserRole.GROUP_ADMIN, groupId) } returns Result.success(Unit)
        coEvery { memberRepository.upsertMember(any()) } returns Result.success(mockk())

        // When
        val result = createGroupUseCase(
            group = group,
            adminEmail = adminEmail,
            adminPassword = adminPassword,
            adminFullName = adminFullName,
            adminPhone = adminPhone,
            adminIdNumber = adminIdNumber
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals(groupId, result.getOrNull())
        coVerify { supabaseRepository.signUp(adminEmail, adminPassword, any()) }
        coVerify { supabaseRepository.updateUserRole(userId, UserRole.GROUP_ADMIN, groupId) }
        coVerify { groupRepository.createGroup(match { it.adminUserId == userId }) }
        coVerify {
            memberRepository.upsertMember(match { member ->
                member.groupId == groupId &&
                member.userId == userId &&
                member.status == MemberStatus.PENDING_PAYMENT &&
                member.fullName == adminFullName &&
                member.phone == adminPhone &&
                member.idNumber == adminIdNumber &&
                member.email == adminEmail &&
                member.province == "Gauteng" &&
                member.city == "Johannesburg"
            })
        }
    }

    @Test
    fun `invoke with existing account should elevate role after creating group`() = runBlocking {
        // Given
        val group = Group(name = "Test Group", province = "Western Cape", city = "Cape Town", probationMonths = 3)
        val userId = "user-123"
        val groupId = "group-456"

        coEvery { supabaseRepository.currentUserId } returns userId
        coEvery { supabaseRepository.getUserRole() } returns UserRole.MEMBER
        coEvery { supabaseRepository.updateUserRole(userId, UserRole.GROUP_ADMIN, groupId) } returns Result.success(Unit)
        coEvery { supabaseRepository.currentSessionEmail } returns "existing@test.com"
        coEvery { groupRepository.createGroup(any()) } returns Result.success(groupId)
        coEvery { memberRepository.upsertMember(any()) } returns Result.success(mockk())

        // When
        val result = createGroupUseCase(group = group)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(groupId, result.getOrNull())
        coVerify { supabaseRepository.updateUserRole(userId, UserRole.GROUP_ADMIN, groupId) }
        coVerify { groupRepository.createGroup(match { it.adminUserId == userId }) }
        coVerify {
            memberRepository.upsertMember(match { member ->
                member.groupId == groupId &&
                member.userId == userId &&
                member.status == MemberStatus.PENDING_PAYMENT
            })
        }
    }

    @Test
    fun `invoke captures all admin information in member record`() = runBlocking {
        // Given
        val group = Group(
            name = "Full Info Test Group",
            province = "KwaZulu-Natal",
            city = "Durban",
            township = "Umlazi",
            probationMonths = 6
        )
        val adminEmail = "fulladmin@test.com"
        val adminPassword = "securePassword123"
        val adminFullName = "Sipho Nkosi"
        val adminPhone = "0833334444"
        val adminIdNumber = "9001015800088"
        val adminStreet = "123 Main Street"
        val adminSuburb = "Phoenix"
        val userId = "user-full-info"
        val groupId = "group-full-info"

        coEvery { supabaseRepository.signUp(any(), any(), any()) } returns Result.success(userId)
        coEvery { groupRepository.createGroup(any()) } returns Result.success(groupId)
        coEvery { supabaseRepository.updateUserRole(userId, UserRole.GROUP_ADMIN, groupId) } returns Result.success(Unit)
        coEvery { memberRepository.upsertMember(any()) } returns Result.success(mockk())

        // When
        val result = createGroupUseCase(
            group = group,
            adminEmail = adminEmail,
            adminPassword = adminPassword,
            adminFullName = adminFullName,
            adminPhone = adminPhone,
            adminIdNumber = adminIdNumber,
            adminStreet = adminStreet,
            adminSuburb = adminSuburb
        )

        // Then
        assertTrue(result.isSuccess)
        coVerify { supabaseRepository.signUp(adminEmail, adminPassword, match { it["role"] == "member" }) }
        coVerify {
            memberRepository.upsertMember(match { member ->
                member.fullName == adminFullName &&
                member.email == adminEmail &&
                member.phone == adminPhone &&
                member.idNumber == adminIdNumber &&
                member.street == adminStreet &&
                member.suburb == adminSuburb &&
                member.province == "KwaZulu-Natal" &&
                member.city == "Durban" &&
                member.probationEndAt != null // Probation end date should be set
            })
        }
    }

    @Test
    fun `invoke with admin email and blank password should use existing session account and not sign up`() = runBlocking {
        // Given
        val group = Group(name = "Mixed Credentials Group", province = "Gauteng", city = "Johannesburg", probationMonths = 3)
        val currentUserId = "existing-user-1"
        val groupId = "group-mixed-1"

        coEvery { supabaseRepository.currentUserId } returns currentUserId
        coEvery { supabaseRepository.getUserRole() } returns UserRole.MEMBER
        coEvery { supabaseRepository.currentSessionEmail } returns "sessionuser@test.com"
        coEvery { groupRepository.createGroup(any()) } returns Result.success(groupId)
        coEvery { supabaseRepository.updateUserRole(currentUserId, UserRole.GROUP_ADMIN, groupId) } returns Result.success(Unit)
        coEvery { memberRepository.upsertMember(any()) } returns Result.success(mockk())

        // When
        val result = createGroupUseCase(
            group = group,
            adminEmail = "adminprovided@test.com",
            adminPassword = "   "
        )

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { supabaseRepository.signUp(any(), any(), any()) }
        coVerify { groupRepository.createGroup(match { it.adminUserId == currentUserId }) }
        coVerify {
            memberRepository.upsertMember(match { member ->
                member.userId == currentUserId &&
                member.email == "adminprovided@test.com" &&
                member.status == MemberStatus.PENDING_PAYMENT
            })
        }
    }

    @Test
    fun `role is not elevated when the creator member record fails`() = runBlocking {
        val userId = "user-rollback"
        val groupId = "group-rollback"

        coEvery { supabaseRepository.currentUserId } returns userId
        coEvery { groupRepository.createGroup(any()) } returns Result.success(groupId)
        coEvery { memberRepository.upsertMember(any()) } returns Result.failure(Exception("network down"))
        coEvery { groupRepository.deleteGroup(groupId) } returns Result.success(Unit)

        val result = createGroupUseCase(Group(name = "Rolled Back Group", probationMonths = 3))

        assertTrue(result.isFailure)
        // A role granted for a group that never survived would leave an admin with no group.
        coVerify(exactly = 0) { supabaseRepository.updateUserRole(any(), any(), any()) }
    }

    @Test
    fun `invoke with whitespace email and whitespace password should not sign up and should fall back to session email`() = runBlocking {
        // Given
        val group = Group(name = "Whitespace Credentials Group", province = "Limpopo", city = "Polokwane", probationMonths = 3)
        val currentUserId = "existing-user-2"
        val groupId = "group-mixed-2"
        val sessionEmail = "fallback@test.com"

        coEvery { supabaseRepository.currentUserId } returns currentUserId
        coEvery { supabaseRepository.getUserRole() } returns UserRole.MEMBER
        coEvery { supabaseRepository.currentSessionEmail } returns sessionEmail
        coEvery { groupRepository.createGroup(any()) } returns Result.success(groupId)
        coEvery { supabaseRepository.updateUserRole(currentUserId, UserRole.GROUP_ADMIN, groupId) } returns Result.success(Unit)
        coEvery { memberRepository.upsertMember(any()) } returns Result.success(mockk())

        // When
        val result = createGroupUseCase(
            group = group,
            adminEmail = "   ",
            adminPassword = "\t\n"
        )

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { supabaseRepository.signUp(any(), any(), any()) }
        coVerify {
            memberRepository.upsertMember(match { member ->
                member.userId == currentUserId &&
                member.email == sessionEmail
            })
        }
    }
}
