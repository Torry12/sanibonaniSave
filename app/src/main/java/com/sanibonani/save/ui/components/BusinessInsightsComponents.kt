package com.sanibonani.save.ui.components


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.GroupFinancialInsight
import com.sanibonani.save.ui.utils.formatPct
import com.sanibonani.save.ui.utils.formatZAR

@Composable
fun RoscaInsights(insight: GroupFinancialInsight) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ROSCA Rotation Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            DetailRow("Pot Total", formatZAR(insight.monthlyPot))
            DetailRow("Cycle Length", "${insight.cycleLength} Months")

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f))

            Text("Next Payout: ${insight.nextPayoutMember}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Current Payout: ${insight.currentPayoutMember}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InvestmentClubInsights(insight: GroupFinancialInsight) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Investment Valuation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            DetailRow("NAV per Unit", formatZAR(insight.navPerUnit))
            DetailRow("Total Return", formatPct(insight.totalReturn))

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f))
            Text("Member Performance", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            DetailRow("Annualised Return", formatPct(insight.annualisedReturn))
        }
    }
}

@Composable
fun StokvelInsights(insight: GroupFinancialInsight) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Annual Payout Projection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            DetailRow("Projected Total Fund", formatZAR(insight.totalProjectedFund))
            DetailRow("Estimated Payout per Member", formatZAR(insight.projectedPayoutPerMember))

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f))

            val progress = (insight.potMilestonePct / 100.0).toFloat()
            Text("Pot Completion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            )
        }
    }
}
