package com.sanibonani.save.domain.utils

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeResultExtensionsCancellationTest {

    private val cancellation = CancellationException("scope cancelled")

    @Test(expected = CancellationException::class)
    fun safeRunCatchingRethrowsCancellation() {
        safeRunCatching<Unit> { throw cancellation }
    }

    @Test(expected = CancellationException::class)
    fun safeCallRethrowsCancellation() {
        safeCall<Unit> { throw cancellation }
    }

    @Test(expected = CancellationException::class)
    fun flatMapRethrowsCancellationFromTransform() {
        Result.success(1).flatMap<Int, Int> { throw cancellation }
    }

    @Test(expected = CancellationException::class)
    fun onSuccessSafeRethrowsCancellation() {
        Result.success(1).onSuccessSafe { throw cancellation }
    }

    @Test(expected = CancellationException::class)
    fun onFailureSafeRethrowsCancellation() {
        Result.failure<Int>(IllegalStateException("boom")).onFailureSafe { throw cancellation }
    }

    @Test
    fun safeRunCatchingConvertsOrdinaryFailures() {
        val result = safeRunCatching<Int> { error("boom") }

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun safeCallReturnsValueOnSuccess() {
        assertEquals(42, safeCall { 42 }.getOrThrow())
    }
}
