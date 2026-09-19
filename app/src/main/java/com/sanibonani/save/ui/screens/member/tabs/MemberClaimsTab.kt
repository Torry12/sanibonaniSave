package com.sanibonani.save.ui.screens.member.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.ui.theme.SuccessGreen
import com.sanibonani.save.ui.utils.formatZAR

@Composable
fun MemberClaimsTab(
    claims: List<BeneficiaryPayoutClaim>,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Payout Claims History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Track the progress of your submitted burial claims.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Spacer(Modifier.height(16.dp))

        if (claims.isEmpty()) {
            EmptyState(
                icon = "📋",
                title = "No claims yet",
                description = "You haven't submitted any burial payout claims yet."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(claims) { claim ->
                    ClaimHistoryItem(claim)
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
                    Text(claim.dateOfDeath, style = MaterialTheme.typography.bodySmall)
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
