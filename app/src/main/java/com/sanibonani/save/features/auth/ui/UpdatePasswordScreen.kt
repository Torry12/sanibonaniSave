package com.sanibonani.save.features.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.KeyboardAwareScrollColumn
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.features.auth.presentation.AuthViewModel

@Composable
fun UpdatePasswordScreen(
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
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
            Text(stringResource(R.string.ui_create_new_password), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.ui_enter_a_strong_new_password_for_your_acc), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

            Spacer(Modifier.height(8.dp))

            if (!state.isLoggedIn && !state.isLoading && state.error == null) {
                InfoBox("Waiting for session... If you see an error about 'missing sub claim', the reset link might be invalid.", type = InfoType.INFO)
            }

            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = state.password,
                onValueChange = { vm.updatePasswordState(it) },
                label = { Text(stringResource(R.string.ui_new_password)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.NewPassword },
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
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NavyLight, unfocusedBorderColor = LightGray)
            )

            var confirmPwVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = state.confirmPw,
                onValueChange = { vm.updateConfirmPw(it) },
                label = { Text(stringResource(R.string.ui_confirm_new_password)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.NewPassword },
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
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NavyLight, unfocusedBorderColor = LightGray)
            )

            state.error?.let { error ->
                InfoBox(error, type = InfoType.ERROR)
            }

            SanibonaniButton(
                text = if (state.isLoading) "Updating..." else "Update Password",
                onClick = { clickDebouncer.processClick { vm.updatePassword() } },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isLoggedIn && !state.isLoading && state.password.isNotBlank() && state.password == state.confirmPw
            )
        }
    }
}
