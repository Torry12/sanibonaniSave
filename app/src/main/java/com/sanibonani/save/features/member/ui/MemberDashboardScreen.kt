package com.sanibonani.save.features.member.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ClickDebouncer
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.ui.utils.formatPct
import com.sanibonani.save.ui.utils.formatZAR

import com.sanibonani.save.features.member.presentation.MemberFinanceViewModel
import com.sanibonani.save.features.member.presentation.MemberBeneficiaryViewModel
import com.sanibonani.save.features.member.presentation.MemberProfileViewModel
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.features.admin.ui.tabs.LedgerEntryDetailDialog
import com.sanibonani.save.features.member.presentation.state.MemberEvent
import com.sanibonani.save.features.common.presentation.UIEvent
import kotlinx.coroutines.launch

@Composable
fun MemberDashboardScreen(
    targetTab         : Int = 0,
    windowSizeClass   : WindowSizeClass,
    onNavigatePayment : (type: String, amount: Money, groupId: String) -> Unit,
    onNavigateAdmin   : () -> Unit,
    onLogout          : () -> Unit,
    onExitSupport     : () -> Unit = {},
    overviewVm        : MemberOverviewViewModel = hiltViewModel(),
    financeVm         : MemberFinanceViewModel = hiltViewModel(),
    profileVm         : MemberProfileViewModel = hiltViewModel(),
    beneficiaryVm     : MemberBeneficiaryViewModel = hiltViewModel(),
) {
    val overviewState by overviewVm.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clickDebouncer = rememberClickDebouncer()

    DisposableEffect(Unit) {
        overviewVm.setActive(true)
        onDispose {
            overviewVm.setActive(false)
        }
    }
    val tabs = remember(overviewState.group?.type) {
        getMemberDashboardTabs(overviewState.group)
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })

    syncDashboardPager(
        selectedTab = overviewState.selectedTab,
        pagerState = pagerState,
        tabs = tabs
    ) { page ->
        tabs.getOrNull(page)?.index?.let(overviewVm::selectTab)
    }

    LaunchedEffect(overviewState.member, overviewState.group) {
        profileVm.setImpersonationContext(overviewState.member, overviewState.group)
        financeVm.setImpersonationContext(overviewState.member, overviewState.group)
        beneficiaryVm.setImpersonationContext(overviewState.member, overviewState.group)
    }

    var viewFileData by remember { mutableStateOf<Triple<String, String, Map<String, String>>?>(null) }
    var showFileActionDialog by remember { mutableStateOf<Triple<String, String, Map<String, String>>?>(null) }
    var selectedLedgerEntry by remember { mutableStateOf<LedgerEntry?>(null) }

    LaunchedEffect(overviewState.exportFile) {
        overviewState.exportFile?.let { file ->
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val isPdf = file.extension.equals("pdf", ignoreCase = true)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = if (isPdf) "application/pdf" else "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserTitle = if (isPdf) "Open/Share Document" else "Share Statement"
            context.startActivity(android.content.Intent.createChooser(intent, chooserTitle))
        }
    }

    LaunchedEffect(Unit) {
        val handleEvent: (UIEvent) -> Unit = { event ->
            when (event) {
                is UIEvent.ShowMessage -> ToastUtils.showInfo(context, event.message)
                is UIEvent.ShowError -> ToastUtils.showError(context, event.message)
                is UIEvent.OpenFile -> {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        event.file
                    )
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK

                    try {
                        val primaryIntent = if (event.mimeType.startsWith("text/")) {
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = event.mimeType
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(flags)
                            }
                        } else {
                            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, event.mimeType)
                                addFlags(flags)
                            }
                        }
                        context.startActivity(android.content.Intent.createChooser(primaryIntent, event.chooserTitle).apply { addFlags(flags) })
                    } catch (_: Exception) {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = event.mimeType
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(flags)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, event.chooserTitle).addFlags(flags))
                    }
                }
                is UIEvent.DownloadFile -> {
                    showFileActionDialog = Triple(event.url, event.fileName, event.headers)
                }
                is MemberEvent.NavigateToPayment -> {
                    onNavigatePayment("custom", event.amount, event.groupId)
                }
                else -> Unit
            }
        }

        launch { overviewVm.events.collect { handleEvent(it) } }
        launch { profileVm.events.collect { handleEvent(it) } }
        launch { financeVm.events.collect { handleEvent(it) } }
        launch { beneficiaryVm.events.collect { handleEvent(it) } }
    }

    LaunchedEffect(targetTab) {
        if (tabs.any { it.index == targetTab }) {
            overviewVm.selectTab(targetTab)
        }
    }

    val navItems = remember(tabs, overviewVm) { memberDashboardNavigationItems(tabs) { overviewVm.selectTab(it) } }

    val selectedRoute = overviewState.selectedTab.toString()

    // ── Privacy Lock Hardening ──
    // If Support Mode is active, we MUST NOT show the dashboard until the specifically
    // targeted member's record is loaded. We check both member.id and member.userId against targetMemberId.
    val isPrivacyLocked = overviewState.isImpersonating && !overviewState.isExiting &&
            (overviewState.member == null ||
             (overviewState.member?.id != overviewState.targetMemberId &&
              overviewState.member?.userId != overviewState.targetMemberId))

    if ((overviewState.isLoading || isPrivacyLocked) && (overviewState.member == null || overviewState.group == null) && !overviewState.isExiting) {
        SanibonaniLoadingScreen(message = if (isPrivacyLocked) stringResource(R.string.ui_entering_support_mode) else stringResource(R.string.ui_syncing_your_portal))
    } else {
        AdaptiveScaffold(
            windowSizeClass = windowSizeClass,
            items = navItems,
            selectedRoute = selectedRoute,
            hideBottomBarOnCompact = true
        ) {
            Scaffold(
                topBar = {
                    MemberDashboardTopBar(
                        overviewState = overviewState,
                        tabs = tabs,
                        pagerState = pagerState,
                        windowSizeClass = windowSizeClass,
                        isPrivacyLocked = isPrivacyLocked,
                        clickDebouncer = clickDebouncer,
                        overviewVm = overviewVm,
                        onNavigateAdmin = onNavigateAdmin,
                        onLogout = onLogout,
                        onExitSupport = onExitSupport
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding)) {
                    if (overviewState.member == null && overviewState.error != null) {
                        MemberLoadErrorContent(
                            message = overviewState.error ?: "Unknown error",
                            onRetry = { overviewVm.refreshData() }
                        )
                    } else if (overviewState.member != null) {
                        MemberDashboardContent(
                            modifier = Modifier.weight(1f),
                            overviewState = overviewState,
                            pagerState = pagerState,
                            windowSizeClass = windowSizeClass,
                            overviewVm = overviewVm,
                            financeVm = financeVm,
                            profileVm = profileVm,
                            beneficiaryVm = beneficiaryVm,
                            onNavigatePayment = onNavigatePayment,
                            onNavigateAdmin = onNavigateAdmin,
                            onRefresh = { overviewVm.refreshData() },
                            onEntryClick = { selectedLedgerEntry = it },
                            onFileAction = { u, n, h -> showFileActionDialog = Triple(u, n, h) },
                            onLogout = {
                                if (overviewState.isImpersonating) {
                                    overviewVm.clearImpersonation { onExitSupport() }
                                } else {
                                    onLogout()
                                }
                            }
                        )
                    }
                }
            }

            selectedLedgerEntry?.let { entry ->
                LedgerEntryDetailDialog(
                    entry = entry,
                    groupName = overviewState.group?.name ?: "Group",
                    onDismiss = { selectedLedgerEntry = null }
                )
            }

            showFileActionDialog?.let { (url, name, headers) ->
                FileActionDialog(
                    onDismiss = { showFileActionDialog = null },
                    onView = { viewFileData = Triple(url, name, headers) },
                    onDownload = {
                        val ext = url.substringAfterLast(".", "pdf").substringBefore("?")
                        val mimeType = when (ext.lowercase()) {
                            "pdf" -> "application/pdf"
                            "jpg", "jpeg" -> "image/jpeg"
                            "png" -> "image/png"
                            else -> "application/octet-stream"
                        }
                        com.sanibonani.save.data.utils.FileDownloader.downloadFile(context, url, name, mimeType, headers)
                        showFileActionDialog = null
                    },
                    fileName = name
                )
            }

            viewFileData?.let { (url, fileName, headers) ->
                FileViewerDialog(
                    url = url,
                    fileName = fileName,
                    headers = headers,
                    onDismiss = { viewFileData = null }
                )
            }
        }
    }
}

