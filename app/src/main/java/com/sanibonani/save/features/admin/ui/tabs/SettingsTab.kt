package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.FinancialProviderType
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.presentation.AdminSettingsViewModel
import com.sanibonani.save.features.financial.presentation.FinancialProviderUiState
import com.sanibonani.save.features.admin.presentation.state.AdminSettingsUiState

@Composable
fun SettingsTab(
    state: AdminSettingsUiState,
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

        SanibonaniTextField(
            value = state.settings.loanInterestRate,
            onValueChange = { vm.updateSetting("interest_rate", it) },
            label = "Loan Interest Rate (%)",
            placeholder = "e.g. 5.0"
        )

        SanibonaniSectionHeader("Group Banking Details")
        Text("Configure the account where group funds should be deposited by the platform.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Linked Bank Provider", style = MaterialTheme.typography.titleSmall)
        if (providerState.isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (provider in providerState.providers.filter { it.type == FinancialProviderType.BANK }) {
                    val isSelected = state.settings.financialProviderId == provider.id
                    Card(
                        onClick = { vm.updateSetting("financial_provider_id", provider.id!!) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp).fillMaxWidth()) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(provider.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            if (provider.supportedOperations.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Supports: " + provider.supportedOperations.joinToString(", ") { it.name.lowercase().replace("_", " ") },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            onBranchCodeChange = { vm.updateSetting("branch_code", it) }
        )

        Spacer(Modifier.height(8.dp))
        SanibonaniButton(text = "Save Settings", onClick = { vm.saveSettings() }, isLoading = state.isSaving, modifier = Modifier.fillMaxWidth())
        if (state.saveSuccess) InfoBox("Settings saved!", InfoType.SUCCESS)
    }
}
