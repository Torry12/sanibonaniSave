package com.sanibonani.save.features.member.ui.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.sanibonani.save.ui.screens.admin.tabs.ActionButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.member.ui.LedgerItem
import com.sanibonani.save.features.member.ui.handleProfilePhotoSelection
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel
import kotlinx.coroutines.flow.map

@Composable
fun MemberOverviewTab(
    member: Member?,
    group: Group?,
    userRole: UserRole,
    contributions: List<Contribution>,
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
                    containerColor = NavyLight.copy(alpha = 0.05f)
                )
            }
        }

        item {
            AccountOverviewCard(
                member, group, calculation, uiState.isLoading,
                onPay, profileImageVersion, profileLauncher, vm, context
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

        val pendingClaims = uiState.burialClaims.filter { it.status != BeneficiaryClaimStatus.PAID && it.status != BeneficiaryClaimStatus.REJECTED && it.status != BeneficiaryClaimStatus.CANCELLED }
        val pendingPayouts = uiState.payouts.filter { it.status != PayoutStatus.COMPLETED && it.status != PayoutStatus.FAILED && it.status != PayoutStatus.CANCELLED }
        val activeLoans = uiState.loans.filter { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID || it.status == LoanStatus.OVERDUE }

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

@Composable
fun AccountOverviewCard(
    member: Member?,
    group: Group?,
    calculation: PaymentCalculation?,
    isLoading: Boolean,
    onPay: (String, Money, String) -> Unit,
    profileImageVersion: Long,
    profileLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?>,
    vm: MemberOverviewViewModel,
    context: android.content.Context
) {
    GlassCard(color = MaterialTheme.colorScheme.surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val feePaid = (member?.totalContributions ?: 0) > 0
                val joiningFeeDue = group?.joiningFee?.isPositive == true && (member?.status == MemberStatus.PENDING_PAYMENT || !feePaid)

                val memberMonthlyContribution = if (member != null && group != null) PaymentCalculator.calculateExpectedMemberGroupPart(group, member) else Money.ZERO
                val amount = if (joiningFeeDue) group?.joiningFee ?: Money.ZERO else (if (calculation?.totalDueNow?.isPositive == true) calculation.totalDueNow else memberMonthlyContribution)

                if (group == null && isLoading) {
                    Text(
                        "Loading group details...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    val showActionRequired = joiningFeeDue || calculation?.isOverdue == true
                    Text(
                        text = if (showActionRequired) "Action Required" else "Account Overview",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (showActionRequired) ErrorRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (joiningFeeDue) "Joining Fee Due" else "Monthly Contribution",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (isLoading) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    if (calculation?.isOverdue == true) {
                        Surface(
                            color = ErrorRed.copy(0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(stringResource(R.string.ui_account_overdue),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = ErrorRed,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    calculation?.let {
                        if (it.overpayment.isPositive) {
                            Surface(
                                color = SuccessGreen.copy(0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Credit Balance: ${formatZAR(it.overpayment)}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    SanibonaniButton(
                        text = if (joiningFeeDue) "Pay Joining Fee (${amount.format()})" else "Make Contribution (${amount.format()})",
                        onClick = { onPay(if (joiningFeeDue) "joining_fee" else "contribution", amount, member?.groupId ?: "") },
                        containerColor = if (calculation?.isOverdue == true) ErrorRed else Navy,
                        isLoading = isLoading,
                        enabled = group != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.width(20.dp))

            ProfilePhotoBox(member, profileImageVersion, profileLauncher, vm, context)
        }
    }
}

@Composable
fun ProfilePhotoBox(
    member: Member?,
    profileImageVersion: Long,
    profileLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?>,
    vm: MemberOverviewViewModel,
    context: android.content.Context
) {
    Box(
        modifier = Modifier.clickable { profileLauncher.launch("image/*") },
        contentAlignment = Alignment.BottomEnd
    ) {
        val url = member?.profilePhotoUrl
        if (!url.isNullOrBlank()) {
            val headers = vm.getDownloadParams(url)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .apply {
                        headers.forEach { (k, v) -> addHeader(k, v) }
                    }
                    .memoryCacheKey("$url-$profileImageVersion")
                    .crossfade(true)
                    .build(),
                contentDescription = "Photo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.5.dp, Navy.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .border(2.5.dp, Navy.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Navy.copy(alpha = 0.3f)
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
            modifier = Modifier.size(24.dp).offset(x = 4.dp, y = 4.dp),
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Edit,
                    null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}

@Composable
fun PendingPayoutsCard(pendingClaims: List<com.sanibonani.save.domain.model.BeneficiaryPayoutClaim>, pendingPayouts: List<com.sanibonani.save.domain.model.PayoutRequest>, onNavigate: () -> Unit) {
    GlassCard(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp)) {
            Text("Pending Payouts", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            pendingClaims.forEach { com.sanibonani.save.features.member.ui.PayoutSummaryItem(claim = it) }
            pendingPayouts.forEach { com.sanibonani.save.features.member.ui.PayoutSummaryItem(payout = it) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onNavigate() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("View Disbursement Hub", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Navy)
                Icon(Icons.Default.ArrowForward, null, tint = Navy, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun TrustBanner(onAuditClick: () -> Unit) {
    GlassCard(color = SuccessGreen.copy(alpha = 0.05f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Trust & Transparency", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
                Text("This group uses immutable ledgers for all transactions.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAuditClick) {
                Text("AUDIT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
        }
    }
}
