package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.ActuarialSettings
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.ViabilityPlan
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Orchestrates the calculation of group viability and financial planning.
 */
class CalculateViabilityUseCase @Inject constructor(
    private val actuarialRepository: ActuarialRepository
) {
    suspend operator fun invoke(
        groupId: String,
        goalAmount: Money,
        periodMonths: Int,
        actuarialSettings: ActuarialSettings = ActuarialSettings()
    ): Result<ViabilityPlan> = safeRunCatching {
        require(goalAmount.isPositive) { "Goal must be > 0" }
        require(periodMonths > 0) { "Period must be > 0" }
        actuarialRepository.calculateViabilityPlan(groupId, goalAmount, periodMonths, actuarialSettings).getOrThrow()
    }
}
