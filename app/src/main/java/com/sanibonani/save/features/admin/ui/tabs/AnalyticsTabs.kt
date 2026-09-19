package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.ui.components.SanibonaniSectionHeader
import com.sanibonani.save.features.admin.ui.components.FullInsightWidget

@Composable
fun InsightsTab(state: AdminOverviewUiState, onDistributePot: (() -> Unit)? = null) {
    val insight = state.businessInsight

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SanibonaniSectionHeader("💎 Business Insights")

        when (insight) {
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Rosca -> RoscaInsights(insight.schedule, onDistributePot)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.InvestmentClub -> InvestmentClubInsights(insight.valuation)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Stokvel -> StokvelInsights(insight.projection)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.FullInsight -> FullInsightWidget(insight.insight)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty -> {}
        }
    }
}

@Composable
fun AnalyticsTab(state: AdminOverviewUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.ui_financial_analytics), style = MaterialTheme.typography.headlineSmall, color = Navy, fontWeight = FontWeight.Bold)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardMetricRow("Expected Annual Claims", formatZAR(state.metrics.expectedAnnualClaims), Navy)
                DashboardMetricRow("Pure Premium (per member)", formatZAR(state.metrics.purePremium), Navy)
                DashboardMetricRow("Gross Premium (incl. admin)", formatZAR(state.metrics.grossPremium), Navy)
                HorizontalDivider()
                DashboardMetricRow("Solvency Margin", "${state.metrics.solvencyMarginPct.toInt()}%", if (state.metrics.solvencyMarginPct > 100) Navy else ErrorRed)
                DashboardMetricRow("Reserve Adequacy", "${state.metrics.reserveAdequacyPct.toInt()}%", if (state.metrics.reserveAdequacyPct > 100) Navy else WarningYellow)
            }
        }

        // Potential Growth
        Card(colors = CardDefaults.cardColors(containerColor = Navy.copy(0.05f))) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.ui_group_health_score), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { (state.metrics.paymentRatePct / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                    color = if (state.metrics.paymentRatePct > 80) Navy else WarningYellow,
                    trackColor = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your group has a historical payment rate of ${state.metrics.paymentRatePct.toInt()}% based on current reserves.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun DashboardMetricRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(0.65f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
