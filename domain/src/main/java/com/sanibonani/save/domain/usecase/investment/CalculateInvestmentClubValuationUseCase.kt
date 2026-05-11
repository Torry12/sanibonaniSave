package com.sanibonani.save.domain.usecase.investment

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import javax.inject.Inject

/**
 * Investment Club specific logic.
 * Calculates net asset value (NAV) and member share value.
 */
class CalculateInvestmentClubValuationUseCase @Inject constructor() {

    data class PortfolioValuation(
        val totalAssets: Double,
        val totalUnits: Double,
        val unitPrice: Double,
        val memberValuations: List<MemberValuation>
    )

    data class MemberValuation(
        val memberId: String,
        val memberName: String,
        val unitsOwned: Double,
        val marketValue: Double,
        val contributionWeight: Double // Percentage of total contributions
    )

    operator fun invoke(group: Group, members: List<Member>): Result<PortfolioValuation> {
        if (group.type != GroupType.INVESTMENT_CLUB) {
            return Result.failure(IllegalArgumentException("Group is not an Investment Club"))
        }

        val totalAssets = group.balance
        
        // In a real scenario, totalUnits would be stored in metadata.
        // For this implementation, we'll derive it from total contributions.
        val totalContributions = members.sumOf { it.totalPaid ?: 0.0 }
        
        if (totalContributions <= 0.0) {
            return Result.success(PortfolioValuation(totalAssets, 0.0, 1.0, emptyList()))
        }

        // 1. Calculate Unit Price (Initial unit price = 1.0)
        // unit price = totalAssets / totalUnits
        // If we assume 1 unit = R1 at inception:
        val totalUnits = totalContributions
        val unitPrice = if (totalUnits > 0) totalAssets / totalUnits else 1.0

        val memberValuations = members.map { member ->
            val memberContribution = member.totalPaid ?: 0.0
            val weight = if (totalContributions > 0) memberContribution / totalContributions else 0.0
            
            MemberValuation(
                memberId = member.id ?: "",
                memberName = member.fullName,
                unitsOwned = memberContribution, // 1 unit per R1 contributed
                marketValue = memberContribution * unitPrice,
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
