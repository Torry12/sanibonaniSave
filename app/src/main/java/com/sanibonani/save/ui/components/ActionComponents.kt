package com.sanibonani.save.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val displayColor = color ?: MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = displayColor.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, displayColor.copy(alpha = 0.2f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = displayColor, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun OutlinedSanibonaniButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}
