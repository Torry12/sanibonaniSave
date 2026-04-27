package com.sanibonani.save.ui.screens.member

import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.sanibonani.save.data.FileUploadLimits
import com.sanibonani.save.data.utils.PaymentCalculation
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanRepayment
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.model.NotificationPref
import com.sanibonani.save.domain.model.SA_PROVINCES
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.ui.components.CenterPlaceholder
import com.sanibonani.save.ui.components.DashboardHeaderWithNotif
import com.sanibonani.save.ui.components.DocumentUploadCard
import com.sanibonani.save.ui.components.EmptyState
import com.sanibonani.save.ui.components.IDNumberTransformation
import com.sanibonani.save.ui.components.InfoBox
import com.sanibonani.save.ui.components.InfoRow
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.components.PhoneNumberTransformation
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.SanibonaniDropdown
import com.sanibonani.save.ui.components.AutoCompleteTextField
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.ui.components.SectionHeader
import com.sanibonani.save.ui.components.StatCard
import com.sanibonani.save.ui.components.StatusChip
import com.sanibonani.save.ui.components.formatPct
import com.sanibonani.save.ui.components.formatZAR
import com.sanibonani.save.ui.theme.Charcoal
import com.sanibonani.save.ui.theme.Cream
import com.sanibonani.save.ui.theme.ErrorRed
import com.sanibonani.save.ui.theme.ForestLight
import com.sanibonani.save.ui.theme.Gold
import com.sanibonani.save.ui.theme.LightGray
import com.sanibonani.save.ui.theme.MidGray
import com.sanibonani.save.ui.theme.SuccessGreen
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.viewmodel.MemberViewModel
import com.sanibonani.save.viewmodel.state.MemberEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.io.ByteArrayOutputStream

private fun isBurialSocietyLike(group: Group?): Boolean {
    // Defensive: some legacy data stores `type` as display text (e.g., "Burial Society"),
    // which may coerce to OTHER. If burial-specific fields exist, treat as burial society.
    return group?.type == GroupType.BURIAL_SOCIETY ||
        group?.maxBeneficiaries != null ||
        group?.beneficiaryIncreasePct != null
}

private fun formatRelativeSyncTime(timestampMillis: Long?): String {
    if (timestampMillis == null || timestampMillis <= 0L) return "Not synced yet"
    val delta = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
    val seconds = delta / 1000
    return when {
        seconds < 60 -> "Synced just now"
        seconds < 3600 -> "Synced ${seconds / 60}m ago"
        seconds < 86400 -> "Synced ${seconds / 3600}h ago"
        else -> "Synced ${seconds / 86400}d ago"
    }
}

// Remove redundant withImageVersion as storage-level pathing handles cache busting via timestamps.

