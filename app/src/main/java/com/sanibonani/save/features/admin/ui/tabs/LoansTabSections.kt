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
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoansFilterBar(
    loans: List<Loan>,
    selectedFilter: LoanStatus?,
    onSelectFilter: (LoanStatus?) -> Unit
) {
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
                onClick = { onSelectFilter(null) },
                label = { Text("All (${loans.size})") }
            )
        }
        item {
            val pendingCount = loans.count { it.status == LoanStatus.PENDING }
            FilterChip(
                selected = selectedFilter == LoanStatus.PENDING,
                onClick = { onSelectFilter(LoanStatus.PENDING) },
                label = { Text("Pending ($pendingCount)") }
            )
        }
        item {
            val activeCount = loans.count { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID }
            FilterChip(
                selected = selectedFilter == LoanStatus.ACTIVE,
                onClick = { onSelectFilter(LoanStatus.ACTIVE) },
                label = { Text("Active ($activeCount)") }
            )
        }
        item {
            val rejectedCount = loans.count { it.status == LoanStatus.REJECTED }
            FilterChip(
                selected = selectedFilter == LoanStatus.REJECTED,
                onClick = { onSelectFilter(LoanStatus.REJECTED) },
                label = { Text("Rejected ($rejectedCount)") }
            )
        }
        item {
            val completedCount = loans.count { it.status == LoanStatus.COMPLETED }
            FilterChip(
                selected = selectedFilter == LoanStatus.COMPLETED,
                onClick = { onSelectFilter(LoanStatus.COMPLETED) },
                label = { Text("Completed ($completedCount)") }
            )
        }
    }
}

@Composable
internal fun RejectLoanDialog(
    loan: Loan,
    reason: String,
    onReasonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Rejection Dialog
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject Loan Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Are you sure you want to decline the loan request of ${formatZAR(loan.amount)}?",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text("Rejection Reason (Optional)") },
                    placeholder = { Text("e.g. Insufficient savings / Existing unpaid loan") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Decline Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun LoanAdminDetailCard(
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
            LoanApplicantHeader(
                applicant = applicant,
                applicantName = applicantName,
                status = loan.status
            )

            HorizontalDivider()

            LoanRequestDetails(loan = loan)

            HorizontalDivider()

            LoanAdminAssessment(
                applicant = applicant,
                applicantSavings = applicantSavings,
                coverageRatio = coverageRatio,
                activeDebtCount = activeDebtCount,
                totalActiveDebt = totalActiveDebt
            )

            if (loan.status == LoanStatus.REJECTED && !loan.rejectionReason.isNullOrBlank()) {
                LoanRejectionReason(reason = loan.rejectionReason.orEmpty())
            }

            // ── Section 4: Action Buttons for PENDING Loans ──────────────────
            if (loan.status == LoanStatus.PENDING) {
                LoanPendingActions(
                    isProcessing = isProcessing,
                    canAct = loan.id != null && !isProcessing,
                    onApprove = onApprove,
                    onRejectClick = onRejectClick
                )
            }
        }
    }
}

@Composable
internal fun LoanApplicantHeader(applicant: Member?, applicantName: String, status: LoanStatus) {
    // ── Section 1: Member Header & Status ────────────────────────────────
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
                        color = if (isKycVerified) SuccessDark else StatusPendingFg,
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
        StatusBadge(status = status)
    }
}

@Composable
internal fun LoanRequestDetails(loan: Loan) {
    // ── Section 2: Loan Parameters Grid (2x2 Grid) ───────────────────────
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
}

@Composable
internal fun LoanAdminAssessment(
    applicant: Member?,
    applicantSavings: Money,
    coverageRatio: Double,
    activeDebtCount: Int,
    totalActiveDebt: Money
) {
    // ── Section 3: Admin Determination Context (2x2 Grid) ────────────────
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
}

@Composable
internal fun LoanRejectionReason(reason: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
    ) {
        Text(
            text = "Rejection Reason: $reason",
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
internal fun LoanPendingActions(
    isProcessing: Boolean,
    canAct: Boolean,
    onApprove: () -> Unit,
    onRejectClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onRejectClick,
            modifier = Modifier.weight(1f),
            enabled = canAct,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Decline", fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick = onApprove,
            modifier = Modifier.weight(1f),
            enabled = canAct,
            colors = ButtonDefaults.buttonColors(containerColor = SuccessDark)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("Approve", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun LoanParameterItem(label: String, value: String, isHighlight: Boolean = false) {
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
internal fun StatusBadge(status: LoanStatus) {
    val (bgColor, textColor, label) = when (status) {
        LoanStatus.PENDING -> Triple(StatusPendingBg, StatusPendingFg, "Pending Approval")
        LoanStatus.APPROVED, LoanStatus.ACTIVE -> Triple(StatusActiveBg, StatusActiveFg, status.displayName)
        LoanStatus.PARTIALLY_PAID -> Triple(StatusPartialBg, StatusPartialFg, status.displayName)
        LoanStatus.COMPLETED -> Triple(StatusCompletedBg, StatusCompletedFg, "Completed")
        LoanStatus.REJECTED, LoanStatus.CANCELLED -> Triple(StatusRejectedBg, StatusRejectedFg, status.displayName)
        LoanStatus.OVERDUE, LoanStatus.DEFAULTED -> Triple(StatusRejectedBg, StatusDefaultedFg, status.displayName)
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
