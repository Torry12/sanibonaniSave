@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.sanibonani.save.ui.screens.auth

import androidx.biometric.BiometricPrompt
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.BiometricHelper
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onLoginSuccess     : (role: UserRole) -> Unit,
    onNavigateRegister : () -> Unit,
    onForgotPassword   : () -> Unit,
    vm                 : AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clickDebouncer = rememberClickDebouncer()

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            ToastUtils.showSuccess(context, "Welcome back! Signing you in...")
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
                title = "SanibonaniSave Login",
                subtitle = "Use your fingerprint, face, or device PIN to sign in",
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

    Box(modifier = Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Cream, Color.White))
    )) {
        Column(
            modifier              = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.linearGradient(listOf(Navy, NavyMid)),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) { Text("🤝", fontSize = 42.sp) }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.ui_welcome_back), style = MaterialTheme.typography.displaySmall,
                color = Color.Black, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.ui_securely_manage_your_groups_future), style = MaterialTheme.typography.bodyLarge,
                color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp, bottom = 40.dp))

            Card(
                shape     = RoundedCornerShape(28.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
                border    = BorderStroke(1.dp, Navy.copy(alpha = 0.05f)),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SanibonaniTextField(
                        value         = state.email,
                        onValueChange = { vm.updateEmail(it) },
                        label = stringResource(R.string.ui_email_address),
                        placeholder = stringResource(R.string.ui_youexamplecom),
                        leadingIcon   = { Icon(Icons.Default.Email, null, tint = Navy.copy(alpha = 0.4f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                     var passwordVisible by remember { mutableStateOf(false) }
                     OutlinedTextField(
                         value         = state.password,
                         onValueChange = { vm.updatePasswordState(it) },
                         label         = { Text(stringResource(R.string.ui_password)) },
                         modifier      = Modifier
                             .fillMaxWidth()
                             .semantics { contentType = ContentType.Password },
                         singleLine    = true,
                         visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                         keyboardOptions      = KeyboardOptions(
                             keyboardType = KeyboardType.Password,
                             imeAction = ImeAction.Done
                         ),
                         shape         = RoundedCornerShape(12.dp),
                         trailingIcon  = {
                             val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                             val description = if (passwordVisible) "Hide password" else "Show password"
                             IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                 Icon(image, description)
                             }
                         },
                         colors        = OutlinedTextFieldDefaults.colors(
                             focusedBorderColor   = NavyLight,
                             unfocusedBorderColor = LightGray
                         )
                     )

                    Surface(
                        color = Color.Black.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(16.dp),
                        onClick = { vm.updateRememberMe(!state.rememberMe) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = state.rememberMe,
                                onCheckedChange = { vm.updateRememberMe(it) },
                                colors = CheckboxDefaults.colors(checkedColor = Color.Black)
                            )
                            Text(stringResource(R.string.ui_remember_me), style = MaterialTheme.typography.bodyMedium, color = Color.Black, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (BiometricHelper.canAuthenticate(context)) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.04f),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                if (state.rememberMe && (state.hasSavedCredentials || state.password.isNotBlank())) {
                                    vm.toggleBiometric(!state.biometricEnabled)
                                }
                            },
                            enabled = state.rememberMe && (state.hasSavedCredentials || state.password.isNotBlank())
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = state.biometricEnabled,
                                    enabled = state.rememberMe && (state.hasSavedCredentials || state.password.isNotBlank()),
                                    onCheckedChange = { vm.toggleBiometric(it) },
                                    colors = CheckboxDefaults.colors(checkedColor = Color.Black)
                                )
                                Text(stringResource(R.string.ui_biometric_quick_login),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (state.rememberMe) Color.Black else Color.Black.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    state.error?.let {
                        val isSuccess = state.errorType == "success"
                        InfoBox(it, if (isSuccess) InfoType.SUCCESS else InfoType.ERROR)

                        if (state.errorType == "unverified") {
                            TextButton(
                                onClick = { clickDebouncer.processClick { vm.resendVerification() } },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Resend Verification Link", color = Navy, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SanibonaniButton(
                            text     = if (state.isLoading) "Processing…" else "Log In",
                            onClick  = { clickDebouncer.processClick { vm.signIn() } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled  = !state.isLoading && state.email.isNotBlank() && state.password.isNotBlank()
                        )

                        OutlinedButton(
                            onClick = { clickDebouncer.processClick { vm.signInWithMagicLink() } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading && state.email.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Navy.copy(alpha = 0.2f))
                        ) {
                            Text("Sign in with Magic Link", color = Navy)
                        }
                    }

                    if (BiometricHelper.canAuthenticate(context) && state.hasSavedCredentials && state.biometricEnabled) {
                        OutlinedButton(
                            onClick = {
							BiometricHelper.showBiometricPrompt(
								context = context,
								title = "SanibonaniSave Login",
								subtitle = "Use your fingerprint, face, or device PIN to sign in",
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
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Navy.copy(alpha = 0.5f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👆 ", fontSize = 20.sp)
                                Text(stringResource(R.string.ui_sign_in_with_biometrics), color = Color.Black, fontWeight = FontWeight.Medium)
                            }
                        }

                        Text(
                            text = "Saved as: ${state.email}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else if (BiometricHelper.canAuthenticate(context) && !state.hasSavedCredentials) {
                        Surface(
                            color = Cream,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👆 ", fontSize = 16.sp)
                                Text(stringResource(R.string.ui_enable_remember_me_to_use_biometric_logi),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else if (BiometricHelper.canAuthenticate(context) && state.hasSavedCredentials && !state.biometricEnabled) {
                        Surface(
                            color = Cream,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👆 ", fontSize = 16.sp)
                                Text(stringResource(R.string.ui_turn_on_biometric_quick_login_to_sign_in),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                     TextButton(
                         onClick = { clickDebouncer.processClick(onForgotPassword) },
                         modifier = Modifier.align(Alignment.CenterHorizontally)
                     ) {
                         Text(stringResource(R.string.ui_forgot_password), color = Color.Black, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                     }
                }
            }

            Spacer(Modifier.height(32.dp))
            Surface(
                onClick = { clickDebouncer.processClick(onNavigateRegister) },
                color = Color.Black.copy(alpha = 0.08f),
                shape = CircleShape
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(stringResource(R.string.ui_dont_have_an_account), style = MaterialTheme.typography.bodyMedium, color = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_register_now), color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
