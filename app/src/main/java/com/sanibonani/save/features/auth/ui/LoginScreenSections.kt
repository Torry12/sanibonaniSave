@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.sanibonani.save.features.auth.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.*
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import com.sanibonani.save.features.auth.presentation.state.AuthState

@Composable
internal fun LoginBrandHeader() {
    // ── App Brand / Icon ──────────────────────────────────────────────────────
    Surface(
        modifier = Modifier.size(88.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("🤝", fontSize = 46.sp)
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
}

@Composable
internal fun LoginFormCard(
    state: AuthState,
    vm: AuthViewModel,
    context: Context,
    clickDebouncer: ClickDebouncer,
    loginPromptTitle: String,
    biometricSubtitle: String,
    onForgotPassword: () -> Unit
) {
    // ── Login Form Card ──────────────────────────────────────────────────────
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
            LoginCredentialsFields(state = state, vm = vm)

            RememberMeCard(state = state, vm = vm)

            if (BiometricHelper.canAuthenticate(context)) {
                BiometricToggleCard(state = state, vm = vm)
            }

            LoginErrorBanner(state = state, vm = vm, clickDebouncer = clickDebouncer)

            LoginActionButtons(state = state, vm = vm, clickDebouncer = clickDebouncer)

            QuickBiometricLogin(
                state = state,
                vm = vm,
                context = context,
                loginPromptTitle = loginPromptTitle,
                biometricSubtitle = biometricSubtitle
            )

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
}

@Composable
internal fun LoginCredentialsFields(state: AuthState, vm: AuthViewModel) {
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
}

@Composable
internal fun RememberMeCard(state: AuthState, vm: AuthViewModel) {
    // ── Remember Me Switch / Checkbox Row ────────────────────────────────────
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
}

