package com.sanibonani.save.features.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.RiskZone
import com.sanibonani.save.features.admin.presentation.HealthScoreViewModel
import com.sanibonani.save.ui.theme.HealthScoreCritical
import com.sanibonani.save.ui.theme.HealthScoreHealthy
import com.sanibonani.save.ui.theme.HealthScoreWarning
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.ui.theme.RiskZoneCritical
import com.sanibonani.save.ui.theme.RiskZoneHealthy
import com.sanibonani.save.ui.theme.RiskZoneWarning
import com.sanibonani.save.ui.utils.rememberClickDebouncer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScoreDetailScreen(
    groupId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onExportReport: () -> Unit = {},
    vm: HealthScoreViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clickDebouncer = rememberClickDebouncer()

    LaunchedEffect(groupId) {
        vm.loadHealthScore(groupId)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = { Text(stringResource(R.string.ui_health_score_details)) },
            navigationIcon = {
                IconButton(onClick = { clickDebouncer.processClick(onBack) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Navy)
            }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Unknown error")
            }
        } else {
            state.score?.let { score ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        // Overall score section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(stringResource(R.string.ui_overall_score),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                LinearProgressIndicator(
                                    progress = { (score.overallScore / 100f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    color = score.zone.backgroundColor(),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${score.overallScore}/100",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = score.zone.backgroundColor().copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            score.zone.label(),
                                            modifier = Modifier.padding(8.dp, 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = score.zone.backgroundColor(),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(stringResource(R.string.ui_component_breakdown),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Component cards
                    items(score.components.size) { index ->
                        val (name, value) = score.components.toList()[index]
                        ComponentScoreCard(
                            name = name,
                            score = value,
                            weight = getComponentWeight(name)
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.ui_recommendations),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(score.recommendations.size) { index ->
                        RecommendationCard(text = score.recommendations[index])
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onExportReport,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(stringResource(R.string.ui_export_full_report_as_pdf))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentScoreCard(
    name: String,
    score: Int,
    weight: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "$score/100",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { (score / 100f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    score < 40 -> HealthScoreCritical
                    score < 70 -> HealthScoreWarning
                    else -> HealthScoreHealthy
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "Weight: ${(weight * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendationCard(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun getComponentWeight(name: String): Float = when (name) {
    "Solvency Ratio" -> 0.25f
    "Loss Ratio" -> 0.25f
    "Reserve Adequacy" -> 0.20f
    "Funding Ratio" -> 0.20f
    "Member Retention" -> 0.10f
    else -> 0f
}

private fun RiskZone.backgroundColor(): Color = when (this) {
    RiskZone.RED -> RiskZoneCritical
    RiskZone.YELLOW -> RiskZoneWarning
    RiskZone.GREEN -> RiskZoneHealthy
}

private fun RiskZone.label(): String = when (this) {
    RiskZone.RED -> "Critical Risk"
    RiskZone.YELLOW -> "Moderate Risk"
    RiskZone.GREEN -> "Healthy"
}
