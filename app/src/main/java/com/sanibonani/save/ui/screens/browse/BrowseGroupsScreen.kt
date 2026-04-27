package com.sanibonani.save.ui.screens.browse

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.viewmodel.GroupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseGroupsScreen(
    onGroupClick: (String) -> Unit,
    onRegisterGroup: () -> Unit,
    onBack: () -> Unit,
    vm: GroupViewModel = hiltViewModel()
) {
    val state by vm.listState.collectAsState()
    var showMap by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedLocationGroups by remember { mutableStateOf<List<Group>>(emptyList()) }

    LaunchedEffect(Unit) {
        vm.loadGroups()
    }

    Scaffold(
        topBar = {
            SanibonaniTopBar(
                title = "Discover Groups",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            imageVector = if (showFilters) Icons.Default.FilterListOff else Icons.Default.FilterList,
                            contentDescription = "Toggle Filters",
                            tint = Forest
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Cream)
                .padding(padding)
        ) {
            // ── Search & View Toggle ──────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { vm.updateFilter(query = it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search groups...", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = MidGray, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { vm.updateFilter(query = "") }) {
                                        Icon(Icons.Default.Close, null, tint = MidGray, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Forest,
                                unfocusedBorderColor = LightGray,
                                focusedContainerColor = Cream.copy(alpha = 0.5f),
                                unfocusedContainerColor = Cream.copy(alpha = 0.5f)
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        // Map/List Switcher
                        FilledTonalIconToggleButton(
                            checked = showMap,
                            onCheckedChange = { showMap = it },
                            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                                containerColor = Cream,
                                checkedContainerColor = Forest,
                                checkedContentColor = Color.White
                            ),
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (showMap) Icons.AutoMirrored.Filled.List else Icons.Default.Map,
                                contentDescription = "Toggle View",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showFilters) {
                        Column(Modifier.padding(top = 12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SanibonaniDropdown(
                                    label = "Province",
                                    options = listOf(null) + SA_PROVINCES,
                                    selectedOption = state.provinceFilter,
                                    onOptionSelected = { vm.updateFilter(province = it) },
                                    optionToString = { it ?: "All Provinces" },
                                    modifier = Modifier.weight(1f)
                                )
                                SanibonaniDropdown(
                                    label = "Group Type",
                                    options = listOf(null) + GroupType.entries,
                                    selectedOption = state.typeFilter,
                                    onOptionSelected = { vm.updateFilter(type = it) },
                                    optionToString = { it?.displayName ?: "All Types" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (state.provinceFilter != null || state.typeFilter != null) {
                                TextButton(
                                    onClick = { vm.updateFilter(province = "All Provinces", clearType = true) },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Clear Filters", color = Forest, style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // ── Main Content ──────────────────────────────────────────────────
            Box(Modifier.fillMaxSize()) {
                if (showMap) {
                    SaOsmMap(
                        groups = state.filteredGroups,
                        onMarker = onGroupClick,
                        onLocationTap = { groupsAtLocation ->
                            selectedLocationGroups = groupsAtLocation.sortedBy { it.name }
                        },
                        autoCenterOnGroups = false,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Helpful overlay when groups have no coordinates yet.
                    val hasAnyCoords = remember(state.filteredGroups) {
                        state.filteredGroups.any { it.latitude != null && it.longitude != null }
                    }
                    if (!hasAnyCoords && !state.isLoading) {
                        EmptyState(
                            icon = "📍",
                            title = "Map data not available yet",
                            description = "These groups don’t have saved locations yet. Try again later or switch to list view.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                } else {
                    if (state.isLoading && state.filteredGroups.isEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(6) { GroupCardShimmer() }
                        }
                    } else if (state.filteredGroups.isEmpty()) {
                        EmptyState(
                            icon = "🔍",
                            title = "No groups found",
                            description = "Try adjusting your search or filters to find what you're looking for.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = state.filteredGroups,
                                key = { it.id ?: "${it.name}_${it.city}" }
                            ) { group ->
                                GroupDiscoveryCard(
                                    group = group,
                                    onClick = { onGroupClick(group.id ?: "") }
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }

                // Register Group FAB
                FloatingActionButton(
                    onClick = onRegisterGroup,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = Forest,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, "Register Group")
                        Spacer(Modifier.width(8.dp))
                        Text("New Group", fontWeight = FontWeight.Bold)
                    }
                }

                // Error Message Overlay
                state.error?.let {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .padding(bottom = 24.dp),
                        color = ErrorBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ErrorRed.copy(0.2f))
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.loadGroups() }) {
                                Text("Retry", color = ErrorRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (selectedLocationGroups.isNotEmpty()) {
                    AlertDialog(
                        onDismissRequest = { selectedLocationGroups = emptyList() },
                        title = { Text("Available Groups") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val locationLabel = selectedLocationGroups.firstOrNull()?.let { group ->
                                    "${group.city}, ${group.province}"
                                } ?: "This location"
                                Text(
                                    text = "${selectedLocationGroups.size} groups found at $locationLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MidGray
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(selectedLocationGroups, key = { it.id ?: it.name }) { group ->
                                        Surface(
                                            onClick = {
                                                selectedLocationGroups = emptyList()
                                                group.id?.let(onGroupClick)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = Cream.copy(alpha = 0.6f)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(group.logoEmoji, style = MaterialTheme.typography.titleMedium)
                                                Spacer(Modifier.width(10.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        text = group.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Charcoal
                                                    )
                                                    Text(
                                                        text = "${group.currentMembers}/${group.maxMembers} members",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MidGray
                                                    )
                                                }
                                                Text(
                                                    text = formatZAR(group.monthlyContribution),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Forest,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { selectedLocationGroups = emptyList() }) {
                                Text("Close", color = Forest)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GroupDiscoveryCard(group: Group, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Forest.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(group.logoEmoji, fontSize = 28.sp)
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Charcoal
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Forest, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${group.city}, ${group.province}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MidGray
                        )
                    }
                }
                
                Surface(
                    color = Cream,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = group.type.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Forest,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = group.description ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MidGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
            
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Cream)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Groups, null, tint = MidGray, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${group.currentMembers} / ${group.maxMembers} members",
                        style = MaterialTheme.typography.labelMedium,
                        color = Charcoal,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Text(
                    text = "${formatZAR(group.monthlyContribution)} /mo",
                    style = MaterialTheme.typography.labelLarge,
                    color = Forest,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

