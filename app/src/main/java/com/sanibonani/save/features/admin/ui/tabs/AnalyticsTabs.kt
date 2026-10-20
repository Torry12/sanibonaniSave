package com.sanibonani.save.features.admin.ui.tabs

import com.sanibonani.save.domain.model.GroupType

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState
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

        val insightData = insight?.getOrNull() ?: return
        when (insightData.groupType) {
            GroupType.ROSCA -> RoscaInsights(insightData)
            GroupType.INVESTMENT_CLUB -> InvestmentClubInsights(insightData)
            GroupType.STOKVEL -> StokvelInsights(insightData)
            else -> FullInsightWidget(insightData)
        }
    }
}
