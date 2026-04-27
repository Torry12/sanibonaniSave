package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegisterMemberUseCaseTest {

    private lateinit var registerMemberUseCase: RegisterMemberUseCase
    private val memberRepository: MemberRepository = mockk()
    private val groupRepository: GroupRepository = mockk()

    @Before
    fun setUp() {
        registerMemberUseCase = RegisterMemberUseCase(memberRepository, groupRepository)
    }

    @Test
    fun `invoke should register member with correct status when group requires joining fee`() = runBlocking {
        // Given
        val groupId = "group-123"
        val member = Member(id = "member-1", groupId = groupId)
        val group = Group(id = groupId, joiningFee = 100.0, registrationPaid = true)

        coEvery { groupRepository.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepository.registerMember(any(), any()) } returns Result.success(member.copy(status = MemberStatus.PENDING_PAYMENT))

        // When
        val result = registerMemberUseCase(member)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(MemberStatus.PENDING_PAYMENT, result.getOrNull()?.status)
    }

    @Test
    fun `invoke should register member with active status when group has no joining fee`() = runBlocking {
        // Given
        val groupId = "group-123"
        val member = Member(id = "member-1", groupId = groupId)
        val group = Group(id = groupId, joiningFee = 0.0, registrationPaid = true)

        coEvery { groupRepository.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepository.registerMember(any(), any()) } returns Result.success(member.copy(status = MemberStatus.ACTIVE))

        // When
        val result = registerMemberUseCase(member)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(MemberStatus.ACTIVE, result.getOrNull()?.status)
    }
}
