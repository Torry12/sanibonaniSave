package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
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
