package com.sanibonani.save.features.group.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.domain.config.SaReferenceData
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.group.presentation.GroupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseGroupsScreen(
    onGroupClick: (String) -> Unit,
    onRegisterGroup: () -> Unit,
    onBack: () -> Unit,
    vm: GroupViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clickDebouncer = rememberClickDebouncer()
    var showMap by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedLocationGroups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var searchQuery by remember { mutableStateOf(state.searchQuery) }

    val userLocation = rememberBrowseGroupsLocation()

    DisposableEffect(Unit) {
        vm.setActive(true)
        onDispose {
            vm.setActive(false)
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery != state.searchQuery) {
            delay(500.milliseconds) // Debounce 500ms
            vm.updateFilter(query = searchQuery)
        }
    }

    LaunchedEffect(state.searchQuery) {
        if (searchQuery != state.searchQuery) {
            searchQuery = state.searchQuery
        }
    }

    Scaffold(
        topBar = {
            SanibonaniTopBar(
                title = "Discover Groups",
                onBack = { clickDebouncer.processClick(onBack) },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            imageVector = if (showFilters) Icons.Default.FilterListOff else Icons.Default.FilterList,
                            contentDescription = "Toggle Filters",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            BrowseGroupsSearchBar(
                state = state,
                vm = vm,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                showMap = showMap,
                onShowMapChange = { showMap = it },
                showFilters = showFilters,
                onToggleFilters = { showFilters = !showFilters }
            )

            // ── Main Content ──────────────────────────────────────────────────
            Box(Modifier.fillMaxSize()) {
                val mapGroups = remember(state.rawGroups) {
                    state.rawGroups.filter {
                        it.latitude != null || SaReferenceData.PROVINCE_CENTERS.containsKey(it.province)
                    }
                }

                when {
                    state.isLoading && state.filteredGroups.isEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(6) { GroupCardShimmer() }
                        }
                    }
                    state.filteredGroups.isEmpty() -> {
                        EmptyState(
                            icon = "🔍",
                            title = "No groups found",
                            description = "Try adjusting your search or filters to find what you're looking for.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    showMap && mapGroups.isEmpty() -> {
                        EmptyState(
                            icon = "📍",
                            title = "Map data not available yet",
                            description = "These groups don’t have saved locations yet. Try again later or switch to list view.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    showMap -> {
                        SaOsmMap(
                            groups = state.filteredGroups,
                            modifier = Modifier.fillMaxSize(),
                            onGroupClick = { id -> clickDebouncer.processClick { onGroupClick(id) } },
                            onLocationTap = { groups -> selectedLocationGroups = groups },
                            userLocation = userLocation,
                            autoCenterOnGroups = true
                        )
                    }
                    else -> {
                        val expandedProvinces = remember { mutableStateMapOf<String, Boolean>() }
                        val isSearching = state.searchQuery.isNotEmpty()

                        PullToRefreshBox(
                            isRefreshing = state.isLoading,
                            onRefresh = { vm.loadGroups() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.groupsByProvince.forEach { (province, provinceGroups) ->
                                    item(key = "province_header_$province") {
                                        val isExpanded = expandedProvinces[province] == true || isSearching
                                        ProvinceHeaderCard(
                                            province = province,
                                            count = provinceGroups.size,
                                            isExpanded = isExpanded,
                                            onClick = { expandedProvinces[province] = !isExpanded }
                                        )
                                    }

                                    if (expandedProvinces[province] == true || isSearching) {
                                        items(
                                            items = provinceGroups,
                                            key = { it.id ?: "${it.name}_${it.city}" }
                                        ) { group ->
                                            GroupDiscoveryCard(
                                                group = group,
                                                onClick = { clickDebouncer.processClick { onGroupClick(group.id ?: "") } }
                                            )
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(80.dp)) }
                            }
                        }
                    }
                }

                SmallFloatingActionButton(
                    onClick = { clickDebouncer.processClick(onRegisterGroup) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Text("New Group", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (selectedLocationGroups.isNotEmpty()) {
            LocationDetailBottomSheet(
                groups = selectedLocationGroups,
                onGroupClick = { id ->
                    selectedLocationGroups = emptyList()
                    clickDebouncer.processClick { onGroupClick(id) }
                },
                onDismiss = { selectedLocationGroups = emptyList() }
            )
        }
    }
}
