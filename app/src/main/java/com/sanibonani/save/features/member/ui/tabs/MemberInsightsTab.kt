package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.features.admin.ui.components.FullInsightWidget
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.ui.components.*

@Composable
fun MemberInsightsTab(state: MemberUiState) {
    val insightResult = state.businessInsight
    val group = state.group

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SanibonaniSectionHeader("Group Insights", subtitle = "Data-driven projections for ${group?.name ?: "your group"}")

        val insight = insightResult?.getOrNull()

        if (insight == null) {
            EmptyState(
                icon = "📊",
                title = "No specialized insights",
                description = "Insights are still being calculated for your group. Check back soon!"
            )
        } else {
            when (group?.type) {
                GroupType.ROSCA -> RoscaInsights(insight)
                GroupType.INVESTMENT_CLUB -> InvestmentClubInsights(insight)
                GroupType.STOKVEL -> StokvelInsights(insight)
                else -> FullInsightWidget(insight)
            }
        }
    }
}
