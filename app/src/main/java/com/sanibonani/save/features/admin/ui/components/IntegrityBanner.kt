package com.sanibonani.save.features.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.IssueSeverity
import com.sanibonani.save.domain.model.VerificationIssue
import com.sanibonani.save.ui.theme.ErrorRed
import com.sanibonani.save.ui.theme.NavyDeep
import com.sanibonani.save.ui.theme.WarningYellow

@Composable
fun IntegrityBanner(
    issue: VerificationIssue?,
    onAction: () -> Unit = {}
) {
    if (issue == null) return

    val isCritical = issue.severity == IssueSeverity.CRITICAL
    val color = if (isCritical) ErrorRed else WarningYellow

    // High-fidelity dark theme (matches Platform Admin Dashboard design)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NavyDeep,
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp).padding(top = 2.dp)
            )
            spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${issue.category} Consistency Warning",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = issue.message,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
                issue.suggestedAction?.let { actionText ->
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Action: $actionText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        TextButton(
                            onClick = onAction,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                "FIX NOW",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun spacer(modifier: Modifier) {
    Spacer(modifier)
}
