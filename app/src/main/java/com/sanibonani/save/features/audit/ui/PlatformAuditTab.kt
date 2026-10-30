package com.sanibonani.save.features.audit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.AsyncResultHandler
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*

@Composable
fun PlatformAuditTab(
    report: AsyncResult<PlatformAuditReport>,
    onLoad: () -> Unit,
    onRegenerate: () -> Unit
) {
    LaunchedEffect(Unit) {
        // Entering the tab reads the stored snapshot. Regenerating is left to the buttons, because
        // the RPC that builds it writes a history row every time it runs.
        if (report is AsyncResult.Uninitialized) onLoad()
    }

    AsyncResultHandler(
        result = report,
        onError = { message, _ ->
            EmptyState(
                icon = "❌",
                title = "Platform Audit Failed",
                description = message,
                actionLabel = "Retry Audit",
                onAction = onRegenerate
            )
        },
        onUninitialized = {
            EmptyState(
                icon = "⚙️",
                title = "Platform Audit Required",
                description = "Generate a global snapshot of platform liquidity, revenue, and system-wide risk.",
                actionLabel = "Run Platform Audit",
                onAction = onRegenerate
            )
        }
    ) { data ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { PlatformAuditSnapshotCard(data.generatedAt) }
            item { PlatformLiquiditySection(data) }
            item { PlatformRiskSection(data.risk) }
            item { PlatformGroupHealthSection(data.groups) }
            item { PlatformAuditAction(onRegenerate) }
        }
    }
}

@Composable
private fun PlatformAuditSnapshotCard(generatedAt: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Platform Global Audit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Generated: $generatedAt", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PlatformLiquiditySection(report: PlatformAuditReport) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SanibonaniSectionHeader("Financial Liquidity")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuditSummaryCard(
                title = "Total Liquidity",
                value = report.liquidity.format(),
                icon = Icons.Default.AccountBalance,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            AuditSummaryCard(
                title = "Platform Revenue",
                value = report.revenue.format(),
                icon = Icons.Default.Payments,
                color = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlatformRiskSection(risk: PlatformRiskStats?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SanibonaniSectionHeader("System Risk")
        risk?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    AuditInfoRow("High Risk Members", it.highRiskMembers.toString(), it.highRiskMembers > 0)
                    AuditInfoRow("Critical Risk Members", it.criticalRiskMembers.toString(), it.criticalRiskMembers > 0)
                }
            }
        }
    }
}

@Composable
private fun PlatformGroupHealthSection(groups: PlatformGroupStats?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SanibonaniSectionHeader("Group Health Summary")
        groups?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    AuditInfoRow("Total Groups", it.totalGroups.toString())
                    AuditInfoRow("Suspended Groups", it.suspendedGroups.toString(), it.suspendedGroups > 0)
                }
            }
        }
    }
}

@Composable
private fun PlatformAuditAction(onRegenerate: () -> Unit) {
    SanibonaniButton(
        text = "Re-Run Global Audit",
        onClick = onRegenerate,
        modifier = Modifier.fillMaxWidth()
    )
}
