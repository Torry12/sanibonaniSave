package com.sanibonani.save.domain.usecase.disbursement
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.service.PayoutGuard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestPayoutUseCaseTest {

    private lateinit var requestPayoutUseCase: RequestPayoutUseCase
    private val payoutRepository: PayoutRepository = mockk()
    private val groupRepository: GroupRepository = mockk()
    private val memberRepository: MemberRepository = mockk()
    private val payoutGuard: PayoutGuard = mockk()
    private val executeRoscaPayoutUseCase: ExecuteRoscaPayoutUseCase = mockk()
    private val calculateStokvelPayoutsUseCase: CalculateStokvelPayoutsUseCase = mockk()
    private val calculateInvestmentClubValuationUseCase: CalculateInvestmentClubValuationUseCase = mockk()

    @Before
    fun setUp() {
        requestPayoutUseCase = RequestPayoutUseCase(
            payoutRepository,
            groupRepository,
            memberRepository,
            payoutGuard,
            com.sanibonani.save.domain.service.GroupFinancialPolicy(),
            executeRoscaPayoutUseCase,
            calculateStokvelPayoutsUseCase,
            calculateInvestmentClubValuationUseCase
        )
    }

    @Test
    fun `a stokvel member payout is valued from one roster sync`() = runBlocking {
        val group = Group(id = "group-1", type = GroupType.STOKVEL)
        val member = Member(
            id = "m-1",
            groupId = "group-1",
            fullName = "Nomsa Dlamini",
            bankName = "FNB",
            accountNo = "1234567890",
            branchCode = "250655"
        )
        coEvery { groupRepository.getGroupById("group-1") } returns Result.success(group)
        coEvery { memberRepository.syncGroupMembers("group-1") } returns Result.success(listOf(member))
        every { calculateStokvelPayoutsUseCase(any(), any(), any()) } returns Money("1500.00")
        coEvery { payoutGuard.validateRequest(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { payoutRepository.requestPayout(any()) } returns Result.success("payout-1")

        val result = requestPayoutUseCase(groupId = "group-1", memberId = "m-1")

        assertTrue(result.isSuccess)
        // The bank details and the pool value come from the same read, so the amount can never be
        // calculated against a roster that has moved on since the member was found.
        coVerify(exactly = 1) { memberRepository.syncGroupMembers("group-1") }
        val submitted = slot<PayoutRequest>()
        coVerify { payoutRepository.requestPayout(capture(submitted)) }
        assertEquals(Money("1500.00"), submitted.captured.amount)
        assertEquals("m-1", submitted.captured.memberId)
        assertEquals(PayoutType.ANNUAL, submitted.captured.type)
    }

    @Test
    fun `invoke with valid inputs submits pending payout request`() = runBlocking {
        val group = Group(id = "group-1", type = GroupType.STOKVEL)
        coEvery { groupRepository.getGroupById("group-1") } returns Result.success(group)
        coEvery { payoutRepository.requestPayout(any()) } returns Result.success("payout-1")
        coEvery { payoutGuard.validateRequest(any(), any(), any(), any()) } returns Result.success(Unit)

        val result = requestPayoutUseCase(
            groupId = "group-1",
            requestedAmount = Money("1500.0"),
            bankNameOverride = "FNB",
            accountNoOverride = "1234567890",
            branchCodeOverride = "250655"
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { payoutRepository.requestPayout(any<PayoutRequest>()) }
    }

    @Test
    fun `invoke with non-positive amount fails validation`() = runBlocking {
        val group = Group(id = "group-1", type = GroupType.STOKVEL)
        coEvery { groupRepository.getGroupById("group-1") } returns Result.success(group)

        val result = requestPayoutUseCase(
            groupId = "group-1",
            requestedAmount = Money("0.0"),
            bankNameOverride = "FNB",
            accountNoOverride = "1234567890",
            branchCodeOverride = "250655"
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }

    @Test
    fun `invoke with invalid account number fails validation`() = runBlocking {
        val group = Group(id = "group-1", type = GroupType.STOKVEL)
        coEvery { groupRepository.getGroupById("group-1") } returns Result.success(group)
        coEvery { payoutGuard.validateRequest(any(), any(), any(), any()) } returns Result.success(Unit)

        val result = requestPayoutUseCase(
            groupId = "group-1",
            requestedAmount = Money("100.0"),
            bankNameOverride = "FNB",
            accountNoOverride = "12345",
            branchCodeOverride = "250655"
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }
}
