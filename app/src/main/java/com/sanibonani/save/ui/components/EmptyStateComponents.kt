package com.sanibonani.save.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.ui.components.SanibonaniButton

@Composable
fun EmptyState(
    icon        : String,
    title       : String,
    description : String,
    modifier    : Modifier = Modifier,
    actionLabel : String?  = null,
    onAction    : (() -> Unit)? = null
) {
    Column(
        modifier            = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val haptic = LocalHapticFeedback.current
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 48.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color      = MaterialTheme.colorScheme.onSurface,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text       = description,
            style      = MaterialTheme.typography.bodyMedium,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign  = TextAlign.Center,
            lineHeight = 20.sp
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            SanibonaniButton(
                text = actionLabel,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAction()
                }
            )
        }
    }
}
