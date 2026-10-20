package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class RegisterMemberUseCaseTest {

    private lateinit var registerMemberUseCase: RegisterMemberUseCase
    private val memberRepository: MemberRepository = mockk(relaxed = true)
    private val groupRepository: GroupRepository = mockk(relaxed = true)
    private val supabaseRepository: SupabaseRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        registerMemberUseCase = RegisterMemberUseCase(memberRepository, groupRepository, supabaseRepository)
        coEvery { supabaseRepository.currentUserId } returns "user-123"
    }

    @Test
    fun `invoke should register member with PENDING_PAYMENT when group requires joining fee`() = runBlocking {
        // Given
        val groupId = "group-123"
        val member = Member(groupId = groupId, idNumber = "9001015000085")
        val group = Group(id = groupId, joiningFee = Money("100.0"), registrationPaid = true, maxMembers = 50, currentMembers = 10, probationMonths = 0)

        coEvery { groupRepository.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepository.getMemberByUserId(any(), any()) } returns Result.failure(Exception("Not found"))
        coEvery { memberRepository.registerMember(any(), any()) } returns Result.success(member.copy(id = "member-1", status = MemberStatus.PENDING_PAYMENT))

        // When
        val result = registerMemberUseCase(member)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(MemberStatus.PENDING_PAYMENT, result.getOrNull()?.status)
    }

    @Test
    fun `invoke should register member with ACTIVE status when group has no joining fee and no probation`() = runBlocking {
        // Given
        val groupId = "group-123"
        val member = Member(groupId = groupId, idNumber = "9001015000085")
        val group = Group(id = groupId, joiningFee = Money("0.0"), registrationPaid = true, maxMembers = 50, currentMembers = 10, probationMonths = 0)

        coEvery { groupRepository.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepository.getMemberByUserId(any(), any()) } returns Result.failure(Exception("Not found"))
        coEvery { memberRepository.registerMember(any(), any()) } returns Result.success(member.copy(id = "member-1", status = MemberStatus.ACTIVE))

        // When
        val result = registerMemberUseCase(member)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(MemberStatus.ACTIVE, result.getOrNull()?.status)
    }

    @Test
    fun `invoke should register member with PROBATION status when group has probation period`() = runBlocking {
        // Given
        val groupId = "group-123"
        val member = Member(groupId = groupId, idNumber = "9001015000085")
        val group = Group(id = groupId, joiningFee = Money("0.0"), registrationPaid = true, maxMembers = 50, currentMembers = 10, probationMonths = 3)

        coEvery { groupRepository.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepository.getMemberByUserId(any(), any()) } returns Result.failure(Exception("Not found"))
        coEvery { memberRepository.registerMember(any(), any()) } returns Result.success(member.copy(id = "member-1", status = MemberStatus.PROBATION))

        // When
        val result = registerMemberUseCase(member)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(MemberStatus.PROBATION, result.getOrNull()?.status)
    }

    @Test
    fun `invoke should fail when group capacity is reached`() = runBlocking {
        // Given
        val groupId = "group-full"
        val member = Member(groupId = groupId, idNumber = "9001015000085")
        val group = Group(id = groupId, registrationPaid = true, maxMembers = 20, currentMembers = 20)

        coEvery { groupRepository.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepository.getMemberByUserId(any(), any()) } returns Result.failure(Exception("Not found"))

        // When
        val result = registerMemberUseCase(member)

        // Then
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("maximum capacity") == true)
        coVerify(exactly = 0) { memberRepository.registerMember(any(), any()) }
    }

    @Test
    fun `invoke should fail when user is already enrolled in the group`() = runBlocking {
        // Given
        val groupId = "group-dup"
        val member = Member(groupId = groupId, userId = "user-123", idNumber = "9001015000085")
        val group = Group(id = groupId, registrationPaid = true, maxMembers = 50, currentMembers = 10)

        coEvery { groupRepository.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepository.getMemberByUserId("user-123", groupId) } returns Result.success(member)

        // When
        val result = registerMemberUseCase(member)

        // Then
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("already enrolled") == true)
        coVerify(exactly = 0) { memberRepository.registerMember(any(), any()) }
    }

    @Test
    fun `invoke should fail when SA ID number format is invalid`() = runBlocking {
        // Given
        val groupId = "group-id-check"
        val member = Member(groupId = groupId, idNumber = "12345") // Invalid ID, not 13 digits
        val group = Group(id = groupId, registrationPaid = true, maxMembers = 50, currentMembers = 10)

        coEvery { groupRepository.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepository.getMemberByUserId(any(), any()) } returns Result.failure(Exception("Not found"))

        // When
        val result = registerMemberUseCase(member)

        // Then
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("13 digits") == true)
        coVerify(exactly = 0) { memberRepository.registerMember(any(), any()) }
    }
}
