package com.sanibonani.save.features.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.ui.tabs.*
import com.sanibonani.save.features.audit.ui.PlatformAuditTab
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.features.admin.presentation.*
import com.sanibonani.save.features.common.presentation.UIEvent
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformAdminScreen(
    onNavigateToCreateAdmin: () -> Unit,
    onNavigateToSandbox: () -> Unit,
    onLogout: () -> Unit,
    onImpersonateGroupAdmin: (groupId: String) -> Unit,
    onImpersonateMember: (memberId: String, groupId: String) -> Unit,
    onOpenMemberPortalFromDisbursement: (groupId: String, payoutId: String?) -> Unit,
    onNavigateToHealthScore: (groupId: String) -> Unit,
    onNavigateToBehaviorTracking: (memberId: String) -> Unit,
    vm: PlatformAdminViewModel = hiltViewModel(),
    analyticsVm: PlatformAnalyticsViewModel = hiltViewModel(),
    disbursementsVm: PlatformDisbursementsViewModel = hiltViewModel(),
    ledgerVm: PlatformLedgerViewModel = hiltViewModel(),
    feesVm: PlatformFeeConfigViewModel = hiltViewModel(),
    auditVm: PlatformAuditViewModel = hiltViewModel(),
    backupVm: PlatformBackupViewModel = hiltViewModel(),
    supportVm: PlatformSupportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val analyticsState by analyticsVm.state.collectAsStateWithLifecycle()
    val disbursementsState by disbursementsVm.state.collectAsStateWithLifecycle()
    val ledgerState by ledgerVm.state.collectAsStateWithLifecycle()
    val feesState by feesVm.state.collectAsStateWithLifecycle()
    val auditState by auditVm.state.collectAsStateWithLifecycle()
    val backupState by backupVm.state.collectAsStateWithLifecycle()
    val supportState by supportVm.state.collectAsStateWithLifecycle()

    if (!state.isAuthorized) {
        AccessDeniedView(onLogout = onLogout)
        return
    }

    LaunchedEffect(state.isAuthorized) {
        if (state.isAuthorized) {
            analyticsVm.load()
            disbursementsVm.load()
            ledgerVm.load()
            feesVm.loadSettings()
        }
    }

    LaunchedEffect(Unit) {
        disbursementsVm.mfaRequired.collect { vm.onMfaRequired() }
    }

    LaunchedEffect(Unit) {
        vm.mfaVerified.collect {
            analyticsVm.load()
            disbursementsVm.load()
            ledgerVm.load()
        }
    }

    LaunchedEffect(Unit) {
        merge(
            vm.events,
            analyticsVm.events,
            disbursementsVm.events,
            ledgerVm.events,
            feesVm.events,
            auditVm.events,
            backupVm.events,
            supportVm.events
        ).collect { event ->
            when (event) {
                is UIEvent.ShowMessage -> ToastUtils.showInfo(context, event.message)
                is UIEvent.ShowError -> ToastUtils.showError(context, event.message)
                is UIEvent.OpenFile -> openSharedPlatformFile(context, event.file, event.mimeType, event.chooserTitle)
                else -> Unit
            }
        }
    }

    val tabs = listOf(
        stringResource(R.string.ui_tab_analytics),
        stringResource(R.string.ui_tab_groups),
        stringResource(R.string.ui_tab_payments),
        stringResource(R.string.ui_tab_ledger),
        stringResource(R.string.ui_tab_fees),
        stringResource(R.string.ui_tab_audit),
        stringResource(R.string.ui_tab_maintenance),
        stringResource(R.string.ui_tab_backups)
    )

    val pagerState = rememberPagerState(
        initialPage = state.selectedTab.coerceIn(0, tabs.size - 1),
        pageCount = { tabs.size }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        if (state.selectedTab != pagerState.currentPage) {
            vm.setTab(pagerState.currentPage)
        }
    }

    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab) {
            pagerState.animateScrollToPage(state.selectedTab)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.ui_platform_administrator),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    LogoutButton(
                        onLogout = onLogout,
                        style = LogoutButtonStyle.IconButton
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SecondaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                                vm.setTab(index)
                            }
                        },
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            if (analyticsState.isLoading && analyticsState.analytics.getOrNull()?.totalGroups == 0) {
                SanibonaniLoadingScreen(message = stringResource(R.string.ui_loading_platform_data))
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                    beyondViewportPageCount = 1
                ) { page ->
                    when (page) {
                        0 -> PlatformAdminAnalyticsPage(analyticsState, analyticsVm)
                        1 -> AllGroupsTab(
                            state = analyticsState,
                            vm = analyticsVm,
                            onNavigateToHealthScore = onNavigateToHealthScore
                        )
                        2 -> PlatformAdminDisbursementsPage(
                            analyticsState = analyticsState,
                            disbursementsState = disbursementsState,
                            supportState = supportState,
                            vm = disbursementsVm,
                            onOpenMemberPortalFromDisbursement = onOpenMemberPortalFromDisbursement,
                            onImpersonateMember = onImpersonateMember,
                            onNavigateToHealthScore = onNavigateToHealthScore,
                            onNavigateToBehaviorTracking = onNavigateToBehaviorTracking
                        )
                        3 -> PlatformLedgerTab(ledgerState, analyticsState, ledgerVm)
                        4 -> FeeManagementTab(feesState, feesVm)
                        5 -> PlatformAuditTab(
                            report = auditState.platformAuditReport,
                            onLoad = { auditVm.load() },
                            onRegenerate = { auditVm.refresh() }
                        )
                        6 -> MaintenanceTab(
                            state = state,
                            analyticsState = analyticsState,
                            supportState = supportState,
                            supportVm = supportVm,
                            onRefreshAll = {
                                analyticsVm.load()
                                disbursementsVm.load()
                                ledgerVm.load()
                            },
                            onNavigateToCreateAdmin = onNavigateToCreateAdmin,
                            onNavigateToSandbox = onNavigateToSandbox,
                            onLogout = onLogout,
                            onImpersonateGroupAdmin = onImpersonateGroupAdmin,
                            onImpersonateMember = onImpersonateMember
                        )
                        7 -> BackupTab(state = backupState, vm = backupVm)
                    }
                }
            }
        }
    }

    state.error?.let { error ->
        GlobalErrorBanner(
            message = error,
            onDismiss = { vm.clearError() },
            isCritical = true,
            actionLabel = "Retry",
            onAction = {
                analyticsVm.load()
                disbursementsVm.load()
                ledgerVm.load()
            }
        )
    }

    if (state.showMfaChallenge) {
        MfaChallengeDialog(
            onVerify = { vm.verifyMfaChallenge(it) },
            onDismiss = { vm.dismissMfaChallenge() },
            error = state.mfaChallengeError,
            isLoading = state.isVerifyingMfa
        )
    }
}

@Composable
private fun AccessDeniedView(onLogout: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("⚠️", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.ui_access_denied),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ui_access_denied_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
            LogoutButton(
                onLogout = onLogout,
                style = LogoutButtonStyle.Filled,
                label = stringResource(R.string.ui_logout_for_security),
                confirmRequired = false
            )
        }
    }
}
