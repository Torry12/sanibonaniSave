package com.sanibonani.save.ui.screens.admin.tabs

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
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.ui.screens.admin.components.SectionHeading

@Composable
fun InsightsTab(state: AdminUiState) {
    val insight = state.businessInsight
    
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("💎 Business Insights")
        
        when (insight) {
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Rosca -> RoscaInsights(insight.schedule)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.InvestmentClub -> InvestmentClubInsights(insight.valuation)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Stokvel -> StokvelInsights(insight.projection)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.FullInsight -> FullInsightWidget(insight.insight)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty -> {}
        }
    }
}

@Composable
fun AnalyticsTab(state: AdminUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("📊 Financial Analytics", style = MaterialTheme.typography.headlineSmall, color = Forest, fontWeight = FontWeight.Bold)
        
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardMetricRow("Expected Annual Claims", formatZAR(state.metrics.expectedAnnualClaims), Forest)
                DashboardMetricRow("Pure Premium (per member)", formatZAR(state.metrics.purePremium), Forest)
                DashboardMetricRow("Gross Premium (incl. admin)", formatZAR(state.metrics.grossPremium), Forest)
                HorizontalDivider()
                DashboardMetricRow("Solvency Margin", "${state.metrics.solvencyMarginPct.toInt()}%", if (state.metrics.solvencyMarginPct > 100) Forest else ErrorRed)
                DashboardMetricRow("Reserve Adequacy", "${state.metrics.reserveAdequacyPct.toInt()}%", if (state.metrics.reserveAdequacyPct > 100) Forest else WarningYellow)
            }
        }

        // Potential Growth
        Card(colors = CardDefaults.cardColors(containerColor = Forest.copy(0.05f))) {
            Column(Modifier.padding(16.dp)) {
                Text("Group Health Score", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { (state.metrics.paymentRatePct / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                    color = if (state.metrics.paymentRatePct > 80) Forest else WarningYellow,
                    trackColor = Color.White
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
