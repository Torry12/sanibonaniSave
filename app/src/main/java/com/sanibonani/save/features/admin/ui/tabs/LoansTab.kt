package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.admin.presentation.AdminFinancialViewModel
import com.sanibonani.save.features.admin.presentation.state.AdminFinancialUiState

@Composable
fun LoansTab(state: AdminFinancialUiState, vm: AdminFinancialViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.groupLoans.isEmpty()) {
            item {
                EmptyState(
                    icon = "🏦",
                    title = "No loan requests",
                    description = "Pending and active loans for this group will appear here."
                )
            }
        } else {
            items(state.groupLoans) { loan ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Loan: ${formatZAR(loan.amount)}", fontWeight = FontWeight.Bold)
                        Text("Status: ${loan.status.name}", style = MaterialTheme.typography.labelSmall)
                        if (loan.status == LoanStatus.PENDING) {
                            Button(
                                onClick = { loan.id?.let { vm.approveLoan(it) } },
                                enabled = loan.id != null
                            ) {
                                Text("Approve")
                            }
                        }
                    }
                }
            }
        }
    }
}
