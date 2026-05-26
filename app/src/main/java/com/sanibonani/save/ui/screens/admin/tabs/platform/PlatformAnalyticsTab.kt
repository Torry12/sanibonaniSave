package com.sanibonani.save.ui.screens.admin.tabs.platform

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.screens.admin.components.DistributionRow

@Composable
fun PlatformAnalyticsTab(analytics: PlatformAnalytics) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Network-Wide KPIs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("🏢", "Total Groups", "${analytics.totalGroups}", "Active across SA", accentColor = Forest, modifier = Modifier.weight(1f))
                StatCard("👥", "Total Members", "${analytics.totalMembers}", "Enrolled users", accentColor = Forest, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("💰", "Network Balance", formatZAR(analytics.totalBalance), "Group holdings", accentColor = Gold, modifier = Modifier.weight(1f))
                StatCard("📈", "Platform Revenue", formatZAR(analytics.totalPlatformFees), "From monthly fees", accentColor = SuccessGreen, modifier = Modifier.weight(1f))
            }
        }
        
        item {
            StatCard("🛡️", "Avg Risk Score", "${analytics.averageRiskScore.toInt()}/100", "Actuarial platform health", accentColor = InfoBlue)
        }

        item {
            SectionTitle("Group Distribution", "By province")
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, LightGray.copy(0.3f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    analytics.provinceDistribution.toList().sortedByDescending { it.second }.forEach { (prov, count) ->
                        val pct = if (analytics.totalGroups > 0) count.toFloat() / analytics.totalGroups else 0f
                        DistributionRow(prov, count, pct, Forest)
                    }
                }
            }
        }

        item {
            SectionTitle("Market Segments", "By group type")
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, LightGray.copy(0.3f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    analytics.groupTypeDistribution.toList().sortedByDescending { it.second }.forEach { (type, count) ->
                        val pct = if (analytics.totalGroups > 0) count.toFloat() / analytics.totalGroups else 0f
                        DistributionRow(type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, count, pct, InfoBlue)
                    }
                }
            }
        }
        
        item { Spacer(Modifier.height(32.dp)) }
    }
}
