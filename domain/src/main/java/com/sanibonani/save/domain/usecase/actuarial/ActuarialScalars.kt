package com.sanibonani.save.domain.usecase.actuarial

import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.Money
import kotlin.math.ceil
import kotlin.math.max

/**
 * Core premium, reserve and risk math shared by every group-type strategy.
 *
 * Pure and stateless by design: strategies depend on this directly instead of
 * calling back into [GroupTypeActuarialEngine], which keeps the engine a plain
 * coordinator and removes the engine/strategy dependency cycle.
 */
object ActuarialScalars {

    fun compute(
        membersCount: Int,
        balance: Money,
        mortalityRatePct: Double,
        avgClaim: Money,
        safetyLoadingPct: Double,
        adminCostPerMemberMonthly: Money,
        annualDiscountRatePct: Double,
        currentPremiumMonthly: Money,
        claimsPaid: Money,
        totalContributions: Money,
        paymentRatePct: Double,
        totalExpectedContributionsAnnual: Money,
        defaultAnnualExpectedClaim: Money = Money.ZERO
    ): ActuarialMetrics {
        val n = membersCount.coerceAtLeast(1)
        val q = max(0.0, mortalityRatePct) / 100.0

        val calculatedAnnualClaims = avgClaim.max(Money.ZERO) * (n * q)
        val expectedAnnualClaims = if (calculatedAnnualClaims.isPositive) {
            calculatedAnnualClaims
        } else if (defaultAnnualExpectedClaim.isPositive) {
            defaultAnnualExpectedClaim
        } else {
            Money.ZERO
        }
        val apv = ActuarialMath.calculateAPV(expectedAnnualClaims, annualDiscountRatePct, 1.0)

        val purePremiumMonthly = (expectedAnnualClaims / n) / 12.0
        val safetyLoadingMonthly = purePremiumMonthly.percent(safetyLoadingPct)
        val grossPremiumMonthly = purePremiumMonthly + safetyLoadingMonthly + adminCostPerMemberMonthly

        val reserveAdequacyPct = if (expectedAnnualClaims.isPositive) balance.ratio(expectedAnnualClaims) * 100.0 else 1000.0
        val contributionSufficiencyPct = if (grossPremiumMonthly.isPositive) currentPremiumMonthly.ratio(grossPremiumMonthly) * 100.0 else 0.0

        val score = ActuarialMath.calculateCompositeRiskScore(
            reserveAdequacyPct = reserveAdequacyPct,
            contributionSufficiencyPct = contributionSufficiencyPct,
            paymentCompliancePct = paymentRatePct
        )

        val monthlyInflow = (totalExpectedContributionsAnnual / 12).percent(paymentRatePct)
        val monthlyClaimsOutflow = expectedAnnualClaims / 12.0
        val monthlyAdminOutflow = adminCostPerMemberMonthly * n
        val monthlyNetFlow = monthlyInflow - monthlyClaimsOutflow - monthlyAdminOutflow
        val insolvencyMonths = ActuarialMath.calculateInsolvencyMonths(balance, -monthlyNetFlow)

        val marginAmtMonthly = currentPremiumMonthly - purePremiumMonthly - safetyLoadingMonthly
        val breakEvenMembers = if (marginAmtMonthly.isPositive) ceil((adminCostPerMemberMonthly * n).ratio(marginAmtMonthly)).toInt() else Int.MAX_VALUE

        return ActuarialMetrics(
            purePremium = purePremiumMonthly,
            grossPremium = grossPremiumMonthly,
            reserveAdequacyPct = ActuarialMath.round(reserveAdequacyPct),
            solvencyMarginPct = ActuarialMath.round(if (expectedAnnualClaims.isPositive) ((balance + totalContributions) - claimsPaid).ratio(expectedAnnualClaims) * 100.0 else 100.0),
            lossRatioPct = ActuarialMath.round(if (totalContributions.isPositive) claimsPaid.ratio(totalContributions) * 100.0 else 0.0),
            contributionSufficiencyPct = ActuarialMath.round(contributionSufficiencyPct),
            breakEvenMembers = breakEvenMembers,
            actuarialPresentValue = apv,
            fundingRatioPct = ActuarialMath.round(if (apv.isPositive) balance.ratio(apv) * 100.0 else 1000.0),
            paymentRatePct = ActuarialMath.round(paymentRatePct),
            compositeRiskScore = score,
            riskLevel = ActuarialMath.riskLevelFor(score),
            insolvencyMonths = insolvencyMonths,
            expectedAnnualClaims = expectedAnnualClaims
        )
    }
}
