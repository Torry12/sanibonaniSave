package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.ViabilityPlan
import com.sanibonani.save.domain.repository.GroupRepository
import javax.inject.Inject

class ApplyViabilityPlanUseCase @Inject constructor(
    private val groupRepo: GroupRepository
) {
    suspend operator fun invoke(groupId: String, plan: ViabilityPlan): Result<Unit> = runCatching {
        val updates = mapOf(
            "monthly_contribution" to plan.suggestedMonthlyContribution.amount.toDouble(),
            "goal_amount" to plan.goalAmount.amount.toDouble(),
            "period_months" to plan.periodMonths
        )
        groupRepo.updateGroupSettings(groupId, updates).getOrThrow()
    }
}
