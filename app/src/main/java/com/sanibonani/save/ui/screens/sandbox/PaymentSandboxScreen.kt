package com.sanibonani.save.ui.screens.sandbox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.domain.model.MockBankDirection
import com.sanibonani.save.domain.model.MockBankTransaction
import com.sanibonani.save.domain.model.PaymentStatus
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.ui.theme.Forest
import com.sanibonani.save.viewmodel.PaymentSandboxViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSandboxScreen(
    onBack: () -> Unit,
    viewModel: PaymentSandboxViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Sandbox") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Forest,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Test Payment Gateways",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Forest
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Simulate gateway and mock bank transactions in a safe environment.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = viewModel::onAmountChange,
                        label = { Text("Amount (ZAR)") },
                        modifier = Modifier.fillMaxWidth(),
                        prefix = { Text("R ") }
                    )

                    Spacer(Modifier.height(16.dp))

                    Text("Payment Method", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(PaymentMethod.STITCH, PaymentMethod.PAYFAST, PaymentMethod.BANK).forEach { method ->
                            FilterChip(
                                selected = state.selectedMethod == method,
                                onClick = { viewModel.onMethodChange(method) },
                                label = { Text(method.displayName) }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("Mock Bank Direction", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MockBankDirection.entries.forEach { direction ->
                            FilterChip(
                                selected = state.selectedDirection == direction,
                                onClick = { viewModel.onDirectionChange(direction) },
                                label = { Text(direction.name.lowercase().replaceFirstChar { it.titlecase() }) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::generateUrl,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Forest)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Payment Link")
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = viewModel::createMockBankTransaction,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                Icon(Icons.Default.Payment, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create Mock Bank Transaction")
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }

            state.generatedUrl?.let { url ->
                Spacer(Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Forest.copy(alpha = 0.05f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Generated URL", fontWeight = FontWeight.Bold)
                        Text(url, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "In a real app, this would open a Custom Tab or a WebView.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            MockBankTransactionsPanel(
                transactions = state.bankTransactions,
                onProcessing = viewModel::markBankTransactionProcessing,
                onComplete = viewModel::markBankTransactionCompleted,
                onFail = viewModel::markBankTransactionFailed,
                onClear = viewModel::clearBankTransactions
            )
        }
    }
}

@Composable
private fun MockBankTransactionsPanel(
    transactions: List<MockBankTransaction>,
    onProcessing: (String) -> Unit,
    onComplete: (String) -> Unit,
    onFail: (String) -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mock Bank Ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClear, enabled = transactions.isNotEmpty()) {
                    Icon(Icons.Default.ClearAll, contentDescription = "Clear mock bank ledger")
                }
            }

            if (transactions.isEmpty()) {
                Text(
                    "No mock bank transactions yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                transactions.forEach { transaction ->
                    MockBankTransactionRow(
                        transaction = transaction,
                        onProcessing = onProcessing,
                        onComplete = onComplete,
                        onFail = onFail
                    )
                }
            }
        }
    }
}

@Composable
private fun MockBankTransactionRow(
    transaction: MockBankTransaction,
    onProcessing: (String) -> Unit,
    onComplete: (String) -> Unit,
    onFail: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = Forest.copy(alpha = 0.05f)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(transaction.reference, fontWeight = FontWeight.Bold)
                    Text(
                        "${transaction.direction.name} • ${transaction.type.displayName} • R ${"%.2f".format(transaction.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(transaction.status.displayName) },
                    leadingIcon = {
                        Icon(
                            imageVector = when (transaction.status) {
                                PaymentStatus.COMPLETED -> Icons.Default.CheckCircle
                                PaymentStatus.FAILED -> Icons.Default.Error
                                else -> Icons.Default.Schedule
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onProcessing(transaction.id) }) {
                    Text("Processing")
                }
                TextButton(onClick = { onComplete(transaction.id) }) {
                    Text("Complete")
                }
                TextButton(onClick = { onFail(transaction.id) }) {
                    Text("Fail")
                }
            }
        }
    }
}
