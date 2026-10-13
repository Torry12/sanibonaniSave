package com.sanibonani.save.ui.screens.admin.tabs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.InfoBox
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.viewmodel.AdminViewModel
import com.sanibonani.save.ui.screens.admin.components.SectionHeading

@Composable
fun SettingsTab(state: AdminUiState, vm: AdminViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("⚙️ Group Settings")

        // General Section
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📝 General", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                
                SanibonaniTextField(
                    value = state.group?.name ?: "",
                    onValueChange = { /* Disabled for now */ },
                    label = "Group Name (Read-only)",
                    enabled = false
                )
            }
        }

        // Fees Section
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("💰 Fee Structure", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                
                SanibonaniTextField(
                    value = state.settings.joiningFee,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateSetting("joiningFee", it)
                        }
                    },
                    label = "Joining Fee (R)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                SanibonaniTextField(
                    value = state.settings.monthlyContribution,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateSetting("monthlyContribution", it)
                        }
                    },
                    label = "Monthly Contribution (R)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                SanibonaniTextField(
                    value = state.settings.lateFee,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateSetting("lateFee", it)
                        }
                    },
                    label = "Late Fee (R)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                SanibonaniTextField(
                    value = state.settings.lateFeeGraceDays,
                    onValueChange = { vm.updateSetting("lateFeeGraceDays", it.filter { c -> c.isDigit() }) },
                    label = "Grace Days",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (state.group?.type == GroupType.BURIAL_SOCIETY) {
                    HorizontalDivider(color = Forest.copy(0.1f))
                    Text("🕊️ Burial Society Settings", style = MaterialTheme.typography.labelLarge, color = Forest)
                    
                    SanibonaniTextField(
                        value = state.settings.maxBeneficiaries,
                        onValueChange = { vm.updateSetting("maxBeneficiaries", it.filter { c -> c.isDigit() }) },
                        label = "Max Beneficiaries per Member",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    SanibonaniTextField(
                        value = state.settings.beneficiaryIncreasePct,
                        onValueChange = { 
                            if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                                vm.updateSetting("beneficiaryIncreasePct", it)
                            }
                        },
                        label = "Increase % for Beneficiaries > 65",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                if (state.group?.type == GroupType.ROSCA) {
                    HorizontalDivider(color = Forest.copy(0.1f))
                    Text("🔄 ROSCA Rotation", style = MaterialTheme.typography.labelLarge, color = Forest)
                    
                    RoscaRotationMethod.entries.forEach { method ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.updateSetting("rotationMethod", method) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = state.settings.rotationMethod == method, onClick = { vm.updateSetting("rotationMethod", method) })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(method.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(method.description, style = MaterialTheme.typography.labelSmall, color = MidGray)
                            }
                        }
                    }
                }
            }
        }

        // Loans Section
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🏦 Loan Rules", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                
                SanibonaniTextField(
                    value = state.settings.loanInterestRate,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateSetting("loanInterestRate", it)
                        }
                    },
                    label = "Annual Interest Rate (%)",
                    placeholder = "e.g. 5.0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                SanibonaniTextField(
                    value = state.settings.loanMaxAmount,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateSetting("loanMaxAmount", it)
                        }
                    },
                    label = "Max Loan Amount (R)",
                    placeholder = "e.g. 5000.00",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                SanibonaniTextField(
                    value = state.settings.loanMaxMonths,
                    onValueChange = { vm.updateSetting("loanMaxMonths", it.filter { c -> c.isDigit() }) },
                    label = "Max Repayment Period (Months)",
                    placeholder = "e.g. 6",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        // Governance Section
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("⚖️ Governance", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                
                SanibonaniTextField(
                    value = state.settings.maxMembers,
                    onValueChange = { vm.updateSetting("maxMembers", it.filter { c -> c.isDigit() }) },
                    label = "Maximum Members",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Surface(
                    onClick = { vm.updateSetting("allowPartialPayment", !state.settings.allowPartialPayment) },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.settings.allowPartialPayment,
                            onCheckedChange = { vm.updateSetting("allowPartialPayment", it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Allow Partial Payments", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("If disabled, members must pay the full monthly amount to stay active.", style = MaterialTheme.typography.labelSmall, color = MidGray)
                        }
                    }
                }

                SanibonaniTextField(
                    value = state.settings.probationMonths,
                    onValueChange = { 
                        vm.updateSetting("probationMonths", it.filter { c -> c.isDigit() })
                    },
                    label = "Probation Period (Months)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        // Viability Goals Section
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🎯 Viability Goals", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                
                SanibonaniTextField(
                    value = state.settings.goalAmount,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateSetting("goalAmount", it)
                        }
                    },
                    label = "Target Goal Amount (R)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                SanibonaniTextField(
                    value = state.settings.periodMonths,
                    onValueChange = { 
                        val filtered = it.filter { c -> c.isDigit() }
                        vm.updateSetting("periodMonths", filtered)
                    },
                    label = "Target Time Period (Months)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        
        SanibonaniButton(
            text = "Save All Changes",
            onClick = { vm.saveSettings() },
            modifier = Modifier.fillMaxWidth()
        )

        if (state.group?.constitutionUrl.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(12.dp), 
                colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.05f)), 
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.2f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📜 Missing Constitution", style = MaterialTheme.typography.titleSmall, color = Charcoal, fontWeight = FontWeight.Bold)
                    Text("Your group doesn't have a constitution uploaded. You can generate a standard one based on your current settings.", style = MaterialTheme.typography.bodySmall)
                    
                    Button(
                        onClick = { vm.generateAndUploadStandardConstitution() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Forest),
                        enabled = !state.isUploading
                    ) {
                        if (state.isUploading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        else Text("Generate Standard Constitution")
                    }
                }
            }
        }

        if (state.saveSuccess) {
            InfoBox("Settings saved successfully!", InfoType.SUCCESS)
        }

        // Sync Section
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Reset Local Cache", style = MaterialTheme.typography.titleSmall, color = ErrorRed, fontWeight = FontWeight.Bold)
                Text(
                    "Clears all locally stored data. Use this if you encounter sync issues. This will not delete data from the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MidGray
                )
                
                var showConfirm by remember { mutableStateOf(false) }
                
                if (!showConfirm) {
                    OutlinedButton(
                        onClick = { showConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        border = BorderStroke(1.dp, ErrorRed)
                    ) {
                        Text("Reset Local Data")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showConfirm = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MidGray)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { 
                                vm.resetLocalData()
                                showConfirm = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                        ) {
                            Text("Confirm Reset")
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(100.dp))
    }
}
