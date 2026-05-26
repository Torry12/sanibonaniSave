package com.sanibonani.save.ui.screens.admin.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.ui.components.InfoRow
import com.sanibonani.save.ui.components.LogoutButton
import com.sanibonani.save.ui.components.LogoutButtonStyle
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.ui.screens.admin.components.SectionHeading

@Composable
fun AccountTab(state: AdminUiState, group: Group?, onLogout: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("👤 Group Profile")

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("Group Name", group?.name ?: "")
                InfoRow("Group Type", group?.type?.name ?: "")
                InfoRow("Location", "${group?.city}, ${group?.province}")
                InfoRow("Members", "${group?.currentMembers} / ${group?.maxMembers}")
            }
        }

        Text("🏦 Banking Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("Bank", group?.bankName ?: "Not set")
                InfoRow("Account Number", group?.accountNumber ?: "Not set")
                InfoRow("Branch Code", group?.branchCode ?: "Not set")
                InfoRow("Account Type", group?.accountType ?: "Not set")
            }
        }
        
        Spacer(Modifier.height(24.dp))
        LogoutButton(
            onClick = onLogout,
            style = LogoutButtonStyle.Outlined
        )
    }
}
