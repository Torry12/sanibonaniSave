package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.ReconciliationStatus
import com.sanibonani.save.features.admin.presentation.PlatformLedgerViewModel
import com.sanibonani.save.features.admin.presentation.state.PlatformAnalyticsUiState
import com.sanibonani.save.features.admin.presentation.state.PlatformLedgerUiState
import com.sanibonani.save.ui.components.AccordionHeader
import com.sanibonani.save.features.admin.ui.components.HeaderWithIcon
import com.sanibonani.save.ui.components.DetailRow
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.SanibonaniDropdown
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.ui.components.GlassCard
import com.sanibonani.save.ui.components.InfoBox
import com.sanibonani.save.ui.theme.ErrorRed
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.ui.theme.SuccessGreen

@Composable
fun PlatformLedgerTab(
    state: PlatformLedgerUiState,
    analyticsState: PlatformAnalyticsUiState,
    vm: PlatformLedgerViewModel
) {
    var selectedEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var selectedGroupIdForAudit by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeepConsolidateConfirm by remember { mutableStateOf<String?>(null) }

    val groupNames = analyticsState.groupNames
    val groupProvinces = analyticsState.groupProvinces

    val groupedEntries = remember(state.platformLedger, groupNames, groupProvinces) {
        state.platformLedger
            .groupBy { it.createdAt?.substringBefore("T") ?: "N/A" }
            .mapValues { (_, entries) ->
                entries.sortedWith(
                    compareBy<LedgerEntry> { groupProvinces[it.groupId] ?: "Other" }
                        .thenBy { groupNames[it.groupId] ?: "Platform Internal" }
                )
            }
    }
    val expandedDates = remember { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            HeaderWithIcon(Icons.Default.AccountBalance, stringResource(R.string.ui_system_wide_digital_ledger))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = { vm.downloadPlatformStatement() }) {
                    Icon(Icons.Default.PictureAsPdf, stringResource(R.string.ui_download_pdf), tint = Navy)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.load() }) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.ui_refresh), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        InfoBox(
            stringResource(R.string.ui_central_record_of_registration_fees_and_), type = InfoType.INFO
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
                    title = stringResource(R.string.ui_group_fund_audits_reconciliation),
                    icon = Icons.Default.AccountBalance,
                    isExpanded = true,
                    onClick = { },
                    count = 0
                )

                GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
                    Text(stringResource(R.string.ui_select_group_to_audit), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))

                    SanibonaniDropdown(
                    label = stringResource(R.string.ui_target_group),
                    items = analyticsState.groups,
                    selectedItem = analyticsState.groups.find { it.id == selectedGroupIdForAudit },
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
                                    text = stringResource(R.string.ui_standard_audit),
                                    onClick = { vm.reconcileGroup(gid) },
                                    isLoading = state.isReconciling,
                                    containerColor = Navy,
                                    modifier = Modifier.weight(1f)
                                )

                                SanibonaniButton(
                                    text = stringResource(R.string.ui_repair_totals),
                                    onClick = { vm.repairGroupFinancials(gid) },
                                    isLoading = state.isRepairing,
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            SanibonaniButton(
                                text = stringResource(R.string.ui_deep_ledger_rebuild),
                                onClick = { showDeepConsolidateConfirm = gid },
                                isLoading = state.isConsolidating,
                                containerColor = Color.Red,
                                contentColor = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        state.lastAuditResult?.let { res ->
                            if (res.groupId == gid) {
                                Spacer(Modifier.height(16.dp))
                                val isBalanced = res.discrepancy.isZero
                                InfoBox(
                                    message = if (isBalanced) stringResource(R.string.ui_ledger_is_balanced) else stringResource(R.string.ui_discrepancy_found_label, com.sanibonani.save.ui.utils.formatZAR(res.discrepancy)),
                                    type = if (isBalanced) InfoType.SUCCESS else InfoType.WARNING
                                )
                            }
                        }

                        val history = state.groupReconciliations[gid] ?: emptyList()
                        if (history.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.ui_audit_history), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            history.forEach { record ->
                                val color = if (record.status == ReconciliationStatus.BALANCED) SuccessGreen else ErrorRed
                                val statusText = when(record.status) {
                                    ReconciliationStatus.BALANCED -> "Balanced"
                                    ReconciliationStatus.DISCREPANCY -> "Discrepancy"
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

            if (state.platformLedger.isEmpty() && !state.hasLoaded && state.isLoading) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.ui_loading_platform_data))
                    }
                }
            } else if (state.platformLedger.isEmpty() && !state.hasLoaded) {
                item {
                    EmptyState(
                        icon = "⚠️",
                        title = stringResource(R.string.ui_platform_ledger_unavailable),
                        description = stringResource(R.string.ui_platform_ledger_load_failed),
                        actionLabel = stringResource(R.string.ui_retry),
                        onAction = { vm.load() }
                    )
                }
            } else if (state.platformLedger.isEmpty()) {
                item {
                    EmptyState(
                        icon = "🧾",
                        title = stringResource(R.string.ui_no_transactions),
                        description = stringResource(R.string.ui_no_transactions_description),
                        actionLabel = stringResource(R.string.ui_refresh),
                        onAction = { vm.load() }
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
                    items(entries, key = { it.id ?: it.hashCode().toString() }) { entry ->
                        PlatformLedgerEntryItem(
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
            title = { Text(stringResource(R.string.ui_critical_ledger_reconstruction)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${stringResource(R.string.ui_target_group)}: ${groupNames[gid]}")
                    Text(stringResource(R.string.ui_deep_ledger_rebuild_warning))
                    Text(stringResource(R.string.ui_accounting_desync_warning), fontWeight = FontWeight.Bold, color = Color.Red)
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
                    Text(stringResource(R.string.ui_reconstruct_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeepConsolidateConfirm = null }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }
}

@Composable
fun PlatformLedgerEntryItem(entry: LedgerEntry, groupName: String, onClick: () -> Unit) {
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
        title = { Text(stringResource(R.string.ui_transaction_proof), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow(stringResource(R.string.ui_group), groupName)
                DetailRow(stringResource(R.string.ui_reference), entry.transactionId ?: "System Internal")
                DetailRow(stringResource(R.string.ui_claim_amount), com.sanibonani.save.ui.utils.formatZAR(entry.amount))
                DetailRow(stringResource(R.string.ui_balance_after), com.sanibonani.save.ui.utils.formatZAR(entry.balanceAfter))
                DetailRow(stringResource(R.string.ui_category), entry.category)
                DetailRow("Date", entry.createdAt ?: "N/A")
                DetailRow(stringResource(R.string.ui_description), entry.description)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close)) }
        }
    )
}
