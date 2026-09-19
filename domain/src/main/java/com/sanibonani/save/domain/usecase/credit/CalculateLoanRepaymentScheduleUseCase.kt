package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.model.AmortizationType
import com.sanibonani.save.domain.model.LoanRepaymentScheduleEntry
import com.sanibonani.save.domain.model.Money
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.pow

/**
 * Handles the mathematical calculation of a loan repayment schedule.
 */
class CalculateLoanRepaymentScheduleUseCase @Inject constructor() {

    data class Params(
        val principal: Money,
        val annualInterestRate: Double,
        val termMonths: Int,
        val amortizationType: AmortizationType = AmortizationType.FLAT,
        val startDate: LocalDate = LocalDate.now()
    )

    data class Result(
        val totalToRepay: Money,
        val totalInterest: Money,
        val monthlyInstallment: Money,
        val effectiveApr: Double,
        val schedule: List<LoanRepaymentScheduleEntry>
    )

    operator fun invoke(params: Params): Result {
        return when (params.amortizationType) {
            AmortizationType.FLAT -> calculateFlatRate(params)
            AmortizationType.REDUCING -> calculateReducingBalance(params)
        }
    }

    private fun calculateFlatRate(params: Params): Result {
        val totalInterest = params.principal * (params.annualInterestRate / 100.0) * (params.termMonths / 12.0)
        val totalToRepay = params.principal + totalInterest
        val monthlyInstallment = totalToRepay / params.termMonths

        val schedule = mutableListOf<LoanRepaymentScheduleEntry>()
        var remainingPrincipal = params.principal
        val principalPerMonth = params.principal / params.termMonths
        val interestPerMonth = totalInterest / params.termMonths

        for (i in 1..params.termMonths) {
            remainingPrincipal -= principalPerMonth
            schedule.add(
                LoanRepaymentScheduleEntry(
                    installmentNumber = i,
                    dueDate = params.startDate.plusMonths(i.toLong()).toString(),
                    principal = principalPerMonth,
                    interest = interestPerMonth,
                    totalInstallment = monthlyInstallment,
                    remainingBalance = remainingPrincipal.max(Money.ZERO)
                )
            )
        }

        return Result(totalToRepay, totalInterest, monthlyInstallment, params.annualInterestRate, schedule)
    }

    private fun calculateReducingBalance(params: Params): Result {
        val monthlyRate = (params.annualInterestRate / 100.0) / 12.0
        val numerator = monthlyRate * (1 + monthlyRate).pow(params.termMonths)
        val denominator = (1 + monthlyRate).pow(params.termMonths) - 1
        val monthlyInstallment = params.principal * (numerator / denominator)

        val schedule = mutableListOf<LoanRepaymentScheduleEntry>()
        var remainingBalance = params.principal
        var totalInterestAccumulated = Money.ZERO

        for (i in 1..params.termMonths) {
            val interestForMonth = remainingBalance * monthlyRate
            val principalForMonth = monthlyInstallment - interestForMonth
            remainingBalance -= principalForMonth
            totalInterestAccumulated += interestForMonth

            schedule.add(
                LoanRepaymentScheduleEntry(
                    installmentNumber = i,
                    dueDate = params.startDate.plusMonths(i.toLong()).toString(),
                    principal = principalForMonth,
                    interest = interestForMonth,
                    totalInstallment = monthlyInstallment,
                    remainingBalance = remainingBalance.max(Money.ZERO)
                )
            )
        }

        return Result(params.principal + totalInterestAccumulated, totalInterestAccumulated, monthlyInstallment, params.annualInterestRate, schedule)
    }
}
