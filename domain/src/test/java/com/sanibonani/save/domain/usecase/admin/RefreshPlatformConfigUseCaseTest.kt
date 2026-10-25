package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.repository.admin.PlatformConfigRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins how the published fee settings reach the in-memory config billing reads:
 * a good read republishes every fee, a bad read changes nothing.
 */
class RefreshPlatformConfigUseCaseTest {

    private val platformRepository: PlatformRepository = mockk()
    private val configRepository: PlatformConfigRepository = mockk(relaxed = true)
    private val useCase = RefreshPlatformConfigUseCase(platformRepository, configRepository)

    private val published = PlatformConfig(
        monthlyMemberFee = Money("25.00"),
        payoutFee = Money("7.50"),
        shortfallRecoveryFee = Money("35.00"),
        autoSuspensionDays = 45
    )

    @Test
    fun `a successful read publishes every fee`() = runBlocking {
        coEvery { platformRepository.getPlatformSettings() } returns Result.success(published)

        val result = useCase()

        assertEquals(published, result.getOrThrow())
        verify(exactly = 1) { configRepository.hydrate(published) }
    }

    @Test
    fun `a failed read leaves the last known config in place`() = runBlocking {
        coEvery { platformRepository.getPlatformSettings() } returns
            Result.failure(IllegalStateException("offline"))

        val result = useCase()

        assertTrue(result.isFailure)
        verify(exactly = 0) { configRepository.hydrate(any()) }
    }

    @Test
    fun `a failed read surfaces the original error`() = runBlocking {
        val cause = IllegalStateException("HTTP 503 Service Unavailable")
        coEvery { platformRepository.getPlatformSettings() } returns Result.failure(cause)

        assertEquals(cause, useCase().exceptionOrNull())
    }
}
