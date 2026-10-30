package com.sanibonani.save.features.group.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.IntentSender
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.sanibonani.save.R
import com.sanibonani.save.domain.config.SaReferenceData
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.features.group.presentation.GroupUiState
import com.sanibonani.save.features.group.presentation.GroupViewModel
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*

/**
 * Search field, map/list toggle and the province/type filter row of the discovery screen.
 */
@Composable
internal fun BrowseGroupsSearchBar(
    state: GroupUiState,
    vm: GroupViewModel,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showMap: Boolean,
    onShowMapChange: (Boolean) -> Unit,
    showFilters: Boolean,
    onToggleFilters: () -> Unit
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
                    value = searchQuery,
                    onValueChange = { onSearchQueryChange(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ui_search_groups), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
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

                FilledTonalIconToggleButton(
                    checked = showMap,
                    onCheckedChange = { onShowMapChange(it) },
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
                        SanibonaniDropdown(
                            label = stringResource(R.string.ui_group_type),
                            items = listOf(null) + GroupType.entries,
                            selectedItem = state.typeFilter,
                            onItemSelected = { vm.updateFilter(type = it) },
                            itemLabel = { it?.displayName ?: "All Types" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if ((state.provinceFilter != null) || (state.typeFilter != null)) {
                        TextButton(
                            onClick = { vm.updateFilter(clearProvince = true, clearType = true) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.ui_clear_filters), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Owns the discovery screen's location pipeline: permission prompt, high-accuracy request,
 * the settings-resolution round trip, and the current fix. Returns null until a fix lands.
 */
@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
internal fun rememberBrowseGroupsLocation(): Location? {
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var triggerLocationRequest by remember { mutableIntStateOf(0) }

    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val fusedLocationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            triggerLocationRequest++
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
                    } catch (e: IntentSender.SendIntentException) {
                        // User dismissed the location-settings prompt; nothing to recover.
                        com.sanibonani.save.domain.logging.AppLogger.i(
                            "BrowseGroupsScreen",
                            "Location settings resolution cancelled"
                        )
                    }
                }
            }
    }

    LaunchedEffect(triggerLocationRequest) {
        if (triggerLocationRequest > 0) requestLocation()
    }

    LaunchedEffect(locationPermissionState.status) {
        if (locationPermissionState.status.isGranted) requestLocation()
    }

    return userLocation
}
