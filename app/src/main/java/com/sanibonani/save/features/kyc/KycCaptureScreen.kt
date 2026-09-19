package com.sanibonani.save.features.kyc

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.sanibonani.save.ui.components.CameraPreview
import com.sanibonani.save.ui.components.takePhoto
import com.sanibonani.save.ui.components.SanibonaniTopBar
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.features.kyc.KycStep
import com.sanibonani.save.features.kyc.KycViewModel
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
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        cameraPermissionState.launchPermissionRequest()
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
                    KycStep.ID_FRONT -> "Capture ID Front"
                    KycStep.SELFIE -> "Capture Selfie"
                    KycStep.SUCCESS -> "KYC Verification"
                },
                onBack = onBack
            )
        }
    ) { padding ->
        if (cameraPermissionState.status.isGranted) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
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

                        Text(
                            text = when (state.step) {
                                KycStep.ID_FRONT -> "Position your SA ID within the frame"
                                KycStep.SELFIE -> "Center your face in the circle"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    SanibonaniButton(
                        text = "Capture Photo",
                        onClick = {
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
                        },
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Processing with AI...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera permission is required to perform KYC.", textAlign = TextAlign.Center)
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
