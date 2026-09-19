package com.sanibonani.save.features.common.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.system.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _isConnecting = MutableStateFlow(true)
    val isConnecting = _isConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError = _connectionError.asStateFlow()

    init {
        checkConnection()
    }

    fun checkConnection() {
        viewModelScope.launch {
            _isConnecting.value = true
            _connectionError.value = null

            val hasAuthenticatedSession = supabaseRepo.isLoggedIn
            val maxAttempts = 3
            val perAttemptTimeoutMs = 15_000L

            for (attempt in 1..maxAttempts) {
                val result = withTimeoutOrNull(perAttemptTimeoutMs) {
                    runCatching {
                        if (supabaseRepo.supabaseUrl.isBlank()) throw Exception("No Supabase URL configured")

                        val url = supabaseRepo.supabaseUrl
                        if (!url.contains("supabase.co") && !url.contains("127.0.0.1") && !url.contains("localhost")) {
                            throw Exception("Invalid Supabase host: $url")
                        }

                        val isOnline = withContext(Dispatchers.IO) {
                            try {
                                if (url.contains("127.0.0.1") || url.contains("localhost")) return@withContext true
                                val host = url.removePrefix("https://").removePrefix("http://").split("/")[0]

                                val socket = java.net.Socket()
                                try {
                                    socket.connect(java.net.InetSocketAddress(host, 443), 1500)
                                    true
                                } finally {
                                    runCatching { socket.close() }
                                }
                            } catch (e: Exception) {
                                false
                            }
                        }

                        if (!isOnline && attempt == maxAttempts && hasAuthenticatedSession) {
                            throw Exception("Network unreachable (DNS failure). Please check your internet.")
                        }
                        if (!isOnline && hasAuthenticatedSession) throw Exception("Waiting for DNS...")

                        true
                    }
                }

                if (result != null && result.isSuccess) {
                    if (hasAuthenticatedSession) {
                        syncRepository.syncAllData()
                    }
                    _isConnecting.value = false
                    return@launch
                }

                if (attempt < maxAttempts) {
                    kotlinx.coroutines.delay(2000)
                } else {
                    _connectionError.value = result?.exceptionOrNull()?.message ?: "Connection timed out"
                }
            }
            _isConnecting.value = false
        }
    }
}
