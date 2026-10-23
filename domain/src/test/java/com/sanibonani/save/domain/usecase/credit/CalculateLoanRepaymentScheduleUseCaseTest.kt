package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.model.AmortizationType
import com.sanibonani.save.domain.model.Money
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repayment plan is the contract a member is billed against, so every row and the totals must
 * add up to exactly what the loan is owed - including when a processing fee is charged and when a
 * rounded instalment cannot clear the balance on its own.
 */
class CalculateLoanRepaymentScheduleUseCaseTest {

    private val useCase = CalculateLoanRepaymentScheduleUseCase()
    private val startDate = LocalDate.of(2026, 1, 31)

    private fun sum(rows: List<com.sanibonani.save.domain.model.LoanRepaymentScheduleEntry>, selector: (com.sanibonani.save.domain.model.LoanRepaymentScheduleEntry) -> Money): Money =
        rows.fold(Money.ZERO) { acc, row -> acc + selector(row) }

    @Test
    fun `flat schedule without fee is internally consistent`() {
        val result = useCase(
            CalculateLoanRepaymentScheduleUseCase.Params(
                principal = Money("1000.00"),
                annualInterestRate = 12.0,
                termMonths = 12,
                startDate = startDate
            )
        )

        assertEquals(Money("1120.00"), result.totalToRepay)
        assertEquals(Money("120.00"), result.totalInterest)
        assertEquals(Money("93.33"), result.monthlyInstallment)
        assertEquals(12, result.schedule.size)
        assertEquals(Money.ZERO, result.schedule.last().remainingBalance)

        result.schedule.forEach { row ->
            assertEquals(row.principal + row.interest, row.totalInstallment)
            assertEquals(startDate.plusMonths(row.installmentNumber.toLong()).toString(), row.dueDate)
        }

        assertEquals(Money("1000.00"), sum(result.schedule) { it.principal })
        assertEquals(Money("120.00"), sum(result.schedule) { it.interest })
        assertEquals(result.totalToRepay, sum(result.schedule) { it.totalInstallment })
    }

    @Test
    fun `flat schedule with processing fee bills the fee through the instalments`() {
        val result = useCase(
            CalculateLoanRepaymentScheduleUseCase.Params(
                principal = Money("1000.00"),
                annualInterestRate = 12.0,
                termMonths = 12,
                processingFee = Money("50.00"),
                startDate = startDate
            )
        )

        assertEquals(Money("1170.00"), result.totalToRepay)
        assertEquals(Money("170.00"), result.totalInterest)
        assertEquals(Money("97.50"), result.monthlyInstallment)
        assertEquals(Money("50.00"), result.processingFee)
        assertEquals(Money("1000.00"), sum(result.schedule) { it.principal })
        assertEquals(result.totalToRepay, sum(result.schedule) { it.totalInstallment })

        val feeBilled = sum(result.schedule) { it.totalInstallment } -
            sum(result.schedule) { it.principal + it.interest }
        assertEquals(Money("50.00"), feeBilled)
    }

    @Test
    fun `reducing balance schedule clears the balance and repays the principal in full`() {
        val result = useCase(
            CalculateLoanRepaymentScheduleUseCase.Params(
                principal = Money("1000.00"),
                annualInterestRate = 12.0,
                termMonths = 12,
                amortizationType = AmortizationType.REDUCING,
                startDate = startDate
            )
        )

        assertEquals(12, result.schedule.size)
        assertEquals(Money.ZERO, result.schedule.last().remainingBalance)
        assertEquals(Money("1000.00"), sum(result.schedule) { it.principal })
        assertEquals(result.totalToRepay, sum(result.schedule) { it.totalInstallment })
        result.schedule.forEach { row ->
            assertEquals(row.principal + row.interest, row.totalInstallment)
        }
    }

    @Test
    fun `reducing balance schedule absorbs the fee in the advertised total`() {
        val result = useCase(
            CalculateLoanRepaymentScheduleUseCase.Params(
                principal = Money("1000.00"),
                annualInterestRate = 12.0,
                termMonths = 12,
                processingFee = Money("50.00"),
                amortizationType = AmortizationType.REDUCING,
                startDate = startDate
            )
        )

        assertEquals(result.totalToRepay, sum(result.schedule) { it.totalInstallment })
        assertEquals(Money("1000.00"), sum(result.schedule) { it.principal })
        assertTrue(result.totalToRepay > Money("1000.00"))
        assertEquals(BigDecimal("0.00"), result.schedule.last().remainingBalance.amount)
    }
}
