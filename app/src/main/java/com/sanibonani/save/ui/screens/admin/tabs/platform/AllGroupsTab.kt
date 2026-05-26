package com.sanibonani.save.ui.screens.admin.tabs.platform

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.ui.components.ModernNavigationLink
import com.sanibonani.save.ui.components.formatPct
import com.sanibonani.save.ui.components.formatZAR
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.viewmodel.PlatformAdminUiState
import com.sanibonani.save.viewmodel.PlatformAdminViewModel
import com.sanibonani.save.ui.screens.admin.components.ProvinceHeaderCard
import com.sanibonani.save.ui.screens.admin.components.MetricRow
import com.sanibonani.save.ui.utils.backgroundColor
import com.sanibonani.save.ui.utils.label

@Composable
fun AllGroupsTab(
    groups: List<Group>,
    vm: PlatformAdminViewModel,
    state: PlatformAdminUiState,
    onNavigateToHealthScore: (groupId: String) -> Unit
) {
    var showMetricsFor by remember { mutableStateOf<Group?>(null) }
    
    val filteredGroups = remember(groups, state.searchQuery) {
        if (state.searchQuery.isBlank()) groups
        else groups.filter { it.name.contains(state.searchQuery, ignoreCase = true) || it.province?.contains(state.searchQuery, ignoreCase = true) == true }
    }

    val groupsByProvince = remember(filteredGroups) {
        filteredGroups.groupBy { it.province ?: "Other" }.toList().sortedBy { it.first }
    }
    val expandedProvinces = remember { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { vm.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search groups by name or province...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Forest,
                unfocusedBorderColor = LightGray,
                focusedLeadingIconColor = Forest,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            )
        )

        Box(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupsByProvince.forEach { (province, provinceGroups) ->
                    item(key = "all_groups_province_$province") {
                        ProvinceHeaderCard(
                            province = province,
                            count = provinceGroups.size,
                            isExpanded = expandedProvinces[province] == true,
                            onToggle = { expandedProvinces[province] = !(expandedProvinces[province] ?: false) }
                        )
                    }

                    if (expandedProvinces[province] == true) {
                        items(provinceGroups, key = { it.id ?: it.hashCode() }) { group ->
                            ModernNavigationLink(
                                title = group.name,
                                subtitle = "${group.type.displayName} • ${group.city}",
                                icon = Icons.Default.Groups,
                                onClick = { 
                                    showMetricsFor = group
                                    vm.fetchGroupMetrics(group.id ?: "")
                                },
                                accentColor = if (group.isPlatformSuspended) ErrorRed else Forest,
                                modifier = Modifier.padding(start = 12.dp).padding(vertical = 4.dp)
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    showMetricsFor?.let { group ->
        AlertDialog(
            onDismissRequest = { showMetricsFor = null },
            title = { Text("Actuarial Health: ${group.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    state.selectedGroupMetrics?.let { metrics ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricRow("Risk Score", "${metrics.compositeRiskScore}/100", 
                                if (metrics.compositeRiskScore < 40) SuccessGreen else ErrorRed)
                            MetricRow("Reserve Adequacy", formatPct(metrics.reserveAdequacyPct), Forest)
                            MetricRow("Solvency Margin", formatPct(metrics.solvencyMarginPct), Forest)
                            MetricRow("Expected Claims (Ann)", formatZAR(metrics.expectedAnnualClaims), Charcoal)
                        }
                    }

                    if (state.isLoadingHealthScore) {
                        Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            CircularProgressIndicator(color = Forest, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        state.selectedGroupHealthScore?.let { healthScore ->
                            HorizontalDivider(color = LightGray.copy(alpha = 0.3f))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Composite Health Score", style = MaterialTheme.typography.labelSmall, color = MidGray)
                                    Text("${healthScore.overallScore}/100", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = healthScore.zone.backgroundColor())
                                }
                                
                                Surface(
                                    color = healthScore.zone.backgroundColor().copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        healthScore.zone.label(),
                                        modifier = Modifier.padding(8.dp, 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = healthScore.zone.backgroundColor(),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Button(
                                onClick = { 
                                    showMetricsFor = null
                                    onNavigateToHealthScore(group.id ?: "")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Forest),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("View Full Health Report")
                            }
                        }
                    }
                    
                    if (state.selectedGroupMetrics == null && !state.isLoadingHealthScore && state.selectedGroupHealthScore == null) {
                         Box(Modifier.fillMaxWidth(), Alignment.Center) {
                             Text("No data available.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                         }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMetricsFor = null }) { Text("Close", color = Forest) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}
