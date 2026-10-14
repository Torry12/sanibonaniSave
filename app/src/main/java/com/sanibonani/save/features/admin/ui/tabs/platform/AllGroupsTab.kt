package com.sanibonani.save.features.admin.ui.tabs.platform

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.features.admin.presentation.PlatformAnalyticsViewModel
import com.sanibonani.save.features.admin.presentation.state.PlatformAnalyticsUiState
import com.sanibonani.save.features.admin.ui.components.ActuarialHealthDialog
import com.sanibonani.save.features.admin.ui.components.AccordionHeader
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*

@Composable
fun AllGroupsTab(
    state: PlatformAnalyticsUiState,
    vm: PlatformAnalyticsViewModel,
    onNavigateToHealthScore: (groupId: String) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMetricsFor by remember { mutableStateOf<Group?>(null) }
    val expandedProvinces = remember { mutableStateMapOf<String, Boolean>() }

    val filteredGroups = remember(searchQuery, state.groups) {
        state.groups.filter { it.name.contains(searchQuery, ignoreCase = true) || it.province?.contains(searchQuery, ignoreCase = true) == true }
    }

    val groupsByProvince = remember(filteredGroups) {
        filteredGroups.groupBy { it.province ?: "Other" }
    }

    LaunchedEffect(groupsByProvince) {
        groupsByProvince.keys.forEach { province ->
            if (expandedProvinces[province] == null) {
                expandedProvinces[province] = true
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(stringResource(R.string.ui_search_groups_by_name_or_province)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groupsByProvince.forEach { (province, groups) ->
                val isExpanded = expandedProvinces[province] ?: true
                item(key = "header_$province") {
                    AccordionHeader(
                        title = province.uppercase(),
                        count = groups.size,
                        isExpanded = isExpanded,
                        onClick = { expandedProvinces[province] = !isExpanded },
                        icon = Icons.Default.LocationOn
                    )
                }

                if (isExpanded) {
                    items(items = groups, key = { it.id ?: it.name }) { group ->
                        DashboardItemCard(
                            title = group.name,
                            subtitle = buildAnnotatedString {
                                append(group.type.name.replace("_", " "))
                                append(" • ")
                                append(group.city ?: "")
                                append(" • ")
                                append(group.currentMembers.toString())
                                append(" members")
                                if (group.isPlatformSuspended) {
                                    withStyle(SpanStyle(color = ErrorRed, fontWeight = FontWeight.Bold)) {
                                        append(" • SUSPENDED")
                                    }
                                }
                            },
                            icon = Icons.Default.Groups,
                            onClick = {
                                showMetricsFor = group
                                vm.fetchGroupMetrics(group.id ?: "")
                            },
                            accentColor = if (group.isPlatformSuspended) ErrorRed else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    showMetricsFor?.let { group ->
        ActuarialHealthDialog(
            group = group,
            metrics = state.selectedGroupMetrics,
            healthScore = state.selectedGroupHealthScore,
            isLoading = state.isLoadingHealthScore,
            onDismiss = { showMetricsFor = null },
            onNavigateToHealthScore = onNavigateToHealthScore,
            onDeleteGroup = { groupId -> vm.deleteGroupFromPlatform(groupId) }
        )
    }
}
