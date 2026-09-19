package com.sanibonani.save.data.remote

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.utils.DevSettings
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseConnectionManager @Inject constructor(
    private val clientProvider: RemoteClientProvider,
    private val dbProvider: LocalDatabaseProvider,
    private val devSettings: DevSettings
) {
    private val tag = "SupabaseConnMgr"

    private val _currentClient = MutableStateFlow<SupabaseClient?>(null)
    val currentClient: StateFlow<SupabaseClient?> = _currentClient.asStateFlow()

    private val _currentDatabase = MutableStateFlow<SanibonaniDatabase?>(null)
    val currentDatabase: StateFlow<SanibonaniDatabase?> = _currentDatabase.asStateFlow()

    init {
        rebuildConnections()
    }

    fun rebuildConnections() {
        val useOnline = devSettings.useOnlineDatabase
        AppLogger.i(tag, "🔄 Rebuilding connections. Online: $useOnline")

        _currentClient.value = clientProvider.createClient(useOnline)

        _currentDatabase.value?.close()
        _currentDatabase.value = dbProvider.createDatabase(useOnline)

        AppLogger.i(tag, "✅ Connections rebuilt.")
    }

    fun getClient(): SupabaseClient = _currentClient.value ?: throw IllegalStateException("SupabaseClient not initialized")
    fun getDatabase(): SanibonaniDatabase = _currentDatabase.value ?: throw IllegalStateException("SanibonaniDatabase not initialized")
}
