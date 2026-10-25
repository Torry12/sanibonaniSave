package com.sanibonani.save.features.member.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ClickDebouncer
import kotlinx.coroutines.launch

@Composable
internal fun MemberDashboardTopBar(
    overviewState: MemberUiState,
    tabs: List<DashboardTabSpec>,
    pagerState: PagerState,
    windowSizeClass: WindowSizeClass,
    isPrivacyLocked: Boolean,
    clickDebouncer: ClickDebouncer,
    overviewVm: MemberOverviewViewModel,
    onNavigateAdmin: () -> Unit,
    onLogout: () -> Unit,
    onExitSupport: () -> Unit
) {
    Column {
        DashboardHeaderWithNotif(
            title = if (overviewState.isImpersonating) "Support: ${overviewState.member?.fullName}" else stringResource(R.string.ui_member_portal),
            subtitle = if (overviewState.isImpersonating) stringResource(R.string.ui_viewing_as_administrator) else (overviewState.member?.fullName ?: "Loading..."),
            notifCount = overviewState.notifications.size,
            onProfileClick = { overviewVm.selectTab(10) },
            onNotifClick = { overviewVm.selectTab(9) },
            profileImageUrl = overviewState.member?.profilePhotoUrl,
            profileImageVersion = overviewState.profileImageVersion,
            onLogoutClick = {
                if (overviewState.isImpersonating) {
                    overviewVm.clearImpersonation { onExitSupport() }
                } else {
                    clickDebouncer.processClick(onLogout)
                }
            },
            onSwitchPortal = { clickDebouncer.processClick(onNavigateAdmin) },
            isPortalSwitchable = !overviewState.isImpersonating,
        )

        // ⚡ Non-blocking visual feedback for refreshes
        SoftLoadingOverlay(
            isLoading = (overviewState.isLoading || isPrivacyLocked) && !overviewState.isExiting,
            message = if (isPrivacyLocked) stringResource(R.string.ui_hydrating_support_data) else stringResource(R.string.ui_refreshing_your_data)
        )

        if (overviewState.isImpersonating && !overviewState.isImpersonationBannerDismissed) {
            SupportModeBanner(onDismiss = { overviewVm.dismissImpersonationBanner() })
        }

        MemberConnectivityBanner(
            isAirplaneMode = overviewState.isAirplaneMode,
            isSystemOnline = overviewState.isSystemOnline
        )

        if (overviewState.memberships.size > 1) {
            MemberGroupSwitcher(
                overviewState = overviewState,
                onSwitchGroup = { overviewVm.switchGroup(it) }
            )
        }

        if (overviewState.isUploading) {
            LinearProgressIndicator(
                progress = { overviewState.uploadProgress?.toFloat() ?: 0f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        }

        if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
            MemberCompactTabs(
                tabs = tabs,
                pagerState = pagerState,
                onSelectTab = { overviewVm.selectTab(it) }
            )
        }
    }
}

@Composable
internal fun SupportModeBanner(onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WarningBg,
        contentColor = WarningAmber
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.ui_support_mode_active_all_actions_are_logged),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onDismiss() }
            )
        }
    }
}

@Composable
internal fun MemberConnectivityBanner(isAirplaneMode: Boolean, isSystemOnline: Boolean) {
    if (isAirplaneMode) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ErrorBg,
            contentColor = ErrorRed
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AirplanemodeActive, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.ui_airplane_mode_active_syncing_is_paused), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    } else if (!isSystemOnline) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = WarningBg,
            contentColor = WarningAmber
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.ui_offline_mode_using_cached_data), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun MemberGroupSwitcher(
    overviewState: MemberUiState,
    onSwitchGroup: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        OutlinedButton(
            onClick = { showMenu = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Navy.copy(0.3f)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GroupIcon(
                            logoEmoji = overviewState.group?.logoEmoji,
                            size = 24.dp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(stringResource(R.string.ui_switch_group_label, overviewState.group?.name ?: "..."), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.ArrowDropDown, null, tint = Navy)
                }
                val currentSync = overviewState.cacheLastSyncByGroup[overviewState.selectedGroupId ?: ""]
                Text(
                    text = formatRelativeSyncTime(currentSync),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surface)
        ) {
            overviewState.memberships.forEach { membership ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(membership.fullName,
                                fontWeight = if (membership.groupId == overviewState.selectedGroupId) FontWeight.Bold else FontWeight.Normal)
                            Text(membership.status.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = {
                        onSwitchGroup(membership.groupId)
                        showMenu = false
                    },
                    leadingIcon = {
                        if (membership.groupId == overviewState.selectedGroupId) {
                            Icon(Icons.Default.CheckCircle, null, tint = Navy, modifier = Modifier.size(20.dp))
                        } else {
                            GroupIcon(logoEmoji = null, size = 20.dp) // Fallback since we don't have group emojis in memberships list
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun MemberCompactTabs(
    tabs: List<DashboardTabSpec>,
    pagerState: PagerState,
    onSelectTab: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    SecondaryScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        containerColor = MaterialTheme.colorScheme.surface,
        edgePadding = 0.dp,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        tabs.forEachIndexed { i, t ->
            Tab(
                selected = pagerState.currentPage == i,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(i)
                        onSelectTab(i)
                    }
                },
                text = { Text(t.title, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
