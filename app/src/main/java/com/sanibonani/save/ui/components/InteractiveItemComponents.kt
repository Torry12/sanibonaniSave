package com.sanibonani.save.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DashboardItemCard(
    title: String,
    subtitle: androidx.compose.ui.text.AnnotatedString,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val displayColor = accentColor ?: MaterialTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current

    val borderGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.1f else 0.5f),
            displayColor.copy(alpha = 0.2f),
            Color.Black.copy(alpha = if (isDark) 0.5f else 0.1f)
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.8f else 0.95f)),
        border = BorderStroke(1.dp, borderGradient),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.03f else 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(displayColor.copy(alpha = 0.2f), displayColor.copy(alpha = 0.05f))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = displayColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        }
    }
}

@Composable
fun ModernNavigationLink(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    DashboardItemCard(
        title = title,
        subtitle = androidx.compose.ui.text.AnnotatedString(subtitle),
        icon = icon,
        onClick = onClick,
        accentColor = accentColor,
        modifier = modifier
    )
}

@Composable
fun SanibonaniListItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing?.invoke() ?: Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
