package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.member.MemberRepository
import io.mockk.coEvery
import io.mockk.coVerify
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

    @Test
    fun `a demotion the old transition table never listed still reaches the repository`() = runBlocking {
        // ACTIVE -> PROBATION is what ProcessLoanForcedSettlementUseCase does to a member two months
        // in arrears, so enforcing the table here would have refused a live sanction. The rule the
        // table tried to state is not honoured by the eight places that write a status, so it is
        // gone rather than half-wired: this use case stays a thin, honest pass-through.
        coEvery { memberRepository.updateMemberStatus("member-1", MemberStatus.PROBATION) } returns
            Result.success(Unit)

        val result = updateMemberStatusUseCase("member-1", MemberStatus.PROBATION)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { memberRepository.updateMemberStatus("member-1", MemberStatus.PROBATION) }
    }
}
