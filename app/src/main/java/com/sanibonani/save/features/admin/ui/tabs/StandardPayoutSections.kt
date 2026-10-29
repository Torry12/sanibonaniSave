package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.ui.components.*
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.ui.theme.SuccessGreen
import com.sanibonani.save.ui.theme.WarningAmber
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.ui.utils.formatDate
import com.sanibonani.save.features.admin.presentation.AdminFinancialViewModel
import com.sanibonani.save.features.admin.presentation.state.AdminFinancialUiState

@Composable
fun StandardPayoutAction(state: AdminFinancialUiState, vm: AdminFinancialViewModel) {
    val groupType = state.group?.type ?: GroupType.STOKVEL
    var showConfirmDialog by remember { mutableStateOf(false) }
    val selectedMember = state.members.find { it.id == state.selectedMemberId }

    val actionTitle = when (groupType) {
        GroupType.STOKVEL -> "Request Individual Savings Payout"
        GroupType.INVESTMENT_CLUB -> "Request Equity Disinvestment / Liquidation"
        GroupType.EMERGENCY_FUND -> "Request Emergency Fund Withdrawal"
        else -> "Request Individual Payout"
    }

    val buttonText = when (groupType) {
        GroupType.STOKVEL -> "Submit Payout Request"
        GroupType.INVESTMENT_CLUB -> "Submit Disinvestment Request"
        GroupType.EMERGENCY_FUND -> "Submit Emergency Withdrawal Request"
        else -> "Submit Payout Request"
    }

    val amountPlaceholder = when (groupType) {
        GroupType.STOKVEL, GroupType.INVESTMENT_CLUB -> "Calculated automatically if blank"
        else -> "Enter amount"
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(actionTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            var showMemberMenu by remember { mutableStateOf(false) }

            Box {
                OutlinedCard(
                    onClick = { showMemberMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(selectedMember?.fullName ?: "Select Member (Required)")
                        }
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                }

                DropdownMenu(expanded = showMemberMenu, onDismissRequest = { showMemberMenu = false }) {
                    state.members.forEach { member ->
                        DropdownMenuItem(
                            text = { Text("${member.fullName} (${member.status.displayName})") },
                            onClick = {
                                vm.updateSelectedMemberId(member.id)
                                showMemberMenu = false
                            }
                        )
                    }
                }
            }

            SanibonaniTextField(
                value = state.payoutAmount,
                onValueChange = { vm.updatePayoutAmount(it) },
                label = "Amount (Optional)",
                placeholder = amountPlaceholder
            )

            SanibonaniButton(
                text = buttonText,
                onClick = { showConfirmDialog = true },
                isLoading = state.isRequestingPayout,
                enabled = state.selectedMemberId != null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showConfirmDialog) {
        val amountDesc = if (state.payoutAmount.isNotBlank()) "amount of R${state.payoutAmount}" else "automatically calculated amount"
        val memberName = selectedMember?.fullName ?: "selected member"

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Payout Submission") },
            text = { Text("Are you sure you want to submit a payout request ($amountDesc) for $memberName?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        vm.submitPayoutRequest()
                    }
                ) { Text("Submit Request") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PayoutHistoryItem(
    payout: PayoutRequest,
    members: List<Member>,
    isProcessing: Boolean = false,
    onClick: () -> Unit,
    onApprove: () -> Unit
) {
    val member = members.find { it.id == payout.memberId }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(member?.fullName ?: "Group Fund", fontWeight = FontWeight.Bold)
                    Text(formatDate(payout.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatZAR(payout.amount), fontWeight = FontWeight.ExtraBold, color = Navy)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(payout.status)
                    if (!payout.supportingDocumentUrl.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Description,
                            contentDescription = "Has Document",
                            modifier = Modifier.size(16.dp),
                            tint = if (payout.supportingDocumentStatus == DocumentStatus.VERIFIED) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (payout.status == PayoutStatus.PENDING) {
                    var showApproveConfirm by remember { mutableStateOf(false) }

                    TextButton(
                        onClick = { showApproveConfirm = true },
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Approve & Escalate", style = MaterialTheme.typography.labelSmall)
                    }

                    if (showApproveConfirm) {
                        AlertDialog(
                            onDismissRequest = { showApproveConfirm = false },
                            title = { Text("Approve & Escalate Payout") },
                            text = { Text("Are you sure you want to approve and escalate the payout of ${formatZAR(payout.amount)} for ${member?.fullName ?: "Group Fund"}?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showApproveConfirm = false
                                        onApprove()
                                    }
                                ) { Text("Approve & Escalate") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showApproveConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: PayoutStatus) {
    val (color, label) = when (status) {
        PayoutStatus.PENDING -> WarningAmber to "Pending Approval"
        PayoutStatus.GROUP_APPROVED -> Color.Blue to "Escalated"
        PayoutStatus.PROCESSING -> Color.Cyan to "Processing"
        PayoutStatus.COMPLETED -> SuccessGreen to "Disbursed"
        PayoutStatus.FAILED -> Color.Red to "Failed"
        PayoutStatus.CANCELLED -> Color.Gray to "Cancelled"
        PayoutStatus.REJECTED -> Color.Red to "Rejected"
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
