package com.sanibonani.save.domain.usecase.disbursement
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Investment Club specific logic.
 * Calculates net asset value (NAV) and member share value.
 */
class CalculateInvestmentClubValuationUseCase @Inject constructor() {
    data class PortfolioValuation(
        val totalAssets: Money,
        val totalUnits: Money,
        val unitPrice: Money,
        val memberValuations: List<MemberValuation>
    )

    data class MemberValuation(
        val memberId: String,
        val memberName: String,
        val unitsOwned: Money,
        val marketValue: Money,
        val contributionWeight: Double // Percentage of total contributions
    )

    operator fun invoke(group: Group, members: List<Member>): Result<PortfolioValuation> {
        if (group.type != GroupType.INVESTMENT_CLUB) {
            return Result.failure(IllegalArgumentException("Group is not an Investment Club"))
        }

        val totalAssets = group.balance
        val totalContributions = members.fold(Money.ZERO) { acc, m -> acc + m.totalPaid }

        if (totalContributions.isZero) {
            return Result.success(PortfolioValuation(totalAssets, Money.ZERO, Money("1.00"), emptyList()))
        }

        val totalUnits = totalContributions
        // Determine unit price by dividing total assets by the total units (contributions)
        val unitPrice = if (totalContributions.isPositive) {
            totalAssets.dividedBy(totalUnits)
        } else {
            Money("1.00")
        }

        val memberValuations = run {
            // The NAV is split once, across all the rows, rather than rounded per row: pricing each
            // share on its own drops every sub-cent tail, and the residue is money the group holds
            // and no member owns.
            val marketValues = allocateProportionally(totalAssets, members.map { it.totalPaid })
            members.mapIndexed { index, member ->
                val memberContribution = member.totalPaid
                val weight = if (totalContributions.isPositive) memberContribution.ratio(totalContributions) else 0.0

                MemberValuation(
                    memberId = member.id ?: "",
                    memberName = member.fullName,
                    unitsOwned = memberContribution, // 1 unit per R1 contributed
                    marketValue = marketValues[index],
                    contributionWeight = weight * 100.0
                )
            }
        }

        return Result.success(
            PortfolioValuation(
                totalAssets = totalAssets,
                totalUnits = totalUnits,
                unitPrice = unitPrice,
                memberValuations = memberValuations
            )
        )
    }
}

/**
 * Split [total] across [weights] so the parts add back to [total] cent for cent.
 *
 * Every share is floored first and the leftover cents - never more than one per member - then go to
 * the rows whose exact share was closest to rounding up, ties broken by contributor order so the
 * same books price the same way every time. Working in cents keeps the arithmetic in BigDecimal:
 * a NAV and a contribution are both scale-2, and their product overflows a Long.
 */
internal fun allocateProportionally(total: Money, weights: List<Money>): List<Money> {
    val denominator = weights.fold(Money.ZERO) { acc, w -> acc + w }
    if (weights.isEmpty() || denominator.isZero) return weights.map { Money.ZERO }

    val totalCents = total.amount.movePointRight(2)
    val denominatorCents = denominator.amount.movePointRight(2)
    val numerators = weights.map { totalCents.multiply(it.amount.movePointRight(2)) }
    val cents = numerators.map { it.divide(denominatorCents, 0, RoundingMode.FLOOR) }.toMutableList()

    // Each floor is at most one cent short of its exact share, so the gap to close is smaller than
    // the number of rows and a single pass over the largest remainders settles it.
    var leftoverCents = totalCents.subtract(cents.fold(BigDecimal.ZERO) { acc, c -> acc + c }).intValueExact()
    val remainders = numerators.mapIndexed { index, numerator ->
        numerator - cents[index].multiply(denominatorCents)
    }
    val byLargestRemainder = remainders.indices.sortedWith(
        compareByDescending<Int> { remainders[it] }.thenBy { it }
    )
    for (rank in 0 until leftoverCents) cents[byLargestRemainder[rank]] = cents[byLargestRemainder[rank]] + BigDecimal.ONE

    return cents.map { Money(it.movePointLeft(2)) }
}
