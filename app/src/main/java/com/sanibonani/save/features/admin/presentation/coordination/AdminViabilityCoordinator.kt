package com.sanibonani.save.features.admin.presentation.coordination

import com.sanibonani.save.domain.model.ActuarialSettings
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.usecase.actuarial.ApplyViabilityPlanUseCase
import com.sanibonani.save.domain.usecase.actuarial.CalculateViabilityUseCase
import com.sanibonani.save.domain.usecase.actuarial.ViabilityUtils
import javax.inject.Inject

class AdminViabilityCoordinator @Inject constructor(
    private val calculateViability: CalculateViabilityUseCase,
    private val applyViabilityPlan: ApplyViabilityPlanUseCase
) {

    fun calculate(
        host: AdminActionHost,
        goalAmount: String,
        periodMonths: String,
        actuarialSettings: ActuarialSettings
    ) {
        val group = host.adminState.value.group ?: return
        val groupId = group.id ?: return
        val months = periodMonths.toIntOrNull()?.takeIf { it > 0 }
            ?: ViabilityUtils.computeDefaultPeriod(group)

        val parsedAmount = goalAmount
            .takeIf { it.isNotBlank() && it.toBigDecimalOrNull() != null }
            ?.let { Money(it) }
        val effectiveGoal = if (parsedAmount != null && parsedAmount.isPositive) {
            parsedAmount
        } else {
            ViabilityUtils.computeDefaultGoal(group)
        }

        host.launchAdminSafeJob("calculate_viability", "Failed to calculate viability") {
            host.updateAdminState { it.copy(isCalculatingViability = true) }
            host.runWithLoading {
                calculateViability(groupId, effectiveGoal, months, actuarialSettings)
                    .onSuccess { plan ->
                        host.updateAdminState { it.copy(isCalculatingViability = false, viabilityPlan = plan) }
                    }
                    .onFailure { e ->
                        host.updateAdminState { it.copy(isCalculatingViability = false) }
                        host.reportError(e)
                    }
            }
        }
    }

    fun apply(host: AdminActionHost) {
        val groupId = host.adminState.value.group?.id ?: return
        val plan = host.adminState.value.viabilityPlan ?: return

        host.launchAdminSafeJob("apply_viability", "Failed to apply viability plan") {
            host.updateAdminState { it.copy(isLoading = true) }
            host.runWithLoading {
                applyViabilityPlan(groupId, plan)
                    .onSuccess {
                        host.updateAdminState { it.copy(isLoading = false, viabilityPlan = null) }
                    }
                    .onFailure { e ->
                        host.updateAdminState { it.copy(isLoading = false) }
                        host.reportError(e)
                    }
            }
        }
    }
}
