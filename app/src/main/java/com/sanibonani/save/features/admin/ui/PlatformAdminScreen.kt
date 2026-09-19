package com.sanibonani.save.features.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.admin.ui.tabs.platform.*
import com.sanibonani.save.features.audit.PlatformAuditTab
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.features.admin.presentation.PlatformAdminViewModel
import com.sanibonani.save.viewmodel.state.UIEvent
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
    vm: PlatformAdminViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val clickDebouncer = rememberClickDebouncer()

    if (!state.isAuthorized) {
        AccessDeniedView(onLogout = onLogout)
        return
    }

    DisposableEffect(Unit) {
        vm.setActive(true)
        onDispose { vm.setActive(false) }
    }

    val tabs = listOf("Analytics", "Groups", "Payments", "Ledger", "Fees", "Audit", "Maintenance", "Backups")

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
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
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Platform Administrator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(onClick = { clickDebouncer.processClick(onLogout) }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { vm.setTab(index) },
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (state.selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            if (state.isLoading && state.analytics.getOrNull()?.totalGroups == 0) {
                SanibonaniLoadingScreen(message = "Loading platform data...")
            } else {
                // ⚡ Performance Hardening: Use key-based rendering for heavy tab content
                key(state.selectedTab) {
                    when (state.selectedTab) {
                        0 -> state.analytics.getOrNull()?.let { analytics ->
                            PlatformAnalyticsTab(
                                analytics = analytics,
                                totalRevenueShare = state.totalRevenueShare,
                                provinceDistribution = state.provinceDistribution,
                                marketSegments = state.marketSegments,
                                vm = vm
                            )
                        }
                        1 -> AllGroupsTab(
                            groups = state.groups,
                            vm = vm,
                            state = state,
                            onNavigateToHealthScore = onNavigateToHealthScore
                        )
                        2 -> {
                            val groupNames = state.groupNames
                            val groupProvinces = state.groupProvinces

                            val payoutsByProvince = remember(state.payouts, groupProvinces) {
                                state.payouts.groupBy { groupProvinces[it.groupId] ?: "Other" }.toList().sortedBy { it.first }
                            }
                            val loanEscalations = remember(state.loanRequestsByGroup) {
                                state.loanRequestsByGroup.values.flatten()
                            }
                            val loansByProvince = remember(loanEscalations, groupProvinces) {
                                loanEscalations.groupBy { groupProvinces[it.groupId] ?: "Other" }.toList().sortedBy { it.first }
                            }
                            val claimsByProvince = remember(state.escalatedClaims, groupProvinces) {
                                state.escalatedClaims.groupBy { groupProvinces[it.groupId] ?: "Other" }.toList().sortedBy { it.first }
                            }

                            DisbursementsTab(
                                state = DisbursementsTabState(
                                    payoutRequests = state.payouts,
                                    loanEscalations = loanEscalations,
                                    claimEscalations = state.escalatedClaims,
                                    behaviorInsights = state.memberBehaviorInsights,
                                    groupNames = groupNames,
                                    loanMemberNames = state.loanMemberNames,
                                    groupProvinces = groupProvinces,
                                    isProcessingPayout = state.isProcessingPayout,
                                    isProcessingLoan = state.isProcessingLoanRequest,
                                    isProcessingClaim = state.isProcessingClaim,
                                    selectedMemberSummary = state.selectedMemberSummary,
                                    isLoadingMemberSummary = state.isLoadingMemberSummary,
                                    payoutsByProvince = payoutsByProvince,
                                    loansByProvince = loansByProvince,
                                    claimsByProvince = claimsByProvince
                                ),
                                vm = vm,
                                onOpenMemberPortalFromDisbursement = onOpenMemberPortalFromDisbursement,
                                onImpersonateMember = onImpersonateMember,
                                onNavigateToHealthScore = onNavigateToHealthScore,
                                onNavigateToBehaviorTracking = onNavigateToBehaviorTracking
                            )
                        }
                        3 -> PlatformLedgerTab(state, vm)
                        4 -> FeeManagementTab(state, vm)
                        5 -> PlatformAuditTab(
                            report = state.platformAuditReport,
                            onRefresh = { vm.loadPlatformAuditReport() }
                        )
                        6 -> MaintenanceTab(
                            state = state,
                            vm = vm,
                            onNavigateToCreateAdmin = onNavigateToCreateAdmin,
                            onNavigateToSandbox = onNavigateToSandbox,
                            onLogout = onLogout,
                            onImpersonateGroupAdmin = onImpersonateGroupAdmin,
                            onImpersonateMember = onImpersonateMember
                        )
                        7 -> BackupTab(state = state, vm = vm)
                    }
                }
            }
        }
    }

    state.error?.let { error ->
        GlobalErrorBanner(
            message = error,
            onRetry = { vm.loadData() },
            onDismiss = { vm.clearError() },
            isCritical = true
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
                "Access Denied",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "You do not have the necessary permissions to access the Platform Administrator portal. This attempt has been logged for security purposes.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Logout for Security")
            }
        }
    }
}
