package com.sanibonani.save.ui.components


import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.ui.theme.Navy

/**
 * A security dialog that challenges the user for a 2FA code.
 * Triggered by high-value disbursements (>= R10,000).
 */
@Composable
fun MfaChallengeDialog(
    onVerify: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    isLoading: Boolean = false
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Navy) },
        title = {
            Text(
                "Security Verification Required",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "This high-value disbursement exceeds R10,000 and requires an active 2FA session.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    label = { Text("Verification Code") },
                    placeholder = { Text("000000") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = error != null,
                    enabled = !isLoading,
                    supportingText = {
                        if (error != null) {
                            Text(error, color = Color.Red)
                        } else {
                            Text("Enter the 6-digit code from your authenticator app.")
                        }
                    }
                )

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onVerify(code) },
                enabled = code.length == 6 && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) {
                Text("Verify & Continue")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}
