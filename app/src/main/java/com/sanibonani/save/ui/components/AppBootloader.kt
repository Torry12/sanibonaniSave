package com.sanibonani.save.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.domain.model.SyncStatus
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import com.sanibonani.save.features.common.presentation.SplashViewModel
import com.sanibonani.save.features.common.presentation.StepStatus
import com.sanibonani.save.ui.theme.*

/**
 * Unified Application Bootloader with 3D Animated Translucent Glass Sphere Branding.
 * Orchestrates branding, connection establishing, and account hydration with buttery smooth transitions.
 */
@Composable
fun AppBootloader(
    splashVm: SplashViewModel,
    authVm: AuthViewModel,
    onRetry: () -> Unit,
) {
    val isConnecting by splashVm.isConnecting.collectAsStateWithLifecycle()
    val connectionError by splashVm.connectionError.collectAsStateWithLifecycle()
    val syncStatus by splashVm.syncStatus.collectAsStateWithLifecycle()
    val hydrationSteps by authVm.hydrationSteps.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(900, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.82f, animationSpec = tween(900, easing = FastOutSlowInEasing)),
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyDeep)
                .testTag("branding_splash"),
            contentAlignment = Alignment.Center
        ) {
            val screenMaxHeight = maxHeight
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = screenMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                // ── Spinning Translucent Sphere Branding ──
                SpinningTranslucentSphere(
                    size = minOf(140.dp, screenMaxHeight * 0.24f)
                )

                Spacer(Modifier.height(36.dp))

                Text(
                    "SanibonaniSave",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Digital Ledger for SA Savings Groups",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                // ── Status Engine ──
                AnimatedContent(
                    targetState = connectionError to isConnecting,
                    transitionSpec = { fadeIn(tween(500, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(500, easing = FastOutSlowInEasing)) },
                    label = "status_engine"
                ) { (error, connecting) ->
                    when {
                        error != null -> {
                            BootloaderError(message = error, onRetry = onRetry)
                        }
                        connecting -> {
                            BootloaderStatus("Establishing secure connection...")
                        }
                        else -> {
                            // Hydration / Sync Phase
                            val activeHydration = hydrationSteps.entries.find { it.value is StepStatus.Loading }
                            val statusText = when {
                                activeHydration != null -> "Initializing ${activeHydration.key}..."
                                syncStatus is SyncStatus.Progress -> (syncStatus as SyncStatus.Progress).message
                                syncStatus is SyncStatus.Completed -> "Vault Ready"
                                else -> "Finalizing..."
                            }
                            BootloaderStatus(statusText, syncStatus)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BootloaderStatus(text: String, syncStatus: SyncStatus? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )

        if (syncStatus is SyncStatus.Progress) {
            Spacer(Modifier.height(32.dp))
            val progressValue = syncStatus.progress
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(
                    progress = { progressValue },
                    color = AccentBlue,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier
                        .width(220.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${(progressValue * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun BootloaderError(message: String, onRetry: () -> Unit) {
    var showTechDetails by remember { mutableStateOf(value = false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Connection Failed",
            style = MaterialTheme.typography.titleLarge,
            color = ErrorRed,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Unable to reach Sanibonani servers.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.width(200.dp).height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))
        ) {
            Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { showTechDetails = !showTechDetails }) {
            Text(
                if (showTechDetails) "Hide Details" else "Technical Details",
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (showTechDetails) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
