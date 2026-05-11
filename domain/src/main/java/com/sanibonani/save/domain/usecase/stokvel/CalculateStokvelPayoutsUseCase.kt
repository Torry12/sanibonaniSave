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

        // Typically Stokvels run for a 12-month period (Jan to Dec)
        val now = LocalDate.now()
        val yearEnd = now.withMonth(12).withDayOfMonth(31)
        val monthsRemaining = ChronoUnit.MONTHS.between(now, yearEnd).toInt().coerceAtLeast(0)

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
