package com.sanibonani.save.ui.screens.admin.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.InfoBox
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.components.StatCard
import com.sanibonani.save.ui.components.formatZARShort
import com.sanibonani.save.ui.theme.Cream
import com.sanibonani.save.ui.theme.Forest
import com.sanibonani.save.ui.theme.LightGray
import com.sanibonani.save.ui.theme.MidGray
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.viewmodel.AdminViewModel
import com.sanibonani.save.ui.screens.admin.components.SectionHeading

@Composable
fun OverviewTab(
    state: AdminUiState,
    vm: AdminViewModel,
    onManualAdjustment: (Double) -> Unit,
    isSupportMode: Boolean = false,
    supportAdminName: String = "",
    supportTicketId: String = "",
    onExitSupport: () -> Unit = {},
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    val group = state.group
    
    Column(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (isSupportMode) {
            InfoBox(
                message = "SUPPORT MODE ACTIVE: Acting as $supportAdminName (Ref: $supportTicketId)",
                type = InfoType.WARNING
            )
            Spacer(Modifier.height(16.dp))
        }

        group?.let { g ->
            AdminFeeBanner(g, state.feeStatus, state.daysOverdue) {
                vm.setTab(5) // Account Tab
            }
            Spacer(Modifier.height(16.dp))
        }

        SectionHeading("Group Snapshot")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                icon = "💰",
                label = "Balance",
                value = formatZARShort(group?.balance ?: 0.0),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = "👥",
                label = "Members",
                value = "${state.members.size}",
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        SectionHeading("Quick Actions")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(Icons.Default.Download, "Statement", Modifier.weight(1f)) {
                vm.downloadPdfStatement()
            }
            ActionButton(Icons.Default.Description, "Constitution", Modifier.weight(1f)) {
                vm.downloadGroupConstitution()
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(Icons.Default.Add, "Add Funds", Modifier.weight(1f)) {
                onManualAdjustment(500.0)
            }
            ActionButton(Icons.Default.Email, "Broadcast", Modifier.weight(1f)) {
                vm.setTab(3) // Messaging
            }
        }

        if (state.ledger.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeading("Recent Activity")
            state.ledger.take(5).forEach { entry ->
                LedgerItem(entry)
                Spacer(Modifier.height(8.dp))
            }
            TextButton(
                onClick = { vm.setTab(8) }, // Ledger tab
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("View Full Ledger", color = Forest)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = Forest, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LedgerItem(entry: LedgerEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).background(
                    if (entry.amount >= 0) Forest.copy(0.1f) else Color.Red.copy(0.1f),
                    RoundedCornerShape(8.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (entry.amount >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = if (entry.amount >= 0) Forest else Color.Red,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.description, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(entry.createdAt?.take(10) ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray)
            }
            Text(
                text = (if (entry.amount >= 0) "+" else "") + formatZARShort(entry.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (entry.amount >= 0) Forest else Color.Red
            )
        }
    }
}

@Composable
fun AdminFeeBanner(group: Group, status: AdminFeeState, daysOverdue: Int, onAction: () -> Unit) {
    if (status == AdminFeeState.PAID) return

    val color = when (status) {
        AdminFeeState.DUE -> Forest
        AdminFeeState.WARNING -> Color(0xFFFFA000)
        AdminFeeState.OVERDUE, AdminFeeState.SUSPENDED -> Color.Red
        else -> Forest
    }

    val message = when (status) {
        AdminFeeState.DUE -> "Platform fee is due."
        AdminFeeState.WARNING -> "Platform fee is late. Pay soon to avoid suspension."
        AdminFeeState.OVERDUE -> "Group suspended due to non-payment ($daysOverdue days late)."
        AdminFeeState.SUSPENDED -> "Account restricted. Please settle outstanding fees."
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(message, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text("Settle your account to keep your group active.", style = MaterialTheme.typography.labelSmall, color = MidGray)
            }
            TextButton(onClick = onAction) {
                Text("PAY NOW", color = color, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
    }
}
