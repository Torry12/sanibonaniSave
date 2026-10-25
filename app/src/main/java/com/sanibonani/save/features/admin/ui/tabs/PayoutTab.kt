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
    val payouts = remember(state.payouts) { state.payouts.sortedByDescending { it.createdAt ?: "" } }
    val burialClaims = remember(state.burialClaims) { state.burialClaims.sortedByDescending { it.createdAt ?: "" } }

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
                items(items = payouts, key = { it.id ?: it.hashCode().toString() }) { payout ->
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
                items(items = burialClaims, key = { it.id ?: it.hashCode().toString() }) { claim ->
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

