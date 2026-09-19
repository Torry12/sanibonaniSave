package com.sanibonani.save.ui.screens.member

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.ui.utils.formatPct
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.viewmodel.MemberViewModel
import com.sanibonani.save.viewmodel.state.MemberEvent
import kotlinx.coroutines.launch

@Composable
fun MemberDashboardScreen(
    targetTab: Int = 0,
    onNavigatePayment : (type: String, amount: Money, groupId: String) -> Unit,
    onNavigateAdmin   : () -> Unit,
    onNavigateBack    : () -> Unit,
    onLogout          : () -> Unit,
    isSupportMode     : Boolean = false,
    vm                : MemberViewModel = hiltViewModel<MemberViewModel>()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clickDebouncer = rememberClickDebouncer()

    DisposableEffect(Unit) {
        vm.setActive(true)
        onDispose {
            vm.setActive(false)
        }
    }
    val tabs  = listOf("Overview", "Transactions", "Insights", "Loans", "Beneficiaries", "Documents", "Messages", "Notifications", "Profile")

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })

    LaunchedEffect(state.selectedTab) {
        val targetPage = state.selectedTab.takeIf { it in tabs.indices } ?: 0
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (state.selectedTab != pagerState.currentPage) {
            vm.selectTab(pagerState.currentPage)
        }
    }

    var viewFileData by remember { mutableStateOf<Triple<String, String, Map<String, String>>?>(null) }
    var showFileActionDialog by remember { mutableStateOf<Triple<String, String, Map<String, String>>?>(null) }

    LaunchedEffect(state.exportFile) {
        state.exportFile?.let { file ->
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
        vm.events.collect { event ->
            when (event) {
                is MemberEvent.ShowMessage -> ToastUtils.showInfo(context, event.message)
                is MemberEvent.OpenFile -> {
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
                is MemberEvent.DownloadFile -> {
                    showFileActionDialog = Triple(event.url, event.fileName, event.headers)
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(targetTab) {
        if (targetTab in tabs.indices) {
            vm.selectTab(targetTab)
        }
    }

    Scaffold(
        topBar = {
            Column {
                DashboardHeaderWithNotif(
                    title = if (state.isImpersonating) "Support: ${state.member?.fullName}" else "Member Portal",
                    subtitle = if (state.isImpersonating) "Viewing as Administrator" else (state.member?.fullName ?: "Loading..."),
                    notifCount = state.notifications.size,
                    onProfileClick = { vm.selectTab(8) },
                    onNotifClick = { vm.selectTab(7) },
                    profileImageUrl = state.member?.profilePhotoUrl,
                    profileImageVersion = state.profileImageVersion,
                    onLogoutClick = { if (state.isImpersonating) vm.clearImpersonation() else clickDebouncer.processClick(onLogout) },
                    onSwitchPortal = { clickDebouncer.processClick(onNavigateAdmin) },
                    isPortalSwitchable = !state.isImpersonating
                )

                if (state.isImpersonating) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = WarningAmber,
                        contentColor = Color.Black
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Impersonation Mode Active — All actions are logged.",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Exit",
                                modifier = Modifier.clickable { vm.clearImpersonation() },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Navy
                            )
                        }
                    }
                }

                if (state.memberships.size > 1) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        OutlinedButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Navy.copy(0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.SwapHoriz, null, tint = Navy, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Switch Group: ${state.group?.name ?: "..."}", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, null, tint = Navy)
                                }
                                val currentSync = state.cacheLastSyncByGroup[state.selectedGroupId ?: ""]
                                Text(
                                    text = formatRelativeSyncTime(currentSync),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black.copy(alpha = 0.6f)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.fillMaxWidth(0.9f).background(Color.White)
                        ) {
                            state.memberships.forEach { membership ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(membership.fullName,
                                                fontWeight = if (membership.groupId == state.selectedGroupId) FontWeight.Bold else FontWeight.Normal)
                                            Text(membership.status.displayName, style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(alpha = 0.6f))
                                        }
                                    },
                                    onClick = {
                                        vm.switchGroup(membership.groupId)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (membership.groupId == state.selectedGroupId) Icons.Default.CheckCircle else Icons.Default.Circle,
                                            contentDescription = null,
                                            tint = if (membership.groupId == state.selectedGroupId) Navy else LightGray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                if (state.isUploading) {
                    LinearProgressIndicator(
                        progress = { state.uploadProgress?.toFloat() ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Black,
                        trackColor = Color.Black.copy(alpha = 0.2f)
                    )
                }
            }
        },
        containerColor = Cream
    ) { padding ->
        Column(Modifier.fillMaxSize().background(Cream).padding(padding)) {
            if (state.isLoading && state.member == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.Black) }
            } else if (state.member == null && state.error != null) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("⚠️", fontSize = 48.sp)
                    Text(stringResource(R.string.ui_could_not_load_membership), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    InfoBox(state.error!!, InfoType.ERROR)
                    Button(onClick = { vm.loadMemberData() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text(stringResource(R.string.ui_retry)) }
                }
            } else {
                val member = state.member
                val group = state.group
                val calculation = state.paymentCalculation

                VerificationSummary(state.verificationResult)

                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val totalAmount = if (state.contributions.isNotEmpty()) {
                        state.contributions.filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }.fold(Money.ZERO) { acc, c -> acc + c.amount }
                    } else {
                        member?.totalPaid ?: Money.ZERO
                    }
                    item { StatCard(title = stringResource(R.string.ui_total_contributed), value = formatZAR(totalAmount), label = "${state.paymentsCount} payments", modifier = Modifier.width(185.dp)) }

                    val memberMonthlyAmount = if (member != null && group != null) PaymentCalculator.calculateMonthlyContribution(group, member) else group?.monthlyContribution ?: Money.ZERO
                    val nextAmount = if (calculation?.totalDueNow?.isPositive == true) calculation.totalDueNow else memberMonthlyAmount
                    item { StatCard(title = stringResource(R.string.ui_next_payment), value = formatZAR(nextAmount), label = state.nextPaymentInfo, color = WarningAmber, modifier = Modifier.width(185.dp)) }

                    val rate = if (calculation != null) {
                        val expected = (calculation.periodsAhead) + (member?.totalContributions ?: 0)
                        if (expected > 0) ((member?.totalContributions ?: 0).toDouble() / expected.toDouble() * 100.0) else 100.0
                    } else 100.0
                    item { StatCard(title = stringResource(R.string.ui_payment_rate), value = formatPct(rate.coerceIn(0.0, 100.0)), label = if (calculation?.isOverdue == true) "OVERDUE" else "Up to date", color = if (calculation?.isOverdue == true) ErrorRed else SuccessGreen, modifier = Modifier.width(185.dp)) }
                }

                SecondaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.White,
                    edgePadding = 0.dp,
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(pagerState.currentPage),
                            color = Color.Black
                        )
                    }
                ) {
                    tabs.forEachIndexed { i, t ->
                        Tab(
                            selected = pagerState.currentPage == i,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(i)
                                    vm.selectTab(i)
                                }
                            },
                            text = { Text(t, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Box(Modifier.weight(1f)) {
                    PullToRefreshBox(
                        isRefreshing = state.isLoading,
                        onRefresh = { vm.loadMemberData() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true,
                            beyondViewportPageCount = 1
                        ) { page ->
                            when (page) {
                                0 -> MemberOverviewTab(
                                    member = state.member,
                                    group = state.group,
                                    userRole = state.userRole,
                                    contributions = state.contributions,
                                    calculation = state.paymentCalculation,
                                    profileImageVersion = state.profileImageVersion,
                                    onPay = onNavigatePayment,
                                    onNavigateAdmin = { clickDebouncer.processClick(onNavigateAdmin) },
                                    vm = vm,
                                    recentActivity = state.recentActivity
                                )
                                1 -> MemberTransactionsTab(
                                    state.contributions,
                                    state.isExporting,
                                    { vm.exportMyStatement() },
                                    { vm.downloadPdfStatement() },
                                    onItemClick = { vm.selectContribution(it) }
                                )
                                2 -> MemberInsightsTab(state)
                                3 -> MemberLoansTab(state.loans, state.group, vm, onFileAction = { url, name, headers -> showFileActionDialog = Triple(url, name, headers) })
                                4 -> MemberBeneficiariesTab(
                                    beneficiaries = state.beneficiaries,
                                    group = state.group,
                                    member = state.member,
                                    vm = vm,
                                    onFileAction = { url, name, headers -> showFileActionDialog = Triple(url, name, headers) }
                                )
                                5 -> MemberDocumentsTab(state.member, state.group, vm, onFileAction = { url, name, headers -> showFileActionDialog = Triple(url, name, headers) })
                                6 -> MemberMessagesTab(state.messages)
                                7 -> MemberNotificationsTab(state.notifications)
                                8 -> MemberProfileTab(state.member, state.group, vm, state.profileImageVersion)
                            }
                        }
                    }
                }
            }
        }
    }

    state.selectedContribution?.let { contribution ->
        ContributionDetailDialog(
            contribution = contribution,
            onDismiss = { vm.selectContribution(null) }
        )
    }

    showFileActionDialog?.let { fileData: Triple<String, String, Map<String, String>> ->
        val url = fileData.first
        val name = fileData.second
        val headers = fileData.third
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
                com.sanibonani.save.domain.utils.FileDownloader.downloadFile(context, url, name, mimeType, headers)
                showFileActionDialog = null
            },
            fileName = name
        )
    }

    viewFileData?.let { fileData: Triple<String, String, Map<String, String>> ->
        val url = fileData.first
        val name = fileData.second
        val headers = fileData.third
        FileViewerDialog(
            url = url,
            fileName = name,
            headers = headers,
            onDismiss = { viewFileData = null }
        )
    }
}
