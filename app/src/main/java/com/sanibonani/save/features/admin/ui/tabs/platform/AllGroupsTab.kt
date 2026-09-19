package com.sanibonani.save.features.admin.ui.tabs.platform

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.RiskZone
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.ui.components.AccordionHeader
import com.sanibonani.save.features.admin.ui.components.MetricRow
import com.sanibonani.save.features.admin.ui.components.ProvinceHeaderCard
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatPct
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.admin.presentation.PlatformAdminViewModel
import com.sanibonani.save.features.admin.presentation.state.PlatformAdminUiState

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
            placeholder = { Text(stringResource(R.string.ui_search_groups_by_name_or_province), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodyMedium
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
                            onClick = { expandedProvinces[province] = !(expandedProvinces[province] ?: false) }
                        )
                    }

                    if (expandedProvinces[province] == true) {
                        items(provinceGroups, key = { it.id ?: it.hashCode() }) { group ->
                            ModernNavigationLink(
                                title = group.name,
                                subtitle = buildString {
                                    append(group.type.displayName)
                                    append(" • ")
                                    append(group.city)
                                    append(" • ")
                                    append(group.currentMembers)
                                    append(" members")
                                    if (group.isPlatformSuspended) {
                                        append(" • SUSPENDED")
                                    }
                                },
                                icon = Icons.Default.Groups,
                                onClick = {
                                    showMetricsFor = group
                                    vm.fetchGroupMetrics(group.id ?: "")
                                },
                                accentColor = if (group.isPlatformSuspended) ErrorRed else Navy,
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
                            MetricRow("Reserve Adequacy", formatPct(metrics.reserveAdequacyPct), Navy)
                            MetricRow("Solvency Margin", formatPct(metrics.solvencyMarginPct), Navy)
                            MetricRow("Expected Claims (Ann)", formatZAR(metrics.expectedAnnualClaims), Charcoal)
                        }
                    }

                    if (state.isLoadingHealthScore) {
                        Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            CircularProgressIndicator(color = Navy, modifier = Modifier.size(24.dp))
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
                                    Text(stringResource(R.string.ui_composite_health_score), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text("${healthScore.overallScore}/100", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (healthScore.zone == RiskZone.GREEN) SuccessGreen else if (healthScore.zone == RiskZone.YELLOW) WarningAmber else ErrorRed)
                                }

                                Surface(
                                    color = (if (healthScore.zone == RiskZone.GREEN) SuccessGreen else if (healthScore.zone == RiskZone.YELLOW) WarningAmber else ErrorRed).copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        healthScore.zone.name,
                                        modifier = Modifier.padding(8.dp, 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (healthScore.zone == RiskZone.GREEN) SuccessGreen else if (healthScore.zone == RiskZone.YELLOW) WarningAmber else ErrorRed,
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
                                colors = ButtonDefaults.buttonColors(containerColor = Navy),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.ui_view_full_health_report))
                            }
                        }
                    }

                    if (state.selectedGroupMetrics == null && !state.isLoadingHealthScore && state.selectedGroupHealthScore == null) {
                         Box(Modifier.fillMaxWidth(), Alignment.Center) {
                             Text(stringResource(R.string.ui_no_data_available), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                         }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMetricsFor = null }) { Text(stringResource(R.string.ui_close), color = Navy) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
