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
        // Status Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("All (${state.groupLoans.size})") }
                )
            }
            item {
                val pendingCount = state.groupLoans.count { it.status == LoanStatus.PENDING }
                FilterChip(
                    selected = selectedFilter == LoanStatus.PENDING,
                    onClick = { selectedFilter = LoanStatus.PENDING },
                    label = { Text("Pending ($pendingCount)") }
                )
            }
            item {
                val activeCount = state.groupLoans.count { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID }
                FilterChip(
                    selected = selectedFilter == LoanStatus.ACTIVE,
                    onClick = { selectedFilter = LoanStatus.ACTIVE },
                    label = { Text("Active ($activeCount)") }
                )
            }
            item {
                val rejectedCount = state.groupLoans.count { it.status == LoanStatus.REJECTED }
                FilterChip(
                    selected = selectedFilter == LoanStatus.REJECTED,
                    onClick = { selectedFilter = LoanStatus.REJECTED },
                    label = { Text("Rejected ($rejectedCount)") }
                )
            }
            item {
                val completedCount = state.groupLoans.count { it.status == LoanStatus.COMPLETED }
                FilterChip(
                    selected = selectedFilter == LoanStatus.COMPLETED,
                    onClick = { selectedFilter = LoanStatus.COMPLETED },
                    label = { Text("Completed ($completedCount)") }
                )
            }
        }

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

    // Rejection Dialog
    if (loanToReject != null) {
        AlertDialog(
            onDismissRequest = {
                loanToReject = null
                rejectionReasonText = ""
            },
            title = { Text("Reject Loan Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Are you sure you want to decline the loan request of ${formatZAR(loanToReject?.amount ?: Money.ZERO)}?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = rejectionReasonText,
                        onValueChange = { rejectionReasonText = it },
                        label = { Text("Rejection Reason (Optional)") },
                        placeholder = { Text("e.g. Insufficient savings / Existing unpaid loan") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = loanToReject?.id
                        if (targetId != null) {
                            val reason = rejectionReasonText.ifBlank { "Declined by group administrator" }
                            vm.rejectLoan(targetId, reason)
                        }
                        loanToReject = null
                        rejectionReasonText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Decline Request")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    loanToReject = null
                    rejectionReasonText = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LoanAdminDetailCard(
    loan: Loan,
    applicant: Member?,
    activeDebtCount: Int,
    totalActiveDebt: Money,
    isProcessing: Boolean,
    onApprove: () -> Unit,
    onRejectClick: () -> Unit
) {
    val applicantName = applicant?.fullName ?: "Member ${loan.memberId.take(8)}"
    val applicantSavings = applicant?.totalPaid ?: Money.ZERO
    val coverageRatio = if (loan.amount.isPositive && applicantSavings.isPositive) applicantSavings.ratio(loan.amount) else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Section 1: Member Header & Status ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = applicantName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        AutoFitText(
                            text = applicantName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isKycVerified = applicant?.document1Status == DocumentStatus.VERIFIED || !applicant?.idNumber.isNullOrBlank()

                            Text(
                                text = if (isKycVerified) "KYC Verified ✅" else "Unverified KYC ⚠️",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isKycVerified) Color(0xFF2E7D32) else Color(0xFFE65100),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Text("•", style = MaterialTheme.typography.labelSmall)

                            Text(
                                text = "Joined ${applicant?.joinedAt?.take(10) ?: "N/A"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Loan Status Badge
                StatusBadge(status = loan.status)
            }

            HorizontalDivider()

            // ── Section 2: Loan Parameters Grid (2x2 Grid) ───────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Loan Request Details",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LoanParameterItem(label = "Requested Amount", value = formatZAR(loan.amount), isHighlight = true)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LoanParameterItem(label = "Total to Repay", value = formatZAR(loan.totalToRepay))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LoanParameterItem(label = "Monthly Instalment", value = formatZAR(loan.monthlyRepayment))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LoanParameterItem(label = "Interest Rate", value = "${loan.interestRate}%")
                    }
                }

                if (!loan.purpose.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Stated Purpose: ${loan.purpose}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider()

            // ── Section 3: Admin Determination Context (2x2 Grid) ────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Admin Assessment Context",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Member Total Savings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatZAR(applicantSavings), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Savings Coverage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val formattedRatio = String.format(java.util.Locale.US, "%.1f", coverageRatio)
                            val coverageText = if (coverageRatio >= 1.0) "🟢 ${formattedRatio}x (Covered)" else "🟠 ${formattedRatio}x (Unsecured)"
                            Text(coverageText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Active Loans / Debt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val debtText = if (activeDebtCount == 0) "None ✅" else "$activeDebtCount active (${formatZAR(totalActiveDebt)})"
                            Text(debtText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bank Details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val bankText = if (!applicant?.bankName.isNullOrBlank()) "${applicant.bankName} (****${applicant.accountNo?.takeLast(4) ?: ""})" else "No bank info ⚠️"
                            Text(bankText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Rejection reason if rejected
            if (loan.status == LoanStatus.REJECTED && !loan.rejectionReason.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "Rejection Reason: ${loan.rejectionReason}",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // ── Section 4: Action Buttons for PENDING Loans ──────────────────
            if (loan.status == LoanStatus.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onRejectClick,
                        modifier = Modifier.weight(1f),
                        enabled = loan.id != null && !isProcessing,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Decline", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        enabled = loan.id != null && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Approve", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanParameterItem(label: String, value: String, isHighlight: Boolean = false) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatusBadge(status: LoanStatus) {
    val (bgColor, textColor, label) = when (status) {
        LoanStatus.PENDING -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "Pending Approval")
        LoanStatus.APPROVED, LoanStatus.ACTIVE -> Triple(Color(0xFFE8F5E9), Color(0xFF1B5E20), status.displayName)
        LoanStatus.PARTIALLY_PAID -> Triple(Color(0xFFE0F7FA), Color(0xFF006064), status.displayName)
        LoanStatus.COMPLETED -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "Completed")
        LoanStatus.REJECTED, LoanStatus.CANCELLED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), status.displayName)
        LoanStatus.OVERDUE, LoanStatus.DEFAULTED -> Triple(Color(0xFFFFEBEE), Color(0xFFB71C1C), status.displayName)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
