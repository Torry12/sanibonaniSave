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
    ): Result<ViabilityPlan> {
        return try {
            actuarialRepository.calculateViabilityPlan(groupId, goalAmount, periodMonths)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
