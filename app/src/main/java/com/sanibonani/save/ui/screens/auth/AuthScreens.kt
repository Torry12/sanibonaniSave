@file:OptIn(ExperimentalComposeUiApi::class)
package com.sanibonani.save.ui.screens.auth

import androidx.compose.ui.Alignment
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.biometric.BiometricPrompt
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.viewmodel.AuthViewModel
import com.sanibonani.save.BuildConfig
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.ui.utils.BiometricHelper
// ── Login Screen ──────────────────────────────────────────────────────────────
@Composable
fun LoginScreen(
    onLoginSuccess     : (role: UserRole) -> Unit,
    onNavigateRegister : () -> Unit,
    onForgotPassword   : () -> Unit,
    onBack             : () -> Unit,
    redirect           : String? = null,
    vm                 : AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Prefill platform admin credentials if redirect == "platform_admin"
    LaunchedEffect(redirect) {
        if (redirect == "platform_admin") {
            vm.prefillPlatformAdmin()
        }
    }

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

    // Login errors are shown inline via InfoBox to avoid duplicate toast + banner feedback.

    Box(modifier = Modifier.fillMaxSize().background(Cream)) {
        Column(
            modifier              = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.Center
        ) {
            // Logo + headline
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(listOf(Forest, ForestMid)),
                        RoundedCornerShape(20.dp)
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { vm.prefillPlatformAdmin() }
                    ),
                contentAlignment = Alignment.Center
            ) { Text("🤝", fontSize = 36.sp) }

            Spacer(Modifier.height(20.dp))
            if (redirect == "platform_admin" || state.email == com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy.EMAIL) {
                Text("Platform Admin Login", style = MaterialTheme.typography.displaySmall,
                    color = Gold, fontWeight = FontWeight.ExtraBold)
                Text("Sign in as Platform Administrator", style = MaterialTheme.typography.bodyMedium,
                    color = Forest, modifier = Modifier.padding(top = 4.dp, bottom = 32.dp))
            } else {
                Text("Welcome Back", style = MaterialTheme.typography.displaySmall,
                    color = Forest, fontWeight = FontWeight.ExtraBold)
                Text("Sign in to SanibonaniSave", style = MaterialTheme.typography.bodyMedium,
                    color = MidGray, modifier = Modifier.padding(top = 4.dp, bottom = 32.dp))
            }

            // Form card
            Card(
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SanibonaniTextField(
                        value         = state.email,
                        onValueChange = { vm.updateEmail(it) },
                        label         = "Email Address",
                        placeholder   = "you@example.com",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                     var passwordVisible by remember { mutableStateOf(false) }
                     val autofill = LocalAutofill.current
                     val autofillNode = remember {
                         AutofillNode(
                             autofillTypes = listOf(AutofillType.Password),
                             onFill = { vm.updatePasswordState(it) }
                         )
                     }
                     LocalAutofillTree.current += autofillNode

                     OutlinedTextField(
                         value         = state.password,
                         onValueChange = { vm.updatePasswordState(it) },
                         label         = { Text("Password") },
                         modifier      = Modifier
                             .fillMaxWidth()
                             .onGloballyPositioned { autofillNode.boundingBox = it.boundsInWindow() }
                             .onFocusChanged { focusState ->
                                 autofill?.run {
                                     if (focusState.isFocused) {
                                         requestAutofillForNode(autofillNode)
                                     } else {
                                         cancelAutofillForNode(autofillNode)
                                     }
                                 }
                             },
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
                             focusedBorderColor   = ForestLight,
                             unfocusedBorderColor = LightGray
                         )
                     )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.rememberMe,
                            onCheckedChange = { vm.updateRememberMe(it) },
                            colors = CheckboxDefaults.colors(checkedColor = Forest)
                        )
                        Text("Remember Me", style = MaterialTheme.typography.bodyMedium, color = MidGray)
                    }

                    if (BiometricHelper.canAuthenticate(context)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = state.biometricEnabled,
                                enabled = state.rememberMe && (state.hasSavedCredentials || state.password.isNotBlank()),
                                onCheckedChange = { vm.toggleBiometric(it) },
                                colors = CheckboxDefaults.colors(checkedColor = Forest)
                            )
                            Text(
                                "Enable biometric quick login on this device",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MidGray
                            )
                        }
                    }

                    state.error?.let {
                        InfoBox(it, InfoType.ERROR)
                    }
                    SanibonaniButton(
                        text     = if (state.isLoading) "Processing…" else if (state.password.isBlank()) "Send Magic Link" else "Log In",
                        onClick  = { vm.signIn() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = !state.isLoading && state.email.isNotBlank()
                    )

                    // Show biometric login only if device supports it AND credentials are saved
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
                            border = BorderStroke(1.dp, Forest.copy(alpha = 0.5f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👆 ", fontSize = 20.sp)
                                Text("Sign in with Biometrics", color = Forest, fontWeight = FontWeight.Medium)
                            }
                        }

                        Text(
                            text = "Saved as: ${state.email}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MidGray,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else if (BiometricHelper.canAuthenticate(context) && !state.hasSavedCredentials) {
                        // Show hint about enabling biometric
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
                                Text(
                                    "Enable 'Remember Me' to use biometric login next time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MidGray
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
                                Text(
                                    "Turn on biometric quick login to sign in with fingerprint/face/PIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MidGray
                                )
                            }
                        }
                    }

                     TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                         Text("Forgot password?", color = Forest)
                     }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Don't have an account?", style = MaterialTheme.typography.bodyMedium, color = MidGray)
                TextButton(onClick = onNavigateRegister) {
                    Text("Register", color = Forest, fontWeight = FontWeight.Bold)
                }
            }

            if (BuildConfig.DEBUG) {
                TextButton(
                    onClick = { vm.prefillPlatformAdmin() },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Platform Admin Login", color = Forest.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ── Register Screen ───────────────────────────────────────────────────────────
@Composable
fun RegisterScreen(
    onRegistered : () -> Unit,
    onBack       : () -> Unit,
    vm           : AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

     LaunchedEffect(state.isLoggedIn) {
         if (state.isLoggedIn) {
             ToastUtils.showSuccess(context, "Account created successfully!")
             onRegistered()
         }
     }

     LaunchedEffect(state.error) {
         state.error?.let { ToastUtils.showError(context, it) }
     }

     // Determine if all required fields are filled for the warning banner
     val allFieldsFilled = state.fullName.length >= 3 &&
             state.email.isNotBlank() &&
             state.password.isNotBlank() &&
             state.confirmPw.isNotBlank() &&
             state.password == state.confirmPw

     // Always allow back navigation
     val onBackAction = onBack

     Scaffold(topBar = { SanibonaniTopBar("Create Account", onBack = onBackAction) }) { padding ->
         Column(
             modifier            = Modifier
                 .fillMaxSize()
                 .background(Cream)
                 .padding(padding)
                 .padding(24.dp)
                 .verticalScroll(rememberScrollState()),
             verticalArrangement = Arrangement.spacedBy(14.dp)
         ) {
             if (!allFieldsFilled) {
                 InfoBox(
                     "⚠️ Please fill in all required fields before leaving this form",
                     InfoType.WARNING
                 )
             }

             Text("Join SanibonaniSave", style = MaterialTheme.typography.headlineMedium, color = Forest)
             Text("Create your account to manage or join savings groups",
                 style = MaterialTheme.typography.bodyMedium, color = MidGray)
             Spacer(Modifier.height(8.dp))
            SanibonaniTextField(
                value         = state.fullName,
                onValueChange = { vm.updateFullName(it) },
                label         = "Full Name",
                placeholder   = "John Doe",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Words)
            )
            SanibonaniTextField(
                value         = state.email,
                onValueChange = { vm.updateEmail(it) },
                label         = "Email Address",
                placeholder   = "you@example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
             var passwordVisible by remember { mutableStateOf(false) }
             val autofill = LocalAutofill.current
             val passwordAutofillNode = remember {
                 AutofillNode(
                     autofillTypes = listOf(AutofillType.Password, AutofillType.NewPassword),
                     onFill = { vm.updatePasswordState(it) }
                 )
             }
             LocalAutofillTree.current += passwordAutofillNode

             OutlinedTextField(
                 value         = state.password,
                 onValueChange = { vm.updatePasswordState(it) },
                 label         = { Text("Password") },
                 modifier      = Modifier
                     .fillMaxWidth()
                     .onGloballyPositioned { passwordAutofillNode.boundingBox = it.boundsInWindow() }
                     .onFocusChanged { focusState ->
                         autofill?.run {
                             if (focusState.isFocused) {
                                 requestAutofillForNode(passwordAutofillNode)
                             } else {
                                 cancelAutofillForNode(passwordAutofillNode)
                             }
                         }
                     },
                 singleLine    = true,
                 visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                 keyboardOptions      = KeyboardOptions(
                     keyboardType = KeyboardType.Password,
                     imeAction = ImeAction.Next
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
                     focusedBorderColor = ForestLight, unfocusedBorderColor = LightGray
                 )
             )
             var confirmPwVisible by remember { mutableStateOf(false) }
             val confirmAutofillNode = remember {
                 AutofillNode(
                     autofillTypes = listOf(AutofillType.Password, AutofillType.NewPassword),
                     onFill = { vm.updateConfirmPw(it) }
                 )
             }
             LocalAutofillTree.current += confirmAutofillNode

             OutlinedTextField(
                 value         = state.confirmPw,
                 onValueChange = { vm.updateConfirmPw(it) },
                 label         = { Text("Confirm Password") },
                 modifier      = Modifier
                     .fillMaxWidth()
                     .onGloballyPositioned { confirmAutofillNode.boundingBox = it.boundsInWindow() }
                     .onFocusChanged { focusState ->
                         autofill?.run {
                             if (focusState.isFocused) {
                                 requestAutofillForNode(confirmAutofillNode)
                             } else {
                                 cancelAutofillForNode(confirmAutofillNode)
                             }
                         }
                     },
                 singleLine    = true,
                 visualTransformation = if (confirmPwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                 keyboardOptions      = KeyboardOptions(
                     keyboardType = KeyboardType.Password,
                     imeAction = ImeAction.Done
                 ),
                 shape         = RoundedCornerShape(12.dp),
                 isError       = state.confirmPw.isNotBlank() && state.password != state.confirmPw,
                 trailingIcon  = {
                     val image = if (confirmPwVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                     val description = if (confirmPwVisible) "Hide password" else "Show password"
                     IconButton(onClick = { confirmPwVisible = !confirmPwVisible }) {
                         Icon(image, description)
                     }
                 },
                 colors        = OutlinedTextFieldDefaults.colors(
                     focusedBorderColor = ForestLight, unfocusedBorderColor = LightGray
                 )
             )
            state.error?.let { InfoBox(it, InfoType.ERROR) }
            SanibonaniButton(
                text    = if (state.isLoading) "Creating account…" else "Create Account",
                onClick = { vm.signUp() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading &&
                    state.fullName.length >= 3 &&
                    state.email.isNotBlank() &&
                    state.password.isNotBlank() &&
                    state.password == state.confirmPw
            )
            InfoBox(
                "Your personal data is protected under POPIA. SanibonaniSave will never sell your information.",
                InfoType.INFO
            )
        }
    }
}

// ── Update Password Screen ───────────────────────────────────────────────────
@Composable
fun UpdatePasswordScreen(
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.navigateTo) {
        if (state.navigateTo == "login") {
            ToastUtils.showSuccess(context, "Password updated successfully!")
            vm.clearNavigation()
            onSuccess()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { ToastUtils.showError(context, it) }
    }

    Scaffold(topBar = { SanibonaniTopBar("Reset Password", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Cream)
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Create New Password", style = MaterialTheme.typography.headlineMedium, color = Forest)
            Text("Enter a strong new password for your account.", color = MidGray)
            
            Spacer(Modifier.height(8.dp))

            if (!state.isLoggedIn && !state.isLoading && state.error == null) {
                InfoBox("Waiting for session... If you see an error about 'missing sub claim', the reset link might be invalid.", InfoType.INFO)
            }

            var passwordVisible by remember { mutableStateOf(false) }
            val autofill = LocalAutofill.current
            val passwordAutofillNode = remember {
                AutofillNode(
                    autofillTypes = listOf(AutofillType.Password, AutofillType.NewPassword),
                    onFill = { vm.updatePasswordState(it) }
                )
            }
            LocalAutofillTree.current += passwordAutofillNode

            OutlinedTextField(
                value = state.password,
                onValueChange = { vm.updatePasswordState(it) },
                label = { Text("New Password") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { passwordAutofillNode.boundingBox = it.boundsInWindow() }
                    .onFocusChanged { focusState ->
                        autofill?.run {
                            if (focusState.isFocused) {
                                requestAutofillForNode(passwordAutofillNode)
                            } else {
                                cancelAutofillForNode(passwordAutofillNode)
                            }
                        }
                    },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(image, "Toggle Visibility")
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestLight, unfocusedBorderColor = LightGray)
            )

            var confirmPwVisible by remember { mutableStateOf(false) }
            val confirmAutofillNode = remember {
                AutofillNode(
                    autofillTypes = listOf(AutofillType.Password, AutofillType.NewPassword),
                    onFill = { vm.updateConfirmPw(it) }
                )
            }
            LocalAutofillTree.current += confirmAutofillNode

            OutlinedTextField(
                value = state.confirmPw,
                onValueChange = { vm.updateConfirmPw(it) },
                label = { Text("Confirm New Password") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { confirmAutofillNode.boundingBox = it.boundsInWindow() }
                    .onFocusChanged { focusState ->
                        autofill?.run {
                            if (focusState.isFocused) {
                                requestAutofillForNode(confirmAutofillNode)
                            } else {
                                cancelAutofillForNode(confirmAutofillNode)
                            }
                        }
                    },
                singleLine = true,
                visualTransformation = if (confirmPwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(12.dp),
                isError = state.confirmPw.isNotBlank() && state.password != state.confirmPw,
                trailingIcon = {
                    val image = if (confirmPwVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                    IconButton(onClick = { confirmPwVisible = !confirmPwVisible }) {
                        Icon(image, "Toggle Visibility")
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ForestLight, unfocusedBorderColor = LightGray)
            )

            if (state.error != null) InfoBox(state.error!!, InfoType.ERROR)

            SanibonaniButton(
                text = if (state.isLoading) "Updating..." else "Update Password",
                onClick = { vm.updatePassword() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && state.password.isNotBlank() && state.password == state.confirmPw
            )
        }
    }
}
