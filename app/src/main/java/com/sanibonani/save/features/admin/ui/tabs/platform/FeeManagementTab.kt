package com.sanibonani.save.features.admin.ui.tabs.platform

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.PlatformFeeConfig
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.admin.ui.components.AccordionHeader
import com.sanibonani.save.features.admin.ui.components.HeaderWithIcon
import com.sanibonani.save.features.admin.presentation.PlatformAdminViewModel
import com.sanibonani.save.features.admin.presentation.state.PlatformAdminUiState

@Composable
fun FeeManagementTab(
    state: PlatformAdminUiState,
    vm: PlatformAdminViewModel
) {
    var globalSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var tieredFeesExpanded by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AccordionHeader(
                title = "Platform Global Settings",
                icon = Icons.Default.Settings,
                isExpanded = globalSettingsExpanded,
                onClick = { globalSettingsExpanded = !globalSettingsExpanded },
                count = 0
            )
        }

        if (globalSettingsExpanded) {
            item {
                GlassCard(color = MaterialTheme.colorScheme.surface) {
                    Text(
                        stringResource(R.string.ui_configure_core_fees_and_operational_cons),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(24.dp))

                    SanibonaniTextField(
                        value = state.memberCharge,
                        onValueChange = vm::updateMemberCharge,
                        label = stringResource(R.string.ui_monthly_service_fee_per_member),
                        prefix = { Text("R", color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    Spacer(Modifier.height(16.dp))

                    SanibonaniTextField(
                        value = state.registrationFee,
                        onValueChange = vm::updateRegistrationFee,
                        label = stringResource(R.string.ui_group_onboardingregistration_fee),
                        prefix = { Text("R", color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    Spacer(Modifier.height(16.dp))

                    SanibonaniTextField(
                        value = state.payoutFee,
                        onValueChange = vm::updatePayoutFee,
                        label = stringResource(R.string.ui_payout_processing_fee_flat),
                        prefix = { Text("R", color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    Spacer(Modifier.height(16.dp))

                    SanibonaniTextField(
                        value = state.lateFeePercent,
                        onValueChange = vm::updateLateFeePercent,
                        label = stringResource(R.string.ui_default_late_fee_penalty),
                        suffix = { Text("%", color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    Spacer(Modifier.height(16.dp))

                    SanibonaniTextField(
                        value = state.shortfallRecoveryFee,
                        onValueChange = vm::updateShortfallRecoveryFee,
                        label = "Shortfall Recovery Fee (Catch-up)",
                        prefix = { Text("R", color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    Spacer(Modifier.height(16.dp))

                    SanibonaniTextField(
                        value = state.autoSuspensionDays,
                        onValueChange = vm::updateAutoSuspensionDays,
                        label = stringResource(R.string.ui_autosuspension_inactivity_threshold),
                        suffix = { Text("Days", color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Spacer(Modifier.height(32.dp))

                    SanibonaniButton(
                        text = "Update Global Configuration",
                        onClick = { vm.saveGlobalFees() },
                        isLoading = state.isSaving,
                        containerColor = SuccessGreen,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        item {
            AccordionHeader(
                title = "Tiered Fees by Group Type",
                icon = Icons.Default.Settings,
                isExpanded = tieredFeesExpanded,
                onClick = { tieredFeesExpanded = !tieredFeesExpanded },
                count = 0
            )
        }

        if (tieredFeesExpanded) {
            items(items = state.granularFees) { config ->
                val title = if (config.groupType == GroupType.ROSCA) {
                    "ROSCA (${config.roscaCycle} Months)"
                } else {
                    config.groupType.name.replace("_", " ").uppercase()
                }

                GlassCard(color = MaterialTheme.colorScheme.surface) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            SanibonaniTextField(
                                value = config.registrationFee.toDouble().toString(),
                                onValueChange = { vm.updateGranularFee(config.groupType, config.roscaCycle, "registration", it) },
                                label = "Registration Fee",
                                prefix = { Text("R") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            SanibonaniTextField(
                                value = config.monthlyFee.toDouble().toString(),
                                onValueChange = { vm.updateGranularFee(config.groupType, config.roscaCycle, "monthly", it) },
                                label = "Monthly Fee",
                                prefix = { Text("R") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            SanibonaniTextField(
                                value = config.shortfallRecoveryFee.toDouble().toString(),
                                onValueChange = { vm.updateGranularFee(config.groupType, config.roscaCycle, "recovery", it) },
                                label = "Recovery Fee",
                                prefix = { Text("R") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }
            }

            item {
                SanibonaniButton(
                    text = "Save Tiered Fees",
                    onClick = { vm.saveGranularFees() },
                    isLoading = state.isSavingGranularFee,
                    containerColor = Navy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (state.saveSuccess) {
            item {
                InfoBox("Configuration updated successfully across the entire ecosystem.", InfoType.SUCCESS)
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}
