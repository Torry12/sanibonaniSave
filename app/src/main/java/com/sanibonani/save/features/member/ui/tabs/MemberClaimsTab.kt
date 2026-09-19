package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.member.ui.PayoutSummaryItem
import com.sanibonani.save.ui.theme.SuccessGreen
import com.sanibonani.save.ui.theme.ErrorRed
import com.sanibonani.save.ui.utils.formatDate
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.viewmodel.disbursement.MemberBeneficiaryViewModel
import com.sanibonani.save.viewmodel.credit.MemberFinanceViewModel

@Composable
fun MemberClaimsTab(
    group: Group?,
    member: Member?,
    claims: List<BeneficiaryPayoutClaim>,
    payouts: List<PayoutRequest>,
    isLoading: Boolean,
    insights: GetGroupBusinessInsightsUseCase.GroupBusinessInsight,
    onRefresh: () -> Unit,
    beneficiaryVm: MemberBeneficiaryViewModel,
    financeVm: MemberFinanceViewModel
) {
    val groupType = group?.type ?: GroupType.BURIAL_SOCIETY

    val title = when (groupType) {
        GroupType.BURIAL_SOCIETY -> "Burial Payout Claims"
        GroupType.STOKVEL, GroupType.ROSCA -> "Group Payouts"
        GroupType.INVESTMENT_CLUB, GroupType.EMERGENCY_FUND -> "Withdrawal Requests"
        else -> "Payout Claims"
    }

    val subtitle = when (groupType) {
        GroupType.BURIAL_SOCIETY -> "Track the progress of burial claims for your beneficiaries."
        GroupType.STOKVEL -> "View your scheduled and completed annual savings payouts."
        GroupType.ROSCA -> "Track your rotating pot payouts and assigned rotation month."
        GroupType.INVESTMENT_CLUB -> "Manage your investment disinvestment and capital withdrawal requests."
        GroupType.EMERGENCY_FUND -> "Request and track emergency fund disbursements."
        else -> "Track your financial disbursements and payout requests."
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Spacer(Modifier.height(16.dp))

            when (groupType) {
                GroupType.BURIAL_SOCIETY -> BurialClaimsContent(member, claims, onRefresh, beneficiaryVm, insights)
                GroupType.STOKVEL -> StokvelPayoutsContent(payouts, onRefresh, insights)
                GroupType.ROSCA -> RoscaPayoutsContent(payouts, onRefresh, insights)
                GroupType.INVESTMENT_CLUB, GroupType.EMERGENCY_FUND -> WithdrawalContent(groupType, group, member, payouts, onRefresh, financeVm, insights)
                else -> GenericClaimsContent(claims, payouts)
            }
        }
    }
}

@Composable
private fun BurialClaimsContent(
    member: Member?,
    claims: List<BeneficiaryPayoutClaim>,
    onRefresh: () -> Unit,
    beneficiaryVm: MemberBeneficiaryViewModel,
    insights: GetGroupBusinessInsightsUseCase.GroupBusinessInsight
) {
    var showDialog by remember { mutableStateOf<Beneficiary?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (insights is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.FullInsight) {
            val metrics = insights.insight
            GlassCard(color = SuccessGreen.copy(alpha = 0.05f)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Society Health", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(if (metrics.isSolvent) "Solvent & Ready for claims" else "Financial Warning: Low Reserves",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (metrics.isSolvent) SuccessGreen else Color.Red)
                    }
                }
            }
        }

        if (member?.beneficiaries?.isNotEmpty() == true) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                SanibonaniButton(
                    text = "Initiate New Claim",
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    member.beneficiaries.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.fullName) },
                            onClick = {
                                showDialog = b
                                expanded = false
                            }
                        )
                    }
                }
            }
        } else {
            InfoBox("You need to register beneficiaries in the Beneficiaries tab before you can make a claim.", InfoType.INFO)
        }

        if (claims.isEmpty()) {
            EmptyState(
                icon = "📋",
                title = "No burial claims",
                description = "You haven't submitted any burial payout claims yet."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(claims) { claim ->
                    ClaimHistoryItem(claim)
                }
            }
        }
    }

    showDialog?.let { b ->
        com.sanibonani.save.features.member.ui.ClaimBeneficiaryDialog(
            beneficiary = b,
            vm = beneficiaryVm,
            onDismiss = { showDialog = null }
        )
    }
}

@Composable
private fun StokvelPayoutsContent(
    payouts: List<PayoutRequest>,
    onRefresh: () -> Unit,
    insights: GetGroupBusinessInsightsUseCase.GroupBusinessInsight
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (insights is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Stokvel) {
            val projection = insights.projection
            GlassCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Projected Annual Payout", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(formatZAR(projection.totalProjectedFund), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Estimated for end of term (${projection.monthsRemaining} months remaining)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }

        if (payouts.isEmpty()) {
            EmptyState(
                icon = "💰",
                title = "No payouts recorded",
                description = "Your scheduled Stokvel payouts will appear here once they are processed."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(payouts) { payout ->
                    PayoutRequestItem(payout)
                }
            }
        }
    }
}

