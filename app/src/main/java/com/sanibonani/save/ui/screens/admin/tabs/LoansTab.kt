package com.sanibonani.save.ui.screens.admin.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.viewmodel.AdminViewModel
import com.sanibonani.save.ui.screens.admin.components.SectionHeading

@Composable
fun LoansTab(
    state: AdminUiState,
    vm: AdminViewModel,
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    val pending = state.groupLoans.filter { it.status == LoanStatus.PENDING }
    val active = state.groupLoans.filter { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID || it.status == LoanStatus.OVERDUE }
    val history = state.groupLoans.filter { it.status == LoanStatus.COMPLETED || it.status == LoanStatus.REJECTED || it.status == LoanStatus.CANCELLED }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (pending.isNotEmpty()) {
            item { SectionHeading("Pending Requests") }
            items(pending) { loan ->
                LoanRequestCard(loan, state, vm)
            }
        }

        if (active.isNotEmpty()) {
            item { SectionHeading("Active Loans") }
            items(active) { loan ->
                ActiveLoanCard(loan, state, vm, onFileAction)
            }
        }

        if (history.isNotEmpty()) {
            item { SectionHeading("Loan History") }
            items(history) { loan ->
                HistoryLoanCard(loan, state)
            }
        }

        if (state.groupLoans.isEmpty()) {
            item {
                EmptyState(
                    icon = "📑",
                    title = "No Loans",
                    description = "Members can apply for loans from their portal."
                )
            }
        }
        
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun LoanRequestCard(loan: Loan, state: AdminUiState, vm: AdminViewModel) {
    val member = state.members.find { it.id == loan.memberId }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(member?.fullName ?: "Unknown Member", style = MaterialTheme.typography.titleMedium)
            // ... truncated for brevity, I'll need to copy the full logic later
        }
    }
}

@Composable
fun ActiveLoanCard(loan: Loan, state: AdminUiState, vm: AdminViewModel, onFileAction: (String, String, Map<String, String>) -> Unit) {
    // ...
}

@Composable
fun HistoryLoanCard(loan: Loan, state: AdminUiState) {
    // ...
}
