package com.sanibonani.save.features.kyc

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.sanibonani.save.ui.components.SanibonaniTopBar
import com.sanibonani.save.ui.components.CameraPreview
import com.sanibonani.save.ui.components.takePhoto
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun KycCaptureScreen(
    memberId: String,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: KycViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clickDebouncer = rememberClickDebouncer()
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(state.step) {
        if (state.step == KycStep.SUCCESS) {
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            SanibonaniTopBar(
                title = when (state.step) {
                    KycStep.ID_FRONT -> "Capture ID Document"
                    KycStep.SELFIE -> "Capture Selfie"
                    KycStep.SUCCESS -> "KYC Verification"
                },
                onBack = onBack
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = cameraPermissionState.status.isGranted,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "CameraPermission"
        ) { isGranted ->
            if (isGranted) {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .background(Color.Black),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.step != KycStep.SUCCESS) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            CameraPreview(
                                imageCapture = imageCapture,
                                cameraSelector = if (state.step == KycStep.SELFIE) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                }
                            )

                            // ── GUIDE OVERLAYS ──
                            if (state.step == KycStep.ID_FRONT) {
                                IdGuideOverlay()
                            } else if (state.step == KycStep.SELFIE) {
                                FaceGuideOverlay()
                            }

                            // ── Progress Indicator ──
                            LinearProgressIndicator(
                                progress = { if (state.step == KycStep.ID_FRONT) 0.5f else 0.9f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent
                            )

                            // ── Instruction Banner ──
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                            ) {
                                Text(
                                    text = when (state.step) {
                                        KycStep.ID_FRONT -> "Position your South African ID within the frame"
                                        KycStep.SELFIE -> "Center your face in the circle"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                                )
                            }
                        }

                        // ── Capture Button Section ──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(vertical = 24.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    clickDebouncer.processClick {
                                        takePhoto(
                                            imageCapture = imageCapture,
                                            outputDirectory = context.cacheDir,
                                            executor = executor,
                                            onImageCaptured = { uri ->
                                                if (state.step == KycStep.ID_FRONT) {
                                                    viewModel.onIdCaptured(uri)
                                                } else {
                                                    viewModel.onSelfieCaptured(uri, memberId)
                                                }
                                            },
                                            onError = { /* Error handled by CameraXModule logging for now */ }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text("Capture Photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // ── Processing State ──
                        Box(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(64.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 6.dp
                                )
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    "Verifying Identity",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Our AI is checking your documents...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                // ── Permission Denied State ──
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Camera Access Required",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "We need access to your camera to capture your ID document and selfie for KYC verification.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = { cameraPermissionState.launchPermissionRequest() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer, contentColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdGuideOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 4.dp.toPx()
        val cornerRadius = 12.dp.toPx()
        val rectWidth = size.width * 0.85f
        val rectHeight = rectWidth * 0.63f // ID card aspect ratio
        val left = (size.width - rectWidth) / 2
        val top = (size.height - rectHeight) / 2

        // Dim background
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            size = size
        )

        // Clear hole
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(rectWidth, rectHeight),
            cornerRadius = CornerRadius(cornerRadius),
            blendMode = BlendMode.Clear
        )

        // Border
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(rectWidth, rectHeight),
            cornerRadius = CornerRadius(cornerRadius),
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
private fun FaceGuideOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 4.dp.toPx()
        val radius = size.width * 0.35f
        val center = Offset(size.width / 2, size.height * 0.45f)

        // Dim background
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            size = size
        )

        // Clear circle
        drawCircle(
            color = Color.Transparent,
            radius = radius,
            center = center,
            blendMode = BlendMode.Clear
        )

        // Border
        drawCircle(
            color = Color.White,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
    }
}
