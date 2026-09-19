package com.sanibonani.save.ui.screens.admin.components

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
import com.sanibonani.save.ui.theme.WarningGold

@Composable
fun IntegrityBanner(issue: VerificationIssue?) {
    if (issue == null) return

    val color = if (issue.severity == IssueSeverity.CRITICAL) ErrorRed else WarningGold

    Box(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${issue.category} Consistency Warning",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    text = issue.message,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp
                )
                issue.suggestedAction?.let {
                    Text(
                        text = "Action: $it",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
