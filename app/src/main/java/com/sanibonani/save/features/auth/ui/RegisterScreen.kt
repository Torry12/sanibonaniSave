package com.sanibonani.save.features.auth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.validation.InputValidator
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.KeyboardAwareScrollColumn
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import com.sanibonani.save.ui.components.InfoType

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RegisterScreen(
    onRegistered : () -> Unit,
    onBack       : () -> Unit,
    vm           : AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clickDebouncer = rememberClickDebouncer()

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            ToastUtils.showSuccess(context, "Account created successfully!")
            onRegistered()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { ToastUtils.showError(context, it) }
    }

    val passwordsMatch = state.password.isNotEmpty() && state.password == state.confirmPw
    val isFormValid = state.fullName.length >= 3 &&
            state.email.isNotBlank() &&
            InputValidator.isValidPassword(state.password) &&
            passwordsMatch

    if (state.isLoading) {
        SanibonaniLoadingScreen(message = stringResource(R.string.ui_creating_account), isOverlay = true)
    }

    Scaffold(topBar = { SanibonaniTopBar("Create Account", onBack = onBack) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            KeyboardAwareScrollColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                // Header Title & Subtitle
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        stringResource(R.string.ui_join_sanibonanisave),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.ui_create_your_account_to_manage_or_join_sa),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Registration Card
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
                            value = state.fullName,
                            onValueChange = { vm.updateFullName(it) },
                            label = stringResource(R.string.ui_full_name),
                            placeholder = stringResource(R.string.ui_john_doe),
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            isError = state.fieldErrors.containsKey("fullName"),
                            supportingText = state.fieldErrors["fullName"]
                        )

                        SanibonaniTextField(
                            value = state.email,
                            onValueChange = { vm.updateEmail(it) },
                            label = stringResource(R.string.ui_email_address),
                            placeholder = stringResource(R.string.ui_youexamplecom),
                            leadingIcon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
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
                                imeAction = ImeAction.Next
                            ),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                val description = if (passwordVisible) "Hide password" else "Show password"
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(image, description)
                                }
                            },
                            isError = state.fieldErrors.containsKey("password"),
                            supportingText = state.fieldErrors["password"]
                        )

                        var confirmPwVisible by remember { mutableStateOf(false) }
                        val confirmError = when {
                            state.confirmPw.isNotBlank() && !passwordsMatch -> "Passwords do not match"
                            state.fieldErrors.containsKey("confirmPw") -> state.fieldErrors["confirmPw"]
                            else -> null
                        }

                        SanibonaniTextField(
                            value = state.confirmPw,
                            onValueChange = { vm.updateConfirmPw(it) },
                            label = stringResource(R.string.ui_confirm_password),
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                            visualTransformation = if (confirmPwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            isError = confirmError != null,
                            supportingText = confirmError,
                            trailingIcon = {
                                val image = if (confirmPwVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                val description = if (confirmPwVisible) "Hide password" else "Show password"
                                IconButton(onClick = { confirmPwVisible = !confirmPwVisible }) {
                                    Icon(image, description)
                                }
                            }
                        )

                        AnimatedVisibility(visible = state.error != null, enter = fadeIn(), exit = fadeOut()) {
                            state.error?.let { InfoBox(it, type = InfoType.ERROR) }
                        }

                        Spacer(Modifier.height(8.dp))

                        SanibonaniButton(
                            text = if (state.isLoading) "Creating account…" else "Create Account",
                            onClick = { clickDebouncer.processClick { vm.signUp() } },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = !state.isLoading && isFormValid
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                // POPIA Privacy Badge
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.ui_popia_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
