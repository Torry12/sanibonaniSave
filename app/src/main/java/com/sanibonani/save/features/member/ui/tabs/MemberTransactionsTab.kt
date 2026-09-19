package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.ui.utils.formatDate
import com.sanibonani.save.ui.utils.formatMonthYear
import com.sanibonani.save.features.member.ui.LedgerItem
import com.sanibonani.save.ui.theme.*

@OptIn(ExperimentalMaterial3AdaptiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MemberTransactionsTab(
    ledger: List<LedgerEntry>,
    isExporting: Boolean,
    onExportCsv: () -> Unit,
    onDownloadPdf: () -> Unit,
    onDownloadGroupPdf: () -> Unit,
    onItemClick: (LedgerEntry) -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<LedgerEntry>()
    var selectedEntry by remember { mutableStateOf<LedgerEntry?>(null) }

    LaunchedEffect(selectedEntry) {
        if (selectedEntry != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, selectedEntry)
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
            MemberTransactionsList(
                ledger,
                isExporting,
                onExportCsv,
                onDownloadPdf,
                onDownloadGroupPdf,
                onItemClick = { selectedEntry = it }
            )
        },
        detailPane = {
            selectedEntry?.let { entry ->
                MemberTransactionDetail(entry)
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a transaction to view details")
            }
        }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MemberTransactionsList(
    ledger: List<LedgerEntry>,
    isExporting: Boolean,
    onExportCsv: () -> Unit,
    onDownloadPdf: () -> Unit,
    onDownloadGroupPdf: () -> Unit,
    onItemClick: (LedgerEntry) -> Unit
) {
    val groupedLedger = remember(ledger) {
        ledger.groupBy { formatMonthYear(it.createdAt) }
    }

    Column(Modifier.fillMaxSize()) {
        if (ledger.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportCsv, modifier = Modifier.weight(1f), enabled = !isExporting) { Icon(Icons.AutoMirrored.Filled.List, null, modifier = Modifier.size(18.dp)); Text(stringResource(R.string.ui_export_csv)) }
                    OutlinedButton(onClick = onDownloadPdf, modifier = Modifier.weight(1f), enabled = !isExporting) { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp)); Text(stringResource(R.string.ui_download_pdf)) }
                }

                Button(
                    onClick = onDownloadGroupPdf,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Navy),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AccountBalance, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download Group Statement")
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ledger.isEmpty()) item {
                EmptyState(
                    icon = "💸",
                    title = "No transactions",
                    description = "No ledger entries found.",
                    actionLabel = "Refresh",
                    onAction = { onDownloadPdf() } // Or any refresh logic
                )
            }
            else {
                groupedLedger.forEach { (month, entries) ->
                    stickyHeader {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = month,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = Navy,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    items(items = entries) { entry ->
                        LedgerItem(entry, onClick = { onItemClick(entry) })
                    }
                }
            }
        }
        if (isExporting) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun MemberTransactionDetail(entry: LedgerEntry) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
    ) {
        Text("Transaction Detail", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        DetailItem("Description", entry.description)
        DetailItem("Category", entry.category.uppercase())
        DetailItem("Amount", entry.amount.format())
        DetailItem("Date", formatDate(entry.createdAt))
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
