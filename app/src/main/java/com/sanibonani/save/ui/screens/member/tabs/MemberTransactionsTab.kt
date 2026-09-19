package com.sanibonani.save.ui.screens.member.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.ui.screens.member.ContributionItem

@Composable
fun MemberTransactionsTab(
    contributions: List<Contribution>,
    isExporting: Boolean,
    onExportCsv: () -> Unit,
    onDownloadPdf: () -> Unit,
    onItemClick: (Contribution) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (contributions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportCsv, modifier = Modifier.weight(1f), enabled = !isExporting) { Icon(Icons.AutoMirrored.Filled.List, null, modifier = Modifier.size(18.dp)); Text(stringResource(R.string.ui_export_csv)) }
                OutlinedButton(onClick = onDownloadPdf, modifier = Modifier.weight(1f), enabled = !isExporting) { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp)); Text(stringResource(R.string.ui_download_pdf)) }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (contributions.isEmpty()) item { EmptyState(icon = "💸", title = "No transactions", description = "No payments yet.") }
            else items(items = contributions) { contrib -> ContributionItem(contrib, onItemClick) }
        }
        if (isExporting) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
    }
}