@Composable
fun MemberDashboardScreen(
    targetTab: Int = 0,
    onNavigatePayment : (type: String, amount: String, groupId: String) -> Unit,
    onNavigateAdmin   : () -> Unit,
    onLogout          : () -> Unit,
    vm                : MemberViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

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

                    // Robust FileProvider sharing:
                    // - Attach ClipData so URI grants propagate reliably through choosers
                    // - Explicitly grant URI permission to all resolved apps
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK

                    fun grantToResolvedApps(intent: android.content.Intent) {
                        val resolved = context.packageManager.queryIntentActivities(
                            intent,
                            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                        )
                        resolved.forEach { info ->
                            context.grantUriPermission(info.activityInfo.packageName, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }

                    try {
                        // CSVs are typically shared (not viewed) on most devices.
                        val primaryIntent = if (event.mimeType.startsWith("text/")) {
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = event.mimeType
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(flags)
                                clipData = android.content.ClipData.newRawUri("file", uri)
                            }
                        } else {
                            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, event.mimeType)
                                addFlags(flags)
                                clipData = android.content.ClipData.newRawUri("file", uri)
                            }
                        }

                        grantToResolvedApps(primaryIntent)

                        val chooser = android.content.Intent.createChooser(primaryIntent, event.chooserTitle).apply {
                            addFlags(flags)
                            clipData = android.content.ClipData.newRawUri("file", uri)
                        }

                        context.startActivity(chooser)
                    } catch (_: Exception) {
                        // Last resort: share
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = event.mimeType
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(flags)
                            clipData = android.content.ClipData.newRawUri("file", uri)
                        }
                        grantToResolvedApps(shareIntent)
                        context.startActivity(
                            android.content.Intent.createChooser(shareIntent, event.chooserTitle)
                                .addFlags(flags)
                        )
                    }
                }
                else -> Unit
            }
        }
    }
    val tabs  = listOf("Overview", "Transactions", "Loans", "Beneficiaries", "Documents", "Messages", "Notifications", "Profile")

    // Handle target tab from deep link or navigation
    LaunchedEffect(targetTab) {
        if (targetTab in tabs.indices) {
            vm.selectTab(targetTab)
        }
    }

    // Only show error toast when error changes (not on every recomposition)
    val currentError = state.error
    LaunchedEffect(currentError) {
        currentError?.let { ToastUtils.showError(context, it) }
    }

    // Remove showUploadSuccess state and logic

    Scaffold(
        topBar = {
            Column {
                DashboardHeaderWithNotif(
                    title = "Member Portal",
                    subtitle = state.member?.fullName ?: "Loading...",
                    notifCount = state.notifications.size,
                    onProfileClick = { vm.selectTab(7) }, // Index 7 is Profile
                    onNotifClick = { vm.selectTab(6) }, // Index 6 is Notifications
                    profileImageUrl = state.member?.profilePhotoUrl,
                    profileImageHeaders = state.member?.profilePhotoUrl
                        ?.let { vm.getDownloadParams(it) }
                        ?: emptyMap(),
                    profileImageVersion = state.profileImageVersion,
                    onLogoutClick = onLogout,
                    onSwitchPortal = onNavigateAdmin,
                    isPortalSwitchable = true
                )
                
                // Group Switcher for Members
                if (state.memberships.size > 1) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        OutlinedButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, com.sanibonani.save.ui.theme.Forest.copy(0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.SwapHoriz, null, tint = com.sanibonani.save.ui.theme.Forest, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Switch Group: ${state.group?.name ?: "..."}", color = com.sanibonani.save.ui.theme.Forest, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, null, tint = com.sanibonani.save.ui.theme.Forest)
                                }
                                val currentSync = state.cacheLastSyncByGroup[state.currentGroupId]
                                Text(
                                    text = formatRelativeSyncTime(currentSync),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MidGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                                fontWeight = if (membership.groupId == state.currentGroupId) FontWeight.Bold else FontWeight.Normal)
                                            Text(membership.status.displayName, style = MaterialTheme.typography.labelSmall, color = MidGray)
                                            Text(
                                                formatRelativeSyncTime(state.cacheLastSyncByGroup[membership.groupId]),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MidGray
                                            )
                                        }
                                    },
                                    onClick = {
                                        vm.switchGroup(membership.groupId)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (membership.groupId == state.currentGroupId) Icons.Default.CheckCircle else Icons.Default.Circle,
                                            contentDescription = null,
                                            tint = if (membership.groupId == state.currentGroupId) com.sanibonani.save.ui.theme.Forest else LightGray,
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
                        color = com.sanibonani.save.ui.theme.Forest,
                        trackColor = com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.2f)
                    )
                }
                AnimatedVisibility(
                    visible = state.notifications.isNotEmpty(),
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    val latestNotif = state.notifications.firstOrNull()
                    if (latestNotif != null) {
                        Surface(
                            color = com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.9f),
                            contentColor = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Notifications, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = latestNotif.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { /* Dismiss */ }, Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = Cream
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(Cream).padding(padding)
        ) {
            if (state.isLoading && state.member == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = com.sanibonani.save.ui.theme.Forest)
                }
                return@Column
            } else if (state.member == null && state.error != null) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("⚠️", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Could not load your membership", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    InfoBox(state.error!!, InfoType.ERROR)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { vm.loadMemberData() },
                        colors = ButtonDefaults.buttonColors(containerColor = com.sanibonani.save.ui.theme.Forest),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Retry Connection")
                    }
                }
                return@Column
            }

            val member = state.member
            val group = state.group
            val calculation = state.calculation

            LazyRow(
                contentPadding        = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Priority: Use the direct count from the member record for immediate UI feedback
                val paymentCount = member?.totalContributions ?: state.contributions.count { it.status == ContributionStatus.PAID }
                // Prefer the authoritative aggregate from the member row (trigger-updated) when available.
                // This avoids showing R0.00 in cases where the contribution list is still syncing.
                val totalAmount = if (state.contributions.isNotEmpty()) {
                    state.contributions
                        .filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }
                        .sumOf { it.amount }
                } else {
                    member?.totalPaid ?: 0.0
                }

                item {
                    StatCard(
                        "💰", "Total Contributed",
                        formatZAR(totalAmount),
                        "$paymentCount successful payments",
                        accentColor = com.sanibonani.save.ui.theme.Forest
                    )
                }

                // Calculate the member's actual monthly contribution
                val memberMonthlyAmount = if (member != null && group != null) {
                    com.sanibonani.save.data.utils.PaymentCalculator.calculateMonthlyContribution(group, member)
                } else {
                    group?.monthlyContribution ?: 0.0
                }
                
                // Show totalDueNow if there's a shortfall, otherwise show monthly contribution
                val nextAmount = if ((calculation?.totalDueNow ?: 0.0) > 0.0) {
                    calculation!!.totalDueNow
                } else {
                    memberMonthlyAmount
                }
                val nextDueDate = calculation?.nextDueDate ?: "N/A"

                item {
                    StatCard(
                        "📅", "Next Payment",
                        formatZAR(nextAmount),
                        if (nextDueDate == "N/A") "N/A" else "Due $nextDueDate",
                        accentColor = com.sanibonani.save.ui.theme.WarningAmber
                    )
                }

                val rate = if (calculation != null) {
                    val expected = (calculation.periodsAhead) + (member?.totalContributions ?: 0)
                    if (expected > 0) ((member?.totalContributions ?: 0).toDouble() / expected.toDouble() * 100.0) else 100.0
                } else 100.0
                
                val statusText = when {
                    calculation == null -> "Loading..."
                    calculation.isOverdue -> "OVERDUE: ${formatZAR(calculation.shortfall)}"
                    calculation.shortfall > 0 -> "${formatZAR(calculation.shortfall)} behind"
                    calculation.overpayment > 0 -> "${formatZAR(calculation.overpayment)} ahead"
                    else -> "Up to date"
                }

                item {
                    StatCard(
                        "✅", "Payment Rate",
                        formatPct(rate.coerceIn(0.0, 100.0)),
                        statusText,
                        accentColor = when {
                            calculation?.isOverdue == true -> ErrorRed
                            rate >= 90.0 -> SuccessGreen
                            else -> com.sanibonani.save.ui.theme.WarningAmber
                        }
                    )
                }

                item {
                    val status = member?.status ?: MemberStatus.PROBATION
                    val probationEnd = member?.probationEndAt?.let {
                        try {
                            val dateStr = it.substringBefore("T")
                            val date = LocalDate.parse(dateStr)
                            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
                            "Ends ${date.format(formatter)}"
                        } catch (_: Exception) { null }
                    }

                    StatCard(
                        "🛡️", "Cover Status",
                        if (status == MemberStatus.ACTIVE) "Full Cover" else status.displayName,
                        probationEnd ?: status.displayName,
                        accentColor = if (status == MemberStatus.ACTIVE) SuccessGreen else com.sanibonani.save.ui.theme.WarningAmber
                    )
                }
            }

            ScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                containerColor   = Color.White,
                edgePadding      = 0.dp
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = state.selectedTab == i,
                        onClick  = { vm.selectTab(i) },
                        text     = { Text(t, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Box(Modifier.weight(1f)) {
                when (state.selectedTab) {
                    0 -> MemberOverviewTab(
                        member = member,
                        group = group,
                        userRole = state.userRole,
                        contributions = state.contributions,
                        calculation = calculation,
                        onPay = onNavigatePayment,
                        onNavigateAdmin = onNavigateAdmin,
                        vm = vm
                    )
                    1 -> MemberTransactionsTab(
                        contributions = state.contributions,
                        isExporting = state.isExporting,
                        onExportCsv = { vm.exportMyStatement() },
                        onDownloadPdf = { vm.downloadPdfStatement() }
                    )
                    2 -> MemberLoansTab(state.loans, state.loanRepayments, group, vm)
                    3 -> MemberBeneficiariesTab(state.beneficiaries, group, vm)
                    4 -> MemberDocumentsTab(member, group, vm)
                    5 -> MemberMessagesTab(state.messages, vm)
                    6 -> MemberNotificationsTab(state.notifications)
                    7 -> MemberProfileTab(member, group, vm, state.profileImageVersion)
                    else -> CenterPlaceholder("Unknown Tab")
                }
            }

            // Overlay for state processing
            if (state.isLoading && state.selectedTab == 0) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = com.sanibonani.save.ui.theme.Forest)
                            Spacer(Modifier.height(16.dp))
                            Text("Processing payment...", color = com.sanibonani.save.ui.theme.Forest, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemberOverviewTab(
    member: Member?, 
    group: Group?,
    userRole: UserRole,
    contributions: List<Contribution>,
    calculation: PaymentCalculation?,
    onPay: (String, String, String) -> Unit,
    onNavigateAdmin: () -> Unit,
    vm: MemberViewModel
) {
    val context = LocalContext.current
    val profileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        handleProfilePhotoSelection(context, vm, uri)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (userRole == UserRole.GROUP_ADMIN) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, Gold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🛠️ Group Administrator", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        SanibonaniButton("Admin Dashboard", onClick = onNavigateAdmin, modifier = Modifier.height(36.dp))
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ForestLight.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val isJoiningFeePaid = (member?.totalContributions ?: 0) > 0
                            val isJoiningFeeRequired = member?.status == MemberStatus.PENDING_PAYMENT || !isJoiningFeePaid
                            val paymentTitle = if (isJoiningFeeRequired) "Joining Fee Required" else "Monthly Contribution Due"
                            
                            // Calculate the member's actual monthly contribution (may include beneficiary adjustments)
                            val memberMonthlyContribution = if (member != null && group != null) {
                                com.sanibonani.save.data.utils.PaymentCalculator.calculateMonthlyContribution(group, member)
                            } else {
                                group?.monthlyContribution ?: 0.0
                            }
                            
                            val amount = if (isJoiningFeeRequired) {
                                group?.joiningFee ?: 0.0
                            } else {
                                // Show totalDueNow if there's a shortfall, otherwise show the monthly contribution
                                val dueNow = calculation?.totalDueNow ?: 0.0
                                if (dueNow > 0.0) dueNow else memberMonthlyContribution
                            }

                            val paymentType = if (isJoiningFeeRequired) "joining_fee" else "contribution"
                            val buttonText = if (isJoiningFeeRequired) "Pay Joining Fee (${formatZAR(amount)})" else "Make Contribution (${formatZAR(amount)})"
                            Text(paymentTitle, color = Charcoal, fontWeight = FontWeight.Bold)
                            
                            if (calculation?.isOverdue == true) {
                                Text("YOUR ACCOUNT IS OVERDUE", color = ErrorRed, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }

                            Text(
                                if (isJoiningFeeRequired) "Pay your joining fee to complete your membership setup."
                                else "Stay active by paying your monthly contribution.",
                                color = MidGray, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { onPay(paymentType, amount.toString(), member?.groupId ?: "") },
                                colors = ButtonDefaults.buttonColors(containerColor = if (calculation?.isOverdue == true) ErrorRed else com.sanibonani.save.ui.theme.Forest),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(buttonText, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        // Profile Photo Upload Option
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier.clickable { profileLauncher.launch("image/*") },
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            val profileUrl = member?.profilePhotoUrl
                            // Recompute headers on recomposition so refreshed auth tokens are picked up.
                            val headers = if (profileUrl.isNullOrBlank()) emptyMap() else vm.getDownloadParams(profileUrl)
                            val headerFingerprint = headers.entries
                                .sortedBy { it.key }
                                .joinToString("|") { "${it.key}=${it.value}" }
                            val profileRequest = remember(profileUrl, headerFingerprint) {
                                if (profileUrl.isNullOrBlank()) null
                                else ImageRequest.Builder(context)
                                    .data(profileUrl)
                                    .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .networkCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(true)
                                    .build()
                            }
                            val personPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Person)

                            AsyncImage(
                                model = profileRequest,
                                contentDescription = "Profile Photo",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, com.sanibonani.save.ui.theme.Forest, CircleShape),
                                contentScale = ContentScale.Crop,
                                placeholder = personPainter,
                                error = personPainter,
                                fallback = personPainter
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(20.dp).background(com.sanibonani.save.ui.theme.Forest, CircleShape).padding(4.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Recent Activity")
            if (contributions.isEmpty()) {
                EmptyState(icon = "💸", title = "No transactions yet", description = "Your contribution history will appear here.")
            } else {
                contributions.take(3).forEach { contrib ->
                    ContributionItem(contrib)
                }
            }
        }
    }
}

@Composable
fun MemberTransactionsTab(
    contributions: List<Contribution>,
    isExporting: Boolean = false,
    onExportCsv: () -> Unit = {},
    onDownloadPdf: () -> Unit = {}
) {
    LocalContext.current

    Column(Modifier.fillMaxSize()) {
        if (contributions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportCsv,
                    modifier = Modifier.weight(1f),
                    enabled = !isExporting,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, com.sanibonani.save.ui.theme.Forest),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, null, modifier = Modifier.size(18.dp), tint = com.sanibonani.save.ui.theme.Forest)
                    Spacer(Modifier.width(4.dp))
                    Text("Export CSV", style = MaterialTheme.typography.labelMedium, color = com.sanibonani.save.ui.theme.Forest)
                }

                OutlinedButton(
                    onClick = onDownloadPdf,
                    modifier = Modifier.weight(1f),
                    enabled = !isExporting,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, com.sanibonani.save.ui.theme.Forest),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp), tint = com.sanibonani.save.ui.theme.Forest)
                    Spacer(Modifier.width(4.dp))
                    Text("Download PDF", style = MaterialTheme.typography.labelMedium, color = com.sanibonani.save.ui.theme.Forest)
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (contributions.isEmpty()) {
                item { EmptyState(icon = "💸", title = "No transactions", description = "You haven't made any payments yet.") }
            } else {
                items(contributions) { contrib ->
                    ContributionItem(contrib)
                }
            }
        }
        
        if (isExporting) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = com.sanibonani.save.ui.theme.Forest,
                trackColor = com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
