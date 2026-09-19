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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.KeyboardAwareScrollColumn
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.features.auth.presentation.AuthViewModel

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

     val allFieldsFilled = state.fullName.length >= 3 &&
             state.email.isNotBlank() &&
             state.password.isNotBlank() &&
             state.confirmPw.isNotBlank() &&
             state.password == state.confirmPw

      if (state.isLoading) {
          SanibonaniLoadingScreen(message = "Creating your account...", isOverlay = true)
      }

      Scaffold(topBar = { SanibonaniTopBar("Create Account", onBack = onBack) }) { padding ->
          KeyboardAwareScrollColumn(
              modifier            = Modifier
                  .fillMaxSize()
                  .background(MaterialTheme.colorScheme.background)
                  .padding(padding)
                  .padding(24.dp),
          ) {
             if (!allFieldsFilled) {
                 InfoBox(
                     "⚠️ Please fill in all required fields before leaving this form",
                     InfoType.WARNING
                 )
             }

             Text(stringResource(R.string.ui_join_sanibonanisave), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
             Text(stringResource(R.string.ui_create_your_account_to_manage_or_join_sa),
                 style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
             Spacer(Modifier.height(8.dp))
            SanibonaniTextField(
                value         = state.fullName,
                onValueChange = { vm.updateFullName(it) },
                label = stringResource(R.string.ui_full_name),
                placeholder = stringResource(R.string.ui_john_doe),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Words),
                isError = state.fieldErrors.containsKey("fullName"),
                errorMessage = state.fieldErrors["fullName"]
            )
            SanibonaniTextField(
                value         = state.email,
                onValueChange = { vm.updateEmail(it) },
                label = stringResource(R.string.ui_email_address),
                placeholder = stringResource(R.string.ui_youexamplecom),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = state.fieldErrors.containsKey("email"),
                errorMessage = state.fieldErrors["email"]
            )
             var passwordVisible by remember { mutableStateOf(false) }
             SanibonaniTextField(
                 value         = state.password,
                 onValueChange = { vm.updatePasswordState(it) },
                 label         = stringResource(R.string.ui_password),
                 visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                 keyboardOptions      = KeyboardOptions(
                     keyboardType = KeyboardType.Password,
                     imeAction = ImeAction.Next
                 ),
                 trailingIcon  = {
                     val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                     val description = if (passwordVisible) "Hide password" else "Show password"
                     IconButton(onClick = { passwordVisible = !passwordVisible }) {
                         Icon(image, description)
                     }
                 },
                 isError = state.fieldErrors.containsKey("password"),
                 errorMessage = state.fieldErrors["password"]
             )
             var confirmPwVisible by remember { mutableStateOf(false) }
             SanibonaniTextField(
                 value         = state.confirmPw,
                 onValueChange = { vm.updateConfirmPw(it) },
                 label         = stringResource(R.string.ui_confirm_password),
                 visualTransformation = if (confirmPwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                 keyboardOptions      = KeyboardOptions(
                     keyboardType = KeyboardType.Password,
                     imeAction = ImeAction.Done
                 ),
                 isError       = state.fieldErrors.containsKey("confirmPw"),
                 errorMessage  = state.fieldErrors["confirmPw"],
                 trailingIcon  = {
                     val image = if (confirmPwVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                     val description = if (confirmPwVisible) "Hide password" else "Show password"
                     IconButton(onClick = { confirmPwVisible = !confirmPwVisible }) {
                         Icon(image, description)
                     }
                 }
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
