package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
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

        item {
            if (group.type == GroupType.ROSCA) {
                RoscaPayoutAction(state, vm)
            } else {
                StandardPayoutAction(state, vm)
            }
        }

        item {
            SanibonaniSectionHeader("Payout History")
        }

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
                PayoutHistoryItem(payout, state.members, onApprove = { vm.approveAndEscalatePayoutRequest(payout.id!!) })
            }
        }
    }
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Automated Pot Distribution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Clicking this will automatically calculate and initiate payouts for all members who are due to receive a pot this month.",
                style = MaterialTheme.typography.bodySmall
            )
            SanibonaniButton(
                text = "Distribute Current Pot(s)",
                onClick = { vm.executeRoscaPayout() },
                isLoading = state.isRequestingPayout,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StandardPayoutAction(state: AdminFinancialUiState, vm: AdminFinancialViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Request Individual Payout", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            var showMemberMenu by remember { mutableStateOf(false) }
            val selectedMember = state.members.find { it.id == state.selectedMemberId }

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
                            text = { Text(member.fullName) },
                            onClick = {
                                vm.updateSelectedMemberId(member.id)
                                showMemberMenu = false
                            }
                        )
                    }
                }
            }

            val amountPlaceholder = when (state.group?.type) {
                GroupType.STOKVEL, GroupType.INVESTMENT_CLUB -> "Calculated if blank"
                else -> "Enter amount"
            }

            SanibonaniTextField(
                value = state.payoutAmount,
                onValueChange = { vm.updatePayoutAmount(it) },
                label = "Amount (Optional)",
                placeholder = amountPlaceholder
            )

            SanibonaniButton(
                text = "Submit Request",
                onClick = { vm.submitPayoutRequest() },
                isLoading = state.isRequestingPayout,
                enabled = state.selectedMemberId != null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PayoutHistoryItem(payout: PayoutRequest, members: List<Member>, onApprove: () -> Unit) {
    val member = members.find { it.id == payout.memberId }

    Card(
        Modifier.fillMaxWidth(),
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
                StatusBadge(payout.status)

                if (payout.status == PayoutStatus.PENDING) {
                    TextButton(onClick = onApprove) {
                        Text("Approve & Escalate", style = MaterialTheme.typography.labelSmall)
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

