package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.repository.PayoutRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestPayoutUseCaseTest {

    private lateinit var requestPayoutUseCase: RequestPayoutUseCase
    private val payoutRepository: PayoutRepository = mockk()

    @Before
    fun setUp() {
        requestPayoutUseCase = RequestPayoutUseCase(payoutRepository)
    }

    @Test
    fun `invoke with valid inputs submits pending payout request`() = runBlocking {
        coEvery { payoutRepository.requestPayout(any()) } returns Result.success("payout-1")

        val result = requestPayoutUseCase(
            groupId = "group-1",
            amount = 1500.0,
            bankName = " FNB ",
            accountNo = " 123456789 ",
            branchCode = " 250655 "
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { payoutRepository.requestPayout(any<PayoutRequest>()) }
    }

    @Test
    fun `invoke with non-positive amount fails validation`() = runBlocking {
        val result = requestPayoutUseCase(
            groupId = "group-1",
            amount = 0.0,
            bankName = "FNB",
            accountNo = "123456789",
            branchCode = "250655"
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }

    @Test
    fun `invoke with invalid account number fails validation`() = runBlocking {
        val result = requestPayoutUseCase(
            groupId = "group-1",
            amount = 100.0,
            bankName = "FNB",
            accountNo = "12345",
            branchCode = "250655"
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }

    @Test
    fun `invoke with invalid branch code fails validation`() = runBlocking {
        val result = requestPayoutUseCase(
            groupId = "group-1",
            amount = 100.0,
            bankName = "FNB",
            accountNo = "123456789",
            branchCode = "25065"
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.requestPayout(any()) }
    }
}

