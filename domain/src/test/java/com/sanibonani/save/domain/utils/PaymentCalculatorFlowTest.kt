package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.service.GroupFinancialPolicy
import com.sanibonani.save.domain.service.PlatformFeeService
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the multi-month payment flow: which months are expected, when a late fee applies,
 * how loans enter the expectation window and how the next due date is derived.
 */
class PaymentCalculatorFlowTest {

    private lateinit var calculator: PaymentCalculator
    private val feeService: PlatformFeeService = mockk()

    @Before
    fun setUp() {
        calculator = PaymentCalculator(feeService, GroupFinancialPolicy())
        every { feeService.calculateMonthlyMemberIndividual(any(), any()) } returns Money.ZERO
        every { feeService.getShortfallRecoveryFee() } returns Money.ZERO
    }

    private fun group(
        monthly: String = "500.0",
        lateFee: String = "25.0",
        graceDays: Int = 5,
        dueDay: Int = 15,
        type: GroupType = GroupType.STOKVEL
    ) = Group(
        id = "group-1",
        name = "Flow Group",
        type = type,
        monthlyContribution = Money(monthly),
        lateFee = Money(lateFee),
        lateFeeGraceDays = graceDays,
        paymentDueDay = dueDay
    )

    private fun member(joinedAt: String? = "2024-01-01T00:00:00Z", totalPaid: String = "0.0") = Member(
        id = "member-1",
        groupId = "group-1",
        joinedAt = joinedAt,
        totalPaid = Money(totalPaid)
    )

    private fun paid(
        amount: String,
        type: String = "contribution",
        status: ContributionStatus = ContributionStatus.PAID,
        dueDate: String = "2024-01-15"
    ) = Contribution(
        memberId = "member-1",
        groupId = "group-1",
        amount = Money(amount),
        status = status,
        type = type,
        dueDate = dueDate
    )

    private fun loan(
        monthlyRepayment: String = "250.0",
        totalRepaid: String = "0.0",
        startDate: String? = "2024-01-01",
        endDate: String? = null,
        status: LoanStatus = LoanStatus.ACTIVE
    ) = Loan(
        id = "loan-1",
        memberId = "member-1",
        groupId = "group-1",
        totalToRepay = Money("3000.0"),
        totalRepaid = Money(totalRepaid),
        monthlyRepayment = Money(monthlyRepayment),
        startDate = startDate,
        endDate = endDate,
        status = status
    )

    // ── Late fee boundary and accrual ────────────────────────────────────────

    @Test
    fun `late fee starts the day after the grace period`() {
        val onGraceEnd = calculator.calculateStatus(
            group(), member(), emptyList(), emptyList(), LocalDate.of(2024, 1, 20)
        )

        assertFalse("grace end day is still within grace", onGraceEnd.isOverdue)
        assertEquals(Money.ZERO, onGraceEnd.breakdown.lateFees)
        assertEquals(Money("500.0"), onGraceEnd.totalDueNow)
        assertEquals(Money.ZERO, onGraceEnd.breakdown.recoveryFee)

        val afterGrace = calculator.calculateStatus(
            group(), member(), emptyList(), emptyList(), LocalDate.of(2024, 1, 21)
        )

        assertTrue(afterGrace.isOverdue)
        assertEquals(Money("25.0"), afterGrace.breakdown.lateFees)
        assertEquals(Money("500.0"), afterGrace.shortfall)
        assertEquals(Money("525.0"), afterGrace.totalOverdue)
    }

    @Test
    fun `one late fee accrues for every overdue month and the recovery fee follows`() {
        every { feeService.getShortfallRecoveryFee() } returns Money("20.0")

        val status = calculator.calculateStatus(
            group(), member(), emptyList(), emptyList(), LocalDate.of(2024, 3, 21)
        )

        assertEquals(Money("1500.0"), status.shortfall)
        assertEquals(Money("75.0"), status.breakdown.lateFees)
        assertEquals(Money("20.0"), status.breakdown.recoveryFee)
        assertEquals(Money("1595.0"), status.totalDueNow)
        assertEquals(status.totalDueNow, status.totalOverdue)
        assertEquals(0.0, status.paymentRate, 0.001)
    }

