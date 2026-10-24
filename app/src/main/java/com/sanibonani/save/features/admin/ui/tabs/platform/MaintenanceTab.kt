package com.sanibonani.save.features.admin.ui.tabs.platform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.ui.components.AccordionHeader
import com.sanibonani.save.features.admin.ui.components.GroupSelectionDialog
import com.sanibonani.save.features.admin.ui.components.MemberImpersonationDialog
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.admin.presentation.PlatformSupportViewModel
import com.sanibonani.save.features.admin.presentation.state.PlatformAdminUiState
import com.sanibonani.save.features.admin.presentation.state.PlatformAnalyticsUiState
import com.sanibonani.save.features.admin.presentation.state.PlatformSupportUiState

@Composable
fun MaintenanceTab(
    state: PlatformAdminUiState,
    analyticsState: PlatformAnalyticsUiState,
    supportState: PlatformSupportUiState,
    supportVm: PlatformSupportViewModel,
    onRefreshAll: () -> Unit,
    onNavigateToCreateAdmin: () -> Unit,
    onNavigateToSandbox: () -> Unit,
    onLogout: () -> Unit,
    onImpersonateGroupAdmin: (groupId: String) -> Unit,
    onImpersonateMember: (memberId: String, groupId: String) -> Unit
) {
    var expandedAdmins by rememberSaveable { mutableStateOf(false) }
    var expandedSync by rememberSaveable { mutableStateOf(true) }
    var expandedPayment by rememberSaveable { mutableStateOf(false) }
    var expandedBroadcast by rememberSaveable { mutableStateOf(false) }
    var expandedSupport by rememberSaveable { mutableStateOf(false) }
    var expandedDanger by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AccordionHeader(
            title = "Administrator Management",
            icon = Icons.Default.AdminPanelSettings,
            isExpanded = expandedAdmins,
            onClick = { expandedAdmins = !expandedAdmins },
            count = 0
        )
        if (expandedAdmins) {
            GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
                Text(stringResource(R.string.ui_create_additional_group_administrator_ac), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                SanibonaniButton(
                    text = stringResource(R.string.ui_create_new_admin),
                    onClick = onNavigateToCreateAdmin,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // --- 2. REALTIME SYSTEM STATUS ---
        AccordionHeader(
            title = "Realtime System Status",
            icon = Icons.Default.CloudSync,
            isExpanded = expandedSync,
            onClick = { expandedSync = !expandedSync },
            count = state.syncMetrics.size
        )
        if (expandedSync) {
            GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
                Text("Active Realtime Subscriptions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                if (state.syncMetrics.isEmpty()) {
                    Text("No active subscriptions. Ensure you are online and logged in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    for ((table, status) in state.syncMetrics) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(table, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (status.isSubscribed) "Active" else "Failed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (status.isSubscribed) SuccessGreen else ErrorRed
                                )
                            }

                            val lastUpdateText = remember(status.lastUpdate) {
                                java.time.Instant.ofEpochMilli(status.lastUpdate)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                            }

                            Text("Last: $lastUpdateText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        status.lastError?.let { lastError ->
                            Text(lastError, style = MaterialTheme.typography.labelSmall, color = ErrorRed, modifier = Modifier.padding(start = 8.dp))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                Spacer(Modifier.height(16.dp))
                SanibonaniButton(
                    text = "Refresh Platform Data",
                    onClick = onRefreshAll,
                    isLoading = analyticsState.isLoading,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            }
        }

        // --- 3. PAYMENT GATEWAY SANDBOX ---
        AccordionHeader(
            title = "Payment Infrastructure",
            icon = Icons.Default.Payments,
            isExpanded = expandedPayment,
            onClick = { expandedPayment = !expandedPayment },
            count = 0
        )
        if (expandedPayment) {
            GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
                Text(stringResource(R.string.ui_test_payment_gateway_integrations_stitch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                SanibonaniButton(
                    text = stringResource(R.string.ui_open_payment_sandbox),
                    onClick = onNavigateToSandbox,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // --- 3. SYSTEM BROADCAST ---
        AccordionHeader(
            title = "Platform-Wide Broadcast",
            icon = Icons.Default.Campaign,
            isExpanded = expandedBroadcast,
            onClick = { expandedBroadcast = !expandedBroadcast },
            count = 0
        )
        if (expandedBroadcast) {
            GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
                Text(stringResource(R.string.ui_send_a_systemwide_message_to_all_members), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                SanibonaniTextField(
                    value = supportState.broadcastMessage,
                    onValueChange = supportVm::updateBroadcastMessage,
                    label = "Broadcast Message",
                    placeholder = "Enter system update message...",
                    singleLine = false,
                    modifier = Modifier.heightIn(min = 100.dp)
                )
                Spacer(Modifier.height(16.dp))
                SanibonaniButton(
                    text = "Broadcast to All",
                    onClick = { supportVm.broadcastMessage(supportState.broadcastMessage) },
                    isLoading = supportState.isBroadcasting,
                    containerColor = ErrorRed,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
                if (supportState.broadcastSuccess) {
                    Spacer(Modifier.height(8.dp))
                    InfoBox("Broadcast sent successfully!", type = InfoType.SUCCESS)
                }
            }
        }

        // --- 4. SUPPORT TOOLS (IMPERSONATION) ---
        AccordionHeader(
            title = "Support Tools",
            icon = Icons.Default.Build,
            isExpanded = expandedSupport,
            onClick = { expandedSupport = !expandedSupport },
            count = 0
        )
        if (expandedSupport) {
            GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
                Text(stringResource(R.string.ui_access_any_group_or_member_portal_for_ma), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        vm = supportVm,
                        onDismiss = { showMemberSelect = false }
                    ) { memberId, groupId ->
                        showMemberSelect = false
                        onImpersonateMember(memberId, groupId)
                    }
                }
            }
        }

        // --- 5. DANGER ZONE ---
        AccordionHeader(
            title = "Danger Zone",
            icon = Icons.Default.Warning,
            isExpanded = expandedDanger,
            onClick = { expandedDanger = !expandedDanger },
            count = 0
        )
        if (expandedDanger) {
            GlassCard(accentColor = Color.Red) { // Very light red background for danger
                Text(stringResource(R.string.ui_systemwide_resets_or_dangerous_operation), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9F1239))
                Spacer(Modifier.height(24.dp))
                LogoutButton(
                    onLogout = onLogout,
                    style = LogoutButtonStyle.Filled,
                    label = "Logout System-Wide",
                    confirmTitle = "System-Wide Logout",
                    confirmMessage = "Are you sure you want to log out system-wide?"
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { supportVm.resetLocalData() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ErrorRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    Text("Reset Local Database Cache", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}