fun MemberBeneficiariesTab(
    beneficiaries: List<Beneficiary>,
    group: Group?,
    vm: MemberViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val isBurial = isBurialSocietyLike(group)
    val limit = group?.maxBeneficiaries ?: 0
    val count = beneficiaries.size

    Column(Modifier.fillMaxSize()) {
        Surface(
            color = com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isBurial) "Beneficiary Allowance" else "Dependents",
                        fontWeight = FontWeight.Bold,
                        color = com.sanibonani.save.ui.theme.Forest
                    )
                    val subtitle = if (isBurial) {
                        if (limit <= 0) "$count added" else "$count of $limit slots used"
                    } else {
                        "Add people who may be covered by the group policy (if applicable)."
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }

                val canAdd = !isBurial || limit <= 0 || count < limit
                if (canAdd) {
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = com.sanibonani.save.ui.theme.Forest),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Text("Add New", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (beneficiaries.isEmpty()) {
                item { 
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EmptyState(
                            icon = "👥",
                            title = "No beneficiaries",
                            description = "Add your dependents to ensure they are covered by the group policy."
                        )
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, com.sanibonani.save.ui.theme.Forest)
                        ) {
                            Icon(Icons.Default.Add, null, tint = com.sanibonani.save.ui.theme.Forest)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Beneficiary", color = com.sanibonani.save.ui.theme.Forest)
                        }
                    }
                }
            } else {
                items(beneficiaries) { beneficiary ->
                    var showEditDialog by remember { mutableStateOf(false) }
                    
                    BeneficiaryItem(
                        beneficiary = beneficiary,
                        onEdit = { showEditDialog = true },
                        onDelete = { beneficiary.id?.let { vm.deleteBeneficiary(it) } },
                        onUploadDocument = { bytes, fileName ->
                            beneficiary.id?.let { id ->
                                vm.uploadBeneficiaryDocument(id, bytes, fileName)
                            }
                        }
                    )
                    
                    if (showEditDialog) {
                        AddBeneficiaryDialog(
                            beneficiary = beneficiary,
                            onDismiss = { showEditDialog = false },
                            onConfirm = { name, id, rel, dob, is65 ->
                                beneficiary.id?.let { vm.updateBeneficiary(it, name, id, rel, dob, is65) }
                                showEditDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddBeneficiaryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, id, rel, dob, is65 ->
                vm.addBeneficiary(name, id, rel, dob, is65)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun BeneficiaryItem(
    beneficiary: Beneficiary,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUploadDocument: (ByteArray, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val fileName = getFileNameFromUri(contentResolver, it)
            val bytes = contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() }
            if (bytes != null) {
                // Check file size (3MB limit)
                if (bytes.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
                    // Show error toast - file too large
                    android.widget.Toast.makeText(
                        context,
                        "File too large. Maximum 3MB allowed.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onUploadDocument(bytes, fileName)
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Cream), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = com.sanibonani.save.ui.theme.Forest)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(beneficiary.fullName, fontWeight = FontWeight.Bold)
                    Text("${beneficiary.relationship ?: "Dependent"} • ${beneficiary.idNumber ?: "No ID"}",
                        style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    if (beneficiary.isOver65) {
                        Text("Over 65 (Premium Increase)", color = Color.Red, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, tint = com.sanibonani.save.ui.theme.Forest, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                }
            }

            // Document upload section
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = LightGray.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (beneficiary.documentUrl != null) Icons.Default.Check else Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = if (beneficiary.documentUrl != null) com.sanibonani.save.ui.theme.Forest else MidGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "ID Document",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when {
                                beneficiary.documentUrl != null -> when (beneficiary.documentStatus) {
                                    DocumentStatus.VERIFIED -> "✓ Verified"
                                    DocumentStatus.REJECTED -> "✗ Rejected - Please reupload"
                                    else -> "Pending verification"
                                }
                                else -> "Required for claims"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                beneficiary.documentStatus == DocumentStatus.VERIFIED -> com.sanibonani.save.ui.theme.Forest
                                beneficiary.documentStatus == DocumentStatus.REJECTED -> Color.Red
                                beneficiary.documentUrl != null -> MidGray
                                else -> Color.Red.copy(alpha = 0.7f)
                            }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { launcher.launch("*/*") },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (beneficiary.documentUrl != null) com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.5f) else Color.Red.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (beneficiary.documentUrl != null) Icons.Default.Refresh else Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (beneficiary.documentUrl != null) com.sanibonani.save.ui.theme.Forest else Color.Red.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (beneficiary.documentUrl != null) "Replace" else "Upload",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (beneficiary.documentUrl != null) com.sanibonani.save.ui.theme.Forest else Color.Red.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun AddBeneficiaryDialog(
    beneficiary: Beneficiary? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(beneficiary?.fullName ?: "") }
    var idNumber by remember { mutableStateOf(beneficiary?.idNumber ?: "") }
    var relationship by remember { mutableStateOf(beneficiary?.relationship ?: "") }
    var dob by remember { mutableStateOf(beneficiary?.dateOfBirth ?: "") }
    var isOver65 by remember { mutableStateOf(beneficiary?.isOver65 ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (beneficiary == null) "Add Beneficiary" else "Update Beneficiary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = "Full Name",
                    placeholder = "John Doe"
                )
                SanibonaniTextField(
                    value = idNumber, 
                    onValueChange = { if (it.length <= 13) idNumber = it }, 
                    label = "ID Number",
                    placeholder = "YYMMDD SSSS CAZ",
                    visualTransformation = IDNumberTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                SanibonaniTextField(
                    value = relationship, 
                    onValueChange = { relationship = it }, 
                    label = "Relationship",
                    placeholder = "Spouse, Child, etc."
                )
                
                DatePickerField(
                    label = "Date of Birth",
                    value = dob,
                    onValueChange = { 
                        dob = it
                        // Optional: Auto-detect over 65 from DOB
                        try {
                            val birthDate = LocalDate.parse(it)
                            val age = ChronoUnit.YEARS.between(birthDate, LocalDate.now())
                            isOver65 = age >= 65
                        } catch (_: Exception) {}
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isOver65, onCheckedChange = { isOver65 = it })
                    Text("Over 65 Years Old", style = MaterialTheme.typography.bodyMedium)
                }
                
                if (isOver65) {
                    InfoBox("Premium increases may apply for beneficiaries over 65.", InfoType.WARNING)
                }
            }
        },
        confirmButton = {
            SanibonaniButton(
                text = if (beneficiary == null) "Add" else "Update",
                onClick = { if (name.isNotBlank()) onConfirm(name, idNumber, relationship, dob, isOver65) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MidGray) }
        }
    )
}

@Composable
fun DatePickerField(label: String, value: String, onValueChange: (String) -> Unit) {
    SanibonaniTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = "YYYY-MM-DD",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun MemberDocumentsTab(member: Member?, group: Group?, vm: MemberViewModel) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    var activeDocIndex by remember { mutableIntStateOf(0) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val fileName = getFileNameFromUri(contentResolver, it)
            val docType = when (activeDocIndex) {
                1 -> "ID"
                2 -> "Proof of Residence"
                3 -> "Beneficiary Form"
                4 -> "Marriage Certificate"
                5 -> "Constitution"
                else -> "Document"
            }
            val bytes = contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                if (bytes.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
                    ToastUtils.showError(context, "File too large. Maximum allowed size is 3MB.")
                } else {
                    vm.uploadDocument(activeDocIndex, bytes, fileName, docType)
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader("Your Documents")
        Text(
            "Upload clear copies of your documents to maintain an active status.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        
        Spacer(Modifier.height(16.dp))
        
        DocumentUploadCard(
            name = "Identity Document (ID)", 
            isUploaded = !member?.document1Url.isNullOrBlank(),
            status = member?.document1Status ?: DocumentStatus.PENDING,
            onUpload = {
                activeDocIndex = 1
                launcher.launch("*/*") // Support PDF and Images
            },
            onDownload = if (!member?.document1Url.isNullOrBlank()) {
                { 
                    val url = member.document1Url ?: ""
                    val headers = vm.getDownloadParams(url)
                    val extension = url.substringAfterLast(".", "pdf")
                    val fileName = "ID_Document_${System.currentTimeMillis()}.$extension"
                    val mimeType = when (extension.lowercase()) {
                        "pdf" -> "application/pdf"
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        else -> "application/octet-stream"
                    }
                    com.sanibonani.save.domain.utils.FileDownloader.downloadFile(context, url, fileName, mimeType, headers)
                }
            } else null
        )
        
        DocumentUploadCard(
            name = "Proof of Residence", 
            isUploaded = !member?.document2Url.isNullOrBlank(),
            status = member?.document2Status ?: DocumentStatus.PENDING,
            onUpload = {
                activeDocIndex = 2
                launcher.launch("*/*") // Support PDF and Images
            },
            onDownload = if (!member?.document2Url.isNullOrBlank()) {
                { 
                    val url = member.document2Url ?: ""
                    val headers = vm.getDownloadParams(url)
                    val extension = url.substringAfterLast(".", "pdf")
                    val fileName = "Proof_of_Residence_${System.currentTimeMillis()}.$extension"
                    val mimeType = when (extension.lowercase()) {
                        "pdf" -> "application/pdf"
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        else -> "application/octet-stream"
                    }
                    com.sanibonani.save.domain.utils.FileDownloader.downloadFile(context, url, fileName, mimeType, headers)
                }
            } else null
        )

        DocumentUploadCard(
            name = if (isBurialSocietyLike(group)) {
                "Beneficiary Form / Documents"
            } else {
                "Beneficiary / Dependent Documents (Optional)"
            },
            isUploaded = !member?.document3Url.isNullOrBlank(),
            status = member?.document3Status ?: DocumentStatus.PENDING,
            onUpload = {
                activeDocIndex = 3
                launcher.launch("*/*")
            },
            onDownload = if (!member?.document3Url.isNullOrBlank()) {
                {
                    val url = member.document3Url ?: ""
                    val headers = vm.getDownloadParams(url)
                    val extension = url.substringAfterLast(".", "pdf")
                    val fileName = "Beneficiary_Doc_${System.currentTimeMillis()}.$extension"
                    val mimeType = when (extension.lowercase()) {
                        "pdf" -> "application/pdf"
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        else -> "application/octet-stream"
                    }
                    com.sanibonani.save.domain.utils.FileDownloader.downloadFile(context, url, fileName, mimeType, headers)
                }
            } else null
        )

        DocumentUploadCard(
            name = "Marriage Certificate (Optional)", 
            isUploaded = !member?.document4Url.isNullOrBlank(),
            status = member?.document4Status ?: DocumentStatus.PENDING,
            onUpload = {
                activeDocIndex = 4
                launcher.launch("*/*")
            },
            onDownload = if (!member?.document4Url.isNullOrBlank()) {
                { 
                    val url = member.document4Url ?: ""
                    val headers = vm.getDownloadParams(url)
                    val extension = url.substringAfterLast(".", "pdf")
                    val fileName = "Marriage_Certificate_${System.currentTimeMillis()}.$extension"
                    val mimeType = when (extension.lowercase()) {
                        "pdf" -> "application/pdf"
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        else -> "application/octet-stream"
                    }
                    com.sanibonani.save.domain.utils.FileDownloader.downloadFile(context, url, fileName, mimeType, headers)
                }
            } else null
        )

        DocumentUploadCard(
            name = "Group Constitution (Signed)", 
            isUploaded = !member?.document5Url.isNullOrBlank(),
            status = member?.document5Status ?: DocumentStatus.PENDING,
            onUpload = {
                activeDocIndex = 5
                launcher.launch("*/*")
            },
            onDownload = if (!member?.document5Url.isNullOrBlank()) {
                { 
                    val url = member.document5Url ?: ""
                    val headers = vm.getDownloadParams(url)
                    val extension = url.substringAfterLast(".", "pdf")
                    val fileName = "Constitution_Signed_${System.currentTimeMillis()}.$extension"
                    val mimeType = when (extension.lowercase()) {
                        "pdf" -> "application/pdf"
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        else -> "application/octet-stream"
                    }
                    com.sanibonani.save.domain.utils.FileDownloader.downloadFile(context, url, fileName, mimeType, headers)
                }
            } else null
        )
        
        Spacer(Modifier.height(24.dp))
        InfoBox(
            message = "Documents are encrypted and only accessible by group administrators for verification purposes.",
            type = InfoType.INFO
        )
    }
}

@Composable
fun MemberProfileTab(member: Member?, group: Group?, vm: MemberViewModel, profileImageVersion: Long = 0L) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        handleProfilePhotoSelection(context, vm, uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f),
                border = BorderStroke(2.dp, com.sanibonani.save.ui.theme.Forest)
            ) {
            if (!member?.profilePhotoUrl.isNullOrBlank()) {
                    val profileUrl = member?.profilePhotoUrl.orEmpty()
                    // Recompute headers on recomposition so refreshed auth tokens are picked up.
                    val headers = vm.getDownloadParams(profileUrl)
                    val headerFingerprint = headers.entries
                        .sortedBy { it.key }
                        .joinToString("|") { "${it.key}=${it.value}" }
                    // Include profileImageVersion so a fresh upload invalidates the in-memory
                    // cache even if Coil would otherwise reuse the previous entry.
                    val request = remember(profileUrl, headerFingerprint, profileImageVersion) {
                        ImageRequest.Builder(context)
                            .data(profileUrl)
                            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                            .memoryCacheKey("$profileUrl-$profileImageVersion")
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .build()
                    }
                    val personPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Person)

                    AsyncImage(
                        model = request,
                        contentDescription = "Profile Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = personPainter,
                        error = personPainter,
                        fallback = personPainter
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(48.dp), tint = com.sanibonani.save.ui.theme.Forest)
                    }
                }
            }
            
            SmallFloatingActionButton(
                onClick = { launcher.launch("image/*") },
                containerColor = com.sanibonani.save.ui.theme.Forest,
                contentColor = Color.White,
                modifier = Modifier.size(32.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Personal Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.sanibonani.save.ui.theme.Forest)
                
                InfoRow("Full Name", member?.fullName ?: "N/A")
                InfoRow("ID Number", member?.idNumber ?: "N/A")
                InfoRow("Phone", member?.phone ?: "N/A")
                InfoRow("Email", member?.email ?: "N/A")
                InfoRow("Joined", member?.joinedAt?.substringBefore("T") ?: "N/A")
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Membership Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.sanibonani.save.ui.theme.Forest)
                
                InfoRow("Group", group?.name ?: "N/A")
                InfoRow("Status", member?.status?.displayName ?: "N/A")
                
                val probationEnd = member?.probationEndAt?.substringBefore("T")
                if (probationEnd != null) {
                    InfoRow("Probation Ends", probationEnd)
                }
            }
        }
    }
}

