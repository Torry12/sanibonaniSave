package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.InsufficientFundsException
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.model.PayoutType
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.service.PayoutGuard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the member-withdrawal rules that used to live inside `MemberFinanceViewModel`:
 * bank details are checked before the treasury is asked, and the request is queued
 * exactly as the guard approved it.
 */
class RequestWithdrawalUseCaseTest {

    private val payoutRepository: PayoutRepository = mockk()
    private val payoutGuard: PayoutGuard = mockk()
    private val useCase = RequestWithdrawalUseCase(payoutRepository, payoutGuard)

    private val group = Group(id = "g1", name = "Test Group")
    private val amount = Money("500.0")

    private fun member(
        id: String? = "m1",
        bankName: String? = "FNB",
        accountNo: String? = "123456789",
        branchCode: String? = "250655"
    ) = Member(
        id = id,
        groupId = "g1",
        fullName = "Alpha Member",
        status = MemberStatus.ACTIVE,
        bankName = bankName,
        accountNo = accountNo,
        branchCode = branchCode
    )

    private fun params(member: Member = member()) = RequestWithdrawalUseCase.Params(
        group = group,
        member = member,
        amount = amount,
        type = PayoutType.EMERGENCY
    )

    @Test
    fun `a missing bank name is rejected before the guard or the repository`() = runBlocking {
        val result = useCase(params(member(bankName = null)))

        assertTrue(result.isFailure)
        assertEquals(
            "Add your bank name before requesting a withdrawal.",
            result.exceptionOrNull()?.message
        )
        coVerify(exactly = 0) { payoutGuard.validateRequest(any(), any(), any(), any()) }
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }

    @Test
    fun `an invalid account number is rejected before the guard or the repository`() = runBlocking {
        val result = useCase(params(member(accountNo = "12AB")))

        assertTrue(result.isFailure)
        assertEquals(
            "Add a valid account number before requesting a withdrawal.",
            result.exceptionOrNull()?.message
        )
        coVerify(exactly = 0) { payoutGuard.validateRequest(any(), any(), any(), any()) }
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }

    @Test
    fun `an invalid branch code is rejected before the guard or the repository`() = runBlocking {
        val result = useCase(params(member(branchCode = "123")))

        assertTrue(result.isFailure)
        assertEquals(
            "Add a valid 6-digit branch code before requesting a withdrawal.",
            result.exceptionOrNull()?.message
        )
        coVerify(exactly = 0) { payoutGuard.validateRequest(any(), any(), any(), any()) }
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }

    @Test
    fun `a valid withdrawal is guarded and queued with cleaned bank details`() = runBlocking {
        val member = member(bankName = " FNB ", accountNo = " 123456789 ", branchCode = " 250655 ")
        coEvery { payoutGuard.validateRequest(group, member, amount, PayoutType.EMERGENCY) } returns Result.success(Unit)
        coEvery { payoutRepository.requestPayout(any()) } returns Result.success("p1")

        val request = useCase(params(member)).getOrThrow()

        assertEquals("g1", request.groupId)
        assertEquals("m1", request.memberId)
        assertEquals(amount, request.amount)
        assertEquals(PayoutType.EMERGENCY, request.type)
        assertEquals(PayoutStatus.PENDING, request.status)
        assertEquals("FNB", request.bankName)
        assertEquals("123456789", request.accountNo)
        assertEquals("250655", request.branchCode)
        coVerify(exactly = 1) { payoutRepository.requestPayout(request) }
    }

    @Test
    fun `a guard rejection is passed back and nothing is queued`() = runBlocking {
        coEvery { payoutGuard.validateRequest(any(), any(), any(), any()) } returns
            Result.failure(InsufficientFundsException(amount, Money("10.0")))

        val result = useCase(params())

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }

    @Test
    fun `a repository failure is passed back to the caller`() = runBlocking {
        coEvery { payoutGuard.validateRequest(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { payoutRepository.requestPayout(any()) } returns
            Result.failure(IllegalStateException("offline"))

        val result = useCase(params())

        assertTrue(result.isFailure)
        assertEquals("offline", result.exceptionOrNull()?.message)
    }

    @Test
    fun `a member without a server id is rejected before the guard`() = runBlocking {
        val result = useCase(params(member(id = null)))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutGuard.validateRequest(any(), any(), any(), any()) }
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }
}
