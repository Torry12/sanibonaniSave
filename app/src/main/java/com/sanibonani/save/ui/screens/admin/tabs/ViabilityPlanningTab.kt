package com.sanibonani.save.ui.screens.admin.tabs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.DetailRow
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.ui.components.formatZAR
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.*
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.viewmodel.AdminViewModel
import com.sanibonani.save.ui.screens.admin.components.SectionHeading
import java.util.Locale
import kotlin.math.abs

@Composable
fun ViabilityPlanningTab(state: AdminUiState, vm: AdminViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("📈 Viability Planning")
        Text(
            "Set your group's financial goals and we'll calculate the required contributions to make it sustainable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MidGray
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, Forest.copy(0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SanibonaniTextField(
                    value = state.settings.goalAmount,
                    onValueChange = { vm.updateSetting("goalAmount", it) },
                    label = "Target Goal Amount (R)",
                    placeholder = "e.g. 10000",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    prefix = { Text("R ", color = Forest, fontWeight = FontWeight.Bold) }
                )
                
                SanibonaniTextField(
                    value = state.settings.periodMonths,
                    onValueChange = { vm.updateSetting("periodMonths", it) },
                    label = "Target Time Period (Months)",
                    placeholder = "e.g. 12",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text(" Months", color = MidGray) }
                )

                SanibonaniButton(
                    text = "Calculate Strategy",
                    onClick = { vm.calculateViability() },
                    isLoading = state.isCalculatingViability,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        state.viabilityPlan?.let { plan ->
            ViabilityResultCard(
                plan = plan,
                groupType = state.group?.type ?: GroupType.OTHER,
                onApply = { vm.applySuggestedContribution() }
            )
        }
    }
}

@Composable
fun ViabilityResultCard(plan: ViabilityPlan, groupType: GroupType, onApply: () -> Unit) {
    var showAllFactors by remember(plan) { mutableStateOf(false) }
    var explainFactor by remember(plan) { mutableStateOf<ViabilityFactorUi?>(null) }
    val factors = remember(plan, showAllFactors, groupType) {
        plan.toViabilityFactors(groupType = groupType, includeNeutral = showAllFactors)
    }
    val panels = remember(plan, showAllFactors, groupType) {
        plan.toViabilityPanels(groupType = groupType, includeNeutral = showAllFactors)
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Forest.copy(0.05f)),
        border = BorderStroke(1.dp, Forest.copy(0.2f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Suggested Strategy", style = MaterialTheme.typography.titleMedium, color = Forest, fontWeight = FontWeight.Bold)
            
            DetailRow("Required Monthly Contribution", formatZAR(plan.suggestedMonthlyContribution))
            DetailRow("Initial Upfront Payment", formatZAR(plan.initialContribution))
            DetailRow("Projected Value", formatZAR(plan.projectedValue))

            if (panels.isNotEmpty()) {
                HorizontalDivider(color = Forest.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Projection Factors", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (showAllFactors) {
                                "Showing all ${factors.size} factors, including neutral multipliers."
                            } else {
                                "Showing ${factors.size} active factors for ${groupType.displayName}. Neutral multipliers are hidden."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MidGray
                        )
                    }
                    FilterChip(
                        selected = showAllFactors,
                        onClick = { showAllFactors = !showAllFactors },
                        label = {
                            Text(if (showAllFactors) "All Factors" else "Active Only")
                        }
                    )
                }
                panels.forEach { panel ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            panel.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = Forest,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            panel.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MidGray
                        )
                        panel.factors.forEach { factor ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(factor.label, style = MaterialTheme.typography.bodyMedium, color = MidGray)
                                    IconButton(
                                        onClick = { explainFactor = factor },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                            contentDescription = "Explain ${factor.label}",
                                            tint = MidGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = formatFactorMultiplier(factor.value),
                                    color = viabilityFactorColor(factor.trend()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (panel != panels.last()) {
                        HorizontalDivider(color = Forest.copy(0.08f), modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                ViabilityFactorsBarChart(factors = factors)
            }
            
            if (plan.messages.isNotEmpty()) {
                HorizontalDivider(color = Forest.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))
                plan.messages.forEach { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MidGray)
                }
            }

            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Forest)
            ) {
                Text(
                    text = "Apply Suggested Contribution",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    explainFactor?.let { factor ->
        AlertDialog(
            onDismissRequest = { explainFactor = null },
            title = { Text(factor.label) },
            text = {
                Text(
                    "${factor.description()}\n\nCurrent multiplier: ${formatFactorMultiplier(factor.value)} (${factor.trend().name.lowercase(Locale.US)})."
                )
            },
            confirmButton = {
                TextButton(onClick = { explainFactor = null }) {
                    Text("Got it")
                }
            }
        )
    }
}

private fun formatFactorMultiplier(value: Double): String {
    return "x${String.format(Locale.US, "%.2f", value)}"
}

private fun viabilityFactorColor(trend: ViabilityFactorTrend): Color {
    return when (trend) {
        ViabilityFactorTrend.LIFT -> SuccessGreen
        ViabilityFactorTrend.HAIRCUT -> ErrorRed
        ViabilityFactorTrend.NEUTRAL -> MidGray
    }
}

@Composable
private fun ViabilityFactorsBarChart(factors: List<ViabilityFactorUi>) {
    val maxDistance = remember(factors) {
        factors.maxOfOrNull { abs(it.value - 1.0) }?.coerceAtLeast(0.05) ?: 0.05
    }
    var selectedTrend by remember { mutableStateOf<ViabilityFactorTrend?>(ViabilityFactorTrend.LIFT) }
    var selectedFactorKey by remember(factors) { mutableStateOf(factors.firstOrNull()?.key) }
    val selectedFactor = factors.firstOrNull { it.key == selectedFactorKey }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        Text(
            "Factor Impact Chart",
            style = MaterialTheme.typography.labelMedium,
            color = MidGray,
            fontWeight = FontWeight.SemiBold
        )

        factors.forEach { factor ->
            val trend = factor.trend()
            val intensity = (abs(factor.value - 1.0) / maxDistance).coerceIn(0.0, 1.0).toFloat()
            val isSelected = selectedFactorKey == factor.key

            Surface(
                onClick = { selectedFactorKey = factor.key },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) viabilityFactorColor(trend).copy(alpha = 0.08f) else Color.Transparent,
                border = if (isSelected) BorderStroke(1.dp, viabilityFactorColor(trend).copy(alpha = 0.3f)) else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            factor.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) viabilityFactorColor(trend) else MidGray,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatFactorMultiplier(factor.value),
                            style = MaterialTheme.typography.labelSmall,
                            color = viabilityFactorColor(trend),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { intensity },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = viabilityFactorColor(trend),
                        trackColor = LightGray.copy(alpha = 0.35f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViabilityLegendItem(
                label = "Lift",
                color = viabilityFactorColor(ViabilityFactorTrend.LIFT),
                isSelected = selectedTrend == ViabilityFactorTrend.LIFT,
                onClick = {
                    selectedTrend = if (selectedTrend == ViabilityFactorTrend.LIFT) null else ViabilityFactorTrend.LIFT
                }
            )
            ViabilityLegendItem(
                label = "Haircut",
                color = viabilityFactorColor(ViabilityFactorTrend.HAIRCUT),
                isSelected = selectedTrend == ViabilityFactorTrend.HAIRCUT,
                onClick = {
                    selectedTrend = if (selectedTrend == ViabilityFactorTrend.HAIRCUT) null else ViabilityFactorTrend.HAIRCUT
                }
            )
            ViabilityLegendItem(
                label = "Neutral",
                color = viabilityFactorColor(ViabilityFactorTrend.NEUTRAL),
                isSelected = selectedTrend == ViabilityFactorTrend.NEUTRAL,
                onClick = {
                    selectedTrend = if (selectedTrend == ViabilityFactorTrend.NEUTRAL) null else ViabilityFactorTrend.NEUTRAL
                }
            )
        }

        selectedTrend?.let { trend ->
            Text(
                text = trend.description(),
                style = MaterialTheme.typography.labelSmall,
                color = MidGray
            )
        }

        selectedFactor?.let { factor ->
            Text(
                text = "${factor.label}: ${factor.description()} Currently ${factor.trend().name.lowercase(Locale.US)} at ${formatFactorMultiplier(factor.value)}.",
                style = MaterialTheme.typography.labelSmall,
                color = MidGray
            )
        }
    }
}

@Composable
private fun ViabilityLegendItem(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (isSelected) color.copy(alpha = 0.12f) else Color.White,
        border = BorderStroke(1.dp, if (isSelected) color.copy(alpha = 0.45f) else LightGray.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = color
            ) {}
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) color else MidGray
            )
        }
    }
}
