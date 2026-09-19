package com.sanibonani.save.features.admin.ui.tabs.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.ui.components.AccordionHeader
import com.sanibonani.save.features.admin.ui.components.HeaderWithIcon
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.admin.presentation.PlatformAdminViewModel
import com.sanibonani.save.features.admin.presentation.state.PlatformAdminUiState

@Composable
fun PlatformLedgerTab(
    state: PlatformAdminUiState,
    vm: PlatformAdminViewModel
) {
    var selectedEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var selectedGroupIdForAudit by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeepConsolidateConfirm by remember { mutableStateOf<String?>(null) }

    val groupNames = remember(state.groups) { state.groups.associate { (it.id ?: "") to it.name } }

    val groupedEntries = remember(state.platformLedger) {
        state.platformLedger.groupBy { it.createdAt?.substringBefore("T") ?: "N/A" }
    }
    val expandedDates = remember { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            HeaderWithIcon(Icons.Default.AccountBalance, "System-Wide Digital Ledger")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = { vm.downloadPlatformStatement() }) {
                    Icon(Icons.Default.PictureAsPdf, "Download PDF", tint = Navy)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.loadData() }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        InfoBox(
            stringResource(R.string.ui_central_record_of_registration_fees_and_),
            InfoType.INFO
        )

        if (state.isExporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Audit & Reconciliation ──
            item {
                AccordionHeader(
                    title = "Group Fund Audits (Reconciliation)",
                    icon = Icons.Default.AccountBalance,
                    isExpanded = true,
                    onClick = { },
                    count = 0
                )

                GlassCard(color = MaterialTheme.colorScheme.surface) {
                    Text("Select a group to perform a high-integrity financial audit.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))

                    SanibonaniDropdown(
                        label = "Target Group",
                        items = state.groups,
                        selectedItem = state.groups.find { it.id == selectedGroupIdForAudit },
                        onItemSelected = {
                            selectedGroupIdForAudit = it.id
                            it.id?.let { id -> vm.fetchReconciliations(id) }
                        },
                        itemLabel = { it.name }
                    )

                    selectedGroupIdForAudit?.let { gid ->
                        Spacer(Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SanibonaniButton(
                                    text = "Standard Audit",
                                    onClick = { vm.reconcileGroup(gid) },
                                    isLoading = state.isReconciling,
                                    containerColor = Navy,
                                    modifier = Modifier.weight(1f)
                                )

                                SanibonaniButton(
                                    text = "Repair Totals",
                                    onClick = { vm.repairGroupFinancials(gid) },
                                    isLoading = state.isRepairing,
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            SanibonaniButton(
                                text = "DEEP LEDGER REBUILD",
                                onClick = { showDeepConsolidateConfirm = gid },
                                isLoading = state.isConsolidating,
                                containerColor = Color.Red,
                                contentColor = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        val history = state.groupReconciliations[gid] ?: emptyList()
                        if (history.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text("Audit History", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            history.forEach { record ->
                                val color = if (record.status == ReconciliationStatus.BALANCED) SuccessGreen else ErrorRed
                                val statusText = when(record.status) {
                                    ReconciliationStatus.BALANCED -> "Balanced"
                                    ReconciliationStatus.DISCREPANCY_FOUND -> "Discrepancy"
                                    ReconciliationStatus.RESOLVED -> "Resolved"
                                    else -> "Pending"
                                }

                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(record.createdAt?.take(16)?.replace("T", " ") ?: "", fontSize = 12.sp)
                                    Text(statusText, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            if (state.platformLedger.isEmpty() && !state.isLoading) {
                item {
                    EmptyState(
                        icon = "🧾",
                        title = "No transactions",
                        description = "Platform-wide financial movements will appear here once groups start registering.",
                        actionLabel = "Refresh",
                        onAction = { vm.loadData() }
                    )
                }
            }

            groupedEntries.forEach { (date, entries) ->
                val isExpanded = expandedDates[date] != false
                item {
                    AccordionHeader(
                        title = date,
                        count = entries.size,
                        isExpanded = isExpanded,
                        onClick = { expandedDates[date] = !isExpanded },
                        label = "transactions",
                        isSubHeader = true
                    )
                }

                if (isExpanded) {
                    items(entries) { entry ->
                        LedgerEntryItem(
                            entry = entry,
                            groupName = groupNames[entry.groupId] ?: "Platform Internal",
                            onClick = { selectedEntry = entry }
                        )
                    }
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        LedgerEntryDetailDialog(
            entry = entry,
            groupName = groupNames[entry.groupId] ?: "Platform Internal",
            onDismiss = { selectedEntry = null }
        )
    }

    showDeepConsolidateConfirm?.let { gid ->
        AlertDialog(
            onDismissRequest = { showDeepConsolidateConfirm = null },
            title = { Text("Critical Ledger Reconstruction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Target: ${groupNames[gid]}")
                    Text("This operation will DESTROY and REBUILD the audit trail for this group using raw sub-transaction data. It fixes chronological balance inaccuracies.")
                    Text("This should only be used for groups with severe accounting desync.", fontWeight = FontWeight.Bold, color = Color.Red)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.consolidateGroupLedger(gid)
                        showDeepConsolidateConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Reconstruct Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeepConsolidateConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LedgerEntryItem(entry: LedgerEntry, groupName: String, onClick: () -> Unit) {
    val isInflow = entry.amount.isPositive
    val color = if (isInflow) SuccessGreen else ErrorRed

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(4.dp, 32.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.description, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(groupName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    text = (if (isInflow) "+" else "") + com.sanibonani.save.ui.utils.formatZAR(entry.amount),
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(entry.category, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun LedgerEntryDetailDialog(entry: LedgerEntry, groupName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaction Proof", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("Group", groupName)
                DetailRow("Reference", entry.transactionId ?: "System Internal")
                DetailRow("Amount", com.sanibonani.save.ui.utils.formatZAR(entry.amount))
                DetailRow("Balance After", com.sanibonani.save.ui.utils.formatZAR(entry.balanceAfter ?: com.sanibonani.save.domain.model.Money.ZERO))
                DetailRow("Category", entry.category)
                DetailRow("Date", entry.createdAt ?: "N/A")
                DetailRow("Description", entry.description)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
