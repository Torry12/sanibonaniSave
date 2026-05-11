package com.sanibonani.save.domain.usecase.groups

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.rosca.CalculateRoscaRotationUseCase
import com.sanibonani.save.domain.usecase.investment.CalculateInvestmentClubValuationUseCase
import com.sanibonani.save.domain.usecase.stokvel.CalculateStokvelPayoutsUseCase
import javax.inject.Inject

/**
 * Aggregator use case that provides specialized business insights
 * based on the specific group type.
 */
class GetGroupBusinessInsightsUseCase @Inject constructor(
    private val roscaRotationUseCase: CalculateRoscaRotationUseCase,
    private val investmentValuationUseCase: CalculateInvestmentClubValuationUseCase,
    private val stokvelPayoutsUseCase: CalculateStokvelPayoutsUseCase
) {

    sealed class GroupBusinessInsight {
        data class Rosca(val schedule: CalculateRoscaRotationUseCase.RoscaSchedule) : GroupBusinessInsight()
        data class InvestmentClub(val valuation: CalculateInvestmentClubValuationUseCase.PortfolioValuation) : GroupBusinessInsight()
        data class Stokvel(val projection: CalculateStokvelPayoutsUseCase.PayoutProjection) : GroupBusinessInsight()
        data object None : GroupBusinessInsight()
    }

    operator fun invoke(group: Group, members: List<Member>): GroupBusinessInsight {
        return when (group.type) {
            GroupType.ROSCA -> {
                roscaRotationUseCase(group, members).fold(
                    onSuccess = { GroupBusinessInsight.Rosca(it) },
                    onFailure = { GroupBusinessInsight.None }
                )
            }
            GroupType.INVESTMENT_CLUB -> {
                investmentValuationUseCase(group, members).fold(
                    onSuccess = { GroupBusinessInsight.InvestmentClub(it) },
                    onFailure = { GroupBusinessInsight.None }
                )
            }
            GroupType.STOKVEL -> {
                stokvelPayoutsUseCase(group, members).fold(
                    onSuccess = { GroupBusinessInsight.Stokvel(it) },
                    onFailure = { GroupBusinessInsight.None }
                )
            }
            else -> GroupBusinessInsight.None
        }
    }
}
