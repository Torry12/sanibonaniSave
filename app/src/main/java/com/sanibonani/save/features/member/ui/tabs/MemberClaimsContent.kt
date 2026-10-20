package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.SuccessGreen
import com.sanibonani.save.ui.theme.ErrorRed
import com.sanibonani.save.ui.utils.formatDate
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.member.presentation.MemberBeneficiaryViewModel
import com.sanibonani.save.features.member.presentation.MemberFinanceViewModel
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.theme.WarningAmber

@Composable
fun BurialClaimsContent(
    member: Member?,
    claims: List<BeneficiaryPayoutClaim>,
    onRefresh: () -> Unit,
    beneficiaryVm: MemberBeneficiaryViewModel,
    insights: Result<GroupFinancialInsight>?
) {
    var showDialog by remember { mutableStateOf<Beneficiary?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val insight = insights?.getOrNull()
        if (insight != null) {
            GlassCard(accentColor = SuccessGreen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Society Health", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (insight.isSolvent) "Solvent & Ready for claims" else "Financial Warning: Low Reserves",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (insight.isSolvent) SuccessGreen else ErrorRed
                        )
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
            InfoBox("You need to register beneficiaries in the Beneficiaries tab before you can make a claim.", type = InfoType.INFO)
        }

        if (claims.isEmpty()) {
            EmptyState(
                icon = "📋",
                title = "No burial claims",
                description = "You haven't submitted any burial payout claims yet."
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                claims.forEach { claim ->
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
fun StokvelPayoutsContent(
    group: Group?,
    member: Member?,
    payouts: List<PayoutRequest>,
    onRefresh: () -> Unit,
    insights: Result<GroupFinancialInsight>?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val insight = insights?.getOrNull()
        val totalMembers = group?.currentMembers?.coerceAtLeast(1) ?: 1
        val totalFund = insight?.totalProjectedFund ?: group?.balance ?: Money.ZERO

        val individualPayout = if (insight?.projectedPayoutPerMember?.isPositive == true) {
            insight.projectedPayoutPerMember
        } else if (totalFund.isPositive && totalMembers > 0) {
            totalFund / totalMembers
        } else {
            member?.totalPaid ?: Money.ZERO
        }

        val remainingMonths = if (insight != null && insight.cycleLength > 0) {
            (insight.cycleLength - insight.currentCycleMonth).coerceAtLeast(0)
        } else {
            group?.periodMonths ?: 0
        }

        GlassCard(accentColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)) {
            Column(Modifier.padding(16.dp)) {
                Text("Your Projected Annual Payout", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(formatZAR(individualPayout), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text("Estimated for end of term ($remainingMonths months remaining). Total fund pool: ${formatZAR(totalFund)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        // Detailed Payout Breakdown Card
        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Payout Calculation Breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                val totalPaid = member?.totalPaid ?: Money.ZERO
                DetailRow("Total Contributions Paid", formatZAR(totalPaid))

                val estimatedGrowth = if (individualPayout > totalPaid) individualPayout - totalPaid else Money.ZERO
                DetailRow("Estimated Interest / Dividends", formatZAR(estimatedGrowth))

                DetailRow("Total Group Members", "$totalMembers members")
                DetailRow("Payout Status", if (remainingMonths == 0) "Ready for Group Disbursal" else "Scheduled for Term End")
            }
        }

        if (payouts.isEmpty()) {
            // Scheduled Payout Details Item
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("💰", fontSize = 20.sp)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Scheduled Annual Distribution", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Expected: ${formatZAR(individualPayout)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (remainingMonths == 0) "Maturity reached • Pending Group Disbursal" else "$remainingMonths month(s) remaining until payout", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (remainingMonths == 0) WarningAmber.copy(0.15f) else SuccessGreen.copy(0.15f)
                    ) {
                        Text(
                            text = if (remainingMonths == 0) "DUE NOW" else "SCHEDULED",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingMonths == 0) WarningAmber else SuccessGreen
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                payouts.forEach { payout ->
                    PayoutRequestItem(payout)
                }
            }
        }
    }
}

@Composable
fun RoscaPayoutsContent(
    group: Group?,
    member: Member?,
    payouts: List<PayoutRequest>,
    onRefresh: () -> Unit,
    insights: Result<GroupFinancialInsight>?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val insight = insights?.getOrNull()
        val totalMembers = group?.currentMembers?.coerceAtLeast(1) ?: 1
        val monthlyPot = insight?.monthlyPot?.takeIf { it.isPositive } ?: (group?.monthlyContribution?.times(totalMembers) ?: Money.ZERO)
        val cycleLength = insight?.cycleLength?.takeIf { it > 0 } ?: group?.periodMonths ?: 6
        val currentMonth = insight?.currentCycleMonth ?: 1

        GlassCard(accentColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Rotating Pot Payout", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        Text(formatZAR(monthlyPot), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh rotation",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Text("Rotation Cycle: Month $currentMonth of $cycleLength", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                if (!insight?.currentPayoutMember.isNullOrBlank()) {
                    DetailRow("Current Month Recipient", insight?.currentPayoutMember ?: "Pending")
                }
                if (!insight?.nextPayoutMember.isNullOrBlank()) {
                    DetailRow("Next Month Recipient", insight?.nextPayoutMember ?: "Pending")
                }
            }
        }

        if (payouts.isEmpty()) {
            EmptyState(
                icon = "🗓️",
                title = "No payouts completed yet",
                description = "Your rotating pot payouts will be logged here when it's your turn in the rotation."
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                payouts.forEach { payout ->
                    PayoutRequestItem(payout)
                }
            }
        }
    }
}

@Composable
fun WithdrawalContent(
    type: GroupType,
    group: Group?,
    member: Member?,
    payouts: List<PayoutRequest>,
    onRefresh: () -> Unit,
    financeVm: MemberFinanceViewModel,
    insights: Result<GroupFinancialInsight>?
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val insight = insights?.getOrNull()
        if (type == GroupType.EMERGENCY_FUND && insight != null) {
            GlassCard(accentColor = ErrorRed.copy(alpha = 0.05f)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                payouts.forEach { payout ->
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
fun GenericClaimsContent(claims: List<BeneficiaryPayoutClaim>, payouts: List<PayoutRequest>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        claims.forEach { ClaimHistoryItem(it) }
        payouts.forEach { PayoutRequestItem(it) }
    }
}

@Composable
fun PayoutRequestItem(payout: PayoutRequest) {
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
                    PayoutStatus.FAILED, PayoutStatus.CANCELLED, PayoutStatus.REJECTED -> Color.Red
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
fun ClaimHistoryItem(claim: BeneficiaryPayoutClaim) {
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
