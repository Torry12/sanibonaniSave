package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.PayoutRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProcessPayoutUseCaseTest {

    private lateinit var processPayoutUseCase: ProcessPayoutUseCase
    private val payoutRepository: PayoutRepository = mockk()
    private val notificationRepository: NotificationRepository = mockk()

    @Before
    fun setUp() {
        processPayoutUseCase = ProcessPayoutUseCase(
            payoutRepository,
            notificationRepository
        )
    }

    @Test
    fun `invoke with COMPLETED status calls completePayoutAtomic`() = runBlocking {
        // Given
        val payoutId = "p1"
        val groupId = "g1"
        val payout = PayoutRequest(id = payoutId, groupId = groupId, amount = 1000.0, bankName = "FNB", accountNo = "123", branchCode = "456")
        
        coEvery { payoutRepository.getPayoutById(payoutId) } returns Result.success(payout)
        coEvery { payoutRepository.completePayoutAtomic(payoutId, "PLATFORM_ADMIN", null) } returns Result.success(Unit)
        coEvery { notificationRepository.sendNotification(any()) } returns Result.success(Unit)

        // When
        val result = processPayoutUseCase(payoutId, groupId, PayoutStatus.COMPLETED, "PLATFORM_ADMIN")

        // Then
        assertTrue(result.isSuccess)
        coVerify { payoutRepository.completePayoutAtomic(payoutId, "PLATFORM_ADMIN", null) }
        coVerify { notificationRepository.sendNotification(any()) }
    }

    @Test
    fun `invoke with PROCESSING status calls updatePayoutStatus`() = runBlocking {
        // Given
        val payoutId = "p1"
        val groupId = "g1"
        val payout = PayoutRequest(id = payoutId, groupId = groupId, amount = 1000.0, bankName = "FNB", accountNo = "123", branchCode = "456")

        coEvery { payoutRepository.getPayoutById(payoutId) } returns Result.success(payout)
        coEvery { payoutRepository.updatePayoutStatus(payoutId, PayoutStatus.PROCESSING, null) } returns Result.success(Unit)
        coEvery { notificationRepository.sendNotification(any()) } returns Result.success(Unit)

        // When
        val result = processPayoutUseCase(payoutId, groupId, PayoutStatus.PROCESSING, "PLATFORM_ADMIN")

        // Then
        assertTrue(result.isSuccess)
        coVerify { payoutRepository.updatePayoutStatus(payoutId, PayoutStatus.PROCESSING, null) }
        coVerify(exactly = 0) { payoutRepository.completePayoutAtomic(any(), any(), any()) }
    }

    @Test
    fun `invoke fails if getPayoutById fails for COMPLETED status`() = runBlocking {
        // Given
        val payoutId = "p1"
        val groupId = "g1"

        coEvery { payoutRepository.getPayoutById(payoutId) } returns Result.failure(Exception("Not found"))

        // When
        val result = processPayoutUseCase(payoutId, groupId, PayoutStatus.COMPLETED, "PLATFORM_ADMIN")

        // Then
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.updatePayoutStatus(any(), any(), any()) }
        coVerify(exactly = 0) { payoutRepository.completePayoutAtomic(any(), any(), any()) }
    }
}
