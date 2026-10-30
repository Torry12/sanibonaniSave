package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.SuccessGreen
import com.sanibonani.save.ui.theme.ErrorRed
import com.sanibonani.save.ui.utils.formatDate
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.member.presentation.MemberBeneficiaryViewModel
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.theme.WarningYellow

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
                        PayoutType.ROSCA -> "Rotation"
                        PayoutType.CUSTOM -> "Custom"
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
        BeneficiaryClaimStatus.ESCALATED -> WarningYellow
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
