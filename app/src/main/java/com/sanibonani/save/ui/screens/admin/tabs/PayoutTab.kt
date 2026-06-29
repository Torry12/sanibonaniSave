package com.sanibonani.save.ui.screens.admin.tabs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.formatZAR
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.uiLabel
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.viewmodel.AdminViewModel
import com.sanibonani.save.ui.screens.admin.components.SectionHeading
import androidx.compose.material3.OutlinedTextFieldDefaults

/**
 * Masks account number for display, showing only last 4 digits.
 * Security measure: prevents full account number visibility in UI.
 * Example: "1234567890" → "****7890"
 */
fun maskAccountNumber(accountNumber: String): String = when {
    accountNumber.length <= 4 -> accountNumber
    else -> "*".repeat(accountNumber.length - 4) + accountNumber.takeLast(4)
}

@Composable
fun PayoutTab(state: AdminUiState, vm: AdminViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeading("Request Payout")
        Text("Request disbursement of group funds from the platform to the group's bank account.", style = MaterialTheme.typography.bodyMedium, color = MidGray)
        
        Spacer(Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                val amountVal = state.payoutAmount.toDoubleOrNull() ?: 0.0
                val balanceVal = state.group?.balance ?: 0.0
                OutlinedTextField(
                    value = state.payoutAmount,
                    onValueChange = { vm.updatePayoutAmount(it) },
                    label = { Text("Amount (R)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountVal > balanceVal,
                    supportingText = {
                        if (amountVal > balanceVal) {
                            Text("Insufficient balance. Current: R$balanceVal", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Current Balance: R$balanceVal")
                        }
                    }
                )
                
                Spacer(Modifier.height(16.dp))

                // Security notice for registered bank details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔒", fontSize = 18.sp)
                        Column {
                            Text(
                                "Registered Bank Details",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                "Payout will be sent to the group's registered bank account. These details are protected from edit.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MidGray
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Banking Details (Read-Only)", fontWeight = FontWeight.Bold)

                // Bank Name - Read Only
                OutlinedTextField(
                    value = state.group?.bankName ?: "",
                    onValueChange = {},  // No-op for read-only
                    label = { Text("Bank Name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = LightGray,
                        disabledTextColor = MidGray,
                        disabledLabelColor = MidGray
                    ),
                    trailingIcon = {
                        Text("🔒", modifier = Modifier.padding(end = 8.dp), fontSize = 14.sp)
                    }
                )
                
                // Account Number - Read Only
                OutlinedTextField(
                    value = state.group?.accountNumber?.let { maskAccountNumber(it) } ?: "",
                    onValueChange = {},  // No-op for read-only
                    label = { Text("Account Number") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = LightGray,
                        disabledTextColor = MidGray,
                        disabledLabelColor = MidGray
                    ),
                    supportingText = {
                        Text("Full account: ${state.group?.accountNumber ?: "N/A"}", style = MaterialTheme.typography.labelSmall, color = LightGray)
                    },
                    trailingIcon = {
                        Text("🔒", modifier = Modifier.padding(end = 8.dp), fontSize = 14.sp)
                    }
                )
                
                // Branch Code - Read Only
                OutlinedTextField(
                    value = state.group?.branchCode ?: "",
                    onValueChange = {},  // No-op for read-only
                    label = { Text("Branch Code") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = LightGray,
                        disabledTextColor = MidGray,
                        disabledLabelColor = MidGray
                    ),
                    trailingIcon = {
                        Text("🔒", modifier = Modifier.padding(end = 8.dp), fontSize = 14.sp)
                    }
                )

                // Warning if bank details missing
                if (state.group == null ||
                    state.group.bankName.isNullOrBlank() ||
                    state.group.accountNumber.isNullOrBlank() ||
                    state.group.branchCode.isNullOrBlank()) {

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        border = BorderStroke(1.dp, ErrorRed)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚠️", fontSize = 18.sp)
                            Column {
                                Text(
                                    "Bank Details Missing",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ErrorRed
                                )
                                Text(
                                    "Please configure the group's bank account details before requesting a payout.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ErrorRed
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                
                val hasBankDetails = state.group != null &&
                                     !state.group.bankName.isNullOrBlank() &&
                                     !state.group.accountNumber.isNullOrBlank() &&
                                     !state.group.branchCode.isNullOrBlank()

                Button(
                    onClick = { vm.submitPayoutRequest() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isRequestingPayout && 
                             state.payoutAmount.isNotEmpty() && 
                             (state.payoutAmount.toDoubleOrNull() ?: 0.0) <= (state.group?.balance ?: 0.0) &&
                             (state.payoutAmount.toDoubleOrNull() ?: 0.0) > 0 &&
                             hasBankDetails,  // ✅ Bank details must exist
                    colors = ButtonDefaults.buttonColors(containerColor = Forest)
                ) {
                    if (state.isRequestingPayout) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Submit to Group Admin")
                    }
                }
                
                if (state.payoutRequestSuccess) {
                    Text("Request submitted and routed for admin validation.", color = Forest, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        SectionHeading("Payout History")

        if (state.payouts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No payout requests found.", color = MidGray)
                TextButton(onClick = { vm.refreshPayouts() }) {
                    Text("Refresh", color = Forest)
                }
            }
        } else {
            state.payouts.sortedByDescending { it.createdAt }.forEach { payout ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LightGray)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(formatZAR(payout.amount), fontWeight = FontWeight.Bold)
                            Text("Requested: ${payout.createdAt}", style = MaterialTheme.typography.labelSmall)
                        }
                        
                        val statusColor = when(payout.status) {
                            PayoutStatus.PENDING -> MidGray
                            PayoutStatus.GROUP_APPROVED -> InfoBlue
                            PayoutStatus.PROCESSING -> Forest
                            PayoutStatus.COMPLETED -> Forest
                            PayoutStatus.FAILED -> ErrorRed
                            PayoutStatus.CANCELLED -> MidGray
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                color = statusColor.copy(0.1f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    payout.status.uiLabel,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            
                            when (payout.status) {
                                PayoutStatus.PENDING -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { vm.approveAndEscalatePayoutRequest(payout.id ?: "") },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Validate & Escalate", color = Forest, style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(
                                            onClick = { vm.cancelPayoutRequest(payout.id ?: "") },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Cancel", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                PayoutStatus.GROUP_APPROVED -> {
                                    Text(
                                        "Awaiting platform admin final approval",
                                        color = InfoBlue,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
