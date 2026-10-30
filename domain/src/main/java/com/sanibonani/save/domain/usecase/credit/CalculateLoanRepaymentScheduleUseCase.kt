package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.model.AmortizationType
import com.sanibonani.save.domain.model.LoanRepaymentScheduleEntry
import com.sanibonani.save.domain.model.Money
import java.math.MathContext
import java.time.LocalDate
import kotlin.math.pow
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
        require(!params.processingFee.isNegative) { "Processing fee must not be negative." }
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

        val schedule = reconcile(calc.schedule, totalToRepayWithFee, monthlyInstallmentWithFee)
        return Result(
            totalToRepay = totalToRepayWithFee,
            totalInterest = totalInterestAndFee,
            monthlyInstallment = monthlyInstallmentWithFee,
            processingFee = params.processingFee,
            effectiveApr = calculateEffectiveApr(params.principal, schedule),
            schedule = schedule
        )
    }

    /**
     * Annualizes the monthly yield implied by the actual billed schedule. This includes fees and
     * the payment timing, unlike the nominal rate supplied to the amortization strategy.
     */
    private fun calculateEffectiveApr(
        principal: Money,
        schedule: List<LoanRepaymentScheduleEntry>
    ): Double {
        val paymentsAsPrincipalRatios = schedule.map { entry ->
            entry.totalInstallment.amount
                .divide(principal.amount, MathContext.DECIMAL128)
                .toDouble()
                .also { require(it.isFinite()) { "Loan payment ratio is outside the supported range." } }
        }

        fun presentValue(monthlyRate: Double): Double =
            paymentsAsPrincipalRatios.withIndex().sumOf { (index, payment) ->
                payment / (1.0 + monthlyRate).pow(index + 1)
            }

        if (presentValue(0.0) <= 1.0) return 0.0

        var lower = 0.0
        var upper = 0.01
        while (presentValue(upper) > 1.0) {
            upper *= 2.0
            require(upper.isFinite()) { "Could not calculate effective APR for this repayment schedule." }
        }
        repeat(100) {
            val middle = (lower + upper) / 2.0
            if (presentValue(middle) > 1.0) lower = middle else upper = middle
        }

        val annualRate = Math.expm1(12.0 * Math.log1p((lower + upper) / 2.0)) * 100.0
        require(annualRate.isFinite()) { "Effective APR is outside the supported range." }
        return annualRate
    }

    /**
     * Bills the fee on the published instalment and settles the difference between the rounded
     * instalment and the whole balance on the final row, so the plan a member is shown adds up to
     * the amount the loan is actually owed.
     */
    private fun reconcile(
        schedule: List<LoanRepaymentScheduleEntry>,
        totalToRepay: Money,
        monthlyInstallment: Money
    ): List<LoanRepaymentScheduleEntry> = schedule.mapIndexed { index, entry ->
        val amount = if (index == schedule.lastIndex) {
            totalToRepay - monthlyInstallment * index
        } else {
            monthlyInstallment
        }
        entry.copy(totalInstallment = amount)
    }
}
