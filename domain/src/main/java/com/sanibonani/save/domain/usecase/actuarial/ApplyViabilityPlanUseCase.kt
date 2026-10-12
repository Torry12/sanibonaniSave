package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.ViabilityPlan
import com.sanibonani.save.domain.repository.group.GroupRepository
import javax.inject.Inject
class ApplyViabilityPlanUseCase @Inject constructor(
    private val groupRepo: GroupRepository
) {
    suspend operator fun invoke(groupId: String, plan: ViabilityPlan): Result<Unit> = runCatching {
        val updates = mapOf(
            "monthly_contribution" to plan.suggestedMonthlyContribution.amount.toPlainString(),
            "goal_amount" to plan.goalAmount.amount.toPlainString(),
            "period_months" to plan.periodMonths
        )
        groupRepo.updateGroupSettings(groupId, updates).getOrThrow()
    }
}
