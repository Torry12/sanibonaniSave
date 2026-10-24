package com.sanibonani.save.features.group.ui.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.sanibonani.save.R
import com.sanibonani.save.domain.config.SaReferenceData
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationState
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import com.sanibonani.save.features.group.ui.components.DEFAULT_LAT
import com.sanibonani.save.features.group.ui.components.DEFAULT_LON
import com.sanibonani.save.features.group.ui.components.SectionHeader
import com.sanibonani.save.ui.components.AutoCompleteTextField
import com.sanibonani.save.ui.components.SaLocationPickerMap
import java.util.Locale

/** Step 2: GPS, address autocomplete, city/township and province. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegStep2(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionHeader(
            title = stringResource(R.string.ui_location),
            description = stringResource(R.string.ui_where_is_your_group_based)
        )

        OutlinedButton(
            onClick = {
                try {
                    val client = LocationServices.getFusedLocationProviderClient(context)
                    client.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) {
                            vm.onEvent(GroupFormEvent.GpsLocationPicked(loc.latitude, loc.longitude, "Current Location"))
                        } else {
                            vm.onEvent(GroupFormEvent.GpsLocationPicked(DEFAULT_LAT, DEFAULT_LON, "Johannesburg"))
                        }
                    }.addOnFailureListener {
                        vm.onEvent(GroupFormEvent.GpsLocationPicked(DEFAULT_LAT, DEFAULT_LON, "Johannesburg"))
                    }
                } catch (_: SecurityException) {
                    vm.onEvent(GroupFormEvent.GpsLocationPicked(DEFAULT_LAT, DEFAULT_LON, "Johannesburg"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ui_use_current_gps_location))
        }

        Spacer(Modifier.height(4.dp))

        AutoCompleteTextField(
            value = s.addressQuery,
            onValueChange = { vm.onEvent(GroupFormEvent.AddressQueryChanged(it)) },
            label = "Search Address / Location",
            placeholder = "Type street, suburb, township, or city...",
            suggestions = s.addressSuggestions,
            onSuggestionClick = { vm.onEvent(GroupFormEvent.AddressSelected(it)) },
            isLoading = s.isSearchingAddress,
            isError = s.fieldErrors.containsKey("city") || s.fieldErrors.containsKey("street"),
            errorMessage = s.fieldErrors["street"] ?: s.fieldErrors["city"]
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SanibonaniTextField(
                value = s.city,
                onValueChange = { vm.onEvent(GroupFormEvent.CityChanged(it)) },
                label = "City / Town *",
                modifier = Modifier.weight(1f),
                isError = s.fieldErrors.containsKey("city"),
                supportingText = s.fieldErrors["city"]
            )

            SanibonaniTextField(
                value = s.township,
                onValueChange = { vm.onEvent(GroupFormEvent.TownshipChanged(it)) },
                label = "Township / Suburb",
                modifier = Modifier.weight(1f),
                isError = s.fieldErrors.containsKey("township"),
                supportingText = s.fieldErrors["township"]
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxWidth()) {
                SaLocationPickerMap(
                    selectedLat = s.latitude,
                    selectedLon = s.longitude,
                    onLocationPicked = { lat, lon -> vm.onEvent(GroupFormEvent.CoordinatesPicked(lat, lon)) }
                )

                if (s.latitude != null && s.longitude != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = String.format(Locale.US, "GPS: %.4f, %.4f", s.latitude, s.longitude),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        SectionHeader(title = stringResource(R.string.ui_province_1))

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SaReferenceData.PROVINCES.forEach { p ->
                val isSelected = s.province == p
                FilterChip(
                    selected = isSelected,
                    onClick = { vm.onEvent(GroupFormEvent.ProvinceSelected(p)) },
                    label = { Text(p, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
    }
}
