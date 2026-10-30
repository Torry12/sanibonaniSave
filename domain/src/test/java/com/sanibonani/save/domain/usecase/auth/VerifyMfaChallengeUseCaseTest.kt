package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.model.MfaFactor
import com.sanibonani.save.domain.service.MfaService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the MFA challenge rules the admin dashboard used to hold: the first enrolled
 * factor takes the code, and an account with no factor is a distinct, actionable failure.
 */
class VerifyMfaChallengeUseCaseTest {

    private val mfaService: MfaService = mockk()
    private val useCase = VerifyMfaChallengeUseCase(mfaService)

    private val factor = MfaFactor(
        id = "factor-1",
        friendlyName = "Authenticator",
        type = "totp",
        status = "verified"
    )

    @Test
    fun `the first enrolled factor verifies the code and saves the challenge`() = runTest {
        coEvery { mfaService.getMfaFactors() } returns Result.success(listOf(factor))
        coEvery { mfaService.verifyMfa(any(), any(), any()) } returns Result.success(Unit)

        val result = useCase("123456")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mfaService.verifyMfa("factor-1", "123456", true) }
    }

    @Test
    fun `an account with no enrolled factor is told to set up two-step verification`() = runTest {
        coEvery { mfaService.getMfaFactors() } returns Result.success(emptyList())
        coEvery { mfaService.verifyMfa(any(), any(), any()) } returns Result.success(Unit)

        val result = useCase("123456")

        assertEquals(
            "No authenticator is enrolled on this account. Set up two-step verification before authorising this action.",
            result.exceptionOrNull()?.message
        )
        coVerify(exactly = 0) { mfaService.verifyMfa(any(), any(), any()) }
    }

    @Test
    fun `a factor lookup failure is surfaced instead of claiming there is no factor`() = runTest {
        val cause = IllegalStateException("network unavailable")
        coEvery { mfaService.getMfaFactors() } returns Result.failure(cause)

        assertEquals(cause, useCase("123456").exceptionOrNull())
        coVerify(exactly = 0) { mfaService.verifyMfa(any(), any(), any()) }
    }

    @Test
    fun `a rejected code keeps the verification error`() = runTest {
        val cause = IllegalStateException("Invalid TOTP code")
        coEvery { mfaService.getMfaFactors() } returns Result.success(listOf(factor))
        coEvery { mfaService.verifyMfa(any(), any(), any()) } returns Result.failure(cause)

        assertEquals(cause, useCase("000000").exceptionOrNull())
    }
}
