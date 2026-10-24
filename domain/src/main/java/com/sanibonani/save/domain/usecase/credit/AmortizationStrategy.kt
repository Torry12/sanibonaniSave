package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.model.LoanRepaymentScheduleEntry
import com.sanibonani.save.domain.model.Money
import java.time.LocalDate
import kotlin.math.pow

/**
 * Strategy interface for calculating loan amortization schedules.
 */
interface AmortizationStrategy {
    fun calculate(
        principal: Money,
        annualInterestRate: Double,
        termMonths: Int,
        startDate: LocalDate
    ): AmortizationResult
}

data class AmortizationResult(
    val totalToRepay: Money,
    val totalInterest: Money,
    val monthlyInstallment: Money,
    val schedule: List<LoanRepaymentScheduleEntry>
)

class FlatAmortizationStrategy : AmortizationStrategy {
    override fun calculate(
        principal: Money,
        annualInterestRate: Double,
        termMonths: Int,
        startDate: LocalDate
    ): AmortizationResult {
        val rate = annualInterestRate / 100.0
        val time = termMonths / 12.0
        val totalInterest = principal * (rate * time)
        val totalToRepay = principal + totalInterest
        val principalPerMonth = principal / termMonths
        val interestPerMonth = totalInterest / termMonths

        val schedule = (1..termMonths).map { i ->
            val isLast = i == termMonths
            // Dividing the loan by the term drops a cent, so the last row carries the remainder and
            // the rows add back up to exactly what was borrowed.
            val principalRow = if (isLast) principal - principalPerMonth * (termMonths - 1) else principalPerMonth
            val interestRow = if (isLast) totalInterest - interestPerMonth * (termMonths - 1) else interestPerMonth
            LoanRepaymentScheduleEntry(
                installmentNumber = i,
                dueDate = startDate.plusMonths(i.toLong()).toString(),
                principal = principalRow,
                interest = interestRow,
                totalInstallment = principalRow + interestRow,
                remainingBalance = if (isLast) Money.ZERO
                else (principal - principalPerMonth * i).max(Money.ZERO)
            )
        }

        return AmortizationResult(totalToRepay, totalInterest, principalPerMonth + interestPerMonth, schedule)
    }
}

class ReducingBalanceAmortizationStrategy : AmortizationStrategy {
    override fun calculate(
        principal: Money,
        annualInterestRate: Double,
        termMonths: Int,
        startDate: LocalDate
    ): AmortizationResult {
        val monthlyRate = (annualInterestRate / 100.0) / 12.0
        val numerator = monthlyRate * (1 + monthlyRate).pow(termMonths)
        val denominator = (1 + monthlyRate).pow(termMonths) - 1
        val monthlyInstallment = principal * (numerator / denominator)

        val schedule = mutableListOf<LoanRepaymentScheduleEntry>()
        var remainingBalance = principal
        var totalInterestAccumulated = Money.ZERO

        for (i in 1..termMonths) {
            val interestForMonth = remainingBalance * monthlyRate
            val isLast = i == termMonths
            // The fixed instalment is rounded, so it cannot land the balance on zero. The last row
            // repays whatever is actually outstanding instead of leaving a phantom balance behind.
            val principalForMonth = if (isLast) remainingBalance else monthlyInstallment - interestForMonth
            val totalForMonth = principalForMonth + interestForMonth
            remainingBalance -= principalForMonth
            totalInterestAccumulated += interestForMonth

            schedule.add(
                LoanRepaymentScheduleEntry(
                    installmentNumber = i,
                    dueDate = startDate.plusMonths(i.toLong()).toString(),
                    principal = principalForMonth,
                    interest = interestForMonth,
                    totalInstallment = totalForMonth,
                    remainingBalance = if (isLast) Money.ZERO else remainingBalance.max(Money.ZERO)
                )
            )
        }

        return AmortizationResult(principal + totalInterestAccumulated, totalInterestAccumulated, monthlyInstallment, schedule)
    }
}
