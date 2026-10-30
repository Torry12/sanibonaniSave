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
    fun `an investment club payout values the member against the whole roster`() = runBlocking {
        val group = Group(id = "group-1", type = GroupType.INVESTMENT_CLUB, balance = Money("1000.00"))
        val requesting = Member(
            id = "m-1",
            groupId = "group-1",
            fullName = "Nomsa Dlamini",
            bankName = "FNB",
            accountNo = "1234567890",
            branchCode = "250655",
            totalPaid = Money("600.00")
        )
        val other = Member(
            id = "m-2",
            groupId = "group-1",
            fullName = "Thabo Nkosi",
            totalPaid = Money("400.00")
        )
        coEvery { groupRepository.getGroupById("group-1") } returns Result.success(group)
        // The requesting member is deliberately not first: taking the first valuation would pay
        // the wrong member's share.
        coEvery { memberRepository.syncGroupMembers("group-1") } returns
            Result.success(listOf(other, requesting))
        every { calculateStokvelPayoutsUseCase(any(), any(), any()) } returns Money.ZERO
        val roster = slot<List<Member>>()
        every { calculateInvestmentClubValuationUseCase(any(), capture(roster)) } returns Result.success(
            CalculateInvestmentClubValuationUseCase.PortfolioValuation(
                totalAssets = Money("1000.00"),
                totalUnits = Money("1000.00"),
                unitPrice = Money("1.00"),
                memberValuations = listOf(
                    CalculateInvestmentClubValuationUseCase.MemberValuation(
                        memberId = "m-2",
                        memberName = "Thabo Nkosi",
                        unitsOwned = Money("400.00"),
                        marketValue = Money("400.00"),
                        contributionWeight = 40.0
                    ),
                    CalculateInvestmentClubValuationUseCase.MemberValuation(
                        memberId = "m-1",
                        memberName = "Nomsa Dlamini",
                        unitsOwned = Money("600.00"),
                        marketValue = Money("600.00"),
                        contributionWeight = 60.0
                    )
                )
            )
        )
        coEvery { payoutGuard.validateRequest(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { payoutRepository.requestPayout(any()) } returns Result.success("payout-1")

        val result = requestPayoutUseCase(groupId = "group-1", memberId = "m-1")

        assertTrue(result.isSuccess)
        assertEquals(2, roster.captured.size)
        val submitted = slot<PayoutRequest>()
        coVerify { payoutRepository.requestPayout(capture(submitted)) }
        assertEquals(Money("600.00"), submitted.captured.amount)
    }

    @Test
    fun `an unavailable investment valuation never becomes a zero-amount fallback`() = runBlocking {
        val group = Group(id = "group-1", type = GroupType.INVESTMENT_CLUB)
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
        every { calculateInvestmentClubValuationUseCase(any(), any()) } returns
            Result.failure(IllegalStateException("valuation unavailable"))

        val result = requestPayoutUseCase(groupId = "group-1", memberId = "m-1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }

    @Test
    fun `an unavailable member roster never becomes an empty roster for a payout`() = runBlocking {
        coEvery { groupRepository.getGroupById("group-1") } returns Result.success(
            Group(id = "group-1", type = GroupType.STOKVEL)
        )
        coEvery { memberRepository.syncGroupMembers("group-1") } returns
            Result.failure(IllegalStateException("offline"))

        val result = requestPayoutUseCase(groupId = "group-1", memberId = "m-1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }

    @Test
    fun `a member payout never falls back to the group bank account`() = runBlocking {
        val group = Group(
            id = "group-1",
            type = GroupType.STOKVEL,
            bankName = "Capitec",
            accountNumber = "9876543210",
            branchCode = "470010"
        )
        val member = Member(
            id = "m-1",
            groupId = "group-1",
            fullName = "Nomsa Dlamini",
            bankName = null,
            accountNo = null,
            branchCode = null
        )
        coEvery { groupRepository.getGroupById("group-1") } returns Result.success(group)
        coEvery { memberRepository.syncGroupMembers("group-1") } returns Result.success(listOf(member))
        every { calculateStokvelPayoutsUseCase(any(), any(), any()) } returns Money("1500.00")
        coEvery { payoutGuard.validateRequest(any(), any(), any(), any()) } returns Result.success(Unit)

        val result = requestPayoutUseCase(groupId = "group-1", memberId = "m-1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }

    @Test
    fun `a group-level payout still uses the group bank account`() = runBlocking {
        val group = Group(
            id = "group-1",
            type = GroupType.STOKVEL,
            bankName = "Capitec",
            accountNumber = "9876543210",
            branchCode = "470010"
        )
        coEvery { groupRepository.getGroupById("group-1") } returns Result.success(group)
        coEvery { payoutGuard.validateRequest(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { payoutRepository.requestPayout(any()) } returns Result.success("payout-1")

        val result = requestPayoutUseCase(groupId = "group-1", requestedAmount = Money("100.00"))

        assertTrue(result.isSuccess)
        val submitted = slot<PayoutRequest>()
        coVerify { payoutRepository.requestPayout(capture(submitted)) }
        assertEquals("Capitec", submitted.captured.bankName)
        assertEquals("9876543210", submitted.captured.accountNo)
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
