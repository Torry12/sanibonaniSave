package com.sanibonani.save.data.remote

import com.sanibonani.save.data.local.LocalDatabaseProvider
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.data.utils.DevSettings
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import com.sanibonani.save.domain.repository.system.ConnectionStatus
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.utils.toUserMessage

/**
 * Consolidated manager for Supabase and Room connections.
 * Implements DataConnectionProtocol for dynamic switching.
 */
@Singleton
class SupabaseConnectionManager @Inject constructor(
    private val clientProvider: RemoteClientProvider,
    private val dbProvider: LocalDatabaseProvider,
    private val devSettings: DevSettings
) : DataConnectionProtocol {
    private val tag = "SupabaseConnMgr"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentClient = MutableStateFlow<SupabaseClient?>(null)
    val currentClient: StateFlow<SupabaseClient?> = _currentClient.asStateFlow()

    private val _currentDatabase = MutableStateFlow<SanibonaniDatabase?>(null)
    val currentDatabase: StateFlow<SanibonaniDatabase?> = _currentDatabase.asStateFlow()

    private val _remoteStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    override val remoteStatus: StateFlow<ConnectionStatus> = _remoteStatus.asStateFlow()

    private val _localStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    override val localStatus: StateFlow<ConnectionStatus> = _localStatus.asStateFlow()

    init {
        rebuildConnections()
        observeSettings()
    }

    private fun observeSettings() {
        scope.launch {
            devSettings.settingsChanged.collect {
                rebuildConnections()
            }
        }
    }

    override suspend fun initialize(): Result<Unit> = safeRunCatching {
        rebuildConnections()
    }

    override suspend fun shutdown(): Result<Unit> = safeRunCatching {
        _currentDatabase.value?.close()
        _currentClient.value?.close()
        _remoteStatus.value = ConnectionStatus.Idle
        _localStatus.value = ConnectionStatus.Idle
    }

    override suspend fun rebuild(): Result<Unit> = safeRunCatching {
        rebuildConnections()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getRemoteClient(clazz: Class<T>): T? {
        val client = _currentClient.value
        return if (clazz.isInstance(client)) client as T else null
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getLocalDatabase(clazz: Class<T>): T? {
        val db = _currentDatabase.value
        return if (clazz.isInstance(db)) db as T else null
    }

    fun rebuildConnections() {
        val useOnline = devSettings.useOnlineDatabase
        AppLogger.i(tag, "🔄 Rebuilding connections. Online: $useOnline")

        _remoteStatus.value = ConnectionStatus.Connecting
        _localStatus.value = ConnectionStatus.Connecting

        try {
            // Close old instances in background to avoid blocking main thread
            val oldClient = _currentClient.value
            val oldDb = _currentDatabase.value
            scope.launch(Dispatchers.IO) {
                safeRunCatching { oldClient?.close() }
                safeRunCatching { oldDb?.close() }
            }

            _currentClient.value = clientProvider.createClient(useOnline)
            _remoteStatus.value = ConnectionStatus.Connected

            _currentDatabase.value = dbProvider.createDatabase(useOnline)
            _localStatus.value = ConnectionStatus.Connected

            AppLogger.i(tag, "✅ Connections rebuilt.")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = if (useOnline) "Server unreachable. Please check your internet." else e.toUserMessage()
            _remoteStatus.value = ConnectionStatus.Error(msg, e)
            _localStatus.value = ConnectionStatus.Error(msg, e)
            AppLogger.e(tag, "❌ Connection rebuild failed", e)
        }
    }

    fun getClient(): SupabaseClient = _currentClient.value ?: throw IllegalStateException("SupabaseClient not initialized")
    fun getDatabase(): SanibonaniDatabase = _currentDatabase.value ?: throw IllegalStateException("SanibonaniDatabase not initialized")
}
