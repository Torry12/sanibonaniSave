package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.utils.DateProvider
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ValidateLoanEligibilityUseCaseTest {

    private val contributionRepository = mockk<ContributionRepository>()
    private val loanRepository = mockk<LoanRepository>()
    private lateinit var useCase: ValidateLoanEligibilityUseCase
    private val fixedNow = LocalDate.of(2026, 4, 29)

    @Before
    fun setup() {
        val feeService = mockk<com.sanibonani.save.domain.service.PlatformFeeService>()
        every { feeService.calculateMonthlyMemberIndividual(any(), any()) } returns Money.ZERO
        every { feeService.getShortfallRecoveryFee() } returns Money.ZERO

        val paymentCalculator = com.sanibonani.save.domain.utils.PaymentCalculator(feeService, com.sanibonani.save.domain.service.GroupFinancialPolicy())
        useCase = ValidateLoanEligibilityUseCase(contributionRepository, loanRepository, paymentCalculator)
        mockkObject(DateProvider)
        every { DateProvider.getCurrentDate() } returns fixedNow
        every { loanRepository.getMemberLoans(any()) } returns flowOf(Result.success(emptyList()))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `invoke - ineligible if member is not active`() = runTest {
        val member = Member(status = MemberStatus.PROBATION)
        val group = Group(type = GroupType.STOKVEL)

        val result = useCase(member, group)

        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        assertEquals("Member not active.", (result as ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible).reason)
    }

    @Test
    fun `invoke - ineligible if contributions are overdue`() = runTest {
        // Joined 7 months before fixedNow
        val joinedDate = fixedNow.minusMonths(7).toString()
        val member = Member(id = "m1", status = MemberStatus.ACTIVE, joinedAt = "${joinedDate}T00:00:00Z")
        val group = Group(id = "g1", type = GroupType.STOKVEL, monthlyContribution = Money("100.0"), paymentDueDay = 1)

        val overdueContribution = Contribution(
            memberId = "m1",
            groupId = "g1",
            amount = Money("100.0"),
            status = ContributionStatus.OVERDUE,
            dueDate = fixedNow.minusDays(10).toString()
        )

        every { contributionRepository.observeMemberContributions("m1", "g1") } returns flowOf(Result.success(listOf(overdueContribution)))

        val result = useCase(member, group)

        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        val reason = (result as ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible).reason
        assertTrue(reason.contains("Not in good standing"))
    }

    /**
     * `groups.loan_max_amount` is the per-loan ceiling an admin sets on the group. Zero is the schema
     * default and means "not configured", so it must not be read as "no lending allowed".
     */
    private fun groupWithCap(cap: Money?) = Group(
        id = "g1",
        type = GroupType.STOKVEL,
        monthlyContribution = Money.ZERO,
        loanMaxAmount = cap
    )

    private fun stubRecentContributions(total: String) {
        every { contributionRepository.observeMemberContributions("m1", "g1") } returns flowOf(
            Result.success(
                listOf(
                    Contribution(
                        memberId = "m1",
                        groupId = "g1",
                        amount = Money(total),
                        status = ContributionStatus.PAID,
                        paidAt = "${fixedNow}T00:00:00Z"
                    )
                )
            )
        )
    }

    private val activeMember = Member(id = "m1", status = MemberStatus.ACTIVE)

    @Test
    fun `invoke - a request over the group cap is refused although the member has the borrowing power`() = runTest {
        // R1000 in the last six months gives R300 of power, but the group caps one loan at R150.
        stubRecentContributions("1000.00")

        val result = useCase(activeMember, groupWithCap(Money("150.00")), requestedAmount = Money("200.00"))

        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        val reason = (result as ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible).reason
        assertTrue("the refusal must name the ceiling it hit: $reason", reason.contains("maximum loan amount"))
        assertTrue(reason.contains("150"))
    }

    @Test
    fun `invoke - a request at the group cap stays eligible`() = runTest {
        stubRecentContributions("1000.00")

        val result = useCase(activeMember, groupWithCap(Money("150.00")), requestedAmount = Money("150.00"))

        assertTrue(
            "the cap is inclusive, so exactly R150 is still a loan the group agreed to allow",
            result is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible
        )
    }

    @Test
    fun `invoke - a zero cap leaves the thirty percent rule as the only limit`() = runTest {
        stubRecentContributions("1000.00")

        val result = useCase(activeMember, groupWithCap(Money("0.00")), requestedAmount = Money("250.00"))

        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible)
    }

    @Test
    fun `invoke - an unset cap refuses only what the borrowing power refuses`() = runTest {
        stubRecentContributions("1000.00")

        val overPower = useCase(activeMember, groupWithCap(null), requestedAmount = Money("350.00"))
        val withinPower = useCase(activeMember, groupWithCap(null), requestedAmount = Money("250.00"))

        assertTrue(overPower is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        assertTrue(withinPower is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible)
    }

    @Test
    fun `invoke - unavailable contribution history cannot grant loan eligibility`() = runTest {
        every { contributionRepository.observeMemberContributions("m1", "g1") } returns
            flowOf(Result.failure(IllegalStateException("offline")))

        val result = useCase(activeMember, groupWithCap(null), requestedAmount = Money("100.00"))

        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        assertEquals(
            "Unable to verify contribution history. Please retry.",
            (result as ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun `invoke - unavailable loan history cannot grant loan eligibility`() = runTest {
        stubRecentContributions("1000.00")
        every { loanRepository.getMemberLoans("m1") } returns
            flowOf(Result.failure(IllegalStateException("offline")))

        val result = useCase(activeMember, groupWithCap(null), requestedAmount = Money("100.00"))

        assertTrue(result is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)
        assertEquals(
            "Unable to verify loan history. Please retry.",
            (result as ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible).reason
        )
    }
}
