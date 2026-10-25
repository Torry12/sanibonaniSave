package com.sanibonani.save.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.domain.repository.system.SyncStatus
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

/**
 * Custom 3D Spinning Translucent Glass Sphere with Multi-layered Orbital Rings
 * and Glass Specular Reflections and buttery smooth animations.
 */
@Composable
fun SpinningTranslucentSphere(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sphere_animation")

    // Continuous 360-degree rotation angles with smooth linear easing.
    // Durations use coprime-ish lengths so rings never realign into a visually static pattern.
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    // Own full-cycle track for the glass body — deriving it as a fraction of
    // outerRotation caused a visible snap (108° → 0°) on every ring wrap.
    val sphereRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sphereRotation"
    )

    // Gentle aura pulse
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    // Central Icon Bobbing
    val bobbingOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobbingOffset"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 1. Ambient Background Radial Aura Glow
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glowPulse }
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentBlue.copy(alpha = 0.5f),
                        WarmSage.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.toPx() / 1.05f
                )
            )
        }

        // 2. Outer Orbital Ring (Tilted 3D Halo)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = outerRotation
                    rotationX = 68f
                    cameraDistance = 12f * density
                }
        ) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        AccentBlue.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.98f),
                        WarmSage.copy(alpha = 0.35f),
                        Color.Transparent,
                        AccentBlue.copy(alpha = 0.95f)
                    )
                ),
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(35f, 18f), 0f)
                )
            )
        }

        // 3. Inner Counter-Spinning Orbital Ring (Y-Tilted Halo)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = innerRotation
                    rotationY = 62f
                    cameraDistance = 12f * density
                }
        ) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        WarmSage.copy(alpha = 0.98f),
                        Color.White.copy(alpha = 0.85f),
                        AccentBlue.copy(alpha = 0.25f),
                        Color.Transparent,
                        WarmSage.copy(alpha = 0.98f)
                    )
                ),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 4. Main 3D Translucent Glass Sphere
        Canvas(
            modifier = Modifier
                .size(size * 0.78f)
                .graphicsLayer {
                    rotationZ = sphereRotation
                }
        ) {
            val radius = this.size.minDimension / 2f

            // Sphere Depth - Radial Glass Gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.38f),
                        AccentBlue.copy(alpha = 0.48f),
                        NavyLight.copy(alpha = 0.72f),
                        NavyDeep.copy(alpha = 0.95f)
                    ),
                    center = Offset(radius * 0.65f, radius * 0.65f),
                    radius = radius * 1.35f
                ),
                radius = radius
            )

            // Glass Specular Highlight (Light Lens Flare Reflection at Top-Left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.88f),
                        Color.White.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(radius * 0.55f, radius * 0.45f),
                    radius = radius * 0.55f
                ),
                radius = radius * 0.48f,
                center = Offset(radius * 0.55f, radius * 0.45f)
            )

            // Glass Outer Rim Highlight
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.88f),
                        AccentBlue.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.22f),
                        WarmSage.copy(alpha = 0.75f),
                        Color.White.copy(alpha = 0.88f)
                    )
                ),
                radius = radius,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 5. Central Floating Handshake Emblem
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationY = bobbingOffset * density
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🤝",
                fontSize = (size.value * 0.36f).sp
            )
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
