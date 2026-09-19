package com.sanibonani.save.domain.repository.system

import kotlinx.coroutines.flow.StateFlow

/**
 * A portable protocol for managing database connections across different providers.
 * Decouples the application logic from specific database implementation details.
 */
interface DataConnectionProtocol {
    /** Reactive stream of the current remote database status. */
    val remoteStatus: StateFlow<ConnectionStatus>

    /** Reactive stream of the current local database status. */
    val localStatus: StateFlow<ConnectionStatus>

    /** Triggers the initialization of both remote and local connections. */
    suspend fun initialize(): Result<Unit>

    /** Gracefully shuts down all active connections. */
    suspend fun shutdown(): Result<Unit>

    /** Forces a rebuild of connections (e.g., when switching from Local to Online mode). */
    suspend fun rebuild(): Result<Unit>

    /**
     * Provides access to the provider-specific remote client instance.
     * Use with caution to maintain portability.
     */
    fun <T : Any> getRemoteClient(clazz: Class<T>): T?

    /**
     * Provides access to the provider-specific local database instance.
     */
    fun <T : Any> getLocalDatabase(clazz: Class<T>): T?
}

sealed class ConnectionStatus {
    object Idle : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Connected : ConnectionStatus()
    data class Error(val message: String, val throwable: Throwable? = null) : ConnectionStatus()
}