@Composable
private fun RoscaPayoutsContent(
    payouts: List<PayoutRequest>,
    onRefresh: () -> Unit,
    insights: GetGroupBusinessInsightsUseCase.GroupBusinessInsight
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (insights is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Rosca) {
            val schedule = insights.schedule
            GlassCard(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh rotation",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Rotation Cycle", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Pot Size: ${formatZAR(schedule.totalPot)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (payouts.isEmpty()) {
            EmptyState(
                icon = "🗓️",
                title = "No payouts yet",
                description = "Your rotating pot payouts will be logged here when it's your turn in the rotation."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(payouts) { payout ->
                    PayoutRequestItem(payout)
                }
            }
        }
    }
}

@Composable
private fun WithdrawalContent(
    type: GroupType,
    group: Group?,
    member: Member?,
    payouts: List<PayoutRequest>,
    onRefresh: () -> Unit,
    financeVm: MemberFinanceViewModel,
    insights: GetGroupBusinessInsightsUseCase.GroupBusinessInsight
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (type == GroupType.EMERGENCY_FUND && insights is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.FullInsight) {
            val metrics = insights.insight
            GlassCard(color = ErrorRed.copy(alpha = 0.05f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Emergency Liquidity", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ErrorRed)
                    Text("The group has ${formatZAR(group?.balance ?: Money.ZERO)} available for withdrawals.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SanibonaniButton(
            text = if (type == GroupType.EMERGENCY_FUND) "Request Emergency Funds" else "Request Disinvestment",
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (type == GroupType.EMERGENCY_FUND) Color.Red else MaterialTheme.colorScheme.primary
        )

        if (payouts.isEmpty()) {
            EmptyState(
                icon = if (type == GroupType.EMERGENCY_FUND) "🚨" else "📈",
                title = "No withdrawals yet",
                description = "Submit a request to withdraw funds based on your group's liquidity rules."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(payouts) { payout ->
                    PayoutRequestItem(payout)
                }
            }
        }
    }

    if (showDialog) {
        com.sanibonani.save.features.member.ui.WithdrawalRequestDialog(
            group = group,
            type = if (type == GroupType.EMERGENCY_FUND) PayoutType.EMERGENCY else PayoutType.DISINVESTMENT,
            member = member,
            onDismiss = { showDialog = false },
            onConfirm = { amount ->
                financeVm.requestWithdrawal(amount, if (type == GroupType.EMERGENCY_FUND) PayoutType.EMERGENCY else PayoutType.DISINVESTMENT)
                showDialog = false
            }
        )
    }
}

@Composable
private fun GenericClaimsContent(claims: List<BeneficiaryPayoutClaim>, payouts: List<PayoutRequest>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(claims) { ClaimHistoryItem(it) }
        items(payouts) { PayoutRequestItem(it) }
    }
}

@Composable
private fun PayoutRequestItem(payout: PayoutRequest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(payout.bankName, fontWeight = FontWeight.Bold)
                    Text("Type: ${payout.type.name.replace("_", " ").uppercase()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                val statusColor = when (payout.status) {
                    PayoutStatus.COMPLETED -> SuccessGreen
                    PayoutStatus.PENDING, PayoutStatus.GROUP_APPROVED, PayoutStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                    PayoutStatus.FAILED, PayoutStatus.CANCELLED -> Color.Red
                }

                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        payout.status.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Amount", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(formatZAR(payout.amount), fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val label = when (payout.type) {
                        PayoutType.EMERGENCY -> "Emergency"
                        PayoutType.DISINVESTMENT -> "Disinvestment"
                        PayoutType.ROTATION -> "Rotation"
                        PayoutType.ANNUAL -> "Annual"
                        else -> "Standard"
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(formatDate(payout.createdAt), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ClaimHistoryItem(claim: BeneficiaryPayoutClaim) {
    val statusColor = when (claim.status) {
        BeneficiaryClaimStatus.PAID, BeneficiaryClaimStatus.APPROVED -> SuccessGreen
        BeneficiaryClaimStatus.SUBMITTED, BeneficiaryClaimStatus.UNDER_REVIEW -> MaterialTheme.colorScheme.primary
        BeneficiaryClaimStatus.ESCALATED -> Color(0xFFF59E0B) // Amber
        BeneficiaryClaimStatus.REJECTED, BeneficiaryClaimStatus.CANCELLED -> Color.Red
    }

    val statusIcon = when (claim.status) {
        BeneficiaryClaimStatus.PAID -> Icons.Default.CheckCircle
        BeneficiaryClaimStatus.REJECTED -> Icons.Default.Error
        else -> Icons.Default.HourglassEmpty
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(claim.beneficiaryName, fontWeight = FontWeight.Bold)
                    Text("Reference: ${claim.id?.take(8) ?: "N/A"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(claim.status.displayName, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Claim Amount", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(formatZAR(claim.claimAmount), fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Death Date", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(formatDate(claim.dateOfDeath), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!claim.adminNotes.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(color = Color.LightGray.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                    Text("Admin Note: ${claim.adminNotes}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                }
            }
        }
    }
}