    @Test
    fun `no late fee accrues once payments cover the elapsed expectations`() {
        val status = calculator.calculateStatus(
            group(),
            member(),
            listOf(paid("1500.0")),
            emptyList(),
            LocalDate.of(2024, 3, 20)
        )

        assertFalse(status.isOverdue)
        assertEquals(Money.ZERO, status.shortfall)
        assertEquals(Money.ZERO, status.overpayment)
        assertEquals(Money.ZERO, status.breakdown.lateFees)
        assertEquals(Money.ZERO, status.totalDueNow)
        assertEquals(100.0, status.paymentRate, 0.001)
    }

    // ── Platform fee share ──────────────────────────────────────────────────

    @Test
    fun `platform fee joins the amount due and stays separate from savings`() {
        every { feeService.calculateMonthlyMemberIndividual(any(), any()) } returns Money("10.0")

        val status = calculator.calculateStatus(
            group(dueDay = 28), member(), emptyList(), emptyList(), LocalDate.of(2024, 1, 28)
        )

        assertEquals(Money("500.0"), status.shortfall)
        assertEquals(Money("500.0"), status.nextContributionAmount)
        assertEquals(Money("10.0"), status.breakdown.platformFees)
        assertEquals(Money("510.0"), status.totalDueNow)

        val withFeePaid = calculator.calculateStatus(
            group(dueDay = 28),
            member(),
            listOf(paid("10.0", type = "member_fee")),
            emptyList(),
            LocalDate.of(2024, 1, 28)
        )

        assertEquals(Money.ZERO, withFeePaid.breakdown.platformFees)
        assertEquals(Money("500.0"), withFeePaid.totalDueNow)
    }

    @Test
    fun `expected monthly outflow adds the platform share to the group share`() {
        every { feeService.calculateMonthlyMemberIndividual(any(), any()) } returns Money("10.0")

        val outflow = calculator.calculateExpectedMonthlyOutflow(group(), member())

        assertEquals(Money("510.0"), outflow)
    }

    // ── Paid-amount classification ──────────────────────────────────────────

    @Test
    fun `loan settlement extraction reduces paid savings`() {
        val status = calculator.calculateStatus(
            group(),
            member(),
            listOf(
                paid("600.0"),
                paid("-100.0", type = "loan_settlement_extraction")
            ),
            emptyList(),
            LocalDate.of(2024, 1, 28)
        )

        assertEquals(Money.ZERO, status.shortfall)
        assertEquals(Money.ZERO, status.overpayment)
    }

    @Test
    fun `unpaid contributions do not count while member total paid is the fallback`() {
        val due = calculator.calculateStatus(
            group(), member(), listOf(paid("500.0", status = ContributionStatus.DUE)), emptyList(), LocalDate.of(2024, 1, 28)
        )
        assertEquals(Money("500.0"), due.shortfall)

        val fallback = calculator.calculateStatus(
            group(),
            member(totalPaid = "500.0"),
            listOf(paid("200.0", type = "joining_fee")),
            emptyList(),
            LocalDate.of(2024, 1, 28)
        )
        assertEquals(Money.ZERO, fallback.shortfall)
    }

    @Test
    fun `payment rate reflects paid savings against expectations`() {
        val status = calculator.calculateStatus(
            group(),
            member(),
            listOf(paid("250.0", status = ContributionStatus.PARTIAL)),
            emptyList(),
            LocalDate.of(2024, 1, 28)
        )

        assertEquals(Money("250.0"), status.shortfall)
        assertEquals(50.0, status.paymentRate, 0.001)
    }

    // ── Overpayment, periods ahead, next due date ──────────────────────────

    @Test
    fun `overpayment advances the next due date and periods ahead`() {
        val status = calculator.calculateStatus(
            group(),
            member(),
            listOf(paid("1750.0")),
            emptyList(),
            LocalDate.of(2024, 1, 28)
        )

        assertEquals(Money.ZERO, status.shortfall)
        assertEquals(Money("1250.0"), status.overpayment)
        assertEquals(2, status.periodsAhead)
        assertEquals("2024-04-15", status.nextDueDate)
    }

    @Test
    fun `periods ahead include the monthly repayment of servicing loans`() {
        val status = calculator.calculateStatus(
            group(),
            member(),
            listOf(paid("1750.0")),
            listOf(loan(monthlyRepayment = "250.0")),
            LocalDate.of(2024, 1, 28)
        )

        assertEquals(Money("250.0"), status.breakdown.loans)
        assertEquals(1, status.periodsAhead)
    }

