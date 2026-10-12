package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.model.*
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
     * Calculates the member's share of the total group fund.
     */
    fun calculateMemberSharePercentage(groupBalance: Money, memberTotalPaid: Money): Double {
        if (groupBalance.isZero) return 0.0
        return memberTotalPaid.amount.divide(groupBalance.amount, 10, RoundingMode.HALF_EVEN)
            .multiply(BigDecimal("100")).toDouble()
    }

    /**
     * Calculates the projected payout for a member.
     */
    fun calculateProjectedPayout(group: Group, members: List<Member>, member: Member? = null): Money

    /**
     * Calculates exit penalty if applicable.
     */
    fun calculateExitPenalty(member: Member, amountToWithdraw: Money, currentDate: LocalDate): Money = Money.ZERO
}

class DefaultAccounting : AccountingStrategy {
    override fun calculateExpectedMemberGroupPart(group: Group, member: Member): Money {
        return member.monthlyContributionOverride ?: group.monthlyContribution
    }

    override fun calculateProjectedPayout(group: Group, members: List<Member>, member: Member?): Money {
        return group.balance / members.size.coerceAtLeast(1)
    }
}

class BurialSocietyAccounting : AccountingStrategy {
    override fun calculateExpectedMemberGroupPart(group: Group, member: Member): Money {
        if (member.monthlyContributionOverride != null) return member.monthlyContributionOverride
        val base = group.monthlyContribution
        val over65Count = member.beneficiaryOver65Count ?: 0
        val increasePct = group.beneficiaryIncreasePct ?: 0.0
        val surcharge = base * (increasePct / 100.0) * over65Count.toDouble()
        return base + surcharge
    }

    override fun calculateProjectedPayout(group: Group, members: List<Member>, member: Member?): Money {
        return group.goalAmount // Burial societies usually pay a fixed goal/benefit amount
    }
}

class StokvelAccounting : AccountingStrategy {
    override fun calculateExpectedMemberGroupPart(group: Group, member: Member): Money {
        return member.monthlyContributionOverride ?: group.monthlyContribution
    }

    override fun calculateProjectedPayout(group: Group, members: List<Member>, member: Member?): Money {
        val monthsRemaining = 12 - LocalDate.now().monthValue
        val expectedMonthlyTotal = group.monthlyContribution * members.size
        val projectedTotal = group.balance + (expectedMonthlyTotal * monthsRemaining)
        return projectedTotal / members.size.coerceAtLeast(1)
    }
}

class RoscaAccounting : AccountingStrategy {
    override fun calculateExpectedMemberGroupPart(group: Group, member: Member): Money {
        return member.monthlyContributionOverride ?: group.monthlyContribution
    }

    override fun calculateProjectedPayout(group: Group, members: List<Member>, member: Member?): Money {
        val activeCount = members.count { it.status != MemberStatus.SUSPENDED }.coerceAtLeast(1)
        return group.monthlyContribution * activeCount
    }
}

class InvestmentClubAccounting : AccountingStrategy {
    override fun calculateExpectedMemberGroupPart(group: Group, member: Member): Money {
        return member.monthlyContributionOverride ?: group.monthlyContribution
    }

    override fun calculateProjectedPayout(group: Group, members: List<Member>, member: Member?): Money {
        if (member == null || group.balance.isZero) return Money.ZERO
        val totalPaidByAll = members.fold(Money.ZERO) { acc, m -> acc + m.totalPaid }
        if (totalPaidByAll.isZero) return Money.ZERO

        return group.balance * member.totalPaid.ratio(totalPaidByAll)
    }

    override fun calculateExitPenalty(member: Member, amountToWithdraw: Money, currentDate: LocalDate): Money {
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
