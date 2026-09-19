package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.ui.components.LogoutButton
import com.sanibonani.save.ui.components.LogoutButtonStyle
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState

@Composable
fun AccountTab(state: AdminOverviewUiState, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(100.dp), tint = Navy)
        Text(state.group?.name ?: "Group Admin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LogoutButton(
            onLogout = onLogout,
            style = LogoutButtonStyle.Outlined,
            label = if (state.isSupportMode) "Exit Support" else "Log Out"
        )
    }
}
