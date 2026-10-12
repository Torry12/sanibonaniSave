package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState

@Composable
fun AccountTab(
    state: AdminOverviewUiState,
    onLogout: () -> Unit,
    onPayPlatformFees: () -> Unit = {}
) {
    val totalOutstanding = state.outstandingPlatformFees.fold(Money.ZERO) { acc, fee -> acc + (fee.amountDue - fee.amountPaid) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(100.dp), tint = Navy)
            Text(state.group?.name ?: "Group Admin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        if (totalOutstanding.isPositive) {
            item {
                GlassCard(accentColor = MaterialTheme.colorScheme.error) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Outstanding Platform Fees", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(totalOutstanding.format(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
                        Text("${state.outstandingPlatformFees.size} unpaid month(s)", style = MaterialTheme.typography.bodySmall)

                        Spacer(Modifier.height(16.dp))

                        SanibonaniButton(
                            text = "Pay from Group Account",
                            onClick = onPayPlatformFees,
                            isLoading = state.isPayingPlatformFee,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )

                        Spacer(Modifier.height(16.dp))

                        Text("Platform Banking Details", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Bank: ${state.platformBankDetails.bankName}", style = MaterialTheme.typography.bodySmall)
                        Text("Acc: ${state.platformBankDetails.accountNumber}", style = MaterialTheme.typography.bodySmall)
                        Text("Branch: ${state.platformBankDetails.branchCode}", style = MaterialTheme.typography.bodySmall)
                        Text("Holder: ${state.platformBankDetails.accountHolder}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (state.group != null) {
            item {
                InfoBox("All platform fees are up to date.", type = InfoType.SUCCESS)
            }
        }

        if (!state.isSupportMode) {
            item {
                LogoutButton(
                    onLogout = onLogout,
                    style = LogoutButtonStyle.Outlined,
                    label = "Log Out"
                )
            }
        }
    }
}
