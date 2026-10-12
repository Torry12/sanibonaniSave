package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.ViabilityPlan
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculateViabilityUseCaseTest {

    private lateinit var calculateViabilityUseCase: CalculateViabilityUseCase
    private val actuarialRepository: ActuarialRepository = mockk()

    @Before
    fun setUp() {
        calculateViabilityUseCase = CalculateViabilityUseCase(actuarialRepository)
    }

    private fun createTestPlan(goalAmount: Money, periodMonths: Int) = ViabilityPlan(
        suggestedMonthlyContribution = Money(250.0),
        initialContribution = Money(1000.0),
        projectedValue = Money(50000.0),
        isViable = true,
        goalAmount = goalAmount,
        periodMonths = periodMonths,
        messages = emptyList(),
        requiredMonthlyToMeetGoal = Money(2000.0),
        shortfallAmount = Money(0.0),
        breakEvenMonths = 12,
        compoundedProjectedValue = Money(55000.0),
        optimisticProjectedValue = Money(60000.0),
        pessimisticProjectedValue = Money(45000.0),
        activeMemberRatio = 1.0,
        inflationAdjustmentFactor = 1.0,
        projectionRetentionFactor = 1.0,
        claimReadinessFactor = 1.0,
        mortalityBufferFactor = 1.0,
        reserveAdequacyFactor = 1.0,
        marketReturnPremiumFactor = 1.0,
        volatilityHaircutFactor = 1.0,
        collectionEfficiencyFactor = 1.0,
        festivePayoutPressureFactor = 1.0,
        defaultRiskFactor = 1.0,
        cycleSlippageFactor = 1.0,
        withdrawalPressureFactor = 1.0,
        inflationSafetyFactor = 1.0,
        survivorUncertaintyFactor = 1.0,
        horizonCompoundingFactor = 1.0,
        goalStretchRatio = 1.0,
        growthConservatismFactor = 1.0
    )

    @Test
    fun `invoke should return viability plan from repository`() = runBlocking {
        // Given
        val groupId = "group-123"
        val goalAmount = Money(50000.0)
        val periodMonths = 24
        val expectedPlan = createTestPlan(goalAmount, periodMonths)

        coEvery {
            actuarialRepository.calculateViabilityPlan(groupId, goalAmount, periodMonths)
        } returns Result.success(expectedPlan)

        // When
        val result = calculateViabilityUseCase(groupId, goalAmount, periodMonths)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedPlan, result.getOrNull())
    }

    @Test
    fun `invoke should return failure when repository fails`() = runBlocking {
        // Given
        val groupId = "group-123"
        val exception = Exception("Calculation failed")
        coEvery {
            actuarialRepository.calculateViabilityPlan(any(), any(), any())
        } returns Result.failure(exception)

        // When
        val result = calculateViabilityUseCase(groupId, Money(10000.0), 12)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
