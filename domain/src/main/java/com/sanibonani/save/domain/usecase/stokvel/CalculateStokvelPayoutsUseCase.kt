package com.sanibonani.save.domain.usecase.stokvel

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Stokvel specific logic.
 * Calculates annual payout projections and savings progress.
 */
class CalculateStokvelPayoutsUseCase @Inject constructor() {

    data class PayoutProjection(
        val totalProjectedFund: Double,
        val monthsRemaining: Int,
        val memberProjections: List<MemberPayoutProjection>
    )

    data class MemberPayoutProjection(
        val memberId: String,
        val memberName: String,
        val currentSavings: Double,
        val projectedFinalPayout: Double
    )

    operator fun invoke(group: Group, members: List<Member>): Result<PayoutProjection> {
        if (group.type != GroupType.STOKVEL) {
            return Result.failure(IllegalArgumentException("Group is not a Stokvel"))
        }

        val now = LocalDate.now()

        // Derive the cycle start month from group.createdAt; fall back to January.
        // A stokvel starting in March runs March→February (12-month cycle).
        val cycleStartMonth = try {
            LocalDate.parse(group.createdAt?.substringBefore("T")).monthValue
        } catch (_: Exception) {
            1 // Default: January (NASASA standard Jan–Dec cycle)
        }

        // Determine the payout month: last month of the 12-month cycle
        // e.g. start=March → payout=February (month 2 of next year)
        val payoutMonth = if (cycleStartMonth == 1) 12 else cycleStartMonth - 1

        // Calculate months remaining to the payout month within the current 12-month window
        val cycleEndThisYear = if (payoutMonth >= now.monthValue) {
            now.withMonth(payoutMonth).withDayOfMonth(now.withMonth(payoutMonth).lengthOfMonth())
        } else {
            now.withYear(now.year + 1).withMonth(payoutMonth)
                .withDayOfMonth(now.withMonth(payoutMonth).lengthOfMonth())
        }
        val monthsRemaining = ChronoUnit.MONTHS.between(now, cycleEndThisYear).toInt().coerceAtLeast(0)

        val totalCurrentSavings = group.balance
        val expectedFutureMonthlyTotal = members.size * group.monthlyContribution
        val totalProjectedFund = totalCurrentSavings + (expectedFutureMonthlyTotal * monthsRemaining)

        val memberProjections = members.map { member ->
            val currentMemberSavings = member.totalPaid ?: 0.0
            val projectedFutureMemberSavings = monthsRemaining * group.monthlyContribution

            MemberPayoutProjection(
                memberId = member.id ?: "",
                memberName = member.fullName,
                currentSavings = currentMemberSavings,
                projectedFinalPayout = currentMemberSavings + projectedFutureMemberSavings
            )
        }

        return Result.success(
            PayoutProjection(
                totalProjectedFund = totalProjectedFund,
                monthsRemaining = monthsRemaining,
                memberProjections = memberProjections
            )
        )
    }
}
