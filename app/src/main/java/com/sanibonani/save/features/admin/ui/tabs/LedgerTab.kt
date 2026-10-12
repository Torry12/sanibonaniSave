package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.BankTransaction
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.ReconciliationStatus
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.ui.utils.formatDate
import com.sanibonani.save.features.admin.presentation.AdminFinancialViewModel
import com.sanibonani.save.features.admin.presentation.state.AdminFinancialUiState
import com.sanibonani.save.ui.components.DetailRow
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.SanibonaniSectionHeader
import com.sanibonani.save.ui.components.SanibonaniTextField

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LedgerTab(
    state: AdminFinancialUiState,
    vm: AdminFinancialViewModel? = null
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<LedgerEntry>()
    var selectedLedgerEntry by remember { mutableStateOf<LedgerEntry?>(null) }

    LaunchedEffect(selectedLedgerEntry) {
        if (selectedLedgerEntry != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, selectedLedgerEntry)
        } else {
            if (navigator.canNavigateBack()) {
                navigator.navigateBack()
            }
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            LedgerListSection(state, vm) { selectedLedgerEntry = it }
        },
        detailPane = {
            selectedLedgerEntry?.let { entry ->
                LedgerDetailSection(entry)
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a transaction to view details")
            }
        }
    )
}

@Composable
fun LedgerListSection(
    state: AdminFinancialUiState,
    vm: AdminFinancialViewModel?,
    onEntryClick: (LedgerEntry) -> Unit
) {
    var selectedBankTx by remember { mutableStateOf<BankTransaction?>(null) }
    var showIgnoreDialog by remember { mutableStateOf<BankTransaction?>(null) }
    var ignoreReason by remember { mutableStateOf("") }
    var showDeepAuditConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SanibonaniSectionHeader(
                    title = "📜 " + stringResource(R.string.ui_group_ledger),
                    subtitle = stringResource(R.string.ui_immutable_audit_trail)
                )
            }
            if (vm != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!state.group?.financialProviderId.isNullOrBlank()) {
                        IconButton(
                            onClick = { vm.refreshBankData() },
                            enabled = !state.isRefreshingBank
                        ) {
                            if (state.isRefreshingBank) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.Sync, "Sync Bank", tint = Navy)
                            }
                        }
                    }

                    SanibonaniButton(
                        text = "Audit Now",
                        onClick = { vm.performReconciliation() },
                        isLoading = state.isReconciling,
                        containerColor = Navy,
                        contentColor = Color.White,
                        modifier = Modifier.width(110.dp).height(38.dp)
                    )

                    IconButton(onClick = { vm.downloadGroupStatement() }) {
                        Icon(Icons.Default.PictureAsPdf, "Download PDF", tint = Navy)
                    }

                    IconButton(
                        onClick = { showDeepAuditConfirm = true },
                        enabled = !state.isConsolidating
                    ) {
                        if (state.isConsolidating) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Build, "Deep Rebuild", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }

        // ── Bank Transaction Reconciliation ──
        if (state.unreconciledBankTx.isNotEmpty()) {
            Text("Unreconciled Bank Transactions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items = state.unreconciledBankTx) { tx ->
                    Card(
                        modifier = Modifier.width(260.dp).clickable { selectedBankTx = tx },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatZAR(tx.amount), fontWeight = FontWeight.Black)
                                Text(formatDate(tx.transactionDate), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(tx.bankReference, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { selectedBankTx = tx }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(0.dp)) {
                                    Text("Reconcile", style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { showIgnoreDialog = tx }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(0.dp)) {
                                    Text("Ignore", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = LightGray.copy(0.3f))
        }

        if (state.ledger.isEmpty()) {
            EmptyState(
                icon = "🧾",
                title = stringResource(R.string.ui_no_transactions),
                description = stringResource(R.string.ui_once_payments_start_they_will_be_logged_h),
                actionLabel = "Refresh",
                onAction = { vm?.refreshBankData() }
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(items = state.ledger) { entry ->
                    LedgerEntryItem(entry) { onEntryClick(entry) }
                }
            }
        }
    }

    selectedBankTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { selectedBankTx = null },
            title = { Text("Reconcile Bank Transaction") },
            text = {
                Column {
                    Text("Reference: ${tx.bankReference}", fontWeight = FontWeight.Bold)
                    Text("Amount: ${formatZAR(tx.amount)}")
                    Spacer(Modifier.height(12.dp))
                    Text("Select a corresponding ledger entry to link this transaction.", style = MaterialTheme.typography.bodySmall)

                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        val candidates = state.ledger.filter { it.amount.abs() == tx.amount.abs() }
                        if (candidates.isEmpty()) {
                            item {
                                Text(
                                    text = "No ledger entries match this transaction amount. Please check the ledger or ignore this transaction if it's a duplicate.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        } else {
                            items(candidates) { entry ->
                                ListItem(
                                    headlineContent = { Text(entry.description) },
                                    supportingContent = { Text("${formatDate(entry.createdAt)} • ${formatZAR(entry.amount)}") },
                                    modifier = Modifier.clickable {
                                        vm?.reconcileManual(tx.id!!, entry.id!!)
                                        selectedBankTx = null
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedBankTx = null }) { Text("Cancel") } }
        )
    }

    showIgnoreDialog?.let { tx ->
        AlertDialog(
            onDismissRequest = { showIgnoreDialog = null },
            title = { Text("Ignore Transaction") },
            text = {
                Column {
                    Text("Why should this transaction be ignored?")
                    SanibonaniTextField(ignoreReason, { ignoreReason = it }, "Reason (e.g. Duplicate)")
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm?.ignoreBankTransaction(tx.id!!, ignoreReason)
                    showIgnoreDialog = null
                    ignoreReason = ""
                }) { Text("Ignore") }
            },
            dismissButton = { TextButton(onClick = { showIgnoreDialog = null }) { Text("Cancel") } }
        )
    }

    if (showDeepAuditConfirm) {
        AlertDialog(
            onDismissRequest = { showDeepAuditConfirm = false },
            title = { Text("Deep Ledger Rebuild") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This is a high-integrity recovery operation that will:")
                    Text("• Reconstruct the ledger from sub-transactions\n• Recalculate all running balances\n• Synchronize group totals", style = MaterialTheme.typography.bodySmall)
                    Text("This may take a moment. Use only if discrepancies persist after a standard audit.", fontWeight = FontWeight.Bold, color = Color.Red)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm?.consolidateLedger()
                        showDeepAuditConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Confirm Rebuild")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeepAuditConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LedgerEntryItem(entry: LedgerEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (entry.isPlatform) Navy.copy(alpha = 0.5f) else LightGray.copy(0.3f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (entry.isPlatform) {
                        Spacer(Modifier.width(8.dp))
                        PlatformBadge()
                    }
                }
                Text("${formatDate(entry.createdAt)} • ${entry.category.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Column(horizontalAlignment = Alignment.End) {
                val isPositive = !entry.amount.isNegative
                val color = if (isPositive) SuccessGreen else ErrorRed
                val prefix = if (isPositive) "+" else ""
                Text("$prefix${formatZAR(entry.amount)}", color = color, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun LedgerDetailSection(entry: LedgerEntry) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
    ) {
        Text("Transaction Detail", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        DetailRow("Description", entry.description)
        DetailRow("Category", entry.category.uppercase())
        DetailRow("Amount", formatZAR(entry.amount))
        DetailRow("Balance After", formatZAR(entry.balanceAfter))
        DetailRow("Date", formatDate(entry.createdAt))
        DetailRow("Platform Transaction", if (entry.isPlatform) "Yes" else "No")
    }
}

@Composable
fun PlatformBadge() {
    Surface(
        color = Navy.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, Navy.copy(alpha = 0.3f))
    ) {
        Text(
            text = "PLATFORM",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = Navy,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
    }
}
