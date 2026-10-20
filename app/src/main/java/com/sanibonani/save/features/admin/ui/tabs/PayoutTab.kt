package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun PayoutTab(state: AdminFinancialUiState, vm: AdminFinancialViewModel) {
    val group = state.group ?: return

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PayoutContextCard(group)
        }

        if (state.successMessage != null) {
            item {
                InfoBox(state.successMessage, type = InfoType.SUCCESS)
            }
        }

        if (state.error != null) {
            item {
                InfoBox(state.error, type = InfoType.ERROR)
            }
        }

        // Group Type Specific Action Panels
        when (group.type) {
            GroupType.ROSCA -> {
                item { RoscaPayoutAction(state, vm) }
                item { SanibonaniSectionHeader("Cycle Payout History") }
            }
            GroupType.STOKVEL -> {
                item { StokvelAnnualPayoutAction(state, vm) }
                item { StandardPayoutAction(state, vm) }
                item { SanibonaniSectionHeader("Active Payout Requests") }
            }
            GroupType.BURIAL_SOCIETY -> {
                item { BurialSocietyPayoutContext() }
                item { SanibonaniSectionHeader("Death Payout Claims") }
            }
            GroupType.EMERGENCY_FUND -> {
                item { EmergencyFundPayoutContext() }
                item { StandardPayoutAction(state, vm) }
                item { SanibonaniSectionHeader("Withdrawal History") }
            }
            GroupType.INVESTMENT_CLUB -> {
                item { InvestmentClubPayoutContext() }
                item { StandardPayoutAction(state, vm) }
                item { SanibonaniSectionHeader("Disinvestment Requests") }
            }
            else -> {
                item { StandardPayoutAction(state, vm) }
                item { SanibonaniSectionHeader("Active Payout Requests") }
            }
        }

        // Render Payouts for non-Burial types primarily
        if (group.type != GroupType.BURIAL_SOCIETY) {
            if (state.payouts.isEmpty()) {
                item {
                    EmptyState(
                        icon = "💰",
                        title = "No payouts found",
                        description = "Request a payout to distribute funds to group members."
                    )
                }
            } else {
                items(items = state.payouts.sortedByDescending { it.createdAt ?: "" }) { payout ->
                    PayoutHistoryItem(
                        payout = payout,
                        members = state.members,
                        isProcessing = state.processingIds.contains(payout.id),
                        onClick = { vm.selectPayout(payout) },
                        onApprove = { payout.id?.let { vm.approveAndEscalatePayoutRequest(it) } }
                    )
                }
            }
        }

        // Burial Claims Section (Always show if it's a Burial Society OR there are active claims)
        if (group.type == GroupType.BURIAL_SOCIETY || state.burialClaims.isNotEmpty()) {
            if (group.type != GroupType.BURIAL_SOCIETY) {
                item { SanibonaniSectionHeader("Other Group Claims") }
            }

            if (state.burialClaims.isEmpty()) {
                item {
                    EmptyState(
                        icon = "📑",
                        title = "No claims found",
                        description = "Burial society claims submitted by members will appear here."
                    )
                }
            } else {
                items(items = state.burialClaims.sortedByDescending { it.createdAt ?: "" }) { claim ->
                    ClaimAdminCard(
                        claim = claim,
                        onVerify = { vm.selectClaim(claim) },
                        onEscalate = { vm.selectClaim(claim) }
                    )
                }
            }
        }
    }
}

@Composable
fun BurialSocietyPayoutContext() {
    InfoBox(
        message = "Claims for burial benefits must be supported by a death certificate and beneficiary ID. Funds are disbursed directly to the verified beneficiary's account.",
        type = InfoType.INFO
    )
}

@Composable
fun EmergencyFundPayoutContext() {
    InfoBox(
        message = "Emergency withdrawals are processed immediately upon approval. Ensure the group has sufficient liquidity before approving large requests.",
        type = InfoType.WARNING
    )
}

@Composable
fun InvestmentClubPayoutContext() {
    InfoBox(
        message = "Disinvestments are calculated based on the current NAV per unit. Ensure market valuations are updated before processing.",
        type = InfoType.INFO
    )
}

@Composable
fun PayoutContextCard(group: Group) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (group.type) {
                        GroupType.ROSCA -> "Automated Payout Rotation"
                        GroupType.STOKVEL -> "Manual or Year-End Payouts"
                        GroupType.INVESTMENT_CLUB -> "Equity Liquidations"
                        else -> "Disbursement Management"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = when (group.type) {
                    GroupType.ROSCA -> "Pots are calculated based on subgroup collections for the current month."
                    GroupType.STOKVEL -> "Payouts for specific members will be calculated based on their projected year-end savings."
                    GroupType.INVESTMENT_CLUB -> "Payouts will be calculated based on the member's current unit valuation."
                    else -> "Request funds to be disbursed to group members from the group treasury."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RoscaPayoutAction(state: AdminFinancialUiState, vm: AdminFinancialViewModel) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Automated Pot Distribution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Clicking this will automatically calculate and initiate payouts for all members who are due to receive a pot this month.",
                style = MaterialTheme.typography.bodySmall
            )
            SanibonaniButton(
                text = "Distribute Current Pot(s)",
                onClick = { showConfirmDialog = true },
                isLoading = state.isRequestingPayout,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Pot Distribution") },
            text = { Text("Are you sure you want to calculate and initiate the ROSCA pot distribution for this cycle?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        vm.executeRoscaPayout()
                    }
                ) { Text("Confirm & Distribute") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun StokvelAnnualPayoutAction(state: AdminFinancialUiState, vm: AdminFinancialViewModel) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Distribute Annual Stokvel Savings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Initiate batch year-end savings payouts for all active members in the Stokvel based on their accumulated fund share.",
                style = MaterialTheme.typography.bodySmall
            )
            SanibonaniButton(
                text = "Distribute Annual Savings",
                onClick = { showConfirmDialog = true },
                isLoading = state.isRequestingPayout,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Annual Savings Distribution") },
            text = { Text("Are you sure you want to trigger annual savings payouts for all active members in this Stokvel?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        vm.executeStokvelBatchPayout()
                    }
                ) { Text("Confirm & Distribute All") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

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
