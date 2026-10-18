package com.sanibonani.save.features.admin.ui

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.features.admin.presentation.CreatePlatformAdminViewModel
import kotlinx.coroutines.delay

@Composable
fun CreatePlatformAdminScreen(
    onBack: () -> Unit,
    vm: CreatePlatformAdminViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clickDebouncer = rememberClickDebouncer()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            SanibonaniTopBar(
                title = "Create Admin Account",
                onBack = { clickDebouncer.processClick(onBack) }
            )
        }
    ) { padding ->
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InfoBox(
                    message = "This will create a new account with group administrative access. For security, subsequent platform admin creation is restricted to the canonical owner.",
                    type = InfoType.WARNING
                )

                Spacer(Modifier.height(24.dp))

                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.elevatedCardElevation(2.dp)
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SanibonaniTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = stringResource(R.string.ui_admin_email),
                            placeholder = stringResource(R.string.ui_eg_adminsanibonanicom),
                            leadingIcon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                        )

                        var passwordVisible by remember { mutableStateOf(false) }
                        SanibonaniTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = stringResource(R.string.ui_password),
                            placeholder = stringResource(R.string.ui_enter_secure_password),
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                val description = if (passwordVisible) "Hide password" else "Show password"
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(image, description)
                                }
                            }
                        )

                        var confirmPwVisible by remember { mutableStateOf(false) }
                        val confirmError = if (confirmPassword.isNotEmpty() && password != confirmPassword) "Passwords do not match" else null

                        SanibonaniTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = stringResource(R.string.ui_confirm_password),
                            placeholder = stringResource(R.string.ui_repeat_password),
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                            visualTransformation = if (confirmPwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
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
                    }
                }

                Spacer(Modifier.height(32.dp))

                if (state.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    SanibonaniButton(
                        text = "CREATE ADMINISTRATOR",
                        onClick = { clickDebouncer.processClick { vm.createPlatformAdmin(email, password, confirmPassword) } },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = email.isNotBlank() && password.length >= 6 && password == confirmPassword
                    )
                }

                if (state.success) {
                    Spacer(Modifier.height(16.dp))
                    InfoBox("Administrative account created successfully!", type = InfoType.SUCCESS)

                    LaunchedEffect(Unit) {
                        delay(2000)
                        onBack()
                    }
                }

                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    InfoBox(it, type = InfoType.ERROR)
                }

                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { clickDebouncer.processClick(onBack) }) {
                    Text(stringResource(R.string.ui_cancel_1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
