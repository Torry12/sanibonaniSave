package com.sanibonani.save.features.financial.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.financial.presentation.PaymentSandboxViewModel

@Composable
fun PaymentSandboxScreen(
    onBack: () -> Unit,
    vm: PaymentSandboxViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            SanibonaniTopBar(
                title = "Payment Sandbox",
                onBack = onBack
            )
        },
        containerColor = Cream
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            InfoBox(
                "This environment allows you to simulate physical bank events that affect group accounting logic.", type = InfoType.INFO
            )

            // --- GROUP SELECTOR ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Target Group", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SanibonaniDropdown(
                        label = "Select Group",
                        items = state.groups,
                        selectedItem = state.groups.find { it.id == state.selectedGroupId },
                        onItemSelected = { it.id?.let { id -> vm.selectGroup(id) } },
                        itemLabel = { it.name }
                    )
                }
            }

            // --- SIMULATION CARDS ---
            Text("Simulations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)

            SimulationControlCard(
                title = "Bank Transfer",
                description = "Simulate a single incoming ZAR transfer into the group's account.",
                icon = Icons.Default.AccountBalanceWallet,
                buttonLabel = "Simulate R500 Transfer",
                isLoading = state.isSimulating,
                onClick = { vm.simulateBankTransfer("500.00", "SIM-TRANSFER-${System.currentTimeMillis().toString().takeLast(4)}") }
            )

            SimulationControlCard(
                title = "Monthly Collections",
                description = "Simulate a typical month where members pay contributions with varying consistency.",
                icon = Icons.Default.Groups,
                buttonLabel = "Simulate Month",
                isLoading = state.isSimulating,
                onClick = { vm.simulateMonthlyCollections() }
            )

            SimulationControlCard(
                title = "Deep Historical Run",
                description = "Generate 6 months of group activity, loans, and repayments in a single pass.",
                icon = Icons.Default.History,
                buttonLabel = "Run 6-Month Sim",
                containerColor = Navy,
                contentColor = Color.White,
                isLoading = state.isSimulating,
                onClick = { vm.simulateDeepHistoricalRun(6) }
            )

            Spacer(Modifier.height(40.dp))
        }
    }

    if (state.successMessage != null || state.error != null) {
        AlertDialog(
            onDismissRequest = { vm.clearMessages() },
            title = { Text(if (state.error != null) "Simulation Error" else "Simulation Successful") },
            text = { Text(state.error ?: state.successMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { vm.clearMessages() }) { Text("OK") }
            }
        )
    }
}

@Composable
fun SimulationControlCard(
    title: String,
    description: String,
    icon: ImageVector,
    buttonLabel: String,
    isLoading: Boolean,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = (if (containerColor == Navy) Color.White else Navy).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = if (containerColor == Navy) Color.White else Navy)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f))

            Spacer(Modifier.height(20.dp))
            SanibonaniButton(
                text = buttonLabel,
                onClick = onClick,
                isLoading = isLoading,
                containerColor = if (containerColor == Navy) Color.White else Navy,
                contentColor = if (containerColor == Navy) Navy else Color.White
            )
        }
    }
}
