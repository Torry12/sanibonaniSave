package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.ViabilityPlan
import com.sanibonani.save.domain.repository.ActuarialRepository
import javax.inject.Inject

/**
 * Orchestrates the calculation of group viability and financial planning.
 */
class CalculateViabilityUseCase @Inject constructor(
    private val actuarialRepository: ActuarialRepository
) {
    suspend operator fun invoke(
        groupId: String,
        goalAmount: Double,
        periodMonths: Int
    ): Result<ViabilityPlan> = runCatching {
        require(goalAmount > 0) { "Goal amount must be greater than zero." }
        require(periodMonths > 0) { "Period must be at least 1 month." }
        actuarialRepository.calculateViabilityPlan(groupId, goalAmount, periodMonths).getOrThrow()
    }
}
