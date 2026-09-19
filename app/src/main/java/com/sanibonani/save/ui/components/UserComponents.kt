package com.sanibonani.save.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.ui.theme.*

enum class LogoutButtonStyle {
    Filled,
    Outlined,
    MenuItem
}

@Composable
fun LogoutButton(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    style: LogoutButtonStyle = LogoutButtonStyle.Outlined,
    isLoading: Boolean = false
) {
    when (style) {
        LogoutButtonStyle.Filled -> {
            Button(
                onClick = onLogout,
                modifier = modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log Out", fontWeight = FontWeight.Bold)
                }
            }
        }
        LogoutButtonStyle.Outlined -> {
            OutlinedButton(
                onClick = onLogout,
                modifier = modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ErrorRed)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log Out", fontWeight = FontWeight.Bold)
                }
            }
        }
        LogoutButtonStyle.MenuItem -> {
            DropdownMenuItem(
                text = { Text("Log Out", color = ErrorRed, fontWeight = FontWeight.Bold) },
                onClick = onLogout,
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = ErrorRed) },
                enabled = !isLoading
            )
        }
    }
}
