package com.sanibonani.save.features.admin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.features.admin.ui.AdminMemberPortalAccess
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardTopBar(
    state: AdminOverviewUiState,
    isSupportMode: Boolean,
    memberPortalAccess: AdminMemberPortalAccess,
    onNavigateBack: () -> Unit,
    onNavigateToMemberPortal: (String, String?) -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    if (isSupportMode) stringResource(R.string.ui_admin_view_support) else stringResource(R.string.ui_admin_dashboard),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                state.group?.name?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        navigationIcon = {
            if (isSupportMode) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        actions = {
            if (isSupportMode) {
                TextButton(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Exit", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
            }

            IconButton(
                onClick = {
                    val gid = memberPortalAccess.groupId
                    if (!gid.isNullOrBlank() && memberPortalAccess.enabled) {
                        onNavigateToMemberPortal(gid, memberPortalAccess.memberId)
                    }
                },
                enabled = memberPortalAccess.enabled
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = memberPortalAccess.buttonLabel,
                    tint = if (memberPortalAccess.enabled) Navy else MidGray
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun AdminDashboardBannerSection(
    state: AdminOverviewUiState,
    onConsolidateLedger: () -> Unit,
    onRefreshMembers: () -> Unit,
    onDismissImpersonation: () -> Unit,
    onNavigateToPayment: (String, Money, String) -> Unit
) {
    Column {
        // ── Impersonation Banner ──
        if (state.isSupportMode && !state.isImpersonationBannerDismissed) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = WarningAmber,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.ui_support_mode_active_access_is_logged),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDismissImpersonation() }
                    )
                }
            }
        }

        // ── Integrity Warnings ──
        if (!state.verificationResult.isValid) {
            val primaryIssue = state.verificationResult.issues.firstOrNull()
            IntegrityBanner(
                issue = primaryIssue,
                onAction = if (primaryIssue?.category == "Integrity") onRefreshMembers else onConsolidateLedger
            )
        }

        if (state.isAirplaneMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Red,
                contentColor = Color.White
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AirplanemodeActive, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_airplane_mode_active_turn_off_to_sync), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        } else if (!state.isSystemOnline) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = WarningAmber,
                contentColor = Color.Black
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_offline_mode_using_cached_data), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (state.feeStatus == AdminFeeState.PENDING_ACTIVATION) {
            state.group?.let { group ->
                AdminFeeBanner(
                    group = group,
                    status = state.feeStatus,
                    daysOverdue = state.daysOverdue,
                    onAction = {
                        group.id?.let { groupId ->
                            onNavigateToPayment("registration", state.platformRegistrationFee, groupId)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun GroupSwitchSelector(
    currentGroup: Group?,
    managedGroups: List<Group>,
    onGroupSelected: (String) -> Unit
) {
    if (managedGroups.size <= 1) return

    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(
            onClick = { showMenu = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Navy.copy(0.3f)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SwapHoriz, null, tint = Navy, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.ui_switch_group) + ": ${currentGroup?.name ?: "..."}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Default.ArrowDropDown, null, tint = Navy)
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surface)
        ) {
            managedGroups.forEach { group ->
                DropdownMenuItem(
                    text = {
                        Text(
                            group.name,
                            fontWeight = if (group.id == currentGroup?.id) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        group.id?.let { onGroupSelected(it) }
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            if (group.id == currentGroup?.id) Icons.Default.CheckCircle else Icons.Default.Circle,
                            contentDescription = null,
                            tint = if (group.id == currentGroup?.id) Navy else LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
    }
}