@Composable
fun MemberLoansTab(
    loans: List<Loan>,
    repayments: List<LoanRepayment>,
    group: Group?,
    vm: MemberViewModel
) {
    var showRequestDialog by remember { mutableStateOf(false) }
    val activeLoan = loans.find { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID }

    Column(Modifier.fillMaxSize()) {
        Surface(
            color = com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Smart Loans", fontWeight = FontWeight.Bold, color = com.sanibonani.save.ui.theme.Forest)
                    Text(
                        if (activeLoan != null) "You have an active loan of ${formatZAR(activeLoan.amount)}"
                        else "Need a boost? Request a loan from your group.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (activeLoan == null) {
                    Button(
                        onClick = { showRequestDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = com.sanibonani.save.ui.theme.Forest),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Text("Request Loan", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loans.isEmpty()) {
                item {
                    EmptyState(
                        icon = "🏦",
                        title = "No loans yet",
                        description = "When you request and receive loans, they will appear here."
                    )
                }
            } else {
                items(loans) { loan ->
                    LoanItem(loan, repayments.filter { it.loanId == loan.id })
                }
            }
        }
    }

    if (showRequestDialog) {
        LoanRequestDialog(
            group = group,
            onDismiss = { showRequestDialog = false },
            onConfirm = { amount, months, purpose ->
                vm.requestLoan(amount, months, purpose)
                showRequestDialog = false
            }
        )
    }
}

@Composable
fun LoanItem(loan: Loan, repayments: List<LoanRepayment>) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(
                        when (loan.status) {
                            LoanStatus.ACTIVE, LoanStatus.PARTIALLY_PAID -> com.sanibonani.save.ui.theme.Forest.copy(0.1f)
                            LoanStatus.PENDING -> com.sanibonani.save.ui.theme.WarningAmber.copy(0.1f)
                            LoanStatus.COMPLETED -> SuccessGreen.copy(0.1f)
                            else -> MidGray.copy(0.1f)
                        },
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💸")
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatZAR(loan.amount), fontWeight = FontWeight.Bold)
                    Text(loan.purpose ?: "Personal Loan", style = MaterialTheme.typography.labelMedium, color = MidGray)
                }
                StatusChip(
                    status = when (loan.status) {
                        LoanStatus.ACTIVE, LoanStatus.PARTIALLY_PAID -> MemberStatus.ACTIVE
                        LoanStatus.PENDING -> MemberStatus.PROBATION
                        LoanStatus.COMPLETED -> MemberStatus.ACTIVE
                        else -> MemberStatus.SUSPENDED
                    },
                    label = loan.status.name
                )
            }

            if (loan.status == LoanStatus.ACTIVE || loan.status == LoanStatus.PARTIALLY_PAID) {
                Spacer(Modifier.height(16.dp))
                val progress = (loan.totalRepaid / loan.totalToRepay).toFloat().coerceIn(0f, 1f)
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Repayment Progress", style = MaterialTheme.typography.labelSmall, color = MidGray)
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = com.sanibonani.save.ui.theme.Forest,
                        trackColor = com.sanibonani.save.ui.theme.Forest.copy(0.1f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Remaining: ${formatZAR(loan.totalToRepay - loan.totalRepaid)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Monthly: ${formatZAR(loan.monthlyRepayment)}", style = MaterialTheme.typography.labelSmall, color = com.sanibonani.save.ui.theme.Forest)
                    }
                }
            }

            if (expanded && repayments.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = LightGray.copy(0.5f))
                Spacer(Modifier.height(8.dp))
                Text("Recent Repayments", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                repayments.take(5).forEach { repayment ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val date = (repayment.paidAt ?: repayment.createdAt ?: "").substringBefore("T")
                        Text(date, style = MaterialTheme.typography.labelSmall)
                        Text(formatZAR(repayment.amount), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun LoanRequestDialog(
    group: Group?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Int, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("3") }
    var purpose by remember { mutableStateOf("") }

    val interestRate = group?.loanInterestRate ?: 0.0
    val maxLoan = group?.loanMaxAmount ?: 5000.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request a Loan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your group allows loans up to ${formatZAR(maxLoan)} at ${interestRate}% interest.", style = MaterialTheme.typography.bodySmall)
                
                SanibonaniTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) amount = it },
                    label = "Amount (ZAR)",
                    placeholder = "e.g. 1000",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                SanibonaniDropdown(
                    label = "Repayment Period",
                    options = listOf(1, 2, 3, 6, 12),
                    selectedOption = months.toInt(),
                    onOptionSelected = { months = it.toString() },
                    optionToString = { "$it Months" }
                )

                SanibonaniTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = "Purpose of Loan",
                    placeholder = "e.g. School fees, Emergency"
                )

                val amtVal = amount.toDoubleOrNull() ?: 0.0
                if (amtVal > 0) {
                    val interest = amtVal * (interestRate / 100.0) * (months.toInt() / 12.0)
                    val total = amtVal + interest
                    val monthly = total / months.toInt()

                    Card(colors = CardDefaults.cardColors(containerColor = Cream)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Repayment:", style = MaterialTheme.typography.labelMedium)
                                Text(formatZAR(total), fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Monthly Installment:", style = MaterialTheme.typography.labelMedium)
                                Text(formatZAR(monthly), fontWeight = FontWeight.Bold, color = com.sanibonani.save.ui.theme.Forest)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            SanibonaniButton(
                text = "Submit Request",
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0 && amt <= maxLoan && purpose.isNotBlank()) {
                        onConfirm(amt, months.toInt(), purpose)
                    }
                },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0 && (amount.toDoubleOrNull() ?: 0.0) <= maxLoan && purpose.isNotBlank()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MidGray) }
        }
    )
}

