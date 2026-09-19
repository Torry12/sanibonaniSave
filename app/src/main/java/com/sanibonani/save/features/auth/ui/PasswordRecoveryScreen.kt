package com.sanibonani.save.features.auth.ui

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.InfoBox
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.ui.utils.KeyboardAwareScrollColumn
import com.sanibonani.save.features.auth.presentation.PasswordRecoveryViewModel
import com.sanibonani.save.features.auth.presentation.RecoveryMethod

@Composable
fun PasswordRecoveryScreen(
    onBack: () -> Unit,
    vm: PasswordRecoveryViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clickDebouncer = rememberClickDebouncer()

    KeyboardAwareScrollColumn(Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.ui_password_recovery), style = MaterialTheme.typography.headlineMedium, color = Navy)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ui_enter_your_email_address_to_receive_a_pas))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.input,
            onValueChange = { vm.updateInput(it) },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(20.dp))
        state.error?.let { error ->
            InfoBox(error, InfoType.ERROR)
            Spacer(Modifier.height(8.dp))
        }
        if (state.success) {
            InfoBox("Recovery instructions sent! Please check your email.", InfoType.SUCCESS)
            Spacer(Modifier.height(8.dp))
        }
        SanibonaniButton(
            text = if (state.isLoading) "Sending..." else "Send Reset Link",
            onClick = { clickDebouncer.processClick { vm.sendRecovery() } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading && state.input.isNotBlank()
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { clickDebouncer.processClick(onBack) }) { Text(stringResource(R.string.ui_back_to_login)) }
    }
}
