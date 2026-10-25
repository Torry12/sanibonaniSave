package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.features.admin.presentation.AdminFinancialViewModel
import com.sanibonani.save.features.admin.presentation.state.AdminFinancialUiState
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.utils.formatZAR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansTab(state: AdminFinancialUiState, vm: AdminFinancialViewModel) {
    var selectedFilter by remember { mutableStateOf<LoanStatus?>(null) }
    var loanToReject by remember { mutableStateOf<Loan?>(null) }
    var rejectionReasonText by remember { mutableStateOf("") }

    val filteredLoans = remember(state.groupLoans, selectedFilter) {
        if (selectedFilter == null) state.groupLoans
        else state.groupLoans.filter { it.status == selectedFilter }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LoansFilterBar(
            loans = state.groupLoans,
            selectedFilter = selectedFilter,
            onSelectFilter = { selectedFilter = it }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (filteredLoans.isEmpty()) {
                item {
                    EmptyState(
                        icon = "🏦",
                        title = if (selectedFilter != null) "No ${selectedFilter?.displayName} loans" else "No loan requests",
                        description = "Loan applications and active loans for this group will appear here."
                    )
                }
            } else {
                items(filteredLoans, key = { it.id ?: it.hashCode().toString() }) { loan ->
                    val applicant = state.members.find { it.id == loan.memberId }
                    val applicantActiveLoans = remember(state.groupLoans, loan.memberId) {
                        state.groupLoans.filter {
                            it.memberId == loan.memberId && it.id != loan.id &&
                            (it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID || it.status == LoanStatus.OVERDUE)
                        }
                    }
                    val totalActiveDebt = applicantActiveLoans.fold(Money.ZERO) { acc, l -> acc + l.balanceRemaining }

                    LoanAdminDetailCard(
                        loan = loan,
                        applicant = applicant,
                        activeDebtCount = applicantActiveLoans.size,
                        totalActiveDebt = totalActiveDebt,
                        isProcessing = state.processingIds.contains(loan.id),
                        onApprove = { loan.id?.let { vm.approveLoan(it) } },
                        onRejectClick = { loanToReject = loan }
                    )
                }
            }
        }
    }

    loanToReject?.let { loan ->
        RejectLoanDialog(
            loan = loan,
            reason = rejectionReasonText,
            onReasonChange = { rejectionReasonText = it },
            onDismiss = {
                loanToReject = null
                rejectionReasonText = ""
            },
            onConfirm = { reason ->
                val finalReason = reason.ifBlank { "Declined by group administrator" }
                loan.id?.let { vm.rejectLoan(it, finalReason) }
                loanToReject = null
                rejectionReasonText = ""
            }
        )
    }
}

