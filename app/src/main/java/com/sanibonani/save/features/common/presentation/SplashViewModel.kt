package com.sanibonani.save.features.common.presentation

import com.sanibonani.save.domain.logging.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.usecase.auth.IsLoggedInUseCase
import com.sanibonani.save.domain.usecase.system.CheckBackendReachabilityUseCase
import com.sanibonani.save.domain.usecase.system.ObserveSyncStatusUseCase
import com.sanibonani.save.domain.usecase.system.SyncAllDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject
import kotlinx.coroutines.delay

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val isLoggedIn: IsLoggedInUseCase,
    private val observeSyncStatus: ObserveSyncStatusUseCase,
    private val syncAllData: SyncAllDataUseCase,
    private val checkBackendReachability: CheckBackendReachabilityUseCase
) : ViewModel() {

    private val _isConnecting = MutableStateFlow(true)
    val isConnecting = _isConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError = _connectionError.asStateFlow()

    val syncStatus = observeSyncStatus()

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

            val hasAuthenticatedSession = isLoggedIn()

            // Start background data sync immediately in parallel
            if (hasAuthenticatedSession && !backgroundSyncStarted) {
                backgroundSyncStarted = true
                viewModelScope.launch(Dispatchers.IO) {
                    syncAllData()
                }
            }

            val isReachable = checkBackendReachability(fallbackResult = hasAuthenticatedSession)

            if (!isReachable) {
                // The probe only answers false when there is also no session to fall back on, so this
                // device has neither the network nor cached money to open: the bootloader's retry
                // screen is the honest state. A returning member never lands here, because the probe
                // hands a signed-in app the "try anyway" fallback and offline startup continues.
                AppLogger.w("SplashViewModel", "Network unavailable with no local session; showing retry")
                _connectionError.value =
                    "Cannot reach the SanibonaniSave servers and no account is signed in on this device. Check your connection and try again."
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
