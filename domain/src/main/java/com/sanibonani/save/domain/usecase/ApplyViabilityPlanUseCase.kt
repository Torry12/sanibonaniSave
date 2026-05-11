package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.ViabilityPlan
import com.sanibonani.save.domain.repository.GroupRepository
import javax.inject.Inject

/**
 * Applies a suggested actuarial viability plan to a group's configuration.
 */
class ApplyViabilityPlanUseCase @Inject constructor(
    private val groupRepo: GroupRepository
) {
    suspend operator fun invoke(groupId: String, plan: ViabilityPlan): Result<Unit> {
        val updates = mapOf(
            "monthly_contribution" to plan.suggestedMonthlyContribution,
            "goal_amount" to plan.goalAmount,
            "period_months" to plan.periodMonths
        )
        return groupRepo.updateGroupSettings(groupId, updates)
    }
}
