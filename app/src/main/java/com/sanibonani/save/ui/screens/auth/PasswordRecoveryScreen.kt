package com.sanibonani.save.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.InfoBox
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.components.PhoneNumberTransformation
import com.sanibonani.save.ui.theme.Forest
import com.sanibonani.save.ui.utils.KeyboardAwareScrollColumn
import com.sanibonani.save.viewmodel.PasswordRecoveryViewModel
import com.sanibonani.save.viewmodel.RecoveryMethod

@Composable
fun PasswordRecoveryScreen(
    onBack: () -> Unit,
    vm: PasswordRecoveryViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var selectedMethod by remember { mutableStateOf(RecoveryMethod.EMAIL) }

    KeyboardAwareScrollColumn(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Password Recovery", style = MaterialTheme.typography.headlineMedium, color = Forest)
        Spacer(Modifier.height(16.dp))
        Text("Enter your email or WhatsApp number to receive a password reset link.")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.input,
            onValueChange = { 
                if (selectedMethod == RecoveryMethod.WHATSAPP) {
                    if (it.length <= 10) vm.updateInput(it)
                } else {
                    vm.updateInput(it)
                }
            },
            label = { Text(if (selectedMethod == RecoveryMethod.EMAIL) "Email Address" else "WhatsApp Number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (selectedMethod == RecoveryMethod.EMAIL) KeyboardType.Email else KeyboardType.Phone
            ),
            visualTransformation = if (selectedMethod == RecoveryMethod.WHATSAPP) PhoneNumberTransformation() else VisualTransformation.None
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = selectedMethod == RecoveryMethod.EMAIL,
                onClick = { selectedMethod = RecoveryMethod.EMAIL },
                label = { Text("Email") }
            )
            FilterChip(
                selected = selectedMethod == RecoveryMethod.WHATSAPP,
                onClick = { selectedMethod = RecoveryMethod.WHATSAPP },
                label = { Text("WhatsApp") }
            )
        }
        Spacer(Modifier.height(20.dp))
        if (state.error != null) {
            InfoBox(state.error!!, InfoType.ERROR)
            Spacer(Modifier.height(8.dp))
        }
        if (state.success) {
            InfoBox("Recovery instructions sent! Please check your ${if (selectedMethod == RecoveryMethod.EMAIL) "email" else "WhatsApp"}.", InfoType.SUCCESS)
            Spacer(Modifier.height(8.dp))
        }
        SanibonaniButton(
            text = if (state.isLoading) "Sending..." else "Send Reset Link",
            onClick = { vm.sendRecovery(selectedMethod) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading && state.input.isNotBlank()
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text("Back to Login") }
    }
}
