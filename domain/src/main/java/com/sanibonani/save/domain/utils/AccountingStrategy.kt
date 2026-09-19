package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.config.PlatformFees
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Strategy interface for group-type specific accounting rules.
 */
interface AccountingStrategy {
    /**
     * Calculates ONLY the group's portion of the contribution (excluding platform fee).
     */
    fun calculateExpectedMemberGroupPart(group: Group, member: Member): Money

    /**
     * Calculates the total expected monthly outflow for a member.
     */
    fun calculateExpectedMonthlyOutflow(group: Group, member: Member): Money {
        return calculateExpectedMemberGroupPart(group, member) + PlatformFees.MONTHLY_MEMBER_FEE
    }

    /**
     * Calculates the member's share of the total group fund.
     */
    fun calculateMemberSharePercentage(groupBalance: Money, memberTotalPaid: Money): Double {
        if (groupBalance.isZero) return 0.0
        return memberTotalPaid.amount.divide(groupBalance.amount, 10, RoundingMode.HALF_EVEN)
            .multiply(BigDecimal("100")).toDouble()
    }

    /**
     * Calculates exit penalty if applicable.
     */
    fun calculateExitPenalty(member: Member, amountToWithdraw: Money, currentDate: LocalDate): Money = Money.ZERO
}

class DefaultAccounting : AccountingStrategy {
    override fun calculateExpectedMemberGroupPart(group: Group, member: Member): Money {
        return member.monthlyContributionOverride ?: group.monthlyContribution
    }
}

class BurialSocietyAccounting : AccountingStrategy {
    override fun calculateExpectedMemberGroupPart(group: Group, member: Member): Money {
        val base = member.monthlyContributionOverride ?: group.monthlyContribution
        val over65Count = member.beneficiaryOver65Count ?: 0
        val increasePct = group.beneficiaryIncreasePct ?: 0.0
        val surcharge = base * (increasePct / 100.0) * over65Count.toDouble()
        return base + surcharge
    }
}

class InvestmentClubAccounting : AccountingStrategy {
    override fun calculateExpectedMemberGroupPart(group: Group, member: Member): Money {
        return member.monthlyContributionOverride ?: group.monthlyContribution
    }

    override fun calculateExitPenalty(member: Member, amountToWithdraw: Money, currentDate: LocalDate): Money {
        // Standard rule: 10% penalty if exiting before lock-in period (e.g., 12 months).
        val joinedStr = member.joinedAt ?: member.createdAt ?: return Money.ZERO
        val joinedDate = LocalDate.parse(joinedStr.substringBefore("T"))
        val monthsMembership = ChronoUnit.MONTHS.between(joinedDate, currentDate)

        return if (monthsMembership < 12) {
            amountToWithdraw * 0.10
        } else {
            Money.ZERO
        }
    }
}
