package com.sanibonani.save.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.ui.components.SanibonaniTopBar
import com.sanibonani.save.ui.theme.Cream

/**
 * Payment Sandbox Screen.
 * Provides a safe environment for testing payment gateway integrations (Stitch, PayFast, YoCo)
 * without processing real financial transactions. Restricted to Platform Admins.
 */
@Composable
fun PaymentSandboxScreen(
    onBack: () -> Unit
) {
    val clickDebouncer = rememberClickDebouncer()
    Scaffold(
        topBar = {
            SanibonaniTopBar(
                title = "Payment Sandbox",
                onBack = { clickDebouncer.processClick(onBack) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Cream)
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("💸 Payment Sandbox", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "This environment allows you to simulate payment successes, failures, and " +
                "gateway redirections to ensure the platform's accounting logic is robust.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            
            // Placeholder for sandbox controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Integration Status", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("• Stitch: READY (Sandbox Mode)", style = MaterialTheme.typography.bodySmall)
                    Text("• PayFast: READY (Sandbox Mode)", style = MaterialTheme.typography.bodySmall)
                    Text("• YoCo: READY (Test Keys)", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = com.sanibonani.save.ui.theme.Forest)
            ) {
                Text("Return to Platform Dashboard")
            }
        }
    }
}
