package com.sanibonani.save.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.GroupHealthScore
import com.sanibonani.save.domain.model.RiskZone

@Composable
fun GroupHealthScoreCard(
    modifier: Modifier = Modifier,
    score: GroupHealthScore?,
    isLoading: Boolean = false,
    onDetailsClick: () -> Unit = {},
    onReportClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Group Health Score",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Health score info",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally)
                )
            } else if (score != null) {
                // Score circle with zone coloring
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(
                            color = score.zone.backgroundColor(),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = score.overallScore.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "/100",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Zone label
                Surface(
                    color = score.zone.backgroundColor().copy(alpha = 0.2f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = score.zone.label(),
                        modifier = Modifier.padding(8.dp, 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = score.zone.backgroundColor(),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Top recommendations
                if (score.recommendations.isNotEmpty()) {
                    Text(
                        "Recommendations",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    score.recommendations.take(3).forEach { rec ->
                        Text(
                            text = "• $rec",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDetailsClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Details")
                    }
                    Button(
                        onClick = onReportClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Report")
                    }
                }
            }
        }
    }
}

// Extension functions for RiskZone
fun RiskZone.backgroundColor(): Color = when (this) {
    RiskZone.RED -> Color(0xFFE53935)
    RiskZone.YELLOW -> Color(0xFFFFA726)
    RiskZone.GREEN -> Color(0xFF66BB6A)
}

fun RiskZone.label(): String = when (this) {
    RiskZone.RED -> "At Risk (Red Zone)"
    RiskZone.YELLOW -> "Caution (Yellow Zone)"
    RiskZone.GREEN -> "Healthy (Green Zone)"
}


