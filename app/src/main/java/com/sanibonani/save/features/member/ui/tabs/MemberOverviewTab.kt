package com.sanibonani.save.features.member.ui.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.sanibonani.save.ui.components.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.features.member.ui.LedgerItem
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel

@Composable
fun MemberOverviewTab(
    member: Member?,
    group: Group?,
    userRole: UserRole,
    calculation: PaymentCalculation?,
    profileImageVersion: Long,
    onPay: (String, Money, String) -> Unit,
    onNavigateAdmin: () -> Unit,
    vm: MemberOverviewViewModel,
    recentActivity: List<LedgerEntry>,
    onEntryClick: (LedgerEntry) -> Unit,
    windowSizeClass: WindowSizeClass,
    onProfilePhotoSelected: (android.net.Uri?) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by vm.state.collectAsStateWithLifecycle()
    val profileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        onProfilePhotoSelected(it)
    }

    val isWide = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (!uiState.isSystemOnline || uiState.isAirplaneMode) {
            item {
                OfflineBanner(
                    isOnline = false,
                    message = if (uiState.isAirplaneMode) "⚠️ Airplane Mode: Syncing is paused." else "⚠️ Offline: Using cached data."
                )
            }
        }

        if (userRole == UserRole.GROUP_ADMIN) {
            item {
                ModernNavigationLink(
                    title = "Admin Dashboard",
                    subtitle = "Manage members and group settings",
                    icon = Icons.Default.AdminPanelSettings,
                    onClick = onNavigateAdmin,
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (member?.status == MemberStatus.SUSPENDED || group?.isPlatformSuspended == true || group?.feeStatus == AdminFeeState.SUSPENDED) {
            item {
                InfoBox(
                    message = "Your account or group is currently suspended due to 90 days of inactivity. You can still make outstanding payments or contributions below to restore active standing.",
                    type = InfoType.WARNING
                )
            }
        }

        val pendingClaims = uiState.burialClaims.filter {
            (it.status != BeneficiaryClaimStatus.PAID) &&
            (it.status != BeneficiaryClaimStatus.REJECTED) &&
            (it.status != BeneficiaryClaimStatus.CANCELLED)
        }
        val pendingPayouts = uiState.payouts.filter { it.status != PayoutStatus.COMPLETED && it.status != PayoutStatus.FAILED && it.status != PayoutStatus.CANCELLED }
        val activeLoans = uiState.loans.filter { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID || it.status == LoanStatus.OVERDUE }

        item {
            AccountOverviewCard(
                member, group, calculation, uiState.isLoading,
                onPay, profileImageVersion, profileLauncher, vm, context,
                memberMonthlyContribution = uiState.monthlyContributionAmount,
                activeLoans = activeLoans
            )
        }

        item {
            SanibonaniSectionHeader("Quick Actions", subtitle = "Statement downloads and governing documents")
            QuickActionGrid(
                windowSizeClass = windowSizeClass,
                actions = listOf(
                    QuickAction("My Statement", Icons.Default.Download) { vm.downloadPdfStatement() },
                    QuickAction("Group Ledger", Icons.Default.AccountBalance) { vm.downloadGroupStatement() },
                    QuickAction("Constitution", Icons.Default.Description) { vm.downloadGroupConstitution() },
                    QuickAction("Support", Icons.Default.SupportAgent) { vm.selectTab(8) }
                )
            )
        }

        if (activeLoans.isNotEmpty() || pendingClaims.isNotEmpty() || pendingPayouts.isNotEmpty()) {
            item {
                SanibonaniSectionHeader("Financial Snapshot", subtitle = "Pending disbursements and active engagements")
                if (isWide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (activeLoans.isNotEmpty()) {
                            Box(Modifier.weight(1f)) {
                                com.sanibonani.save.features.member.ui.LoanSummaryCard(activeLoans)
                            }
                        }
                        if (pendingClaims.isNotEmpty() || pendingPayouts.isNotEmpty()) {
                            Box(Modifier.weight(1f)) {
                                PendingPayoutsCard(pendingClaims, pendingPayouts) { vm.selectTab(6) }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (activeLoans.isNotEmpty()) {
                            com.sanibonani.save.features.member.ui.LoanSummaryCard(activeLoans)
                        }
                        if (pendingClaims.isNotEmpty() || pendingPayouts.isNotEmpty()) {
                            PendingPayoutsCard(pendingClaims, pendingPayouts) { vm.selectTab(6) }
                        }
                    }
                }
            }
        }

        item {
            TrustBanner { vm.selectTab(4) }
        }

        item {
            SanibonaniSectionHeader("Recent Activity", subtitle = "Your latest ledger entries")
            if (recentActivity.isEmpty()) {
                EmptyState(
                    icon = "💸",
                    title = "No transactions",
                    description = "Your financial history will appear here.",
                    actionLabel = "Refresh",
                    onAction = { vm.refreshData() }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    recentActivity.take(3).forEach { LedgerItem(it, onClick = { onEntryClick(it) }) }
                }
            }
        }
    }
}
