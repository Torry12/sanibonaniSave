package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.FinancialProviderType
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.presentation.AdminSettingsViewModel
import com.sanibonani.save.features.financial.presentation.FinancialProviderUiState
import com.sanibonani.save.features.admin.presentation.state.AdminSettingsState
import com.sanibonani.save.ui.components.InfoType

@Composable
fun SettingsTab(
    state: AdminSettingsState,
    vm: AdminSettingsViewModel,
    providerState: FinancialProviderUiState
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SanibonaniSectionHeader("Group Settings")

        SanibonaniTextField(
            value = state.settings.monthlyContribution,
            onValueChange = { vm.updateSetting("monthly_contribution", it) },
            label = "Monthly Contribution (R)",
            placeholder = "e.g. 500"
        )

        if (state.group?.type == com.sanibonani.save.domain.model.GroupType.BURIAL_SOCIETY) {
            SanibonaniSectionHeader("Burial Society Policy Settings")
            Text(
                "Configure default death benefit claim limits, minimum active membership threshold, and reserve requirements.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SanibonaniTextField(
                value = state.settings.burialClaimAmount,
                onValueChange = { vm.updateSetting("burial_claim_amount", it) },
                label = "Burial Claim Amount (R)",
                placeholder = "e.g. 10000"
            )

            SanibonaniTextField(
                value = state.settings.minMembersForClaims,
                onValueChange = { vm.updateSetting("min_members_for_claims", it) },
                label = "Min. Members to Process Claims",
                placeholder = "e.g. 10"
            )

            SanibonaniTextField(
                value = state.settings.minReservePercentage,
                onValueChange = { vm.updateSetting("min_reserve_percentage", it) },
                label = "Min. Reserve Percentage (%)",
                placeholder = "e.g. 20"
            )
        }

        if (state.group?.type == com.sanibonani.save.domain.model.GroupType.HYBRID_SAVINGS_GROUP) {
            SanibonaniSectionHeader("Hybrid Group Policy Settings")
            Text(
                "Configure multi-pillar parameters including virtual pot allocation rules, credit interest rates, and loan limits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SanibonaniTextField(
                value = state.settings.loanInterestRate,
                onValueChange = { vm.updateSetting("loan_interest_rate", it) },
                label = "Loan Interest Rate (%)",
                placeholder = "e.g. 5.0"
            )

            SanibonaniTextField(
                value = state.settings.loanMaxAmount,
                onValueChange = { vm.updateSetting("loan_max_amount", it) },
                label = "Max Loan Limit (R)",
                placeholder = "e.g. 5000"
            )

            SanibonaniTextField(
                value = state.settings.loanMaxMonths,
                onValueChange = { vm.updateSetting("loan_max_months", it) },
                label = "Max Loan Repayment Term (Months)",
                placeholder = "e.g. 6"
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Allow Partial Payments", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("Permit members to make partial monthly contribution payments.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.settings.allowPartialPayment,
                    onCheckedChange = { vm.updateSetting("allow_partial_payment", it) }
                )
            }
        }

        SanibonaniSectionHeader("Group Banking Details")
        Text("Configure the account where group funds should be deposited by the platform.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Linked Bank Provider", style = MaterialTheme.typography.titleSmall)

        if (state.bankConnection != null) {
            InfoBox(
                message = "Connected to ${state.bankConnection.accountName ?: "Bank Account"} (${state.bankConnection.accountNumberHidden ?: "****"})",
                type = InfoType.SUCCESS
            )
            SanibonaniButton(
                text = "Unlink Bank Account",
                onClick = { vm.unlinkBankAccount() },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (providerState.isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (provider in providerState.providers.filter { it.type == FinancialProviderType.BANK }) {
                    val isSelected = state.settings.financialProviderId == provider.id
                    Card(
                        onClick = {
                            vm.updateSetting("financial_provider_id", provider.id!!)
                            vm.initiateBankLinking(provider.id)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(20.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(provider.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                if (isSelected) {
                                    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(24.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                            if (provider.supportedOperations.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Supported: " + provider.supportedOperations.joinToString(", ") { it.name.lowercase().replace("_", " ") },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        BankingDetailsFields(
            bankName = state.settings.bankName,
            accountNo = state.settings.accountNumber,
            branchCode = state.settings.branchCode,
            onBankNameChange = { vm.updateSetting("bank_name", it) },
            onAccountNoChange = { vm.updateSetting("account_number", it) },
            onBranchCodeChange = { vm.updateSetting("branch_code", it) },
            isReadOnly = (state.bankConnection != null) || (!state.isPlatformAdmin && !state.group?.bankName.isNullOrBlank())
        )

        Spacer(Modifier.height(8.dp))
        SanibonaniButton(text = "Save Settings", onClick = { vm.saveSettings() }, isLoading = state.isSaving, modifier = Modifier.fillMaxWidth())
        if (state.saveSuccess) InfoBox("Settings saved!", type = InfoType.SUCCESS)

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        SanibonaniSectionHeader("System Control")
        Text("Force a manual synchronization of all group data from the cloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SanibonaniButton(
            text = "Sync All Data",
            onClick = { vm.triggerFullSync() },
            isLoading = state.isSyncing,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
