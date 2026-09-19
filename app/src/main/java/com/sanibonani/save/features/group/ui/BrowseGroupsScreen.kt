package com.sanibonani.save.features.group.ui

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import android.location.Location
import com.google.android.gms.location.*
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ResolvableApiException
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.domain.config.SaReferenceData
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.ui.components.ProvinceHeaderCard
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.group.presentation.GroupViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun BrowseGroupsScreen(
    onGroupClick: (String) -> Unit,
    onRegisterGroup: () -> Unit,
    onBack: () -> Unit,
    vm: GroupViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clickDebouncer = rememberClickDebouncer()
    var showMap by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedLocationGroups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var triggerLocationRequest by remember { mutableStateOf(0) }

    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val fusedLocationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            triggerLocationRequest++
        }
    }

    DisposableEffect(Unit) {
        vm.setActive(true)
        onDispose {
            vm.setActive(false)
        }
    }

    fun requestLocation() {
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(10f)
            .build()

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client: SettingsClient = LocationServices.getSettingsClient(context)
        client.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        userLocation = location
                    } else {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { freshLocation ->
                                userLocation = freshLocation
                            }
                    }
                }
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                        locationSettingsLauncher.launch(intentSenderRequest)
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore
                    }
                }
            }
    }

    LaunchedEffect(triggerLocationRequest) {
        if (triggerLocationRequest > 0) {
            requestLocation()
        }
    }

    LaunchedEffect(Unit) {
        if (locationPermissionState.status.isGranted) {
            requestLocation()
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
                            tint = Navy
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
            // ── Search & View Toggle ──────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth().zIndex(1f),
                color = MaterialTheme.colorScheme.surface,
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
                            placeholder = { Text(stringResource(R.string.ui_search_groups), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { vm.updateFilter(query = "") }) {
                                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                        )

                        // Map/List Switcher
                        FilledTonalIconToggleButton(
                            checked = showMap,
                            onCheckedChange = { showMap = it },
                            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary
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
                                    label = stringResource(R.string.ui_province),
                                    items = listOf(null) + SaReferenceData.PROVINCES,
                                    selectedItem = state.provinceFilter,
                                    onItemSelected = { vm.updateFilter(province = it) },
                                    itemLabel = { it ?: "All Provinces" },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                SanibonaniDropdown(
                                    label = stringResource(R.string.ui_group_type),
                                    items = listOf(null) + GroupType.entries,
                                    selectedItem = state.typeFilter,
                                    onItemSelected = { vm.updateFilter(type = it) },
                                    itemLabel = { it?.displayName ?: "All Types" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (state.provinceFilter != null || state.typeFilter != null) {
                                TextButton(
                                    onClick = { vm.updateFilter(clearProvince = true, clearType = true) },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(stringResource(R.string.ui_clear_filters), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
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
                // For map view, we show ALL groups that have coordinates for better discovery,
                // while the list view remains filtered.
                val mapGroups = remember(state.rawGroups) {
                    state.rawGroups.filter { it.latitude != null && it.longitude != null }
                }

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
                } else if (showMap && mapGroups.isEmpty()) {
                    EmptyState(
                        icon = "📍",
                        title = "Map data not available yet",
                        description = "These groups don’t have saved locations yet. Try again later or switch to list view.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (showMap) {
                    SaOsmMap(
                        groups = mapGroups,
                        onMarker = { groupId -> clickDebouncer.processClick { onGroupClick(groupId) } },
                        onLocationTap = { groupsAtLocation ->
                            selectedLocationGroups = groupsAtLocation.sortedBy { it.name }
                        },
                        userLocation = userLocation,
                        onRequestLocation = { requestLocation() },
                        autoCenterOnGroups = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
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

                // ── Floating Action Button ────────────────────────────────────
                SmallFloatingActionButton(
                    onClick = { clickDebouncer.processClick(onRegisterGroup) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = Navy,
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

        // Location Detail Bottom Sheet
        if (selectedLocationGroups.isNotEmpty()) {
            ModalBottomSheet(
                onDismissRequest = { selectedLocationGroups = emptyList() },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = LightGray) }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp)
                ) {
                    Text(
                        text = "Groups at this location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = Navy
                    )

                    HorizontalDivider(color = Cream)

                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedLocationGroups) { group ->
                            Card(
                                onClick = {
                                    selectedLocationGroups = emptyList()
                                    clickDebouncer.processClick { onGroupClick(group.id ?: "") }
                                },
                                colors = CardDefaults.cardColors(containerColor = Cream.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Navy.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GroupIcon(
                                        logoEmoji = group.logoEmoji,
                                        size = 44.dp,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(group.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("${group.type.displayName} • ${group.currentMembers}/${group.maxMembers} members", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Navy)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupDiscoveryCard(
    group: Group,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GroupIcon(
                logoEmoji = group.logoEmoji,
                size = 56.dp,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${group.type.displayName} • ${group.city}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp), tint = Navy)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${group.currentMembers}/${group.maxMembers} members",
                        style = MaterialTheme.typography.labelSmall,
                        color = Navy,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = LightGray
            )
        }
    }
}
