package com.sanibonani.save.features.admin.ui.tabs.platform

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.admin.presentation.PlatformAnalyticsViewModel

@Composable
fun PlatformAnalyticsTab(
    analytics: PlatformAnalytics,
    totalRevenueShare: Money,
    provinceDistribution: List<Pair<String, Int>>,
    marketSegments: List<Pair<String, Int>>,
    vm: PlatformAnalyticsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    title = "👥",
                    label = "Total Groups",
                    value = analytics.totalGroups.toString(),
                    trend = "Active across SA",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "👤",
                    label = "Total Members",
                    value = analytics.totalMembers.toString(),
                    trend = "Enrolled users",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    title = "💰",
                    label = "Total Balance",
                    value = analytics.totalBalance.formatShort(),
                    trend = "Group holdings",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "📈",
                    label = "Rev Share",
                    value = totalRevenueShare.formatShort(),
                    trend = "Actual yield",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield,
                        null,
                        tint = if (analytics.averageRiskScore > 70) SuccessGreen else if (analytics.averageRiskScore > 40) WarningAmber else Color.Red,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_average_risk_score), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.ui_platform_actuarial_health), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${analytics.averageRiskScore.toInt()}/100", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        if (analytics.averageRiskScore == 0.0) {
                            TextButton(
                                onClick = { vm.syncPlatformMetrics() },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("SYNC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }

        item {
            SanibonaniSectionHeader("Group Distribution", subtitle = "By province")
            if (provinceDistribution.isEmpty()) {
                EmptyStateSmall("No distribution data available.")
            } else {
                provinceDistribution.forEach { (prov, count) ->
                    val pct = if (analytics.totalGroups > 0) count.toFloat() / analytics.totalGroups else 0f
                    DistributionRow(prov, count, pct, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        item {
            SanibonaniSectionHeader("Market Segments", subtitle = "By group type")
            if (marketSegments.isEmpty()) {
                EmptyStateSmall("No market segment data available.")
            } else {
                marketSegments.forEach { (type, count) ->
                    val pct = if (analytics.totalGroups > 0) count.toFloat() / analytics.totalGroups else 0f
                    val label = type.replace("_", " ")
                        .lowercase()
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                    DistributionRow(label, count, pct, MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}
