package com.sanibonani.save.simulation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.service.PlatformFeeService
import com.sanibonani.save.domain.usecase.credit.CalculateLoanRepaymentScheduleUseCase
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.domain.utils.DateProvider
import com.sanibonani.save.domain.utils.PaymentCalculator
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class LoanProcessingContingencySimulationTest {

    private val contributionRepo = mockk<ContributionRepository>()
    private val loanRepo = mockk<LoanRepository>()
    private val behaviorRepo = mockk<BehaviorTrackingRepository>()
    private val feeService = mockk<PlatformFeeService>()

    private lateinit var validateEligibility: ValidateLoanEligibilityUseCase
    private val calculateSchedule = CalculateLoanRepaymentScheduleUseCase()

    private val fixedNow = LocalDate.of(2026, 4, 29)

    @Before
    fun setup() {
        every { feeService.calculateMonthlyMemberIndividual(any(), any()) } returns Money.ZERO
        every { feeService.getShortfallRecoveryFee() } returns Money.ZERO
        val paymentCalculator = PaymentCalculator(feeService)

        validateEligibility = ValidateLoanEligibilityUseCase(contributionRepo, loanRepo, behaviorRepo, paymentCalculator)
        mockkObject(DateProvider)
        every { DateProvider.getCurrentDate() } returns fixedNow
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `contingency state 1 - investment club loan prohibition`() = runTest {
        val group = Group(type = GroupType.INVESTMENT_CLUB, balance = Money("10000.0"))
        val member = Member(status = MemberStatus.ACTIVE)

        val result = validateEligibility(member, group, Money("1000.0"), 2)
        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        assertEquals("No loans for Investment Clubs.", (result as ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible).reason)
    }

    @Test
    fun `contingency state 2 - loan term limit enforcement (max 3 months)`() = runTest {
        val group = Group(type = GroupType.STOKVEL, balance = Money("10000.0"))
        val member = Member(status = MemberStatus.ACTIVE)

        val result = validateEligibility(member, group, Money("1000.0"), 6) // Term 6 months
        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        assertEquals("Loan term exceeds the 3-month maximum limit.", (result as ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible).reason)
    }

    @Test
    fun `contingency state 3 - over borrowing limit rejection`() = runTest {
        val joinedDate = fixedNow.minusMonths(6).toString()
        val group = Group(id = "g1", type = GroupType.STOKVEL, monthlyContribution = Money("500.0"))
        val member = Member(id = "m1", status = MemberStatus.ACTIVE, joinedAt = "${joinedDate}T00:00:00Z")

        // Last 6 months contributions total R500 -> Max borrowing power = R150 (30%)
        val contributions = listOf(
            Contribution(memberId = "m1", groupId = "g1", amount = Money("500.0"), status = ContributionStatus.PAID, paidAt = fixedNow.minusMonths(1).toString())
        )
        every { contributionRepo.observeMemberContributions("m1", "g1") } returns flowOf(Result.success(contributions))
        every { loanRepo.getMemberLoans("m1") } returns flowOf(Result.success(emptyList()))
        coEvery { behaviorRepo.getMemberBehavior("m1") } returns Result.success(mockk(relaxed = true))

        // Attempting to borrow R2,500 (> R150 max borrowing power)
        val result = validateEligibility(member, group, Money("2500.0"), 2)
        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        val reason = (result as ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible).reason
        assertTrue(reason.contains("exceeds remaining available borrowing power"))
    }

    @Test
    fun `contingency state 4 - amortization and platform processing fee calculation`() = runTest {
        val principal = Money("2000.0")
        val annualRate = 20.0 // 20% p.a.
        val term = 3
        val processingFee = Money("15.0")

        val scheduleRes = calculateSchedule(
            CalculateLoanRepaymentScheduleUseCase.Params(
                principal = principal,
                annualInterestRate = annualRate,
                termMonths = term,
                processingFee = processingFee,
                amortizationType = AmortizationType.FLAT,
                startDate = fixedNow
            )
        )

        // R2000 principal + interest (R100) + R15 processing fee = R2115 total repayable
        assertEquals(Money("2115.0"), scheduleRes.totalToRepay)
        assertEquals(3, scheduleRes.schedule.size)
        assertEquals(processingFee, scheduleRes.processingFee)
    }
}
