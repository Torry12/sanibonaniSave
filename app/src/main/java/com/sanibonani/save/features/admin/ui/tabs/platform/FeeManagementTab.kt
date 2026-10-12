package com.sanibonani.save.features.admin.ui.tabs.platform

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
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
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.admin.ui.components.AccordionHeader
import com.sanibonani.save.features.admin.ui.components.HeaderWithIcon
import com.sanibonani.save.features.admin.presentation.PlatformFeeConfigViewModel
import com.sanibonani.save.features.admin.presentation.state.PlatformFeeConfigUiState

@Composable
fun FeeManagementTab(
    state: PlatformFeeConfigUiState,
    vm: PlatformFeeConfigViewModel
) {
    var globalSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var bankSettingsExpanded by rememberSaveable { mutableStateOf(true) }
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
                GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
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
                        value = state.loanInterestRate,
                        onValueChange = vm::updateLoanInterestRate,
                        label = "Default Loan Interest Rate (%)",
                        suffix = { Text("% p.a.", color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    Spacer(Modifier.height(16.dp))

                    SanibonaniTextField(
                        value = state.loanProcessingFee,
                        onValueChange = vm::updateLoanProcessingFee,
                        label = "Platform Loan Processing Fee (Flat)",
                        prefix = { Text("R", color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    Spacer(Modifier.height(16.dp))

                    SanibonaniTextField(
                        value = state.groupSuspensionLiftingFee,
                        onValueChange = vm::updateGroupSuspensionLiftingFee,
                        label = "Group Suspension Lifting Fee (Flat)",
                        prefix = { Text("R", color = MaterialTheme.colorScheme.onSurface) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    Spacer(Modifier.height(16.dp))

                    SanibonaniTextField(
                        value = state.memberSuspensionLiftingFee,
                        onValueChange = vm::updateMemberSuspensionLiftingFee,
                        label = "Member Suspension Lifting Fee (Flat)",
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

                    Spacer(Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = vm::mapGlobalToGranularFees,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Map Global Fields to Group Tiers")
                    }
                }
            }
        }

        item {
            AccordionHeader(
                title = "Platform Bank Account",
                icon = Icons.Default.AccountCircle,
                isExpanded = bankSettingsExpanded,
                onClick = { bankSettingsExpanded = !bankSettingsExpanded },
                count = 0
            )
        }

        if (bankSettingsExpanded) {
            item {
                GlassCard(accentColor = MaterialTheme.colorScheme.secondary) {
                    Text(
                        "Set the central bank account for platform fee collections.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(24.dp))

                    SanibonaniTextField(
                        value = state.platformAccountHolder,
                        onValueChange = vm::updatePlatformAccountHolder,
                        label = "Account Holder"
                    )

                    Spacer(Modifier.height(16.dp))

                    SanibonaniTextField(
                        value = state.platformBankName,
                        onValueChange = vm::updatePlatformBankName,
                        label = "Bank Name"
                    )

                    Spacer(Modifier.height(16.dp))

                    SanibonaniTextField(
                        value = state.platformAccountNumber,
                        onValueChange = vm::updatePlatformAccountNumber,
                        label = "Account Number",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Spacer(Modifier.height(16.dp))

                    SanibonaniTextField(
                        value = state.platformBranchCode,
                        onValueChange = vm::updatePlatformBranchCode,
                        label = "Branch Code",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }

        item {
            SanibonaniButton(
                text = "Update Global Configuration",
                onClick = { vm.saveGlobalFees() },
                isLoading = state.isSaving,
                containerColor = SuccessGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            AccordionHeader(
                title = "Tiered Fees by Group Type",
                icon = Icons.Default.Settings,
                isExpanded = tieredFeesExpanded,
                onClick = { tieredFeesExpanded = !tieredFeesExpanded },
                count = state.granularFees.size
            )
        }

        if (tieredFeesExpanded) {
            items(
                items = state.granularFees,
                key = { "${it.groupType}-${it.roscaCycle}" }
            ) { config ->
                val title = if (config.groupType == GroupType.ROSCA) {
                    "ROSCA (${config.roscaCycle} Months)"
                } else {
                    config.groupType.name.replace("_", " ").uppercase()
                }

                val regKey = "${config.groupType}_${config.roscaCycle}_registration"
                val monthlyKey = "${config.groupType}_${config.roscaCycle}_monthly"
                val shortfallKey = "${config.groupType}_${config.roscaCycle}_shortfall"

                val regValue = state.granularFeeInputs[regKey] ?: config.registrationFee.toPlainString()
                val monthlyValue = state.granularFeeInputs[monthlyKey] ?: config.monthlyFee.toPlainString()
                val shortfallValue = state.granularFeeInputs[shortfallKey] ?: config.shortfallRecoveryFee.toPlainString()

                GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SanibonaniTextField(
                            value = regValue,
                            onValueChange = { vm.updateGranularFeeInput(config.groupType, config.roscaCycle, "registration", it) },
                            label = "Group Onboarding / Registration Fee (R)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) {
                                SanibonaniTextField(
                                    value = monthlyValue,
                                    onValueChange = { vm.updateGranularFeeInput(config.groupType, config.roscaCycle, "monthly", it) },
                                    label = "Monthly Service Fee per Member (R)",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                SanibonaniTextField(
                                    value = shortfallValue,
                                    onValueChange = { vm.updateGranularFeeInput(config.groupType, config.roscaCycle, "shortfall", it) },
                                    label = "Shortfall Recovery Fee (Catch-up) (R)",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
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
                InfoBox("Configuration updated successfully across the entire ecosystem.", type = InfoType.SUCCESS)
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}
