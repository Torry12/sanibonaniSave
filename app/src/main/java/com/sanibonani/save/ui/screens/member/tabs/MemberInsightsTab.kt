package com.sanibonani.save.ui.screens.member.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.screens.admin.components.FullInsightWidget
import com.sanibonani.save.viewmodel.state.MemberUiState

@Composable
fun MemberInsightsTab(state: MemberUiState) {
    val insight = state.businessInsight

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader("Group Insights")

        when (insight) {
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Rosca -> RoscaInsights(insight.schedule)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.InvestmentClub -> InvestmentClubInsights(insight.valuation)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Stokvel -> StokvelInsights(insight.projection)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.FullInsight -> FullInsightWidget(insight.insight)
            else -> {
                EmptyState(
                    icon = "📊",
                    title = "No specialized insights",
                    description = "Insights are still being calculated for your group. Check back soon!"
                )
            }
        }
    }
}
