package com.sanibonani.save.features.auth.ui

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.utils.KeyboardAwareScrollColumn
import com.sanibonani.save.features.auth.presentation.PasswordRecoveryViewModel

@Composable
fun PasswordRecoveryScreen(
    onBack: () -> Unit,
    vm: PasswordRecoveryViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clickDebouncer = rememberClickDebouncer()

    Scaffold(
        topBar = {
            SanibonaniTopBar(
                title = "Password Recovery",
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
            KeyboardAwareScrollColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Reset Your Password",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Enter your registered email address below and we'll send you instructions to reset your password.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))

                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
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
                            value = state.input,
                            onValueChange = { vm.updateInput(it) },
                            label = "Email Address",
                            placeholder = "you@example.com",
                            leadingIcon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                            isError = state.error != null
                        )

                        state.error?.let { error ->
                            InfoBox(error, type = InfoType.ERROR)
                        }

                        if (state.success) {
                            InfoBox("Recovery instructions sent! Please check your email inbox.", type = InfoType.SUCCESS)
                        }

                        SanibonaniButton(
                            text = if (state.isLoading) "Sending..." else "Send Reset Link",
                            onClick = { clickDebouncer.processClick { vm.sendRecovery() } },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = !state.isLoading && state.input.isNotBlank()
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = { clickDebouncer.processClick(onBack) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        stringResource(R.string.ui_back_to_login),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
