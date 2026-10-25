package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.member.MemberRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateMemberStatusUseCaseTest {

    private lateinit var updateMemberStatusUseCase: UpdateMemberStatusUseCase
    private val memberRepository: MemberRepository = mockk()

    @Before
    fun setUp() {
        updateMemberStatusUseCase = UpdateMemberStatusUseCase(memberRepository)
    }

    @Test
    fun `invoke should call member repository`() = runBlocking {
        // Given
        val memberId = "member-1"
        val status = MemberStatus.SUSPENDED
        coEvery { memberRepository.updateMemberStatus(memberId, status) } returns Result.success(Unit)

        // When
        val result = updateMemberStatusUseCase(memberId, status)

        // Then
        assertTrue(result.isSuccess)
    }
}
