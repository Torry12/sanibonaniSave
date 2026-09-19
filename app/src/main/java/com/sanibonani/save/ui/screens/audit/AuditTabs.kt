package com.sanibonani.save.ui.screens.audit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.GroupAuditReport
import com.sanibonani.save.domain.model.MemberAuditReport
import com.sanibonani.save.domain.model.PlatformAuditReport
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.ui.utils.formatZAR

@Composable
fun PlatformAuditTab(
    report: PlatformAuditReport?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (report == null) onRefresh()
    }

    if (isLoading && report == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (report == null) {
        EmptyState(
            title = "No Audit Data",
            description = "Click to generate platform audit report",
            actionLabel = "Generate Report",
            onAction = onRefresh
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AuditSectionHeader("Platform Liquidity & Revenue")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AuditSummaryCard(
                        title = "Total Liquidity",
                        value = report.liquidity.formatZAR(),
                        icon = Icons.Default.AccountBalanceWallet,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    AuditSummaryCard(
                        title = "Total Revenue",
                        value = report.revenue.formatZAR(),
                        icon = Icons.Default.TrendingUp,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                AuditSectionHeader("System Risk Profile")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        AuditInfoRow("High Risk Members", report.risk.highRiskMembers.toString(), report.risk.highRiskMembers > 0)
                        AuditInfoRow("Critical Risk Members", report.risk.criticalRiskMembers.toString(), report.risk.criticalRiskMembers > 0)
                    }
                }
            }

            item {
                AuditSectionHeader("Group Ecosystem")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        AuditInfoRow("Total Groups", report.groups.totalGroups.toString())
                        AuditInfoRow("Suspended Groups", report.groups.suspendedGroups.toString(), report.groups.suspendedGroups > 0)
                    }
                }
            }
        }
    }
}

@Composable
fun GroupAuditTab(
    report: GroupAuditReport?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (report == null) onRefresh()
    }

    if (isLoading && report == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (report == null) {
        EmptyState(
            title = "No Group Audit",
            description = "Click to generate group audit report",
            actionLabel = "Generate Report",
            onAction = onRefresh
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AuditSectionHeader("Financial Health")
                AuditSummaryCard(
                    title = "Group Balance",
                    value = report.balance.formatZAR(),
                    icon = Icons.Default.AccountBalance,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Total Inflow", style = MaterialTheme.typography.labelSmall)
                            Text(report.finances.totalInflow.formatZAR(), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                    }
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Total Outflow", style = MaterialTheme.typography.labelSmall)
                            Text(report.finances.totalOutflow.formatZAR(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                AuditSectionHeader("Reconciliation Status")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (report.reconciliation.status == "balanced") Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    )
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (report.reconciliation.status == "balanced") Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (report.reconciliation.status == "balanced") Color(0xFF4CAF50) else Color(0xFFFFA000)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Status: ${report.reconciliation.status.uppercase()}", fontWeight = FontWeight.Bold)
                            if (report.reconciliation.discrepancy.amount != 0.0) {
                                Text("Discrepancy: ${report.reconciliation.discrepancy.formatZAR()}", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item {
                AuditSectionHeader("Membership Breakdown")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        AuditInfoRow("Active", report.membership.active.toString())
                        AuditInfoRow("Probation", report.membership.probation.toString())
                        AuditInfoRow("Suspended", report.membership.suspended.toString(), report.membership.suspended > 0)
                    }
                }
            }
        }
    }
}

@Composable
fun MemberAuditTab(
    report: MemberAuditReport?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (report == null) onRefresh()
    }

    if (isLoading && report == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (report == null) {
        EmptyState(
            title = "No Member Audit",
            description = "Click to generate member audit report",
            actionLabel = "Generate Report",
            onAction = onRefresh
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AuditSectionHeader("Contribution Summary")
                AuditSummaryCard(
                    title = "Total Paid",
                    value = report.contributions.totalPaid.formatZAR(),
                    icon = Icons.Default.Savings,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        AuditInfoRow("Outstanding Amount", report.contributions.totalOutstanding.formatZAR(), report.contributions.totalOutstanding.amount > 0)
                        AuditInfoRow("Total Contributions Count", report.contributions.totalCount.toString())
                        AuditInfoRow("Last Payment", report.contributions.lastPayment ?: "Never")
                    }
                }
            }

            item {
                AuditSectionHeader("Loan Position")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        AuditInfoRow("Total Borrowed", report.loans.totalBorrowed.formatZAR())
                        AuditInfoRow("Total Repaid", report.loans.totalRepaid.formatZAR())
                        AuditInfoRow("Current Debt", report.loans.outstanding.formatZAR(), report.loans.outstanding.amount > 0)
                    }
                }
            }

            item {
                AuditSectionHeader("Behavior & Risk")
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Consistency Score", style = MaterialTheme.typography.labelSmall)
                            Text("${(report.behavior.consistency * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        RiskIndicator(report.behavior.riskLevel)
                    }
                }
            }
        }
    }
}
