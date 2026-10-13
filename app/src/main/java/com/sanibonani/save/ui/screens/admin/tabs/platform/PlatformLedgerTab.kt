
package com.sanibonani.save.ui.screens.admin.tabs.platform
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.viewmodel.PlatformAdminUiState

/**
 * Extracted from PlatformAdminScreen for maintainability.
 */
@Composable
fun PlatformLedgerTab(state: PlatformAdminUiState) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("\uD83D\uDCC8 Platform Revenue Ledger")
        Text("Central record of registration fees and monthly member charges.", style = MaterialTheme.typography.bodyMedium, color = MidGray)

        if (state.platformLedger.isEmpty()) {
            EmptyState(icon = "\uD83E\uDDE2", title = "No transactions", description = "Platform-wide financial movements will appear here.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.platformLedger) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, LightGray.copy(0.3f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("${entry.createdAt?.take(16)?.replace("T", " ")} • ${entry.category}", style = MaterialTheme.typography.labelSmall, color = MidGray)
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
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}
