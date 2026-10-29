@file:OptIn(ExperimentalComposeUiApi::class)
package com.sanibonani.save.features.auth.ui

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.R
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.service.DevSettingsService
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.*
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import com.sanibonani.save.features.auth.presentation.state.AuthState
import com.sanibonani.save.features.common.presentation.DevSettingsViewModel
import com.sanibonani.save.ui.components.InfoType

@Composable
fun LoginScreen(
    onLoginSuccess     : (role: UserRole) -> Unit,
    onNavigateRegister : () -> Unit,
    onForgotPassword   : () -> Unit,
    vm                 : AuthViewModel = hiltViewModel(),
    devSettings        : DevSettingsService = hiltViewModel<DevSettingsViewModel>().devSettings
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clickDebouncer = rememberClickDebouncer()
    val welcomeBackMessage = stringResource(R.string.ui_welcome_back_signing_in)
    val loginPromptTitle = stringResource(R.string.ui_sanibonanisave_login)
    val biometricSubtitle = stringResource(R.string.ui_biometric_subtitle)

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            ToastUtils.showSuccess(context, welcomeBackMessage)
            AppLogger.d(
                tag = "AuthScreens",
                message = "Login success emitted role=${state.userRole} email=${state.email}"
            )
            onLoginSuccess(state.userRole)
        }
    }

    var biometricTriggered by remember { mutableStateOf(false) }
    if (state.biometricEnabled && state.hasSavedCredentials && !biometricTriggered) {
        LaunchedEffect(Unit) {
            biometricTriggered = true
            triggerBiometricPrompt(context, loginPromptTitle, biometricSubtitle, vm)
        }
    }

    if (state.isLoading) {
        SanibonaniLoadingScreen(message = stringResource(R.string.ui_signing_you_in), isOverlay = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Language Selector ─────────────────────────────────────────────
            LanguageSelector(
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LoginBrandHeader()

            LoginFormCard(
                state = state,
                vm = vm,
                context = context,
                clickDebouncer = clickDebouncer,
                loginPromptTitle = loginPromptTitle,
                biometricSubtitle = biometricSubtitle,
                onForgotPassword = onForgotPassword
            )

            // ── Debug Connection Switch ─────────────────────────────────────
            if (BuildConfig.DEBUG) {
                DevSettingsOverlay(devSettings)
            }

            Spacer(Modifier.height(28.dp))

            LoginRegisterPill(
                clickDebouncer = clickDebouncer,
                onNavigateRegister = onNavigateRegister
            )

            Spacer(Modifier.height(24.dp))

            LoginSecurityFooter()
        }
    }
}

