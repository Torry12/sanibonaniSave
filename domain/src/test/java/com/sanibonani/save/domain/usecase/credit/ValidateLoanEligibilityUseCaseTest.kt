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
}
