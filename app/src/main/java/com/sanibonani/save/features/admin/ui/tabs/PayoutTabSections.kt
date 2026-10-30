package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.ui.components.*
import com.sanibonani.save.features.admin.presentation.AdminFinancialViewModel
import com.sanibonani.save.features.admin.presentation.state.AdminFinancialUiState


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
