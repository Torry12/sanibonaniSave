package com.sanibonani.save.ui.components


import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.data.utils.DevSettings

@Composable
fun DevSettingsOverlay(devSettings: DevSettings) {
    var useOnline by remember { mutableStateOf(devSettings.useOnlineDatabase) }
    val context = LocalContext.current

    Surface(
        color = Color.Red.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .clickable {
                useOnline = !useOnline
                devSettings.useOnlineDatabase = useOnline
                Toast.makeText(context, "Database switched to ${if (useOnline) "ONLINE" else "LOCAL"}. RESTART APP NOW.", Toast.LENGTH_LONG).show()
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "DEBUG: Database Connection",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (useOnline) "CONNECTED TO ONLINE SUPABASE" else "CONNECTED TO LOCAL SUPABASE",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black
                )
            }
            Switch(
                checked = useOnline,
                onCheckedChange = {
                    useOnline = it
                    devSettings.useOnlineDatabase = it
                    Toast.makeText(context, "Database switched to ${if (it) "ONLINE" else "LOCAL"}. RESTART APP NOW.", Toast.LENGTH_LONG).show()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Red,
                    checkedTrackColor = Color.Red.copy(alpha = 0.3f)
                )
            )
        }
    }
}
