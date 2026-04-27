package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.ViabilityPlan
import com.sanibonani.save.domain.repository.ActuarialRepository
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

    @Test
    fun `invoke should return viability plan from repository`() = runBlocking {
        // Given
        val groupId = "group-123"
        val goalAmount = 50000.0
        val periodMonths = 24
        val expectedPlan = ViabilityPlan(
            suggestedMonthlyContribution = 250.0,
            initialContribution = 1000.0,
            projectedValue = 50000.0,
            isViable = true,
            goalAmount = goalAmount,
            periodMonths = periodMonths
        )

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
        val result = calculateViabilityUseCase(groupId, 10000.0, 12)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