@Composable
fun MemberNotificationsTab(notifications: List<AppNotification>) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (notifications.isEmpty()) {
            item { EmptyState(icon = "🔔", title = "All caught up!", description = "No new notifications for you.") }
        } else {
            items(notifications) { notif ->
                NotificationItem(notif)
            }
        }
    }
}

@Composable
fun MemberMessagesTab(messages: List<AppNotification>, vm: MemberViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()

    var showInquiryDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Surface(
            color = com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.05f),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Need help?", fontWeight = FontWeight.Bold, color = com.sanibonani.save.ui.theme.Forest)
                    Text("Send a message to your group admin.", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { showInquiryDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = com.sanibonani.save.ui.theme.Forest),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    // Use correct reference for AutoMirrored Send icon
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Contact Admin", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item { EmptyState(icon = "💬", title = "No messages", description = "Direct messages from your group admin will appear here.") }
            } else {
                items(messages) { msg ->
                    MessageItem(msg)
                }
            }
        }
    }

    if (showInquiryDialog) {
        AlertDialog(
            onDismissRequest = { showInquiryDialog = false },
            title = { Text("Message Group Admin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SanibonaniTextField(
                        value = state.messageText,
                        onValueChange = { vm.updateMessageText(it) },
                        label = "Your Message",
                        placeholder = "Type your question or request..."
                    )
                    InfoBox(
                        message = "Your message will be sent to the current group admin.",
                        type = InfoType.INFO
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.sendMessageToAdmin()
                        showInquiryDialog = false
                    },
                    enabled = !state.isSendingMessage
                ) {
                    if (state.isSendingMessage) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = com.sanibonani.save.ui.theme.Forest
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Send", color = com.sanibonani.save.ui.theme.Forest)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInquiryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }


    if (state.messageSentSuccess) {
        AlertDialog(
            onDismissRequest = { vm.dismissMessageSuccess() },
            title = { Text("Message Sent") },
            text = { Text("Your message has been sent to the group admin.") },
            confirmButton = {
                TextButton(onClick = { vm.dismissMessageSuccess() }) {
                    Text("OK", color = com.sanibonani.save.ui.theme.Forest)
                }
            }
        )
    }
}

@Composable
fun ContributionItem(contribution: Contribution) {
    val formattedDate = remember(contribution.createdAt) {
        try {
            val dt = LocalDateTime.parse(contribution.createdAt)
            dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
        } catch (_: Exception) {
            contribution.createdAt?.take(16)?.replace("T", " ") ?: "Just now"
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(if (contribution.type == "joining_fee") "🎟️" else "💰")
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (contribution.type == "joining_fee") "Joining Fee" else "Monthly Contribution",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MidGray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatZAR(contribution.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = com.sanibonani.save.ui.theme.Forest
                )
                StatusChip(
                    status = if (contribution.status == ContributionStatus.PAID) MemberStatus.ACTIVE else MemberStatus.SUSPENDED,
                    label = contribution.status.name
                )
            }
        }
    }
}


@Composable
fun NotificationItem(notification: AppNotification) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (notification.triggerEvent == NotifEvent.ACTUARIAL_ALERT || notification.triggerEvent == NotifEvent.GROUP_SUSPENDED) Color.Red else com.sanibonani.save.ui.theme.WarningAmber).align(Alignment.CenterVertically))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(notification.message, style = MaterialTheme.typography.bodyMedium)
                Text(notification.createdAt?.let { 
                    try {
                        val dt = LocalDateTime.parse(it)
                        dt.format(DateTimeFormatter.ofPattern("dd MMM, HH:mm"))
                    } catch (_: Exception) { "Just now" }
                } ?: "Just now", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun MessageItem(message: AppNotification) {
    val isAdminMessage = message.triggerEvent == NotifEvent.CUSTOM
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAdminMessage) Color.White else com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.05f)
        ),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(
                        if (isAdminMessage) com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f) else Color.White
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isAdminMessage) Icons.Default.AdminPanelSettings else Icons.Default.Person, 
                        null, 
                        tint = com.sanibonani.save.ui.theme.Forest, 
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isAdminMessage) "Admin Broadcast" else "Your Inquiry", 
                        style = MaterialTheme.typography.labelMedium, 
                        fontWeight = FontWeight.Bold, 
                        color = com.sanibonani.save.ui.theme.Forest
                    )
                    Text(message.createdAt?.let { 
                        try {
                            val dt = LocalDateTime.parse(it)
                            dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                        } catch (_: Exception) { "Just now" }
                    } ?: "Just now", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = message.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Charcoal
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterMemberScreen(
    groupId: String,
    onMemberRegistered: (Double) -> Unit,
    onBack: () -> Unit,
    vm: MemberViewModel = hiltViewModel()
) {
    val state by vm.registerState.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(groupId) {
        vm.initializeRegistration(groupId)
    }

    LaunchedEffect(state.success) {
        if (state.success) {
            ToastUtils.showSuccess(context, "Successfully joined group!")
            onMemberRegistered(state.joiningFee)
        }
    }

    // Only show error toast when error changes (not on every recomposition)
    val currentError = state.error
    LaunchedEffect(currentError) {
        currentError?.let {
            ToastUtils.showError(context, it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Group", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Cream
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Complete your profile to join the group and start saving.", color = Color.Gray)
            
            Spacer(Modifier.height(32.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SanibonaniTextField(
                    value = state.fullName,
                    onValueChange = { vm.onFieldChange("fullName", it) },
                    label = "Full Name",
                    leadingIcon = { Icon(Icons.Default.Person, null) }
                )
                
                SanibonaniTextField(
                    value = state.idNumber,
                    onValueChange = { if (it.length <= 13) vm.onFieldChange("idNumber", it) },
                    label = "ID Number",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = IDNumberTransformation()
                )

                SanibonaniTextField(
                    value = state.phone,
                    onValueChange = { if (it.length <= 10) vm.onFieldChange("phone", it) },
                    label = "WhatsApp Number",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    visualTransformation = PhoneNumberTransformation()
                )

                SanibonaniTextField(
                    value = state.email,
                    onValueChange = { vm.onFieldChange("email", it) },
                    label = "Email Address",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Text("Address Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                
                AutoCompleteTextField(
                    value = state.street,
                    onValueChange = { vm.onFieldChange("street", it) },
                    label = "Street Address",
                    suggestions = state.addressSuggestions,
                    onSuggestionClick = { vm.onAddressSelected(it) },
                    isLoading = state.isSearchingAddress,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SanibonaniTextField(
                        value = state.suburb,
                        onValueChange = { vm.onFieldChange("suburb", it) },
                        label = "Suburb",
                        modifier = Modifier.weight(1f)
                    )
                    SanibonaniTextField(
                        value = state.city,
                        onValueChange = { vm.onFieldChange("city", it) },
                        label = "City",
                        modifier = Modifier.weight(1f)
                    )
                }
                
                var showProvincePicker by remember { mutableStateOf(false) }
                OutlinedCard(
                    onClick = { showProvincePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.Gray)
                        Spacer(Modifier.width(12.dp))
                        Text(state.province.ifEmpty { "Select Province" }, color = if (state.province.isEmpty()) Color.Gray else Color.Black)
                    }
                }

                if (showProvincePicker) {
                    SanibonaniDropdown(
                        label = "Province",
                        options = SA_PROVINCES,
                        selectedOption = state.province,
                        onOptionSelected = { 
                            vm.onFieldChange("province", it)
                            showProvincePicker = false
                        },
                        optionToString = { it }
                    )
                }

                Text("Notification Preference", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotificationPref.entries.forEach { pref ->
                        FilterChip(
                            selected = state.notificationPref == pref,
                            onClick = { vm.setNotificationPref(pref) },
                            label = { Text(pref.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                if (state.error != null) {
                    InfoBox(state.error!!, InfoType.ERROR)
                    Spacer(Modifier.height(16.dp))
                }

                SanibonaniButton(
                    text = "Register & Continue",
                    onClick = { vm.submit(groupId) },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = state.isSubmitting
                )
                
                InfoBox(
                    message = "By joining, you agree to the group's constitution and the SanibonaniSave platform terms.",
                    type = InfoType.INFO
                )
            }
        }
    }
}

private fun getFileNameFromUri(contentResolver: android.content.ContentResolver, uri: android.net.Uri): String {
    var name = "document"
    val cursor = contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }
    }
    return name
}

private data class PreparedProfileUpload(
    val bytes: ByteArray,
    val fileName: String
)

private fun handleProfilePhotoSelection(
    context: android.content.Context,
    vm: MemberViewModel,
    uri: android.net.Uri?
) {
    if (uri == null) return

    runCatching {
        prepareProfileUpload(context.contentResolver, uri)
    }.onSuccess { prepared ->
        vm.uploadDocument(0, prepared.bytes, prepared.fileName, "Profile Photo")
    }.onFailure { error ->
        ToastUtils.showError(context, error.message ?: "Please select a valid image file.")
    }
}

private fun prepareProfileUpload(
    contentResolver: android.content.ContentResolver,
    uri: android.net.Uri
): PreparedProfileUpload {
    val mimeType = contentResolver.getType(uri).orEmpty().lowercase()
    if (!mimeType.startsWith("image/")) {
        throw IllegalArgumentException("Only image files are allowed for profile photo uploads.")
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IllegalArgumentException("Could not read image. Please choose another file.")
    }

    val targetMaxPx = 512
    var inSampleSize = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    while ((largest / inSampleSize) > targetMaxPx * 2) {
        inSampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    val decoded = contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOptions)
    } ?: throw IllegalArgumentException("Could not decode image. Please choose another file.")

    val resized = scaleBitmapToMax(decoded, targetMaxPx)
    if (resized !== decoded) decoded.recycle()

    val output = ByteArrayOutputStream()
    var quality = 85
    resized.compress(Bitmap.CompressFormat.JPEG, quality, output)
    while (output.size() > FileUploadLimits.MAX_FILE_SIZE_BYTES && quality > 45) {
        output.reset()
        quality -= 10
        resized.compress(Bitmap.CompressFormat.JPEG, quality, output)
    }
    resized.recycle()

    if (output.size() > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
        throw IllegalArgumentException("Image is too large after compression. Please choose a smaller photo.")
    }

    return PreparedProfileUpload(
        bytes = output.toByteArray(),
        fileName = "profile_photo_${System.currentTimeMillis()}.jpg"
    )
}

private fun scaleBitmapToMax(bitmap: Bitmap, maxPx: Int): Bitmap {
    val largest = maxOf(bitmap.width, bitmap.height)
    if (largest <= maxPx) return bitmap

    val scale = maxPx.toFloat() / largest.toFloat()
    val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

