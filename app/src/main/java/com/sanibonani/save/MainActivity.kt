package com.sanibonani.save

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.data.utils.AppDateSync
import com.sanibonani.save.features.common.presentation.SplashViewModel
import com.sanibonani.save.ui.AppBootloader
import com.sanibonani.save.features.common.presentation.StartupDataViewModel
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import com.sanibonani.save.ui.navigation.SanibonaniNavGraph
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val startupDataViewModel: StartupDataViewModel by viewModels()
    private val splashViewModel: SplashViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.remove()
        }
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Initialize synchronized date
        AppDateSync.syncOnLaunch(this)

        startupDataViewModel.preloadMapData()

        handleIntent(intent)

        setContent {
            val isConnecting by splashViewModel.isConnecting.collectAsStateWithLifecycle()
            val connectionError by splashViewModel.connectionError.collectAsStateWithLifecycle()
            val windowSizeClass = calculateWindowSizeClass(this)

            SanibonaniTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val isBooting = (isConnecting || connectionError != null)

                    Crossfade(
                        targetState = isBooting,
                        animationSpec = tween(500),
                        label = "splash_crossfade"
                    ) { booting ->
                        if (booting) {
                            AppBootloader(
                                splashVm = splashViewModel,
                                authVm = authViewModel,
                                onRetry = { splashViewModel.checkConnection() }
                            )
                        } else {
                            SanibonaniNavGraph(windowSizeClass = windowSizeClass)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        intent?.data?.toString()?.let { url ->
            if (url.contains("reset-password")) {
                ToastUtils.showInfo(this, "Processing your password reset link...")
            }
            authViewModel.handleDeepLink(url)
        }
    }

    override fun onResume() {
        super.onResume()
        AppDateSync.syncOnResume(this)
    }

    override fun onPause() {
        super.onPause()
        AppDateSync.syncOnExit(this)
    }

    override fun onStop() {
        super.onStop()
        AppDateSync.syncOnExit(this)
    }
}
