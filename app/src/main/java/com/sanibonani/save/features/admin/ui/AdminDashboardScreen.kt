package com.sanibonani.save.features.admin.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.ui.components.*
import com.sanibonani.save.features.admin.ui.tabs.*
import com.sanibonani.save.features.admin.ui.tabs.platform.*
import com.sanibonani.save.features.audit.GroupAuditTab
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.rememberClickDebouncer

import com.sanibonani.save.features.admin.presentation.AdminOverviewViewModel
import com.sanibonani.save.features.admin.presentation.AdminMemberViewModel
import com.sanibonani.save.features.admin.presentation.AdminFinancialViewModel
import com.sanibonani.save.features.admin.presentation.AdminSettingsViewModel
import com.sanibonani.save.features.financial.presentation.FinancialProviderViewModel
import com.sanibonani.save.viewmodel.state.UIEvent
import com.sanibonani.save.features.admin.presentation.state.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    windowSizeClass: WindowSizeClass,
    groupId: String? = null, // Added to allow reactive selection
    onNavigateToPayment: (type: String, amount: Money, groupId: String) -> Unit,
    onNavigateToMemberPortal: (groupId: String, memberId: String?) -> Unit,
    onNavigateToHealthScore: (groupId: String) -> Unit,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    isSupportMode: Boolean = false,
    overviewVm: AdminOverviewViewModel = hiltViewModel(),
    memberVm: AdminMemberViewModel = hiltViewModel(),
    financialVm: AdminFinancialViewModel = hiltViewModel(),
    settingsVm: AdminSettingsViewModel = hiltViewModel(),
    providerVm: FinancialProviderViewModel = hiltViewModel()
) {
    val overviewState by overviewVm.state.collectAsStateWithLifecycle()
    val memberState by memberVm.state.collectAsStateWithLifecycle()
    val financialState by financialVm.state.collectAsStateWithLifecycle()
    val settingsState by settingsVm.state.collectAsStateWithLifecycle()
    val providerState by providerVm.state.collectAsStateWithLifecycle()

    // ── Reactive Group Selection Hardening ──
    LaunchedEffect(groupId, isSupportMode) {
        if (isSupportMode) {
            overviewVm.setSupportMode(true)
        }
        if (!groupId.isNullOrBlank()) {
            overviewVm.selectGroup(groupId)
        }
    }

    val adminTabs = remember {
        listOf(
            DashboardTabSpec(0, "Overview"),
            DashboardTabSpec(1, "Members"),
            DashboardTabSpec(2, "Alerts"),
            DashboardTabSpec(3, "Messaging"),
            DashboardTabSpec(4, "Viability"),
            DashboardTabSpec(5, "Account"),
            DashboardTabSpec(6, "Settings"),
            DashboardTabSpec(7, "Payouts"),
            DashboardTabSpec(8, "Loans"),
            DashboardTabSpec(9, "Insights"),
            DashboardTabSpec(10, "Ledger"),
            DashboardTabSpec(11, "Audit")
        )
    }

    val pagerState = rememberPagerState(pageCount = { adminTabs.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clickDebouncer = rememberClickDebouncer()

    val snackbarHostState = remember { SnackbarHostState() }

    val memberPortalAccess = resolveAdminMemberPortalAccess(
        group = overviewState.group,
        managedGroups = overviewState.managedGroups.getOrNull() ?: emptyList(),
        selectedMember = memberState.selectedMember,
        supportActive = isSupportMode,
        currentUserId = overviewState.currentUserId
    )

    DisposableEffect(Unit) {
        overviewVm.setActive(true)
        onDispose {
            overviewVm.setActive(false)
        }
    }

    syncDashboardPager(
        selectedTab = overviewState.selectedTab,
        pagerState = pagerState,
        tabs = adminTabs
    ) { page ->
        adminTabs.getOrNull(page)?.index?.let(overviewVm::setTab)
    }

    LaunchedEffect(overviewVm.events, memberVm.events, financialVm.events) {
        fun handleEvent(event: UIEvent) {
            when (event) {
                is UIEvent.ShowMessage -> {
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
                }
                is UIEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is UIEvent.OpenFile -> {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        event.file
                    )
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK

                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, event.mimeType)
                            addFlags(flags)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, event.chooserTitle).apply { addFlags(flags) })
                    } catch (_: Exception) {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = event.mimeType
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(flags)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, event.chooserTitle).addFlags(flags))
                    }
                }
                else -> Unit
            }
        }

        launch { overviewVm.events.collect { handleEvent(it) } }
        launch { memberVm.events.collect { handleEvent(it) } }
        launch { financialVm.events.collect { handleEvent(it) } }
    }

    val navItems = remember(overviewVm) { adminDashboardNavigationItems(overviewVm) }

    val selectedRoute = overviewState.selectedTab.toString()

    if (overviewState.isLoading && overviewState.group == null) {
        SanibonaniLoadingScreen(message = "Loading dashboard...")
    } else {
        AdaptiveScaffold(
            windowSizeClass = windowSizeClass,
            items = navItems,
            selectedRoute = selectedRoute,
            hideBottomBarOnCompact = true
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Column {
                        AdminDashboardTopBar(
                            state = overviewState,
                            isSupportMode = isSupportMode,
                            memberPortalAccess = memberPortalAccess,
                            onNavigateBack = { clickDebouncer.processClick(onNavigateBack) },
                            onNavigateToMemberPortal = onNavigateToMemberPortal
                        )

                        // ⚡ Non-blocking visual feedback for refreshes
                        SoftLoadingOverlay(
                            isLoading = overviewState.isLoading && overviewState.group != null,
                            message = "Refreshing group data..."
                        )

                        AdminDashboardBannerSection(
                            state = overviewState,
                            onConsolidateLedger = { clickDebouncer.processClick {
                                // 🛡️ Link FIX NOW to both operations for complete recovery
                                overviewVm.consolidateLedger()
                                overviewVm.refreshMembers()
                            } },
                            onRefreshMembers = { clickDebouncer.processClick { overviewVm.refreshMembers() } },
                            onDismissImpersonation = { overviewVm.dismissImpersonationBanner() },
                            onNavigateToPayment = onNavigateToPayment
                        )

                        GroupSwitchSelector(
                            currentGroup = overviewState.group,
                            managedGroups = overviewState.managedGroups.getOrNull() ?: emptyList(),
                            onGroupSelected = { overviewVm.selectGroup(it) }
                        )

                        Surface(
                            tonalElevation = 3.dp,
                            shadowElevation = 2.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            SecondaryScrollableTabRow(
                                selectedTabIndex = pagerState.currentPage,
                                containerColor = MaterialTheme.colorScheme.surface,
                                edgePadding = 0.dp,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ) {
                                adminTabs.forEachIndexed { i, tab ->
                                    Tab(
                                        selected = pagerState.currentPage == i,
                                        onClick = {
                                            scope.launch {
                                                pagerState.animateScrollToPage(i)
                                                overviewVm.setTab(tab.index)
                                            }
                                        },
                                        text = { Text(tab.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    PullToRefreshBox(
                        isRefreshing = overviewState.isLoading,
                        onRefresh = { overviewState.group?.id?.let { overviewVm.selectGroup(it) } },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true,
                            beyondViewportPageCount = 1
                        ) { page ->
                            val logicalIndex = adminTabs.getOrNull(page)?.index ?: 0
                            when (logicalIndex) {
                                0 -> OverviewTab(overviewState, overviewVm,
                                    windowSizeClass = windowSizeClass,
                                    onPayClick = { amount ->
                                        val type = if (amount.isZero) "custom" else "registration"
                                        onNavigateToPayment(type, amount, overviewState.group?.id ?: "")
                                    },
                                    onNavigateToHealthScore = {
                                        overviewState.group?.id?.let { onNavigateToHealthScore(it) }
                                    },
                                    clickDebouncer = clickDebouncer
                                )
                                1 -> MembersTab(
                                    state = memberState,
                                    windowSizeClass = windowSizeClass,
                                    onMemberClick = { memberVm.selectMember(it) },
                                    onRefresh = { overviewVm.refreshMembers() }
                                )
                                2 -> NotificationsTab(overviewState)
                                3 -> MessagingTab(overviewState, overviewVm)
                                4 -> ViabilityPlanningTab(overviewState, overviewVm)
                                5 -> AccountTab(overviewState, onLogout)
                                6 -> SettingsTab(settingsState, settingsVm, providerState)
                                7 -> PayoutTab(financialState, financialVm)
                                8 -> LoansTab(financialState, financialVm)
                                9 -> InsightsTab(overviewState) { overviewVm.executeRoscaPayout() }
                                10 -> LedgerTab(
                                    state = financialState,
                                    vm = financialVm,
                                    windowSizeClass = windowSizeClass
                                )
                                11 -> GroupAuditTab(
                                    report = overviewState.groupAuditReport,
                                    onRefresh = { overviewVm.loadGroupAuditReport() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Dialogs & Error Handling ──
        AdminDashboardDialogs(
            overviewState = overviewState,
            memberState = memberState,
            financialState = financialState,
            overviewVm = overviewVm,
            memberVm = memberVm,
            financialVm = financialVm,
            onNavigateToMemberPortal = onNavigateToMemberPortal,
            isSupportMode = isSupportMode,
            context = context
        )
    }
}
