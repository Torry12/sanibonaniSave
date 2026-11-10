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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.ui.theme.*

@Composable
fun OfflineBanner(
    isAirplaneMode: Boolean = false,
    isSystemOnline: Boolean = true,
    isOnline: Boolean? = null,
    message: String? = null,
    modifier: Modifier = Modifier,
    airplaneColors: Pair<Color, Color> = ErrorRed to Color.White,
    offlineColors: Pair<Color, Color> = WarningAmber to Color.Black
) {
    val displayMessage = message ?: when {
        isAirplaneMode -> stringResource(R.string.ui_airplane_mode_active_syncing_is_paused)
        !isSystemOnline -> stringResource(R.string.ui_offline_mode_using_cached_data)
        else -> null
    }

    if (displayMessage != null) {
        val (containerColor, onContainer) =
            if (isAirplaneMode || (isOnline == false && message != null)) airplaneColors else offlineColors

        Surface(
            modifier = modifier.fillMaxWidth(),
            color = containerColor,
            contentColor = onContainer
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isAirplaneMode) Icons.Default.AirplanemodeActive else Icons.Default.CloudOff, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    displayMessage,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
