package com.sanibonani.save

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.AppDateSync
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.repository.SyncRepository
import com.sanibonani.save.domain.repository.SyncStatus
import com.sanibonani.save.ui.navigation.SanibonaniNavGraph
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    val syncStatus = syncRepository.syncStatus

    init {
        checkConnection()
    }

    fun checkConnection() {
        viewModelScope.launch {
            _isConnecting.value = true
            _connectionError.value = null

            val startTime = System.currentTimeMillis()
            var lastError: String? = null
            var isConnected = false
            val hasAuthenticatedSession = supabaseRepo.currentUserId != null
            val maxAttempts = if (hasAuthenticatedSession) 3 else 1
            val perAttemptTimeoutMs = if (hasAuthenticatedSession) 10_000L else 3_000L
            val retryDelayMs = if (hasAuthenticatedSession) 1_500L else 0L

            // Keep startup fast for login/public forms; use stricter retries only for signed-in sync paths.
            for (attempt in 1..maxAttempts) {
                android.util.Log.d("SplashViewModel", "Connection attempt $attempt/$maxAttempts...")
                val result = withTimeoutOrNull(perAttemptTimeoutMs) {
                    runCatching {
                        if (supabaseRepo.supabaseUrl.isBlank()) throw Exception("No Supabase URL configured")

                        android.util.Log.d("SplashViewModel", "Pinging Supabase at ${supabaseRepo.supabaseUrl}")

                        val url = supabaseRepo.supabaseUrl
                        if (!url.contains("supabase.co") && !url.contains("127.0.0.1") && !url.contains("localhost")) {
                            throw Exception("Invalid Supabase host: $url")
                        }

                        // For signed-out flows, we don't need to block on a DNS gate.
                        // For signed-in flows, we try to reach the host but don't hard-fail if it takes too long
                        // unless we've exhausted all attempts.
                        val isOnline = withContext(Dispatchers.IO) {
                            try {
                                if (url.contains("127.0.0.1") || url.contains("localhost")) return@withContext true
                                val host = url.removePrefix("https://").removePrefix("http://").split("/")[0]
                                
                                // Optimized: Use a simple socket connection with short timeout
                                val socket = java.net.Socket()
                                try {
                                    socket.connect(java.net.InetSocketAddress(host, 443), 1500)
                                    true
                                } finally {
                                    runCatching { socket.close() }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SplashViewModel", "Network check failed for $url", e)
                                false
                            }
                        }

                        if (!isOnline && attempt == maxAttempts && hasAuthenticatedSession) {
                            // Only throw on the final attempt if we have a session we need to sync
                            throw Exception("Network unreachable (DNS failure). Please check your internet.")
                        }
                        if (!isOnline && hasAuthenticatedSession) throw Exception("Waiting for DNS...")
                        
                        // If we are here and not online, but don't have a session, just proceed
                        true
                    }
                }

                if (result != null) {
                    if (result.isSuccess) {
                        android.util.Log.i("SplashViewModel", "Connection successful on attempt $attempt")
                        isConnected = true
                        break
                    } else {
                        val exception = result.exceptionOrNull()
                        lastError = exception?.message ?: "Unknown error"
                        android.util.Log.e("SplashViewModel", "Attempt $attempt failed: $lastError")
                    }
                } else {
                    lastError = "Connection timeout (Attempt $attempt)"
                    android.util.Log.e("SplashViewModel", lastError)
                }

                if (attempt < maxAttempts) delay(retryDelayMs)
            }

            if (isConnected) {
                if (hasAuthenticatedSession) {
                    syncRepository.syncAllData()
                }

                val elapsedTime = System.currentTimeMillis() - startTime
                val remainingDelay = (500 - elapsedTime).coerceAtLeast(0L)
                delay(remainingDelay)
                _isConnecting.value = false
            } else {
                if (hasAuthenticatedSession) {
                    _connectionError.value = "Unable to connect to Sanibonani servers.\n\nTechnical Details: ${lastError ?: "No response"}\n\nPlease check your internet connection and try again."
                }
                // For signed-out users, allow app entry and show errors contextually in forms.
                _isConnecting.value = false
            }
        }
    }
}

@HiltViewModel
class StartupDataViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {
    private var preloaded = false

    fun preloadMapData() {
        if (preloaded) return
        preloaded = true

        viewModelScope.launch {
            // Warm public groups cache on app launch so map screens render faster.
            withTimeoutOrNull(7000) {
                runCatching { groupRepository.getPublicGroups().first() }
            }
        }
    }
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val startupDataViewModel: StartupDataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startupDataViewModel.preloadMapData()

        // Handle deep link from intent if app was opened via link
        intent?.data?.toString()?.let { url ->
            if (url.contains("reset-password")) {
                ToastUtils.showInfo(this, "Processing your password reset link...")
            }
            authViewModel.handleDeepLink(url)
        }

        setContent {
            SanibonaniTheme {
                SanibonaniNavGraph()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let { url ->
            if (url.contains("reset-password")) {
                ToastUtils.showInfo(this, "Processing your password reset link...")
            }
            authViewModel.handleDeepLink(url)
        }
    }

    override fun onResume() {
        super.onResume()
        // Sync date when app returns from background
        AppDateSync.syncOnResume(this)
    }

    override fun onPause() {
        super.onPause()
        // Sync date when app goes to background
        AppDateSync.syncOnExit(this)
    }

    override fun onStop() {
        super.onStop()
        // Additional sync when activity stops
        AppDateSync.syncOnExit(this)
    }
}

@Composable
fun BrandingSplashScreen(viewModel: SplashViewModel) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Forest, ForestMid))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            color = Gold.copy(alpha = 0.2f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🤝", fontSize = 48.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "SanibonaniSave",
            style = MaterialTheme.typography.displaySmall,
            color = Gold,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        
        val statusText = when (val status = syncStatus) {
            is SyncStatus.Progress -> status.message
            is SyncStatus.Error -> "Sync error: ${status.message}"
            is SyncStatus.Completed -> "Ready"
            else -> "Connecting to community wealth..."
        }
        
        Text(
            statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        
        Spacer(Modifier.height(48.dp))
        
        if (syncStatus is SyncStatus.Progress) {
            val progressValue = (syncStatus as SyncStatus.Progress).progress
            LinearProgressIndicator(
                progress = { progressValue },
                color = Gold,
                trackColor = Gold.copy(alpha = 0.2f),
                modifier = Modifier.width(200.dp).height(4.dp)
            )
        } else {
            CircularProgressIndicator(
                color = Gold,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun ConnectionErrorScreen(message: String, onRetry: () -> Unit) {
    var showTechDetails by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = Color(0xFFFDE2E2),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Using a satellite dish icon to match the user's screenshot
                Text("📡", fontSize = 48.sp)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            "Connection Failed",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Forest
            )
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            "Unable to connect to Sanibonani servers. Please check your internet connection.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(40.dp))
        
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Forest)
        ) {
            Text("Try Again", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(Modifier.height(16.dp))
        
        TextButton(onClick = { showTechDetails = !showTechDetails }) {
            Text(
                if (showTechDetails) "Hide Technical Details" else "Show Technical Details",
                color = Forest.copy(alpha = 0.6f)
            )
        }
        
        if (showTechDetails) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
