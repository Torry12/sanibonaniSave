@file:OptIn(ExperimentalComposeUiApi::class)
package com.sanibonani.save.features.auth.ui

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.R
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.data.utils.DevSettings
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.*
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import com.sanibonani.save.features.common.presentation.DevSettingsViewModel
import com.sanibonani.save.ui.components.InfoType

@Composable
fun LoginScreen(
    onLoginSuccess     : (role: UserRole) -> Unit,
    onNavigateRegister : () -> Unit,
    onForgotPassword   : () -> Unit,
    vm                 : AuthViewModel = hiltViewModel(),
    devSettings        : DevSettings = hiltViewModel<DevSettingsViewModel>().devSettings
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
            BiometricHelper.showBiometricPrompt(
                context = context,
                title = loginPromptTitle,
                subtitle = biometricSubtitle,
                onSuccess = { _ -> vm.quickLogin() },
                onError = { code, msg ->
                    val ignored = setOf(
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED
                    )
                    if (code !in ignored) vm.updateError(msg.toString())
                }
            )
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
            // â”€â”€ Language Selector â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            LanguageSelector(
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // â”€â”€ App Brand / Icon â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("ðŸ¤", fontSize = 46.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.ui_welcome_back),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black
            )
            Text(
                text = stringResource(R.string.ui_securely_manage_your_groups_future),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            // â”€â”€ Login Form Card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SanibonaniTextField(
                        value = state.email,
                        onValueChange = { vm.updateEmail(it) },
                        label = stringResource(R.string.ui_email_address),
                        placeholder = stringResource(R.string.ui_youexamplecom),
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = state.fieldErrors.containsKey("email"),
                        supportingText = state.fieldErrors["email"]
                    )

                    var passwordVisible by remember { mutableStateOf(false) }
                    SanibonaniTextField(
                        value = state.password,
                        onValueChange = { vm.updatePasswordState(it) },
                        label = stringResource(R.string.ui_password),
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                            val description = if (passwordVisible) stringResource(R.string.ui_hide_password) else stringResource(R.string.ui_show_password)
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(image, description)
                            }
                        },
                        isError = state.fieldErrors.containsKey("password"),
                        supportingText = state.fieldErrors["password"]
                    )

                    // â”€â”€ Remember Me Switch / Checkbox Row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    Card(
                        onClick = { vm.updateRememberMe(!state.rememberMe) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = state.rememberMe,
                                onCheckedChange = { vm.updateRememberMe(it) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ui_remember_me),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // â”€â”€ Biometric Setting Toggle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    if (BiometricHelper.canAuthenticate(context)) {
                        Card(
                            onClick = {
                                if (state.rememberMe && (state.hasSavedCredentials || state.password.isNotBlank())) {
                                    vm.toggleBiometric(!state.biometricEnabled)
                                }
                            },
                            enabled = state.rememberMe && (state.hasSavedCredentials || state.password.isNotBlank()),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = state.biometricEnabled,
                                    enabled = state.rememberMe && (state.hasSavedCredentials || state.password.isNotBlank()),
                                    onCheckedChange = { vm.toggleBiometric(it) },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.ui_biometric_quick_login),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (state.rememberMe) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // â”€â”€ Error Info Banner â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    AnimatedVisibility(visible = state.error != null, enter = fadeIn(), exit = fadeOut()) {
                        state.error?.let { err ->
                            val isSuccess = state.errorType == "success"
                            val isConnectionError = err.contains("Connection failed", ignoreCase = true)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                InfoBox(err, type = if (isSuccess) InfoType.SUCCESS else InfoType.ERROR)

                                if (isConnectionError) {
                                    Button(
                                        onClick = { clickDebouncer.processClick { vm.reconnect() } },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.ui_reconnect_try_again))
                                    }
                                }

                                if (state.errorType == "unverified") {
                                    TextButton(
                                        onClick = { clickDebouncer.processClick { vm.resendVerification() } },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            stringResource(R.string.ui_resend_verification_link),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // â”€â”€ Action Buttons â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SanibonaniButton(
                            text = if (state.isLoading) stringResource(R.string.ui_signing_you_in) else stringResource(R.string.ui_log_in),
                            onClick = { clickDebouncer.processClick { vm.signIn() } },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = !state.isLoading && state.email.isNotBlank() && state.password.isNotBlank()
                        )

                        OutlinedButton(
                            onClick = { clickDebouncer.processClick { vm.signInWithMagicLink() } },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = !state.isLoading && state.email.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                stringResource(R.string.ui_sign_in_with_magic_link),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // â”€â”€ Quick Biometric Login Button â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    if (BiometricHelper.canAuthenticate(context) && state.hasSavedCredentials && state.biometricEnabled) {
                        OutlinedButton(
                            onClick = {
                                BiometricHelper.showBiometricPrompt(
                                    context = context,
                                    title = loginPromptTitle,
                                    subtitle = biometricSubtitle,
                                    onSuccess = { _ -> vm.quickLogin() },
                                    onError = { code, msg ->
                                        val ignored = setOf(
                                            BiometricPrompt.ERROR_USER_CANCELED,
                                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                            BiometricPrompt.ERROR_CANCELED
                                        )
                                        if (code !in ignored) vm.updateError(msg.toString())
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.ui_sign_in_with_biometrics),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.ui_saved_as, state.email),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else if (BiometricHelper.canAuthenticate(context) && !state.hasSavedCredentials) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.ui_enable_remember_me_to_use_biometric_logi),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = { clickDebouncer.processClick(onForgotPassword) },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            stringResource(R.string.ui_forgot_password),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // â”€â”€ Debug Connection Switch â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (BuildConfig.DEBUG) {
                DevSettingsOverlay(devSettings)
            }

            Spacer(Modifier.height(28.dp))

            // â”€â”€ Register Navigation Pill â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Surface(
                onClick = { clickDebouncer.processClick(onNavigateRegister) },
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        stringResource(R.string.ui_dont_have_an_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.ui_register_now),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // â”€â”€ Security Footer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ui_e2e_encrypted_ledger),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
