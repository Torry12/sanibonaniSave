package com.sanibonani.save.features.common.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.domain.repository.system.SyncStatus
import com.sanibonani.save.features.common.presentation.SplashViewModel
import com.sanibonani.save.features.common.presentation.StepStatus
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import com.sanibonani.save.ui.theme.*

/**
 * Unified Application Bootloader.
 * Orchestrates branding, connection establishing, and account hydration.
 */
@Composable
fun AppBootloader(
    splashVm: SplashViewModel,
    authVm: AuthViewModel,
    onRetry: () -> Unit
) {
    val isConnecting by splashVm.isConnecting.collectAsStateWithLifecycle()
    val connectionError by splashVm.connectionError.collectAsStateWithLifecycle()
    val syncStatus by splashVm.syncStatus.collectAsStateWithLifecycle()
    val hydrationSteps by authVm.hydrationSteps.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // ── Branding ──
            Surface(
                modifier = Modifier.size(100.dp),
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🤝", fontSize = 52.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "SanibonaniSave",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))

            // ── Status Engine ──
            AnimatedContent(
                targetState = connectionError to isConnecting,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
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
                            activeHydration != null -> "Hydrating: ${activeHydration.key}..."
                            syncStatus is SyncStatus.Progress -> (syncStatus as SyncStatus.Progress).message
                            syncStatus is SyncStatus.Completed -> "Vault Ready"
                            else -> "Synchronizing..."
                        }
                        BootloaderStatus(statusText, syncStatus)
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
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(64.dp))

        if (syncStatus is SyncStatus.Progress) {
            val progressValue = syncStatus.progress
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(
                    progress = { progressValue },
                    color = AccentBlue,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier
                        .width(200.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${(progressValue * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
        } else {
            CircularProgressIndicator(
                color = AccentBlue,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun BootloaderError(message: String, onRetry: () -> Unit) {
    var showTechDetails by remember { mutableStateOf(false) }

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
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
        ) {
            Text("Try Again", color = Color.White)
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { showTechDetails = !showTechDetails }) {
            Text(
                if (showTechDetails) "Hide Details" else "Technical Details",
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (showTechDetails) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
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
