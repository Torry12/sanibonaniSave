package com.sanibonani.save.features.admin.ui.tabs.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.features.admin.presentation.PlatformSupportViewModel
import com.sanibonani.save.features.admin.presentation.state.PlatformAnalyticsUiState
import com.sanibonani.save.features.admin.presentation.state.PlatformSupportUiState
import com.sanibonani.save.features.admin.ui.components.GroupSelectionDialog
import com.sanibonani.save.features.admin.ui.components.MemberImpersonationDialog
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.Navy

@Composable
fun SupportTab(
    analyticsState: PlatformAnalyticsUiState,
    supportState: PlatformSupportUiState,
    vm: PlatformSupportViewModel,
    onImpersonateGroupAdmin: (groupId: String) -> Unit,
    onImpersonateMember: (memberId: String, groupId: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Member & Group Support",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Navy
        )

        Text(
            "Access any group or member portal to provide direct support. All impersonation sessions are logged for audit purposes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SupportAgent, null, tint = Navy, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Text("Impersonation Tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            var showGroupSelect by remember { mutableStateOf(false) }
            SanibonaniButton(
                text = "Impersonate Group Admin",
                onClick = { showGroupSelect = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )

            if (showGroupSelect) {
                GroupSelectionDialog(
                    groups = analyticsState.groups,
                    onDismiss = { showGroupSelect = false }
                ) { groupId ->
                    showGroupSelect = false
                    onImpersonateGroupAdmin(groupId)
                }
            }

            Spacer(Modifier.height(12.dp))

            var showMemberSelect by remember { mutableStateOf(false) }
            SanibonaniButton(
                text = "Impersonate Member",
                onClick = { showMemberSelect = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )

            if (showMemberSelect) {
                MemberImpersonationDialog(
                    groups = analyticsState.groups,
                    state = supportState,
                    vm = vm,
                    onDismiss = { showMemberSelect = false }
                ) { memberId, groupId ->
                    showMemberSelect = false
                    onImpersonateMember(memberId, groupId)
                }
            }
        }

        // Add more support features here (e.g. Audit Log for support actions)

        Spacer(Modifier.height(48.dp))
    }
}
