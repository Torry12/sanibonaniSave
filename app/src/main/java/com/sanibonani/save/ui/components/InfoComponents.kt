package com.sanibonani.save.ui.components

import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.IssueSeverity
import com.sanibonani.save.domain.model.VerificationResult
import com.sanibonani.save.ui.theme.*

/**
 * Shared informational UI: severity enums, the inline [InfoBox], a data-integrity
 * summary, a labelled detail section, and the global error banner.
 *
 * Consolidated here from the former `features.common.ui` and `ui.components`
 * copies, which were importing each other. There is now exactly one home for
 * shared UI (`com.sanibonani.save.ui.components`).
 */

/** Defines the visual style for the logout button. */
enum class LogoutButtonStyle {
    Filled,
    Outlined,
    MenuItem,
    IconButton
}

/** Standard info box types. */
enum class InfoType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

@Composable
fun InfoBox(
    message: String,
    modifier: Modifier = Modifier,
    type: InfoType = InfoType.INFO
) {
    val (color, icon) = when (type) {
        InfoType.INFO    -> InfoBlue to Icons.Default.Info
        InfoType.SUCCESS -> SuccessGreen to Icons.Default.CheckCircle
        InfoType.WARNING -> WarningAmber to Icons.Default.Warning
        InfoType.ERROR   -> ErrorRed to Icons.Default.Error
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color    = color.copy(alpha = 0.15f),
        shape    = RoundedCornerShape(16.dp),
        border   = BorderStroke(1.5.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun VerificationSummary(
    result: VerificationResult,
    modifier: Modifier = Modifier
) {
    if (result.isValid) return

    val issues = result.issues
    if (issues.isEmpty()) return

    val criticalIssues = issues.filter { it.severity == IssueSeverity.CRITICAL }
    val warningIssues = issues.filter { it.severity == IssueSeverity.WARNING }

    val primaryIssue = criticalIssues.firstOrNull() ?: warningIssues.firstOrNull() ?: issues.first()
    val type = when (primaryIssue.severity) {
        IssueSeverity.CRITICAL -> InfoType.ERROR
        IssueSeverity.WARNING -> InfoType.WARNING
        else -> InfoType.INFO
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoBox(
            message = "Data Integrity: ${primaryIssue.message}${if (issues.size > 1) " (+${issues.size - 1} more)" else ""}",
            type = type
        )
    }
}

@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            title.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
    var isVisible by remember { mutableStateOf(true) }

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            color = if (isCritical) ErrorRed else WarningAmber,
            contentColor = if (isCritical) Color.White else Color.Black
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

                IconButton(onClick = {
                    isVisible = false
                    onDismiss()
                }) {
                    Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
