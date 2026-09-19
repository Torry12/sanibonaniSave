package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money

data class GroupStats(
    val activeMemberCount: Int,
    val monthsActive: Int,
    val totalExpectedAnnualContributions: Money,
    val paymentRatePct: Double,
    val activeMembers: List<Member>
)

data class BurialSocietyMetrics(
    val expectedAnnualClaimsCount: Double,
    val purePremium: Money,
    val safetyLoadedPremium: Money,
    val grossPremium: Money,
    val actuarialPresentValue: Money,
    val solvencyRatio: Double,
    val capitalAdequacyPct: Double,
    val requiredReserveAmount: Money,
    val reserveCoverageMonths: Double,
    val minimumCapitalAmount: Money,
    val isSolvent: Boolean,
    val isCapitalAdequate: Boolean,
    val isReserveAdequate: Boolean,
    val benefitAdequacyPct: Double,
    val claimRatePerThousand: Double,
    val projectedClaimsNextYear: Money,
    val yearsToInsolvencyAtCurrentRate: Double,
    val warnings: List<String>
)