@Composable
private fun MemberLoadErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("⚠️", fontSize = 48.sp)
        Text(stringResource(R.string.ui_could_not_load_membership), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        InfoBox(message, type = InfoType.ERROR)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Text(stringResource(R.string.ui_retry))
        }
    }
}

@Composable
private fun MemberDashboardContent(
    modifier: Modifier,
    overviewState: MemberUiState,
    pagerState: PagerState,
    windowSizeClass: WindowSizeClass,
    overviewVm: MemberOverviewViewModel,
    financeVm: MemberFinanceViewModel,
    profileVm: MemberProfileViewModel,
    beneficiaryVm: MemberBeneficiaryViewModel,
    onNavigatePayment: (type: String, amount: Money, groupId: String) -> Unit,
    onNavigateAdmin: () -> Unit,
    onRefresh: () -> Unit,
    onEntryClick: (LedgerEntry) -> Unit,
    onFileAction: (String, String, Map<String, String>) -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier) {
        VerificationSummary(overviewState.verificationResult)

        MemberStatCards(overviewState)

        PullToRefreshBox(
            isRefreshing = overviewState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
                beyondViewportPageCount = 1
            ) { page ->
                MemberTabContent(
                    page = page,
                    state = overviewState,
                    windowSizeClass = windowSizeClass,
                    onNavigatePayment = onNavigatePayment,
                    onNavigateAdmin = onNavigateAdmin,
                    overviewVm = overviewVm,
                    financeVm = financeVm,
                    profileVm = profileVm,
                    beneficiaryVm = beneficiaryVm,
                    onEntryClick = onEntryClick,
                    onFileAction = onFileAction,
                    onLogout = onLogout
                )
            }
        }
    }
}
