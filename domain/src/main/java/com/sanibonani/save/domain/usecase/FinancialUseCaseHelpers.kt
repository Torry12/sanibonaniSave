package com.sanibonani.save.domain.usecase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.sanibonani.save.domain.usecase.financialResult
import com.sanibonani.save.domain.usecase.firstValueOrNull

/**
 * Shared Result wrapper for financial use cases.
 *
 * Keeps cancellation propagation intact while avoiding repeated try/catch boilerplate.
 */
internal suspend inline fun <T> financialResult(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

internal suspend fun <T> Flow<Result<T>>.firstValueOrNull(): T? {
    return first().getOrNull()
}
