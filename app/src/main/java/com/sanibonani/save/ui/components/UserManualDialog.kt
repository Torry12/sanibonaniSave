package com.sanibonani.save.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.ui.theme.Navy

@Composable
fun UserManualDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("📖 SanibonaniSave User Manual", fontWeight = FontWeight.Bold, color = Navy)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ManualSection("1. Getting Started", "Launch the app to experience the 3D glass bootloader. Log in securely with your credentials.")
                ManualSection("2. Member Portal", "Track savings, view upcoming payment due dates, export CSV and PDF statements, apply for short-term micro-loans with transparent amortization, and upload beneficiary documents.")
                ManualSection("3. Administrator Portal", "Oversee group financial health, review loan applications, reconcile group ledgers, manage fee configurations, and browse groups via collapsible provincial accordions.")
                ManualSection("4. Support Mode & Auditing", "Platform admins can enter Support Mode to assist members. The Actuarial Position card provides real-time solvency and reserve metrics.")
                ManualSection("5. Security & Privacy", "All transactions and records use cent-accurate BigDecimal arithmetic and Supabase RLS row-level security encryption.")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Navy)) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ManualSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
