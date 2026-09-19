package com.sanibonani.save.ui.screens.kyc

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.ui.components.CameraPreview
import com.sanibonani.save.ui.components.takePhoto
import com.sanibonani.save.ui.components.SanibonaniTopBar
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.viewmodel.KycStep
import com.sanibonani.save.viewmodel.KycViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KycCaptureScreen(
    memberId: String,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: KycViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }

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
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
