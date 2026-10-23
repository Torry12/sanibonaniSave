package com.sanibonani.save.features.common.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.logging.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.BuildConfig
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.system.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlinx.coroutines.delay

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _isConnecting = MutableStateFlow(true)
    val isConnecting = _isConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError = _connectionError.asStateFlow()

    val syncStatus = syncRepository.syncStatus

    private var connectionJob: Job? = null

    @Volatile
    private var backgroundSyncStarted = false

    init {
        checkConnection()
    }

    fun checkConnection() {
        // Cancel any in-flight check so repeated retry taps cannot stack coroutines.
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            _isConnecting.value = true
            _connectionError.value = null

            val startTime = System.currentTimeMillis()
            val minDisplayDurationMs = 3000L // 3s branding display

            val hasAuthenticatedSession = supabaseRepo.isLoggedIn

            // Start background data sync immediately in parallel
            if (hasAuthenticatedSession && !backgroundSyncStarted) {
                backgroundSyncStarted = true
                viewModelScope.launch(Dispatchers.IO) {
                    safeRunCatching { syncRepository.syncAllData() }
                }
            }

            val url = BuildConfig.SUPABASE_URL_ONLINE.trim().removeSurrounding("\"")
            val isReachable = withContext(Dispatchers.IO) {
                try {
                    val uri = URI(url)
                    val host = uri.host ?: "google.com"
                    val port = if (uri.port != -1) uri.port else 443

                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 3000)
                    }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w("SplashViewModel", "Connectivity check skipped/failed: ${e.message}")
                    hasAuthenticatedSession
                }
            }

            if (!isReachable) {
                // Offline-first startup continues with the local Room cache. Network recovery
                // and outbox synchronization happen after the UI is available.
                AppLogger.i("SplashViewModel", "Network unavailable; continuing in offline mode")
            } else {
                AppLogger.i("SplashViewModel", "Connection verified successfully")
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            val remainingTime = minDisplayDurationMs - elapsedTime
            if (remainingTime > 0) {
                delay(remainingTime)
            }

            _isConnecting.value = false
        }
    }
}