    @Test
    fun `next due date clamps the due day in a short month`() {
        val leap = calculator.calculateStatus(
            group(monthly = "100.0", dueDay = 31),
            member(joinedAt = "2024-02-01T00:00:00Z"),
            emptyList(),
            emptyList(),
            LocalDate.of(2024, 2, 29)
        )

        assertEquals(Money("100.0"), leap.shortfall)
        assertEquals("2024-02-29", leap.nextDueDate)

        val nonLeap = calculator.calculateStatus(
            group(monthly = "100.0", dueDay = 31),
            member(joinedAt = "2023-02-01T00:00:00Z"),
            emptyList(),
            emptyList(),
            LocalDate.of(2023, 2, 28)
        )

        assertEquals(Money("100.0"), nonLeap.shortfall)
        assertEquals("2023-02-28", nonLeap.nextDueDate)
    }

    @Test
    fun `next due date keeps counting months past the old two year horizon`() {
        // 100 000 paid against 100/month covers 1000 months, i.e. 2107-05.
        // A horizon tied to the real clock stops after two years and reports "N/A".
        val prepaid = calculator.calculateStatus(
            group(monthly = "100.0"),
            member(),
            listOf(paid("100000.0")),
            emptyList(),
            LocalDate.of(2024, 1, 28)
        )

        assertEquals(Money.ZERO, prepaid.shortfall)
        assertEquals(Money("99900.0"), prepaid.overpayment)
        assertEquals("2107-05-15", prepaid.nextDueDate)
    }

    // ── Joined date resolution ─────────────────────────────────────────────

    @Test
    fun `unparseable join date falls back to the earliest contribution due date`() {
        val status = calculator.calculateStatus(
            group(),
            member(joinedAt = "not-a-date"),
            listOf(paid("0.0", status = ContributionStatus.DUE, dueDate = "2023-11-15")),
            emptyList(),
            LocalDate.of(2024, 1, 28)
        )

        assertEquals(Money("1500.0"), status.shortfall)
        assertEquals("2023-11-15", status.nextDueDate)
    }

    @Test
    fun `a join date after the calculation date anchors expectations there`() {
        val status = calculator.calculateStatus(
            group(),
            member(joinedAt = "2024-06-10T00:00:00Z"),
            emptyList(),
            emptyList(),
            LocalDate.of(2024, 1, 28)
        )

        assertEquals(Money.ZERO, status.shortfall)
        assertEquals(Money.ZERO, status.totalDueNow)
        assertEquals("2024-06-15", status.nextDueDate)
    }

    // ── Loan expectation window ────────────────────────────────────────────

    @Test
    fun `loan expectations start in the loan start month and stop at the end date`() {
        val group = group(monthly = "0.0")

        val status = calculator.calculateStatus(
            group,
            member(),
            emptyList(),
            listOf(loan(monthlyRepayment = "100.0", startDate = "2024-02-10", endDate = "2024-04-10")),
            LocalDate.of(2024, 5, 20)
        )

        assertEquals(Money("300.0"), status.breakdown.loans)
        assertEquals(Money("300.0"), status.totalDueNow)
        assertEquals(Money.ZERO, status.shortfall)
        assertEquals("2024-01-15", status.nextDueDate)
        assertEquals(100.0, status.paymentRate, 0.001)
    }

    @Test
    fun `loan expectations default to a twelve month term when no end date is set`() {
        val status = calculator.calculateStatus(
            group(monthly = "0.0"),
            member(),
            emptyList(),
            listOf(loan(monthlyRepayment = "100.0", startDate = "2024-02-10", endDate = null)),
            LocalDate.of(2025, 6, 20)
        )

        assertEquals(Money("1300.0"), status.breakdown.loans)
    }

    @Test
    fun `loan repayments already made reduce the loan shortfall`() {
        val status = calculator.calculateStatus(
            group(monthly = "0.0"),
            member(),
            emptyList(),
            listOf(loan(monthlyRepayment = "100.0", totalRepaid = "50.0", startDate = "2024-02-10", endDate = "2024-04-10")),
            LocalDate.of(2024, 5, 20)
        )

        assertEquals(Money("250.0"), status.breakdown.loans)
    }

