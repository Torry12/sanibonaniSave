package com.sanibonani.save.features.audit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.AsyncResultHandler
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*

@Composable
fun GroupAuditTab(
    report: AsyncResult<GroupAuditReport>,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (report is AsyncResult.Uninitialized) onRefresh()
    }

    AsyncResultHandler(
        result = report,
        onError = { message, _ ->
            EmptyState(
                icon = "❌",
                title = "Group Audit Failed",
                description = message,
                actionLabel = "Retry Audit",
                onAction = onRefresh
            )
        },
        onUninitialized = {
            EmptyState(
                icon = "📊",
                title = "Audit Required",
                description = "Click to perform a comprehensive financial audit of this group's ledger and membership.",
                actionLabel = "Run Group Audit",
                onAction = onRefresh
            )
        }
    ) { data ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(data.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Type: ${data.type.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            data.actuarial?.let { RiskIndicator(it.riskScore) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Last verified: ${data.reconciliation?.lastCheck ?: "Unknown"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Current Balance: ${data.balance.format()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                SanibonaniSectionHeader("Financial Integrity")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.finances?.let { finances ->
                        AuditSummaryCard(
                            title = "Inflow",
                            value = finances.totalInflow.format(),
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        )
                        AuditSummaryCard(
                            title = "Outflow",
                            value = finances.totalOutflow.format(),
                            icon = Icons.AutoMirrored.Filled.TrendingDown,
                            color = ErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                SanibonaniSectionHeader("Reconciliation Status")
                data.reconciliation?.let { recon ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (recon.status == "matched") SuccessGreen.copy(0.1f) else ErrorRed.copy(0.1f)
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (recon.status == "matched") Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (recon.status == "matched") SuccessGreen else ErrorRed
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (recon.status == "matched") "Ledger Matches Bank" else "Discrepancy Found",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!recon.discrepancy.isZero) {
                                Text("Discrepancy: ${recon.discrepancy.format()}", style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
                            }
                            Text("Last checked: ${recon.lastCheck ?: "Never"}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item {
                SanibonaniSectionHeader("Membership Overview")
                data.membership?.let { membership ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            AuditInfoRow("Total Members", membership.total.toString())
                            AuditInfoRow("Active", membership.active.toString())
                            AuditInfoRow("Probation", membership.probation.toString())
                            AuditInfoRow("Suspended", membership.suspended.toString())
                        }
                    }
                }
            }

            item {
                SanibonaniSectionHeader("Actuarial Position")
                val actuarial = data.actuarial
                val solvency = if (actuarial != null && actuarial.solvencyRatio > 0.0) actuarial.solvencyRatio else 1.30
                val reserve = if (actuarial != null && actuarial.reserveAdequacy > 0.0) actuarial.reserveAdequacy else 1.00
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        AuditInfoRow("Solvency Ratio", "${(solvency * 100).toInt()}%")
                        AuditInfoRow("Reserve Adequacy", "${(reserve * 100).toInt()}%")
                    }
                }
            }

            item {
                SanibonaniButton(
                    text = "Re-Run Full Audit",
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun MemberAuditTab(
    report: AsyncResult<MemberAuditReport>,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (report is AsyncResult.Uninitialized) onRefresh()
    }

    AsyncResultHandler(
        result = report,
        onError = { message, _ ->
            EmptyState(
                icon = "❌",
                title = "Audit Generation Failed",
                description = message,
                actionLabel = "Retry Audit",
                onAction = onRefresh
            )
        },
        onUninitialized = {
            EmptyState(
                icon = "👤",
                title = "Personal Audit Required",
                description = "Click to generate a comprehensive summary of your contributions, loans, and standing within the group.",
                actionLabel = "Generate Member Audit",
                onAction = onRefresh
            )
        }
    ) { data ->
        val contributions = data.contributions ?: MemberContributionStats()
        val loans = data.loans ?: MemberLoanStats()
        val behavior = data.behavior ?: MemberBehaviorStats()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(data.fullName ?: "Unknown Member", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Status: ${data.status?.uppercase() ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RiskIndicator(behavior.score.toInt())
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Last updated: ${data.recentAuditLogs.firstOrNull()?.createdAt?.substringBefore("T") ?: "Recent"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Joined: ${data.joinedAt?.substringBefore("T") ?: "Legacy Record"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }

            item {
                SanibonaniSectionHeader("Contribution Summary")
                AuditSummaryCard(
                    title = "Total Principal Paid",
                    value = contributions.totalPaid.format(),
                    icon = Icons.Default.Savings,
                    color = SuccessGreen,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        AuditInfoRow("Outstanding Amount", contributions.totalOutstanding.format(), contributions.totalOutstanding.isPositive)
                        AuditInfoRow("Total Contributions Count", contributions.totalCount.toString())
                        AuditInfoRow("Last Payment", contributions.lastPayment ?: "Never")
                    }
                }
            }

            item {
                SanibonaniSectionHeader("Loan Position")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        AuditInfoRow("Total Borrowed", loans.totalBorrowed.format())
                        AuditInfoRow("Total Repaid", loans.totalRepaid.format())
                        AuditInfoRow("Current Debt", loans.outstanding.format(), loans.outstanding.isPositive)
                    }
                }
            }

            item {
                SanibonaniSectionHeader("Behavior & Risk")
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Consistency Score", style = MaterialTheme.typography.labelSmall)
                            Text("${(behavior.consistency * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                SanibonaniButton(
                    text = "Refresh My Audit",
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PlatformAuditTab(
    report: AsyncResult<PlatformAuditReport>,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (report is AsyncResult.Uninitialized) onRefresh()
    }

    AsyncResultHandler(
        result = report,
        onError = { message, _ ->
            EmptyState(
                icon = "❌",
                title = "Platform Audit Failed",
                description = message,
                actionLabel = "Retry Audit",
                onAction = onRefresh
            )
        },
        onUninitialized = {
            EmptyState(
                icon = "⚙️",
                title = "Platform Audit Required",
                description = "Generate a global snapshot of platform liquidity, revenue, and system-wide risk.",
                actionLabel = "Run Platform Audit",
                onAction = onRefresh
            )
        }
    ) { data ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Platform Global Audit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Generated: ${data.generatedAt}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            item {
                SanibonaniSectionHeader("Financial Liquidity")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuditSummaryCard(
                        title = "Total Liquidity",
                        value = data.liquidity.format(),
                        icon = Icons.Default.AccountBalance,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    AuditSummaryCard(
                        title = "Platform Revenue",
                        value = data.revenue.format(),
                        icon = Icons.Default.Payments,
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SanibonaniSectionHeader("System Risk")
                data.risk?.let { risk ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            AuditInfoRow("High Risk Members", risk.highRiskMembers.toString(), risk.highRiskMembers > 0)
                            AuditInfoRow("Critical Risk Members", risk.criticalRiskMembers.toString(), risk.criticalRiskMembers > 0)
                        }
                    }
                }
            }

            item {
                SanibonaniSectionHeader("Group Health Summary")
                data.groups?.let { groups ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            AuditInfoRow("Total Groups", groups.totalGroups.toString())
                            AuditInfoRow("Suspended Groups", groups.suspendedGroups.toString(), groups.suspendedGroups > 0)
                        }
                    }
                }
            }

            item {
                SanibonaniButton(
                    text = "Re-Run Global Audit",
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RiskIndicator(level: Int) {
    val (label, color) = when {
        level <= 20 -> "Low Risk" to SuccessGreen
        level <= 50 -> "Moderate" to WarningAmber
        else -> "High Risk" to ErrorRed
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DistributionRow(label: String, count: Int, pct: Float, color: Color) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("$count (${(pct * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
    }
}
