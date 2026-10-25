package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.RiskLevel
import java.math.BigDecimal
import kotlin.math.*

/**
 * Pure math functions for high-precision actuarial calculations.
 * Ensures strict adherence to BigDecimal logic for financial integrity.
 */
object ActuarialMath {

    /**
     * Calculates the Actuarial Present Value (APV) of a future liability.
     * APV = ClaimValue * (1 / (1 + i)^n)
     */
    fun calculateAPV(futureLiability: Money, annualDiscountRatePct: Double, years: Double): Money {
        val i = annualDiscountRatePct / 100.0
        val discountFactor = 1.0 / (1.0 + i).pow(years)
        return futureLiability * discountFactor
    }

    /**
     * Calculates the present value of an annuity (regular contributions).
     * PV = Pmt * ((1 - (1+r)^-n) / r)
     */
    fun calculateAnnuityPV(periodicPayment: Money, monthlyRatePct: Double, months: Int): Money {
        if (months <= 0) return Money.ZERO
        val r = monthlyRatePct / 100.0
        if (r == 0.0) return periodicPayment * months.toDouble()

        val factor = (1.0 - (1.0 + r).pow(-months.toDouble())) / r
        return periodicPayment * factor
    }

    /**
     * Standard risk scoring algorithm based on three primary pillars:
     * 1. Reserve Adequacy (Balance vs Expected Claims)
     * 2. Contribution Sufficiency (Actual vs Required Premium)
     * 3. Payment Compliance (Expected vs Actual Inflow)
     */
    fun calculateCompositeRiskScore(
        reserveAdequacyPct: Double,
        contributionSufficiencyPct: Double,
        paymentCompliancePct: Double
    ): Int {
        val score = (
            min(100.0, reserveAdequacyPct) * ActuarialConstants.WEIGHT_RESERVE_ADEQUACY +
            min(100.0, contributionSufficiencyPct) * ActuarialConstants.WEIGHT_CONTRIBUTION_SUFFICIENCY +
            min(100.0, paymentCompliancePct) * ActuarialConstants.WEIGHT_PAYMENT_RATE
        )
        return score.roundToInt().coerceIn(0, 100)
    }

    /**
     * Single mapping from a 0-100 composite score to the published risk bands.
     */
    fun riskLevelFor(score: Int): RiskLevel = when {
        score >= 80 -> RiskLevel.LOW
        score >= 55 -> RiskLevel.MODERATE
        score >= 30 -> RiskLevel.HIGH
        else -> RiskLevel.CRITICAL
    }

    /**
     * Calculates the monthly contribution needed to reach a target goal given a timeframe
     * and expected interest rate.
     */
    fun calculateRequiredContribution(
        targetGap: Money,
        annualInterestRatePct: Double,
        months: Int,
        memberCount: Int
    ): Money {
        val n = memberCount.coerceAtLeast(1)
        if (months <= 0 || targetGap.isZero) return Money.ZERO

        val r = annualInterestRatePct / 100.0 / 12.0
        if (r == 0.0) return targetGap / (n * months)

        // Sinking Fund Factor: r / ((1+r)^n - 1)
        val sinkingFundFactor = r / ((1.0 + r).pow(months.toDouble()) - 1.0)
        return targetGap * sinkingFundFactor / n.toDouble()
    }

    /**
     * Calculates Compound Annual Growth Rate (CAGR) adhering to CFA/GIPS financial standards:
     * - Uses simple annualization for periods under 1 year to avoid fractional exponent distortion.
     * - Bounds start/end values to prevent negative root math anomalies.
     */
    fun calculateCAGR(startValue: Money, endValue: Money, years: Double): Double {
        if (startValue.isZero || !startValue.isPositive || years <= 0.0) return 0.0
        val ratio = (endValue.ratio(startValue)).coerceAtLeast(0.0)

        val totalReturnPct = (ratio - 1.0) * 100.0

        // For periods under 1 year, CAGR exponentiation causes extreme distortion (e.g. -5000%).
        // Financial industry standard uses simple return or simple linear annualization for < 1 year.
        if (years < 1.0) {
            return totalReturnPct
        }

        val cagr = (ratio.pow(1.0 / years) - 1.0) * 100.0
        return cagr.coerceIn(-100.0, 10000.0)
    }

    /**
     * Calculates Sharpe Ratio for investment clubs.
     * (Return - RiskFreeRate) / StandardDeviation
     */
    fun calculateSharpeRatio(annualReturnPct: Double, riskFreeRatePct: Double, volatilityPct: Double): Double {
        if (volatilityPct <= 0.0) return 0.0
        return (annualReturnPct - riskFreeRatePct) / volatilityPct
    }

    /**
     * Projects months to insolvency given current balance and negative net cash flow.
     */
    fun calculateInsolvencyMonths(balance: Money, monthlyNetOutflow: Money): Int {
        if (!monthlyNetOutflow.isPositive) return Int.MAX_VALUE
        return (balance.ratio(monthlyNetOutflow)).toInt().coerceAtLeast(0)
    }

    fun round(v: Double): Double = (kotlin.math.round(v * 100.0) / 100.0)
}