    @Test
    fun `repayments on a completed loan do not cover another loan shortfall`() {
        val completedLoan = loan(
            monthlyRepayment = "100.0",
            totalRepaid = "1200.0",
            startDate = "2023-01-01",
            endDate = "2023-12-01",
            status = LoanStatus.COMPLETED
        )
        val activeLoan = loan(
            monthlyRepayment = "100.0",
            totalRepaid = "0.0",
            startDate = "2024-01-01",
            endDate = "2024-03-01"
        )

        val status = calculator.calculateStatus(
            group(monthly = "0.0"),
            member(),
            emptyList(),
            listOf(completedLoan, activeLoan),
            LocalDate.of(2024, 3, 20)
        )

        assertEquals(Money("300.0"), status.breakdown.loans)
    }

    @Test
    fun `preview savings exclude platform fees and use the member join date`() {
        val member = member(joinedAt = "2024-01-01T00:00:00Z")
        val paid = listOf(
            paid("1000.0", dueDate = "2024-01-15"),
            paid("50.0", type = "member_fee", dueDate = "2024-01-15")
        )

        assertEquals(Money("1000.0"), calculator.calculatePaidSavings(paid, member))
        assertEquals(
            LocalDate.of(2024, 1, 1),
            calculator.resolveJoinedDate(member, LocalDate.of(2024, 6, 1), paid)
        )
    }

    @Test
    fun `a loan starting after the calculation date expects nothing yet`() {
        val status = calculator.calculateStatus(
            group(monthly = "0.0"),
            member(),
            emptyList(),
            listOf(loan(startDate = "2024-07-01")),
            LocalDate.of(2024, 5, 20)
        )

        assertEquals(Money.ZERO, status.breakdown.loans)
        assertEquals(Money.ZERO, status.totalDueNow)
    }

    @Test
    fun `settled loans no longer expect repayments`() {
        val status = calculator.calculateStatus(
            group(monthly = "0.0"),
            member(),
            emptyList(),
            listOf(loan(startDate = "2024-01-01", status = LoanStatus.COMPLETED)),
            LocalDate.of(2024, 5, 20)
        )

        assertEquals(Money.ZERO, status.breakdown.loans)
        assertEquals(Money.ZERO, status.totalDueNow)
    }

    // ── Realtime preview ───────────────────────────────────────────────────

    @Test
    fun `realtime preview applies the late fee before clearing the shortfall`() {
        val preview = calculator.calculateRealtime(
            inputAmount = Money("1000.0"),
            currentShortfall = Money("500.0"),
            currentOverpayment = Money.ZERO,
            lateFee = Money("25.0"),
            monthlyContribution = Money("500.0"),
            group = group(),
            joinedDate = LocalDate.of(2024, 1, 1),
            totalPaidBefore = Money.ZERO
        )

        assertEquals(Money.ZERO, preview.shortfall)
        assertEquals(Money("475.0"), preview.overpayment)
        assertEquals("2024-02-15", preview.nextDueDate)
    }

    @Test
    fun `realtime input smaller than the late fee leaves the shortfall untouched`() {
        val preview = calculator.calculateRealtime(
            inputAmount = Money("10.0"),
            currentShortfall = Money("500.0"),
            currentOverpayment = Money.ZERO,
            lateFee = Money("25.0"),
            monthlyContribution = Money("500.0"),
            group = group(),
            joinedDate = LocalDate.of(2024, 1, 1),
            totalPaidBefore = Money.ZERO
        )

        assertEquals(Money("500.0"), preview.shortfall)
        assertEquals(Money.ZERO, preview.overpayment)
        assertEquals("2024-01-15", preview.nextDueDate)
    }

    @Test
    fun `realtime with no input echoes the current position`() {
        val preview = calculator.calculateRealtime(
            inputAmount = Money.ZERO,
            currentShortfall = Money("250.0"),
            currentOverpayment = Money("100.0"),
            lateFee = Money("25.0"),
            monthlyContribution = Money("500.0"),
            group = group(),
            joinedDate = LocalDate.of(2024, 1, 1),
            totalPaidBefore = Money("1000.0")
        )

        assertEquals(Money("250.0"), preview.shortfall)
        assertEquals(Money("100.0"), preview.overpayment)
        assertEquals("2024-03-15", preview.nextDueDate)
    }
}
