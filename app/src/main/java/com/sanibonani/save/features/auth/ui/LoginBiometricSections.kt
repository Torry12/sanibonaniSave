@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.sanibonani.save.features.auth.ui

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.*
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import com.sanibonani.save.features.auth.presentation.state.AuthState
import com.sanibonani.save.ui.components.InfoType

@Composable
internal fun BiometricToggleCard(state: AuthState, vm: AuthViewModel) {
    // ── Biometric Setting Toggle ─────────────────────────────────────────────
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

@Composable
internal fun LoginErrorBanner(state: AuthState, vm: AuthViewModel, clickDebouncer: ClickDebouncer) {
    // ── Error Info Banner ────────────────────────────────────────────────────
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
}

@Composable
internal fun LoginActionButtons(state: AuthState, vm: AuthViewModel, clickDebouncer: ClickDebouncer) {
    // ── Action Buttons ───────────────────────────────────────────────────────
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
}

@Composable
internal fun ColumnScope.QuickBiometricLogin(
    state: AuthState,
    vm: AuthViewModel,
    context: Context,
    loginPromptTitle: String,
    biometricSubtitle: String
) {
    // ── Quick Biometric Login Button ─────────────────────────────────────────
    if (BiometricHelper.canAuthenticate(context) && state.hasSavedCredentials && state.biometricEnabled) {
        OutlinedButton(
            onClick = {
                triggerBiometricPrompt(context, loginPromptTitle, biometricSubtitle, vm)
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
}

@Composable
internal fun LoginRegisterPill(clickDebouncer: ClickDebouncer, onNavigateRegister: () -> Unit) {
    // ── Register Navigation Pill ─────────────────────────────────────────────
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
}

@Composable
internal fun LoginSecurityFooter() {
    // ── Security Footer ──────────────────────────────────────────────────────
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

internal fun triggerBiometricPrompt(
    context: Context,
    title: String,
    subtitle: String,
    vm: AuthViewModel
) {
    BiometricHelper.showBiometricPrompt(
        context = context,
        title = title,
        subtitle = subtitle,
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
