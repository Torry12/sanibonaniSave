package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.model.AmortizationType
import com.sanibonani.save.domain.model.LoanRepaymentScheduleEntry
import com.sanibonani.save.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The repayment schedule is the instalment plan a member is billed against, so the rows have to add
 * up to the amount actually owed. Dividing an amount by the term throws away the remainder, and a
 * schedule that silently loses or invents a cent is a ledger that never reconciles.
 */
class LoanScheduleReconciliationTest {

    private val useCase = CalculateLoanRepaymentScheduleUseCase()
    private val start = LocalDate.of(2026, 1, 31)

    private fun List<LoanRepaymentScheduleEntry>.sumOf(
        selector: (LoanRepaymentScheduleEntry) -> Money
    ): Money = fold(Money.ZERO) { acc, entry -> acc + selector(entry) }

    @Test
    fun `a flat schedule collects exactly the amount owed`() {
        val result = useCase(
            CalculateLoanRepaymentScheduleUseCase.Params(
                principal = Money("1000.00"),
                annualInterestRate = 20.0,
                termMonths = 3,
                processingFee = Money("7.00"),
                startDate = start
            )
        )

        // R1 057.00 over three months divides to R352.33, which would only collect R1 056.99.
        assertEquals(Money("1057.00"), result.totalToRepay)
        assertEquals(Money("1057.00"), result.schedule.sumOf { it.totalInstallment })
        assertEquals(Money("352.34"), result.schedule.last().totalInstallment)
    }

    @Test
    fun `a flat schedule splits the borrowed amount without losing cents`() {
        val result = useCase(
            CalculateLoanRepaymentScheduleUseCase.Params(
                principal = Money("1000.00"),
                annualInterestRate = 20.0,
                termMonths = 3,
                processingFee = Money("7.00"),
                startDate = start
            )
        )

        // R1 000.00 / 3 = R333.33 per row, which is a cent short of the loan it is repaying.
        assertEquals(Money("1000.00"), result.schedule.sumOf { it.principal })
        assertEquals(Money("50.00"), result.schedule.sumOf { it.interest })
        assertEquals(Money("0.00"), result.schedule.last().remainingBalance)
    }

    @Test
    fun `a reducing balance schedule settles to zero and pays exactly the total`() {
        val result = useCase(
            CalculateLoanRepaymentScheduleUseCase.Params(
                principal = Money("1000.00"),
                annualInterestRate = 20.0,
                termMonths = 3,
                processingFee = Money.ZERO,
                amortizationType = AmortizationType.REDUCING,
                startDate = start
            )
        )

        assertEquals(result.totalToRepay, result.schedule.sumOf { it.totalInstallment })
        assertEquals(Money("1000.00"), result.schedule.sumOf { it.principal })
        assertEquals(result.totalInterest, result.schedule.sumOf { it.interest })
        assertEquals(Money("0.00"), result.schedule.last().remainingBalance)
    }
}
