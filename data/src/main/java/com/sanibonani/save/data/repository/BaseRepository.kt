package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.utils.logAndGetMessage
import io.ktor.client.plugins.ResponseException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Base class for all repositories in the data layer.
 * Standardizes offline-first synchronization, error handling, and retry logic.
 */
abstract class BaseRepository(protected val tag: String) {

    /**
     * Runs a financial mutation once against its authoritative server-side transaction.
     * The caller owns idempotency through a stable transaction key; blindly retrying an
     * ambiguous timeout could otherwise post the same ledger movement twice.
     */
    protected suspend fun <T> authoritativeCall(
        operation: String,
        block: suspend () -> T
    ): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        AppLogger.e(tag, "Authoritative operation '$operation' failed: ${e.logAndGetMessage(tag)}")
        Result.failure(e)
    }

    /**
     * Standard offline-first synchronization pattern.
     * 1. Returns local DB data immediately as a Flow.
     * 2. Simultaneously fetches from network.
     * 3. Syncs network results back to the local DB.
     */
    protected fun <T, E> observeAndSync(
        dbFlow: Flow<List<E>>,
        mapper: (E) -> T,
        toEntity: (T) -> E,
        networkFetch: suspend () -> List<T>,
        cacheSync: suspend (List<E>) -> Unit
    ): Flow<Result<List<T>>> = channelFlow {
        // Observe local database
        val dbJob = launch {
            dbFlow.collect { entities ->
                send(Result.success(entities.map(mapper)))
            }
        }

        // Sync from network
        try {
            val remoteData = networkFetch()
            cacheSync(remoteData.map(toEntity))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e(tag, "Sync failed: ${e.logAndGetMessage(tag)}")
        }

        awaitClose { dbJob.cancel() }
    }

    /**
     * Variant of observeAndSync for a single item.
     *
     * toEntity may return null when the remote row does not exist: an absent item is not a
     * failed sync, so nothing is cached and the local copy simply stays as it is.
     */
    protected fun <T, E> observeAndSyncItem(
        dbFlow: Flow<E?>,
        mapper: (E) -> T,
        toEntity: (T) -> E?,
        networkFetch: suspend () -> T,
        cacheSync: suspend (E) -> Unit
    ): Flow<Result<T?>> = channelFlow {
        val dbJob = launch {
            dbFlow.collect { entity ->
                send(Result.success(entity?.let(mapper)))
            }
        }

        try {
            val remoteData = networkFetch()
            toEntity(remoteData)?.let { cacheSync(it) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e(tag, "Item sync failed: ${e.logAndGetMessage(tag)}")
        }

        awaitClose { dbJob.cancel() }
    }

    /**
     * Observe remote data only (no local caching).
     */
    protected fun <T> observeRemoteOnly(
        networkFetch: suspend () -> List<T>
    ): Flow<Result<List<T>>> = flow {
        try {
            emit(Result.success(networkFetch()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.failure(e))
        }
    }

    /**
     * Standardized retry logic with exponential backoff.
     */
    protected suspend fun <T> retryWithExponentialBackoff(
        maxRetries: Int = 3,
        initialDelay: Long = 1000L,
        block: suspend () -> T
    ): Result<T> {
        var currentDelay = initialDelay
        repeat(maxRetries) { attempt ->
            try {
                return Result.success(block())
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                val isRecoverable = e is io.ktor.client.plugins.HttpRequestTimeoutException ||
                                   e is java.net.SocketTimeoutException ||
                                   e is IOException

                if (attempt == maxRetries - 1 || !isRecoverable) return Result.failure(e)

                AppLogger.w(tag, "Attempt ${attempt + 1} failed, retrying in $currentDelay ms... (Recoverable: $isRecoverable)")
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return Result.failure(Exception("Retry failed after $maxRetries attempts"))
    }

    /**
     * Safely fetches data trying primary columns first, then fallback columns if schema is out of sync.
     */
    protected suspend fun <T> safeColumnFetch(
        primaryColumns: String,
        fallbackColumns: String,
        block: suspend (String) -> T
    ): T {
        return try {
            block(primaryColumns)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val msg = e.message.orEmpty()
            if (msg.contains("column", ignoreCase = true) && msg.contains("does not exist", ignoreCase = true)) {
                AppLogger.w(tag, "Schema missing extended columns; using fallback projection.")
                block(fallbackColumns)
            } else {
                throw e
            }
        }
    }

    /**
     * Executes an API call safely, auto-queuing to the offline outbox on failure.
     */
    protected suspend fun <T> safeApiCall(
        operationType: String,
        groupId: String?,
        payload: String,
        dao: com.sanibonani.save.data.local.PendingOperationDao,
        block: suspend () -> T
    ): Result<T> = safeRunCatching("$tag.$operationType") {
        block()
    }.onFailure { e ->
        if (e is CancellationException) throw e

        val isRecoverable = e is IOException ||
                           (e is ResponseException && e.response.status.value >= 500)

        if (isRecoverable) {
            AppLogger.w(tag, "$operationType queued for retry.")
            dao.upsert(com.sanibonani.save.data.local.PendingOperationEntity(
                id = java.util.UUID.randomUUID().toString(),
                type = operationType,
                payload = payload,
                groupId = groupId,
                createdAt = System.currentTimeMillis()
            ))
        } else {
            AppLogger.e(tag, "$operationType failed with a permanent error; not queued.")
        }
    }
}
