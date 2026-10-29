package com.sanibonani.save.domain.usecase.disbursement
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
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
            totalAssets.dividedBy(totalUnits.toDouble())
        } else {
            Money("1.00")
        }

        val memberValuations = members.map { member ->
            val memberContribution = member.totalPaid
            val weight = if (totalContributions.isPositive) memberContribution.ratio(totalContributions) else 0.0

            MemberValuation(
                memberId = member.id ?: "",
                memberName = member.fullName,
                unitsOwned = memberContribution, // 1 unit per R1 contributed
                marketValue = totalAssets.proportionOf(memberContribution, totalContributions),
                contributionWeight = weight * 100.0
            )
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
