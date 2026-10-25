package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.utils.DueBreakdown
import com.sanibonani.save.domain.utils.PaymentCalculation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaymentAllocationServiceTest {

    private val service = PaymentAllocationService()

    private fun calculation(
        recovery: String = "0.0",
        platform: String = "0.0",
        late: String = "0.0",
        contributions: String = "0.0",
        loans: String = "0.0",
        nextDueDate: String = "2024-06-15"
    ): PaymentCalculation {
        val breakdown = DueBreakdown(
            contributions = Money(contributions),
            loans = Money(loans),
            lateFees = Money(late),
            platformFees = Money(platform),
            recoveryFee = Money(recovery)
        )
        return PaymentCalculation(Money.ZERO, Money.ZERO, nextDueDate, breakdown.total, breakdown = breakdown)
    }

    private fun loan(
        id: String,
        outstanding: String,
        repaid: String = "0.0",
        createdAt: String? = "2024-01-01"
    ) = Loan(
        id = id,
        totalToRepay = Money(outstanding),
        totalRepaid = Money(repaid),
        createdAt = createdAt
    )

    @Test
    fun `waterfall pays recovery then platform then late fees then loans then contribution`() {
        val allocations = service.calculateAllocations(
            amount = Money("1000.0"),
            calculation = calculation(recovery = "20.0", platform = "10.0", late = "25.0", loans = "200.0"),
            activeLoans = listOf(loan("loan-1", "200.0"))
        )

        assertEquals(
            listOf("member_fee", "member_fee", "late_fee", "loan_repayment", "contribution"),
            allocations.map { it.type }
        )
        assertEquals(
            listOf("Shortfall Recovery Fee", "Monthly Platform Fee", "Late Fee Payment", "Loan Repayment: loan-", "Monthly Contribution"),
            allocations.map { it.description }
        )
        assertEquals(
            listOf(Money("20.0"), Money("10.0"), Money("25.0"), Money("200.0"), Money("745.0")),
            allocations.map { it.amount }
        )
        assertEquals(listOf(null, null, null, "loan-1", null), allocations.map { it.loanId })
        assertTrue(allocations.all { it.dueDate == "2024-06-15" })
    }

    @Test
    fun `a partial payment settles buckets in order`() {
        val calc = calculation(recovery = "20.0", platform = "10.0", late = "25.0", loans = "200.0")

        val fifteen = service.calculateAllocations(Money("15.0"), calc, listOf(loan("loan-1", "200.0")))
        assertEquals(listOf("member_fee"), fifteen.map { it.type })
        assertEquals(Money("15.0"), fifteen.single().amount)

        val fifty = service.calculateAllocations(Money("50.0"), calc, listOf(loan("loan-1", "200.0")))
        assertEquals(listOf("member_fee", "member_fee", "late_fee"), fifty.map { it.type })
        assertEquals(listOf(Money("20.0"), Money("10.0"), Money("20.0")), fifty.map { it.amount })
    }

    @Test
    fun `loans are repaid oldest first by creation date`() {
        val allocations = service.calculateAllocations(
            amount = Money("150.0"),
            calculation = calculation(loans = "200.0"),
            activeLoans = listOf(
                loan("newer", "100.0", createdAt = "2024-02-01"),
                loan("older", "100.0", createdAt = "2024-01-01")
            )
        )

        assertEquals(listOf("older", "newer"), allocations.map { it.loanId })
        assertEquals(listOf(Money("100.0"), Money("50.0")), allocations.map { it.amount })
    }

    @Test
    fun `loans without a creation date are repaid first`() {
        val allocations = service.calculateAllocations(
            amount = Money("150.0"),
            calculation = calculation(loans = "200.0"),
            activeLoans = listOf(
                loan("dated", "100.0", createdAt = "2024-01-01"),
                loan("undated", "100.0", createdAt = null)
            )
        )

        assertEquals(listOf("undated", "dated"), allocations.map { it.loanId })
    }

    @Test
    fun `loan allocation is capped at the outstanding balance`() {
        val allocations = service.calculateAllocations(
            amount = Money("100.0"),
            calculation = calculation(loans = "50.0"),
            activeLoans = listOf(loan("loan-1", "300.0", repaid = "250.0"))
        )

        assertEquals(listOf("loan_repayment", "contribution"), allocations.map { it.type })
        assertEquals(listOf(Money("50.0"), Money("50.0")), allocations.map { it.amount })
    }

    @Test
    fun `a settled loan receives nothing and the remainder goes to the contribution`() {
        val allocations = service.calculateAllocations(
            amount = Money("100.0"),
            calculation = calculation(loans = "100.0"),
            activeLoans = listOf(loan("loan-1", "300.0", repaid = "300.0"))
        )

        assertEquals(listOf("contribution"), allocations.map { it.type })
        assertEquals(Money("100.0"), allocations.single().amount)
    }

    @Test
    fun `payments with nothing outstanding become a contribution`() {
        val allocations = service.calculateAllocations(Money("100.0"), calculation())

        assertEquals(listOf("contribution"), allocations.map { it.type })
        assertEquals(Money("100.0"), allocations.single().amount)
    }

    @Test
    fun `zero or negative amounts allocate nothing`() {
        assertTrue(service.calculateAllocations(Money.ZERO, calculation(recovery = "20.0")).isEmpty())
        assertTrue(service.calculateAllocations(Money("-50.0"), calculation(recovery = "20.0")).isEmpty())
    }

    @Test
    fun `a missing due date falls back to today`() {
        val today = LocalDate.now().toString()

        val notAvailable = service.calculateAllocations(Money("100.0"), calculation(nextDueDate = "N/A"))
        val blank = service.calculateAllocations(Money("100.0"), calculation(nextDueDate = " "))

        assertEquals(today, notAvailable.single().dueDate)
        assertEquals(today, blank.single().dueDate)
    }
}
