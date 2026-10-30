package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.model.AmortizationType
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the loan-application rules that used to live inside `MemberFinanceViewModel`:
 * eligibility gates the request, which interest rate wins, how the schedule prices the
 * loan, and what a failed settings read means for the request.
 */
class RequestLoanUseCaseTest {

    private val loanRepository: LoanRepository = mockk()
    private val platformRepository: PlatformRepository = mockk()
    private val validateEligibility: ValidateLoanEligibilityUseCase = mockk()
    private val calculateSchedule = CalculateLoanRepaymentScheduleUseCase()
    private val useCase =
        RequestLoanUseCase(loanRepository, platformRepository, calculateSchedule, validateEligibility)

    @Before
    fun stubEligibility() {
        coEvery { validateEligibility(any(), any(), any(), any()) } returns
            ValidateLoanEligibilityUseCase.EligibilityResult.Eligible()
    }

    private val startDate = LocalDate.of(2026, 10, 25)
    private val config = PlatformConfig(
        loanInterestRate = Money("20.00"),
        loanProcessingFee = Money("150.00")
    )

    private fun group(rate: Double? = null) = Group(
        id = "group-1",
        name = "Loan Group",
        type = GroupType.STOKVEL,
        loanInterestRate = rate,
        loanMaxAmount = Money("50000.00"),
        loanMaxMonths = 24
    )

    private fun member() = Member(id = "member-1", groupId = "group-1")

    private fun params(groupRate: Double? = null, termMonths: Int = 6) = RequestLoanUseCase.Params(
        group = group(groupRate),
        member = member(),
        amount = Money("10000.00"),
        purpose = "Stock for the shop",
        termMonths = termMonths,
        startDate = startDate
    )

    private fun stubSettings(value: PlatformConfig = config) {
        coEvery { platformRepository.getPlatformSettings() } returns Result.success(value)
    }

    private fun stubSave() {
        coEvery { loanRepository.requestLoan(any()) } returns Result.success("loan-1")
    }

    @Test
    fun `a group rate overrides the platform rate`() = runBlocking {
        stubSettings()
        stubSave()

        val loan = useCase(params(groupRate = 12.0)).getOrThrow()

        assertEquals(12.0, loan.interestRate, 0.0)
    }

    @Test
    fun `a missing group rate falls back to the platform rate`() = runBlocking {
        stubSettings()
        stubSave()

        val loan = useCase(params(groupRate = null)).getOrThrow()

        assertEquals(20.0, loan.interestRate, 0.0)
    }

    @Test
    fun `a zero group rate falls back to the platform rate`() = runBlocking {
        stubSettings()
        stubSave()

        val loan = useCase(params(groupRate = 0.0)).getOrThrow()

        assertEquals(20.0, loan.interestRate, 0.0)
    }

    @Test
    fun `the loan is a pending flat request priced from the schedule`() = runBlocking {
        stubSettings()
        stubSave()

        val loan = useCase(params(groupRate = 12.0, termMonths = 6)).getOrThrow()
        val expected = calculateSchedule(
            CalculateLoanRepaymentScheduleUseCase.Params(
                principal = Money("10000.00"),
                annualInterestRate = 12.0,
                termMonths = 6,
                processingFee = Money("150.00"),
                startDate = startDate
            )
        )

        assertEquals(LoanStatus.PENDING, loan.status)
        assertEquals(AmortizationType.FLAT, loan.amortizationType)
        assertEquals(expected.totalToRepay, loan.totalToRepay)
        assertEquals(expected.monthlyInstallment, loan.monthlyRepayment)
        assertEquals("group-1", loan.groupId)
        assertEquals("member-1", loan.memberId)
        assertEquals(Money("10000.00"), loan.amount)
        assertEquals("Stock for the shop", loan.purpose)
    }

    @Test
    fun `the term is written as start and end dates`() = runBlocking {
        stubSettings()
        stubSave()

        val loan = useCase(params(termMonths = 9)).getOrThrow()

        assertEquals("2026-10-25", loan.startDate)
        assertEquals("2027-07-25", loan.endDate)
    }

    @Test
    fun `a settings read failure stops the request before anything is saved`() = runBlocking {
        coEvery { platformRepository.getPlatformSettings() } returns Result.failure(IllegalStateException("offline"))

        val result = useCase(params())

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { loanRepository.requestLoan(any()) }
    }

    @Test
    fun `a save failure is passed back to the caller`() = runBlocking {
        stubSettings()
        coEvery { loanRepository.requestLoan(any()) } returns Result.failure(IllegalStateException("queue full"))

        val result = useCase(params())

        assertTrue(result.isFailure)
        assertEquals("queue full", result.exceptionOrNull()?.message)
    }

    @Test
    fun `the request is validated with its own amount and term`() = runBlocking {
        stubSettings()
        stubSave()

        useCase(params(termMonths = 6)).getOrThrow()

        coVerify {
            validateEligibility(
                match { it.id == "member-1" },
                match { it.id == "group-1" },
                match { it == Money("10000.00") },
                match { it == 6 }
            )
        }
    }

    @Test
    fun `an ineligible request fails before pricing or saving`() = runBlocking {
        val reason = "Requested amount (R10 000.00) exceeds the group's maximum loan amount (R5 000.00)."
        coEvery { validateEligibility(any(), any(), any(), any()) } returns
            ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible(reason)

        val result = useCase(params(groupRate = 12.0))

        assertTrue(result.isFailure)
        assertEquals(reason, result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { platformRepository.getPlatformSettings() }
        coVerify(exactly = 0) { loanRepository.requestLoan(any()) }
    }
}
