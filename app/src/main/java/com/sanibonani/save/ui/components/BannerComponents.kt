package com.sanibonani.save.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.ui.theme.*

@Composable
fun OfflineBanner(
    isAirplaneMode: Boolean = false,
    isSystemOnline: Boolean = true,
    isOnline: Boolean? = null,
    message: String? = null
) {
    val actualOnline = isOnline ?: (isSystemOnline && !isAirplaneMode)
    val displayMessage = message ?: when {
        isAirplaneMode -> "Airplane Mode Active — Syncing is paused."
        !isSystemOnline -> "Offline Mode — Using cached data."
        else -> null
    }

    if (displayMessage != null || actualOnline == false) {
        val color = if (isAirplaneMode || (isOnline == false && message != null)) ErrorRed else WarningAmber
        val textColor = if (color == ErrorRed) Color.White else Color.Black
        val icon = if (isAirplaneMode) Icons.Default.AirplanemodeActive else Icons.Default.CloudOff

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            color = color,
            contentColor = textColor
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    displayMessage ?: "You are currently offline.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
