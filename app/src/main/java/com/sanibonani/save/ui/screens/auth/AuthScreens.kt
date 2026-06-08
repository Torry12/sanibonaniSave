@file:OptIn(ExperimentalComposeUiApi::class)
package com.sanibonani.save.ui.screens.auth

import androidx.compose.ui.Alignment
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.*
import androidx.biometric.BiometricPrompt
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.ui.utils.KeyboardAwareScrollColumn
import com.sanibonani.save.viewmodel.AuthViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.ui.utils.BiometricHelper
import com.sanibonani.save.ui.utils.rememberClickDebouncer

// ── Login Screen ──────────────────────────────────────────────────────────────
@Composable
fun LoginScreen(
    onLoginSuccess     : (role: UserRole) -> Unit,
    onNavigateRegister : () -> Unit,
    onForgotPassword   : () -> Unit,
    vm                 : AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
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

    // Login errors are shown inline via InfoBox to avoid duplicate toast + banner feedback.

    // Auto-trigger biometric if enabled
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
            // Logo + headline
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.linearGradient(listOf(Forest, ForestMid)),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) { Text("🤝", fontSize = 42.sp) }

            Spacer(Modifier.height(24.dp))
            Text("Welcome Back", style = MaterialTheme.typography.displaySmall,
                color = Forest, fontWeight = FontWeight.Black)
            Text("Securely manage your group's future.", style = MaterialTheme.typography.bodyLarge,
                color = MidGray, modifier = Modifier.padding(top = 4.dp, bottom = 40.dp))

            // Form card
            Card(
                shape     = RoundedCornerShape(28.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
                border    = BorderStroke(1.dp, Forest.copy(alpha = 0.05f)),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SanibonaniTextField(
                        value         = state.email,
                        onValueChange = { vm.updateEmail(it) },
                        label         = "Email Address",
                        placeholder   = "you@example.com",
                        leadingIcon   = { Icon(Icons.Default.Email, null, tint = Forest.copy(alpha = 0.4f)) },
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
                    
                    Surface(
                        color = Forest.copy(alpha = 0.04f),
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
                                colors = CheckboxDefaults.colors(checkedColor = Forest)
                            )
                            Text("Remember Me", style = MaterialTheme.typography.bodyMedium, color = Charcoal, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (BiometricHelper.canAuthenticate(context)) {
                        Surface(
                            color = Forest.copy(alpha = 0.04f),
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
                                    colors = CheckboxDefaults.colors(checkedColor = Forest)
                                )
                                Text(
                                    "Biometric Quick Login",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (state.rememberMe) Charcoal else MidGray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    state.error?.let {
                        InfoBox(it, InfoType.ERROR)
                    }
                    SanibonaniButton(
                        text     = if (state.isLoading) "Processing…" else "Log In",
                        onClick  = { clickDebouncer.processClick { vm.signIn() } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = !state.isLoading && state.email.isNotBlank() && state.password.isNotBlank()
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

                     TextButton(
                         onClick = { clickDebouncer.processClick(onForgotPassword) }, 
                         modifier = Modifier.align(Alignment.CenterHorizontally)
                     ) {
                         Text("Forgot password?", color = Forest, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                     }
                }
            }

            Spacer(Modifier.height(32.dp))
            Surface(
                onClick = { clickDebouncer.processClick(onNavigateRegister) },
                color = Forest.copy(alpha = 0.08f),
                shape = CircleShape
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("Don't have an account?", style = MaterialTheme.typography.bodyMedium, color = Charcoal)
                    Spacer(Modifier.width(8.dp))
                    Text("Register Now", color = Forest, fontWeight = FontWeight.Black)
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

     // Determine if all required fields are filled for the warning banner
     val allFieldsFilled = state.fullName.length >= 3 &&
             state.email.isNotBlank() &&
             state.password.isNotBlank() &&
             state.confirmPw.isNotBlank() &&
             state.password == state.confirmPw

     // Always allow back navigation
     val onBackAction = onBack

      Scaffold(topBar = { SanibonaniTopBar("Create Account", onBack = onBackAction) }) { padding ->
          KeyboardAwareScrollColumn(
              modifier            = Modifier
                  .fillMaxSize()
                  .background(Cream)
                  .padding(padding)
                  .padding(24.dp),
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
                onClick = { clickDebouncer.processClick { vm.signUp() } },
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
    val clickDebouncer = rememberClickDebouncer()

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
         KeyboardAwareScrollColumn(
             modifier = Modifier
                 .fillMaxSize()
                 .background(Cream)
                 .padding(padding)
                 .padding(horizontal = 24.dp),
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
                onClick = { clickDebouncer.processClick { vm.updatePassword() } },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isLoggedIn && !state.isLoading && state.password.isNotBlank() && state.password == state.confirmPw
            )
        }
    }
}
