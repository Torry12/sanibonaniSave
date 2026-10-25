package com.sanibonani.save.features.admin.ui.components

import android.content.Context
import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.data.utils.FileDownloader
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.admin.presentation.*
import com.sanibonani.save.features.admin.presentation.state.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog


@Composable
fun ActuarialHealthDialog(
    group: Group,
    metrics: ActuarialMetrics?,
    healthScore: GroupHealthScore?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onNavigateToHealthScore: (String) -> Unit,
    onDeleteGroup: ((String) -> Unit)? = null
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete Group from Platform?", color = ErrorRed, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete \"${group.name}\"? This action will remove the group and all its records from the platform.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDelete = false
                        onDismiss()
                        onDeleteGroup?.invoke(group.id ?: "")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Actuarial Health: ${group.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (onDeleteGroup != null) {
                    TextButton(
                        onClick = { showConfirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                    ) {
                        Text("Delete Group", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                metrics?.let { m ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricRow(
                            label = "Actuarial Health Score",
                            value = "${m.compositeRiskScore}/100",
                            color = when {
                                m.compositeRiskScore >= 80 -> SuccessGreen
                                m.compositeRiskScore >= 55 -> WarningAmber
                                else -> ErrorRed
                            }
                        )

                        if (m.keyMetrics.isNotEmpty()) {
                            m.keyMetrics.forEach { metric ->
                                MetricRow(
                                    label = metric.title,
                                    value = metric.resultText,
                                    color = when (metric.status) {
                                        "OK", "OPTIMAL" -> SuccessGreen
                                        "WARNING", "LOW" -> WarningAmber
                                        "CRITICAL" -> ErrorRed
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        } else {
                            MetricRow("Reserve Adequacy", "${m.reserveAdequacyPct}%", MaterialTheme.colorScheme.primary)
                            MetricRow("Solvency Margin", "${m.solvencyMarginPct}%", MaterialTheme.colorScheme.primary)
                            MetricRow("Expected Claims (Ann)", formatZAR(m.expectedAnnualClaims), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                } else {
                    healthScore?.let { score ->
                        HorizontalDivider(color = LightGray.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(stringResource(R.string.ui_composite_health_score), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Text("${score.overallScore}/100", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (score.zone == RiskZone.GREEN) SuccessGreen else if (score.zone == RiskZone.YELLOW) WarningAmber else ErrorRed)
                            }

                            Surface(
                                color = (if (score.zone == RiskZone.GREEN) SuccessGreen else if (score.zone == RiskZone.YELLOW) WarningAmber else ErrorRed).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    score.zone.name,
                                    modifier = Modifier.padding(8.dp, 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (score.zone == RiskZone.GREEN) SuccessGreen else if (score.zone == RiskZone.YELLOW) WarningAmber else ErrorRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onNavigateToHealthScore(group.id ?: "")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.ui_view_full_health_report))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_close))
            }
        }
    )
}