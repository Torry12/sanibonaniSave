package com.sanibonani.save.domain.usecase.groups

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.ActuarialRepository
import com.sanibonani.save.domain.usecase.investment.CalculateInvestmentClubValuationUseCase
import com.sanibonani.save.domain.usecase.rosca.CalculateRoscaRotationUseCase
import com.sanibonani.save.domain.usecase.stokvel.CalculateStokvelPayoutsUseCase
import javax.inject.Inject

/**
 * Aggregator use case that provides specialized business insights
 * based on the specific group type.
 *
 * Covers all 8 group types:
 *  - ROSCA          → [GroupBusinessInsight.Rosca]      (Besley-Coate-Loury rotation model)
 *  - INVESTMENT_CLUB→ [GroupBusinessInsight.InvestmentClub] (NAV / CAGR / Sharpe)
 *  - STOKVEL        → [GroupBusinessInsight.Stokvel]    (NASASA payout projection)
 *  - BURIAL_SOCIETY → [GroupBusinessInsight.FullInsight] (FSCA prudential standard)
 *  - EMERGENCY_FUND → [GroupBusinessInsight.FullInsight] (SA 6-month coverage standard)
 *  - COMMUNITY_SAVINGS → [GroupBusinessInsight.FullInsight]
 *  - TONTINE        → [GroupBusinessInsight.FullInsight] (mortality-adjusted survivor benefit)
 *  - OTHER          → [GroupBusinessInsight.FullInsight] (generic composite)
 *
 * Legacy callers can keep using [invoke] for a direct answer. New callers should
 * prefer [evaluate] to receive a [Result] wrapper and explicit failure handling.
 */
class GetGroupBusinessInsightsUseCase @Inject constructor(
    private val roscaRotationUseCase: CalculateRoscaRotationUseCase,
    private val investmentValuationUseCase: CalculateInvestmentClubValuationUseCase,
    private val stokvelPayoutsUseCase: CalculateStokvelPayoutsUseCase,
    private val actuarialRepository: ActuarialRepository
) {

    sealed class GroupBusinessInsight {
        // ── Type-specific thin insight wrappers ──────────────────────────────
        data class Rosca(val schedule: CalculateRoscaRotationUseCase.RoscaSchedule) : GroupBusinessInsight()
        data class InvestmentClub(val valuation: CalculateInvestmentClubValuationUseCase.PortfolioValuation) : GroupBusinessInsight()
        data class Stokvel(val projection: CalculateStokvelPayoutsUseCase.PayoutProjection) : GroupBusinessInsight()

        /**
         * Full industry-standard insight for group types that use the actuarial engine:
         * BURIAL_SOCIETY, EMERGENCY_FUND, COMMUNITY_SAVINGS, TONTINE, OTHER.
         */
        data class FullInsight(val insight: GroupFinancialInsight) : GroupBusinessInsight()

        /**
         * Represents a successful computation that produced no actionable insight
         * (e.g. group has no members yet).  Distinct from an error.
         */
        data object Empty : GroupBusinessInsight()

        /**
         * Backward-compatible alias retained for older callers.
         */
        @Deprecated("Use Empty", ReplaceWith("Empty"))
        data object None : GroupBusinessInsight()
    }

    /**
     * Computes the appropriate insight for [group] given its [members].
     *
     * Returns a direct value for compatibility with existing UI and ViewModel
     * code that already expects a non-Result result.
     */
    operator fun invoke(group: Group, members: List<Member>): GroupBusinessInsight =
        evaluate(group, members).getOrElse { GroupBusinessInsight.Empty }

    /**
     * Result-based variant for callers that want explicit error handling.
     */
    fun evaluate(group: Group, members: List<Member>): Result<GroupBusinessInsight> {
        if (members.isEmpty()) return Result.success(GroupBusinessInsight.Empty)

        return when (group.type) {
            GroupType.ROSCA ->
                roscaRotationUseCase(group, members)
                    .map { GroupBusinessInsight.Rosca(it) }

            GroupType.INVESTMENT_CLUB ->
                investmentValuationUseCase(group, members)
                    .map { GroupBusinessInsight.InvestmentClub(it) }

            GroupType.STOKVEL ->
                stokvelPayoutsUseCase(group, members)
                    .map { GroupBusinessInsight.Stokvel(it) }

            // All remaining types use the full actuarial engine (synchronous pure function)
            GroupType.BURIAL_SOCIETY,
            GroupType.EMERGENCY_FUND,
            GroupType.COMMUNITY_SAVINGS,
            GroupType.TONTINE,
            GroupType.OTHER ->
                runCatching { actuarialRepository.computeGroupInsight(group, members) }
                    .map { GroupBusinessInsight.FullInsight(it) }
        }
    }
}
