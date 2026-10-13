package com.sanibonani.save.ui.screens.admin.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.ui.components.formatZAR
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.ui.screens.admin.components.SectionHeading

@Composable
fun LedgerTab(state: AdminUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("📖 Group Ledger")
        Text("Detailed record of all group financial movements.", style = MaterialTheme.typography.bodyMedium, color = MidGray)

        if (state.ledger.isEmpty()) {
            EmptyState(
                icon = "📒",
                title = "Empty Ledger",
                description = "No ledger entries recorded yet. Financial actions will appear here."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.ledger) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, LightGray.copy(0.5f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("${entry.createdAt?.take(16)} • ${entry.category}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val color = if (entry.amount >= 0) SuccessGreen else Color.Red
                                val prefix = if (entry.amount >= 0) "+" else ""
                                Text("$prefix${formatZAR(entry.amount)}", color = color, fontWeight = FontWeight.Black)
                                Text("Bal: ${formatZAR(entry.balanceAfter)}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                            }
                        }
                    }
                }
            }
        }
    }
}
