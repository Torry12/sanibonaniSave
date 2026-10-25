package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutType
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single authority for rules that change with the group type.
 *
 * These rules were previously spread across four files:
 *  - expected contribution and projected payout lived in `AccountingStrategy`
 *  - the reserve floor lived in `TreasuryServiceImpl`
 *  - the payout kind lived in `RequestPayoutUseCase`
 *  - the probation restriction lived in `PayoutGuardImpl`
 *
 * A group type now has exactly one place to answer all of them.
 */
@Singleton
class GroupFinancialPolicy @Inject constructor() {

    fun expectedMemberContribution(group: Group, member: Member): Money {
        member.monthlyContributionOverride?.let { return it }
        if (group.type != GroupType.BURIAL_SOCIETY) return group.monthlyContribution

        val over65Count = member.beneficiaryOver65Count ?: 0
        val increasePct = group.beneficiaryIncreasePct ?: 0.0
        val surcharge = group.monthlyContribution * (increasePct / 100.0) * over65Count.toDouble()
        return group.monthlyContribution + surcharge
    }

    fun projectedPayout(
        group: Group,
        members: List<Member>,
        member: Member? = null,
        currentDate: LocalDate = LocalDate.now()
    ): Money = when (group.type) {
        GroupType.BURIAL_SOCIETY -> group.goalAmount
        GroupType.STOKVEL -> projectedStokvelPayout(group, members, currentDate)
        GroupType.ROSCA -> projectedRoscaPayout(group, members)
        GroupType.INVESTMENT_CLUB -> projectedInvestmentClubPayout(group, members, member)
        else -> group.balance / members.size.coerceAtLeast(1)
    }

    /**
     * Burial cover is the configured claim amount; the working reserve is five months of
     * contributions. Stokvel holds 10% and emergency funds 20% of their balance. Everything
     * else holds nothing.
     */
    fun requiredReserve(group: Group): Money = when (group.type) {
        GroupType.BURIAL_SOCIETY -> group.monthlyContribution * 5.0
        GroupType.STOKVEL -> group.balance * 0.10
        GroupType.EMERGENCY_FUND -> group.balance * 0.20
        else -> Money.ZERO
    }

    fun payoutTypeFor(group: Group): PayoutType = when (group.type) {
        GroupType.STOKVEL -> PayoutType.ANNUAL
        GroupType.EMERGENCY_FUND -> PayoutType.EMERGENCY
        GroupType.INVESTMENT_CLUB -> PayoutType.DISINVESTMENT
        else -> PayoutType.STANDARD
    }

    /**
     * Probation blocks the payout kinds that draw on capital the member has not yet built up.
     * ROSCA and the generic types place no probation restriction on payouts.
     */
    fun isProbationRestricted(groupType: GroupType, payoutType: PayoutType): Boolean =
        when (groupType) {
            GroupType.BURIAL_SOCIETY -> payoutType == PayoutType.CLAIM
            GroupType.STOKVEL -> payoutType == PayoutType.STANDARD || payoutType == PayoutType.EMERGENCY
            GroupType.INVESTMENT_CLUB -> payoutType == PayoutType.DISINVESTMENT
            else -> false
        }

    private fun projectedStokvelPayout(group: Group, members: List<Member>, currentDate: LocalDate): Money {
        val monthsRemaining = 12 - currentDate.monthValue
        val expectedMonthlyTotal = group.monthlyContribution * members.size
        val projectedTotal = group.balance + (expectedMonthlyTotal * monthsRemaining)
        return projectedTotal / members.size.coerceAtLeast(1)
    }

    private fun projectedRoscaPayout(group: Group, members: List<Member>): Money {
        val activeCount = members.count { it.status != MemberStatus.SUSPENDED }.coerceAtLeast(1)
        return group.monthlyContribution * activeCount
    }

    private fun projectedInvestmentClubPayout(group: Group, members: List<Member>, member: Member?): Money {
        if (member == null || group.balance.isZero) return Money.ZERO
        val totalPaidByAll = members.fold(Money.ZERO) { acc, m -> acc + m.totalPaid }
        if (totalPaidByAll.isZero) return Money.ZERO
        return group.balance * member.totalPaid.ratio(totalPaidByAll)
    }
}
