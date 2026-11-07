package com.sanibonani.save.domain.usecase.payment

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PaymentDestination
import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.repository.admin.PlatformConfigRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Pins what the payment screen must resolve before it can take money: the payer, the
 * group, the amount owed, and which wallet the payment belongs to.
 */
class LoadPaymentContextUseCaseTest {

    private val authRepository: SupabaseRepository = mockk()
    private val groupRepository: GroupRepository = mockk()
    private val memberRepository: MemberRepository = mockk()
    private val contributionRepository: ContributionRepository = mockk()
    private val loanRepository: LoanRepository = mockk()
    private val configRepository: PlatformConfigRepository = mockk()
    private val paymentCalculator: PaymentCalculator = mockk()

    private val useCase = LoadPaymentContextUseCase(
        authRepository,
        groupRepository,
        memberRepository,
        contributionRepository,
        loanRepository,
        configRepository,
        paymentCalculator
    )

    private val groupId = "group-1"
    private val member = Member(id = "member-1", groupId = groupId, status = MemberStatus.ACTIVE)
    private val group = Group(id = groupId, monthlyContribution = Money("200.00"), allowPartialPayment = true)
    private val config = PlatformConfig(monthlyMemberFee = Money("25.00"))
    private val calculation = PaymentCalculation(Money("200.00"), Money.ZERO, "2026-11-01", Money("200.00"))

    private fun params(type: String = "contribution") =
        LoadPaymentContextUseCase.Params(userId = "user-1", groupId = groupId, type = type)

    private fun stubResolvedContext(memberToReturn: Member = member) {
        every { memberRepository.observeMemberByUserId("user-1", groupId) } returns
            flowOf(Result.success(memberToReturn))
        coEvery { groupRepository.getGroupById(groupId) } returns Result.success(group)
        every { contributionRepository.observeMemberContributions(any(), any()) } returns
            flowOf(Result.success(emptyList()))
        every { loanRepository.getMemberLoans(any()) } returns flowOf(Result.success(emptyList()))
        every { configRepository.current() } returns config
        every { paymentCalculator.calculateStatus(any(), any(), any(), any(), any()) } returns calculation
    }

    private suspend fun outcome(type: String = "contribution"): LoadPaymentContextUseCase.Outcome =
        useCase(params(type)).first()

    @Test
    fun `a resolved member and group produce a priced payment context`() = runTest {
        stubResolvedContext()

        val ready = outcome() as LoadPaymentContextUseCase.Outcome.Ready

        assertEquals(member, ready.context.member)
        assertEquals(group, ready.context.group)
        assertEquals(calculation, ready.context.calculation)
        assertEquals(config, ready.context.config)
        assertTrue(ready.context.contributions.isEmpty())
        assertTrue(ready.context.loans.isEmpty())
    }

    @Test
    fun `group contributions are paid into the group wallet`() = runTest {
        stubResolvedContext()

        val ready = outcome("contribution") as LoadPaymentContextUseCase.Outcome.Ready

        assertEquals(PaymentDestination.GROUP, ready.context.destination)
    }

    @Test
    fun `fee payments are paid into the platform wallet whatever the casing`() = runTest {
        stubResolvedContext()

        listOf("registration", "platform_fee", "joining_fee", "PLATFORM_FEE").forEach { type ->
            val ready = outcome(type) as LoadPaymentContextUseCase.Outcome.Ready
            assertEquals("type=$type", PaymentDestination.PLATFORM, ready.context.destination)
        }
    }

    @Test
    fun `a missing group is unavailable rather than an empty payment screen`() = runTest {
        stubResolvedContext()
        coEvery { groupRepository.getGroupById(groupId) } returns
            Result.failure(IllegalStateException("Group not found"))

        assertEquals(LoadPaymentContextUseCase.Outcome.Unavailable, outcome())
    }

    @Test
    fun `a member without a server id is unavailable`() = runTest {
        stubResolvedContext(memberToReturn = Member(groupId = groupId))

        assertEquals(LoadPaymentContextUseCase.Outcome.Unavailable, outcome())
    }

    @Test
    fun `the signed-in user is the one the context resolves`() = runTest {
        stubResolvedContext()
        every { authRepository.currentUserId } returns "user-1"
        every { memberRepository.observeMemberByUserId("user-1", groupId) } returns
            flowOf(Result.success(member))

        assertEquals("user-1", useCase.currentUserId())
        assertTrue(outcome() is LoadPaymentContextUseCase.Outcome.Ready)
    }

    // A refresh that did not land now arrives as its own emission behind the cache. Blanking the
    // legs there would re-price the payment from nothing: the member would be billed the monthly
    // amount again as if no contribution had ever been booked.
    @Test
    fun `a refused refresh prices the payment from the rows already read`() = runTest {
        val contribution = Contribution(
            id = "c-1",
            memberId = "member-1",
            groupId = groupId,
            amount = Money("200.00"),
            status = com.sanibonani.save.domain.model.ContributionStatus.PAID
        )
        val loan = Loan(id = "loan-1", groupId = groupId)
        stubResolvedContext()
        every { contributionRepository.observeMemberContributions("member-1", groupId) } returns flowOf(
            Result.success(listOf(contribution)),
            Result.failure(IOException("contribution refresh refused"))
        )
        every { loanRepository.getMemberLoans("member-1") } returns flowOf(
            Result.success(listOf(loan)),
            Result.failure(IOException("loan refresh refused"))
        )

        val emissions = useCase(params()).toList()
        val refused = emissions.last() as LoadPaymentContextUseCase.Outcome.Ready

        assertEquals(listOf(contribution), refused.context.contributions)
        assertEquals(listOf(loan), refused.context.loans)
        assertFalse(
            "the figures are kept, but the screen cannot claim the server agreed to them",
            refused.context.amountsVerified
        )
        verify {
            paymentCalculator.calculateStatus(group, member, listOf(contribution), listOf(loan), any())
        }
    }

    @Test
    fun `a payment priced from rows the server answered is verified`() = runTest {
        stubResolvedContext()

        val ready = outcome() as LoadPaymentContextUseCase.Outcome.Ready

        assertTrue(ready.context.amountsVerified)
    }

    @Test
    fun `one refused book is enough to make the pricing unverified`() = runTest {
        // The two legs refuse independently, and the member cannot tell which of the two numbers they
        // are about to pay against is the stale one.
        stubResolvedContext()
        every { contributionRepository.observeMemberContributions("member-1", groupId) } returns
            flowOf(Result.success(emptyList()))
        every { loanRepository.getMemberLoans("member-1") } returns flowOf(
            Result.success(emptyList()),
            Result.failure(IOException("loan refresh refused"))
        )

        val emissions = useCase(params()).toList()

        assertTrue((emissions.first() as LoadPaymentContextUseCase.Outcome.Ready).context.amountsVerified)
        assertFalse((emissions.last() as LoadPaymentContextUseCase.Outcome.Ready).context.amountsVerified)
    }
}
