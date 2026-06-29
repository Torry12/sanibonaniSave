package com.sanibonani.save.data.repository

import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.utils.logAndGetMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext

abstract class BaseRepository(protected val tag: String) {
    /**
     * Standardized offline-first observation logic for lists.
     * 1. Emits cached data immediately (if available).
     * 2. Triggers a network fetch and updates the cache.
     * 3. Continuously observes the local database for changes from the sync.
     */
    protected fun <T, E> observeAndSync(
        dbFlow: Flow<List<E>>,
        mapper: (E) -> T,
        toEntity: (T) -> E,
        networkFetch: suspend () -> List<T>,
        cacheSync: suspend (List<E>) -> Unit
    ): Flow<Result<List<T>>> = channelFlow {
        var hasEmittedFromDb = false

        val dbJob = launch {
            dbFlow.collect { list ->
                if (list.isNotEmpty()) {
                    hasEmittedFromDb = true
                }
                runCatching { list.map(mapper) }
                    .onSuccess { send(Result.success(it)) }
                    .onFailure { e ->
                        val userMsg = e.logAndGetMessage(tag)
                        AppLogger.e(tag, "Local data mapping failed: $userMsg")
                        send(Result.failure(e))
                    }
            }
        }

        launch {
            try {
                AppLogger.d(tag, "Starting network sync...")
                val remoteData = retryWithExponentialBackoff { networkFetch() }
                AppLogger.d(tag, "Network fetch completed, syncing ${remoteData.size} items to cache")
                cacheSync(remoteData.map { toEntity(it) })
                AppLogger.d(tag, "Cache sync completed")
                } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                val userMsg = e.logAndGetMessage(tag)
                AppLogger.e(tag, "Sync failed: $userMsg")
                if (!hasEmittedFromDb) {
                    send(Result.failure(e))
                }
            }
        }

        awaitClose {
            dbJob.cancel()
        }
    }.flowOn(Dispatchers.IO)

    protected fun <T, E> observeAndSyncItem(
        dbFlow: Flow<E?>,
        mapper: (E) -> T,
        toEntity: (T) -> E,
        networkFetch: suspend () -> T,
        cacheSync: suspend (E) -> Unit
    ): Flow<Result<T?>> = channelFlow {
        var hasEmittedFromDb = false
        var hasEmittedNull = false

        val dbJob = launch {
            dbFlow.collect { item ->
                if (item != null) {
                    hasEmittedFromDb = true
                    hasEmittedNull = false
                    runCatching { mapper(item) }
                        .onSuccess { send(Result.success(it)) }
                        .onFailure { e ->
                            val userMsg = e.logAndGetMessage(tag)
                            AppLogger.e(tag, "Local item mapping failed: $userMsg")
                            send(Result.failure(e))
                        }
                } else if (!hasEmittedFromDb && !hasEmittedNull) {
                    hasEmittedNull = true
                    send(Result.success(null))
                }
            }
        }

        launch {
            try {
                val remoteData = retryWithExponentialBackoff { networkFetch() }
                cacheSync(toEntity(remoteData))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                val userMsg = e.logAndGetMessage(tag)
                AppLogger.e(tag, "Item sync failed: $userMsg")
                if (!hasEmittedFromDb) {
                    send(Result.failure(e))
                }
            }
        }

        awaitClose {
            dbJob.cancel()
        }
    }.flowOn(Dispatchers.IO)
    
    protected suspend fun <T> retryWithExponentialBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 30_000L,
        block: suspend () -> T
    ): T = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        var delayMs = initialDelayMs
        
        repeat(maxRetries) { attempt ->
            try {
                return@withContext block()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastException = e
                if (attempt < maxRetries - 1) {
                    val userMsg = e.logAndGetMessage(tag)
                    AppLogger.d(tag, "Retry attempt ${attempt + 1}/$maxRetries after ${delayMs}ms: $userMsg")
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
                }
            }
        }
        
        throw lastException ?: Exception("Failed after $maxRetries retry attempts")
    }
}
