package com.sanibonani.save.domain.usecase.financial

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
        val totalInterest = principal * (annualInterestRate / 100.0) * (termMonths / 12.0)
        val totalToRepay = principal + totalInterest
        val monthlyInstallment = totalToRepay / termMonths

        val schedule = (1..termMonths).map { i ->
            val principalPerMonth = principal / termMonths
            val interestPerMonth = totalInterest / termMonths
            LoanRepaymentScheduleEntry(
                installmentNumber = i,
                dueDate = startDate.plusMonths(i.toLong()).toString(),
                principal = principalPerMonth,
                interest = interestPerMonth,
                totalInstallment = monthlyInstallment,
                remainingBalance = (principal - (principalPerMonth * i)).max(Money.ZERO)
            )
        }

        return AmortizationResult(totalToRepay, totalInterest, monthlyInstallment, schedule)
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
            val principalForMonth = monthlyInstallment - interestForMonth
            remainingBalance -= principalForMonth
            totalInterestAccumulated += interestForMonth

            schedule.add(
                LoanRepaymentScheduleEntry(
                    installmentNumber = i,
                    dueDate = startDate.plusMonths(i.toLong()).toString(),
                    principal = principalForMonth,
                    interest = interestForMonth,
                    totalInstallment = monthlyInstallment,
                    remainingBalance = remainingBalance.max(Money.ZERO)
                )
            )
        }

        return AmortizationResult(principal + totalInterestAccumulated, totalInterestAccumulated, monthlyInstallment, schedule)
    }
}
