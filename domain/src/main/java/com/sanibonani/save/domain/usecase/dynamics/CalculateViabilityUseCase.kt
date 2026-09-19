package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.ViabilityPlan
import com.sanibonani.save.domain.repository.dynamics.ActuarialRepository
import javax.inject.Inject

/**
 * Orchestrates the calculation of group viability and financial planning.
 */
class CalculateViabilityUseCase @Inject constructor(
    private val actuarialRepository: ActuarialRepository
) {
    suspend operator fun invoke(
        groupId: String,
        goalAmount: Money,
        periodMonths: Int
    ): Result<ViabilityPlan> = runCatching {
        require(goalAmount.isPositive) { "Goal must be > 0" }
        require(periodMonths > 0) { "Period must be > 0" }
        actuarialRepository.calculateViabilityPlan(groupId, goalAmount, periodMonths).getOrThrow()
    }
}
