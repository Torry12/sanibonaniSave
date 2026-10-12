package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.model.AmortizationType
import com.sanibonani.save.domain.model.LoanRepaymentScheduleEntry
import com.sanibonani.save.domain.model.Money
import java.time.LocalDate
import javax.inject.Inject

/**
 * Handles the mathematical calculation of a loan repayment schedule.
 * Includes platform loan processing fee charging.
 */
class CalculateLoanRepaymentScheduleUseCase @Inject constructor() {
    data class Params(
        val principal: Money,
        val annualInterestRate: Double,
        val termMonths: Int,
        val processingFee: Money = Money.ZERO,
        val amortizationType: AmortizationType = AmortizationType.FLAT,
        val startDate: LocalDate = LocalDate.now()
    )
    data class Result(
        val totalToRepay: Money,
        val totalInterest: Money,
        val monthlyInstallment: Money,
        val processingFee: Money = Money.ZERO,
        val effectiveApr: Double,
        val schedule: List<LoanRepaymentScheduleEntry>
    )

    private val strategies = mapOf(
        AmortizationType.FLAT to FlatAmortizationStrategy(),
        AmortizationType.REDUCING to ReducingBalanceAmortizationStrategy()
    )

    operator fun invoke(params: Params): Result {
        val strategy = strategies[params.amortizationType]
            ?: throw IllegalArgumentException("Unsupported amortization type: ${params.amortizationType}")

        val calc = strategy.calculate(
            principal = params.principal,
            annualInterestRate = params.annualInterestRate,
            termMonths = params.termMonths,
            startDate = params.startDate
        )

        val totalToRepayWithFee = calc.totalToRepay + params.processingFee
        val totalInterestAndFee = calc.totalInterest + params.processingFee
        val monthlyInstallmentWithFee = totalToRepayWithFee / params.termMonths

        return Result(
            totalToRepay = totalToRepayWithFee,
            totalInterest = totalInterestAndFee,
            monthlyInstallment = monthlyInstallmentWithFee,
            processingFee = params.processingFee,
            effectiveApr = params.annualInterestRate,
            schedule = calc.schedule
        )
    }
}
