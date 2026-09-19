package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.ViabilityPlan
import com.sanibonani.save.ui.components.DetailRow
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState
import com.sanibonani.save.features.admin.presentation.AdminOverviewViewModel

@Composable
fun ViabilityPlanningTab(state: AdminOverviewUiState, vm: AdminOverviewViewModel) {
    var goalAmount by remember { mutableStateOf(state.group?.goalAmount?.toDouble()?.toString() ?: "0") }
    var periodMonths by remember { mutableStateOf(state.group?.periodMonths?.toString() ?: "12") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("📈 Viability Planning", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.ui_set_your_groups_financial_goals_and_well),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Navy.copy(0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SanibonaniTextField(
                    value = goalAmount,
                    onValueChange = { goalAmount = it },
                    label = stringResource(R.string.ui_target_goal_amount_r),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    prefix = { Text("R ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
                )

                SanibonaniTextField(
                    value = periodMonths,
                    onValueChange = { periodMonths = it },
                    label = stringResource(R.string.ui_target_time_period_months),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text(stringResource(R.string.ui_months), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
                )

                SanibonaniButton(
                    text = "Calculate Strategy",
                    onClick = { vm.calculateViability(goalAmount, periodMonths) },
                    isLoading = state.isCalculatingViability,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        state.viabilityPlan?.let { plan ->
            ViabilityResultCard(
                plan = plan,
                groupType = state.group?.type ?: GroupType.OTHER,
                onApply = { vm.applyViabilityPlan() }
            )
        }
    }
}

@Composable
fun ViabilityResultCard(plan: ViabilityPlan, groupType: GroupType, onApply: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(0.05f)),
        border = BorderStroke(1.dp, Navy.copy(0.2f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ui_suggested_strategy), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)

            DetailRow("Required Monthly Contribution", formatZAR(plan.suggestedMonthlyContribution))
            DetailRow("Initial Upfront Payment", formatZAR(plan.initialContribution))
            DetailRow("Projected Value", formatZAR(plan.projectedValue))

            Spacer(Modifier.height(8.dp))
            SanibonaniButton(
                text = "Apply Suggested Contribution",
                onClick = onApply,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
