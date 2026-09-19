package com.sanibonani.save.features.common.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.ui.theme.*

@Composable
fun VerificationSummary(
    result: com.sanibonani.save.domain.model.VerificationResult,
    modifier: Modifier = Modifier
) {
    if (result.isValid) return

    val issues = result.issues
    if (issues.isEmpty()) return

    val criticalIssues = issues.filter { it.severity == com.sanibonani.save.domain.model.IssueSeverity.CRITICAL }
    val warningIssues = issues.filter { it.severity == com.sanibonani.save.domain.model.IssueSeverity.WARNING }

    val primaryIssue = criticalIssues.firstOrNull() ?: warningIssues.firstOrNull() ?: issues.first()
    val type = when (primaryIssue.severity) {
        com.sanibonani.save.domain.model.IssueSeverity.CRITICAL -> InfoType.ERROR
        com.sanibonani.save.domain.model.IssueSeverity.WARNING -> InfoType.WARNING
        else -> InfoType.INFO
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoBox(
            message = "Data Integrity: ${primaryIssue.message}${if (issues.size > 1) " (+${result.issues.size - 1} more)" else ""}",
            type = type
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, isBold: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.6f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    }
}

@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun GlobalErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    isCritical: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isCritical) ErrorRed else WarningAmber,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Error, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))

            if (actionLabel != null) {
                TextButton(
                    onClick = onAction ?: onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(actionLabel.uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun GroupCardShimmer() {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition()
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Card(
        modifier = Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(Modifier.fillMaxSize().background(brush))
    }
}

enum class InfoType { INFO, SUCCESS, WARNING, ERROR }

@Composable
fun InfoBox(message: String, type: InfoType = InfoType.INFO) {
    val (bg, fg, icon) = when (type) {
        InfoType.INFO    -> Triple(InfoBg, InfoBlue, Icons.Default.Info)
        InfoType.SUCCESS -> Triple(SuccessBg, SuccessGreen, Icons.Default.CheckCircle)
        InfoType.WARNING -> Triple(WarningBg, WarningAmber, Icons.Default.Warning)
        InfoType.ERROR   -> Triple(ErrorBg, ErrorRed, Icons.Default.Error)
    }

    // Since backgrounds are fixed pastel colors, we force a dark text color for readability
    val textColor = Color(0xFF1A1C1E) // High contrast dark charcoal

    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
