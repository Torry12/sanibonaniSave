package com.sanibonani.save.ui.screens.member

import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.sanibonani.save.domain.config.FileUploadLimits
import com.sanibonani.save.data.utils.PaymentCalculation
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.viewmodel.MemberViewModel
import com.sanibonani.save.viewmodel.state.MemberEvent
import com.sanibonani.save.viewmodel.state.MemberUiState
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.io.ByteArrayOutputStream
import android.content.ContentResolver
import android.net.Uri
import android.content.Context

private fun isBurialSocietyLike(group: Group?): Boolean {
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
    val snackbarHostState = remember { SnackbarHostState() }
    val tabs  = listOf("Overview", "Transactions", "Insights", "Loans", "Beneficiaries", "Documents", "Messages", "Notifications", "Profile")

    // In-app file viewing state
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
                    title = "Member Portal",
                    subtitle = state.member?.fullName ?: "Loading...",
                    notifCount = state.notifications.size,
                    onProfileClick = { vm.selectTab(7) },
                    onNotifClick = { vm.selectTab(6) },
                    profileImageUrl = state.member?.profilePhotoUrl,
                    profileImageVersion = state.profileImageVersion,
                    onLogoutClick = onLogout,
                    onSwitchPortal = onNavigateAdmin,
                    isPortalSwitchable = true
                )
                
                if (state.memberships.size > 1) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        OutlinedButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Forest.copy(0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.SwapHoriz, null, tint = Forest, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Switch Group: ${state.group?.name ?: "..."}", color = Forest, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, null, tint = Forest)
                                }
                                val currentSync = state.cacheLastSyncByGroup[state.currentGroupId]
                                Text(
                                    text = formatRelativeSyncTime(currentSync),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MidGray
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
                                            tint = if (membership.groupId == state.currentGroupId) Forest else LightGray,
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
                        color = Forest,
                        trackColor = Forest.copy(alpha = 0.2f)
                    )
                }
            }
        },
        containerColor = Cream
    ) { padding ->
        Column(Modifier.fillMaxSize().background(Cream).padding(padding)) {
            if (state.isLoading && state.member == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Forest) }
            } else if (state.member == null && state.error != null) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("⚠️", fontSize = 48.sp)
                    Text("Could not load membership", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    InfoBox(state.error!!, InfoType.ERROR)
                    Button(onClick = { vm.loadMemberData() }, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Retry") }
                }
            } else {
                val member = state.member
                val group = state.group
                val calculation = state.calculation

                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val totalAmount = if (state.contributions.isNotEmpty()) {
                        state.contributions.filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }.sumOf { it.amount }
                    } else {
                        member?.totalPaid ?: 0.0
                    }
                    item { StatCard(icon = "💰", label = "Total Contributed", value = formatZAR(totalAmount), subtitle = "${member?.totalContributions ?: 0} payments", accentColor = Forest) }

                    val memberMonthlyAmount = if (member != null && group != null) PaymentCalculator.calculateMonthlyContribution(group, member) else group?.monthlyContribution ?: 0.0
                    val nextAmount = if ((calculation?.totalDueNow ?: 0.0) > 0.0) calculation!!.totalDueNow else memberMonthlyAmount
                    item { StatCard(icon = "📅", label = "Next Payment", value = formatZAR(nextAmount), subtitle = "Due ${calculation?.nextDueDate ?: "N/A"}", accentColor = WarningAmber) }

                    val rate = if (calculation != null) {
                        val expected = (calculation.periodsAhead) + (member?.totalContributions ?: 0)
                        if (expected > 0) ((member?.totalContributions ?: 0).toDouble() / expected.toDouble() * 100.0) else 100.0
                    } else 100.0
                    item { StatCard(icon = "✅", label = "Payment Rate", value = formatPct(rate.coerceIn(0.0, 100.0)), subtitle = if (calculation?.isOverdue == true) "OVERDUE" else "Up to date", accentColor = if (calculation?.isOverdue == true) ErrorRed else SuccessGreen) }
                }

                ScrollableTabRow(selectedTabIndex = state.selectedTab, containerColor = Color.White, edgePadding = 0.dp) {
                    tabs.forEachIndexed { i, t -> Tab(selected = state.selectedTab == i, onClick = { vm.selectTab(i) }, text = { Text(t, style = MaterialTheme.typography.labelSmall) }) }
                }

                Box(Modifier.weight(1f)) {
                    when (state.selectedTab) {
                        0 -> MemberOverviewTab(member, group, state.userRole, state.contributions, calculation, state.profileImageVersion, onNavigatePayment, onNavigateAdmin, vm)
                        1 -> MemberTransactionsTab(state.contributions, state.isExporting, { vm.exportMyStatement() }, { vm.downloadPdfStatement() })
                        2 -> MemberInsightsTab(state)
                        3 -> MemberLoansTab(state.loans, state.loanRepayments, group, vm, onFileAction = { url, name, headers -> showFileActionDialog = Triple(url, name, headers) })
                        4 -> MemberBeneficiariesTab(state.beneficiaries, group, vm, onFileAction = { url, name, headers -> showFileActionDialog = Triple(url, name, headers) })
                        5 -> MemberDocumentsTab(member, group, vm, onFileAction = { url, name, headers -> showFileActionDialog = Triple(url, name, headers) })
                        6 -> MemberMessagesTab(state.messages, vm)
                        7 -> MemberNotificationsTab(state.notifications)
                        8 -> MemberProfileTab(member, group, vm, state.profileImageVersion)
                    }
                }
            }
        }
    }

    // ── File Handling Dialogs ───────────────────────────────────────────
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
                com.sanibonani.save.domain.utils.FileDownloader.downloadFile(context, url, name, mimeType, headers)
            },
            fileName = name
        )
    }

    viewFileData?.let { (url, name, headers) ->
        FileViewerDialog(
            url = url,
            fileName = name,
            headers = headers,
            onDismiss = { viewFileData = null }
        )
    }
}

@Composable
fun MemberOverviewTab(member: Member?, group: Group?, userRole: UserRole, contributions: List<Contribution>, calculation: PaymentCalculation?, profileImageVersion: Long, onPay: (String, String, String) -> Unit, onNavigateAdmin: () -> Unit, vm: MemberViewModel) {
    val context = LocalContext.current
    val profileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { handleProfilePhotoSelection(context, vm, it) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (userRole == UserRole.GROUP_ADMIN) {
            item { ModernNavigationLink(title = "Admin Dashboard", subtitle = "Manage members and settings", icon = Icons.Default.AdminPanelSettings, onClick = onNavigateAdmin, accentColor = Gold, containerColor = Gold.copy(alpha = 0.05f)) }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ForestLight.copy(alpha = 0.1f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val isJoiningFeePaid = (member?.totalContributions ?: 0) > 0
                            val isJoiningFeeRequired = member?.status == MemberStatus.PENDING_PAYMENT || !isJoiningFeePaid
                            val memberMonthlyContribution = if (member != null && group != null) PaymentCalculator.calculateMonthlyContribution(group, member) else group?.monthlyContribution ?: 0.0
                            val amount = if (isJoiningFeeRequired) group?.joiningFee ?: 0.0 else (if ((calculation?.totalDueNow ?: 0.0) > 0.0) calculation!!.totalDueNow else memberMonthlyContribution)
                            Text(if (isJoiningFeeRequired) "Joining Fee Required" else "Monthly Contribution Due", fontWeight = FontWeight.Bold)
                            if (calculation?.isOverdue == true) Text("YOUR ACCOUNT IS OVERDUE", color = ErrorRed, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { onPay(if (isJoiningFeeRequired) "joining_fee" else "contribution", amount.toString(), member?.groupId ?: "") }, colors = ButtonDefaults.buttonColors(containerColor = if (calculation?.isOverdue == true) ErrorRed else Forest), modifier = Modifier.fillMaxWidth()) {
                                Text(if (isJoiningFeeRequired) "Pay Joining Fee (${formatZAR(amount)})" else "Make Contribution (${formatZAR(amount)})", fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.clickable { profileLauncher.launch("image/*") }, contentAlignment = Alignment.BottomEnd) {
                            AsyncImage(model = ImageRequest.Builder(context).data(member?.profilePhotoUrl).memoryCacheKey("${member?.profilePhotoUrl}-$profileImageVersion").crossfade(true).build(), contentDescription = "Photo", modifier = Modifier.size(64.dp).clip(CircleShape).border(2.dp, Forest, CircleShape), contentScale = ContentScale.Crop)
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp).background(Forest, CircleShape).padding(4.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("Recent Activity")
            if (contributions.isEmpty()) EmptyState("💸", "No transactions", "History will appear here.")
            else {
                contributions.take(3).forEach { ContributionItem(it) }
            }
        }
    }
}

@Composable
fun MemberTransactionsTab(contributions: List<Contribution>, isExporting: Boolean, onExportCsv: () -> Unit, onDownloadPdf: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (contributions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportCsv, modifier = Modifier.weight(1f), enabled = !isExporting) { Icon(Icons.AutoMirrored.Filled.List, null, modifier = Modifier.size(18.dp)); Text("Export CSV") }
                OutlinedButton(onClick = onDownloadPdf, modifier = Modifier.weight(1f), enabled = !isExporting) { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp)); Text("Download PDF") }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (contributions.isEmpty()) item { EmptyState("💸", "No transactions", "No payments yet.") }
            else items(contributions) { ContributionItem(it) }
        }
        if (isExporting) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Forest)
    }
}

@Composable
fun MemberBeneficiariesTab(
    beneficiaries: List<Beneficiary>, 
    group: Group?, 
    vm: MemberViewModel,
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isBurial = isBurialSocietyLike(group)
    val limit = group?.maxBeneficiaries ?: 0
    val count = beneficiaries.size
    Column(Modifier.fillMaxSize()) {
        Surface(color = Forest.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isBurial) "Beneficiary Allowance" else "Dependents", fontWeight = FontWeight.Bold, color = Forest)
                    Text(if (isBurial) (if (limit <= 0) "$count added" else "$count of $limit slots used") else "Add covered people.", style = MaterialTheme.typography.bodySmall)
                }
                if (!isBurial || limit <= 0 || count < limit) {
                    Button(onClick = { showAddDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text("Add New") }
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (beneficiaries.isEmpty()) item { EmptyState("👥", "No beneficiaries", "Add dependents here.") }
            else items(beneficiaries) { b ->
                var showEdit by remember { mutableStateOf(false) }
                var showClaim by remember { mutableStateOf(false) }
                BeneficiaryItem(
                    beneficiary = b,
                    isBurial = isBurial,
                    onEdit = { showEdit = true },
                    onDelete = { b.id?.let { vm.deleteBeneficiary(it) } },
                    onUpload = { bytes, name -> b.id?.let { vm.uploadBeneficiaryDocument(it, bytes, name) } },
                    onClaim = { showClaim = true },
                    onDownload = { url, label -> 
                        val headers = vm.getDownloadParams(url)
                        val ext = url.substringAfterLast(".", "pdf").substringBefore("?")
                        val fileName = "Beneficiary_ID_${label.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                        onFileAction(url, fileName, headers)
                    }
                )
                if (showEdit) AddBeneficiaryDialog(b, { showEdit = false }, { n, id, r, d, i -> b.id?.let { vm.updateBeneficiary(it, n, id, r, d, i) }; showEdit = false })
                if (showClaim) ClaimBeneficiaryDialog(b, { showClaim = false }, { c, d, a, bk, ac, br, h, nt -> vm.submitBeneficiaryClaim(b, c, d, a, bk, ac, br, h, nt); showClaim = false })
            }
        }
    }
    if (showAddDialog) AddBeneficiaryDialog(null, { showAddDialog = false }, { n, id, r, d, i -> vm.addBeneficiary(n, id, r, d, i); showAddDialog = false })
}

@Composable
fun BeneficiaryItem(
    beneficiary: Beneficiary, 
    isBurial: Boolean, 
    onEdit: () -> Unit, 
    onDelete: () -> Unit, 
    onUpload: (ByteArray, String) -> Unit, 
    onClaim: () -> Unit, 
    onDownload: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
            if (bytes != null) onUpload(bytes, getFileNameFromUri(context.contentResolver, it))
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Cream), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Forest) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(beneficiary.fullName, fontWeight = FontWeight.Bold)
                    Text("${beneficiary.relationship} • ${beneficiary.idNumber}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = Forest, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(8.dp)); HorizontalDivider(color = LightGray.copy(0.5f)); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(if (beneficiary.documentUrl != null) Icons.Default.Check else Icons.Default.UploadFile, null, tint = if (beneficiary.documentUrl != null) Forest else MidGray, modifier = Modifier.size(18.dp))
                    Text(if (beneficiary.documentUrl != null) "ID Document Verified" else "ID Required", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp))
                    
                    val dlUrl = beneficiary.documentUrl
                    if (dlUrl != null && onDownload != null) {
                        IconButton(onClick = { 
                            onDownload(dlUrl, beneficiary.fullName)
                        }, modifier = Modifier.size(24.dp).padding(start = 8.dp)) {
                            Icon(Icons.Default.PictureAsPdf, "View ID", tint = Forest, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (isBurial) TextButton(onClick = onClaim, enabled = beneficiary.documentUrl != null) { Text("MAKE CLAIM", color = if (beneficiary.documentUrl != null) Color.Red else Color.LightGray, fontWeight = FontWeight.Bold) }
                else OutlinedButton(onClick = { launcher.launch("*/*") }) { Text(if (beneficiary.documentUrl != null) "Replace" else "Upload") }
            }
        }
    }
}

@Composable
fun ClaimBeneficiaryDialog(beneficiary: Beneficiary, onDismiss: () -> Unit, onConfirm: (String, String, Double, String, String, String, String, String?) -> Unit) {
    var cause by remember { mutableStateOf("") }; var date by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("5000.0") }
    var bk by remember { mutableStateOf("") }; var ac by remember { mutableStateOf("") }; var br by remember { mutableStateOf("") }; var h by remember { mutableStateOf("") }; var nt by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Submit Payout Claim", fontWeight = FontWeight.Bold) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Beneficiary: ${beneficiary.fullName}", fontWeight = FontWeight.Bold)
            SanibonaniTextField(cause, { cause = it }, "Cause of Death")
            SanibonaniDatePickerField(
                label = "Date of Death",
                value = date,
                onValueChange = { date = it }
            )
            SanibonaniTextField(amount, { amount = it }, "Claim Amount (R)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("Payout Banking Details", fontWeight = FontWeight.Bold)
            SanibonaniTextField(bk, { bk = it }, "Bank Name")
            SanibonaniTextField(ac, { ac = it }, "Account Number")
            SanibonaniTextField(br, { br = it }, "Branch Code")
            SanibonaniTextField(h, { h = it }, "Account Holder")
            SanibonaniTextField(nt, { nt = it }, "Notes (Optional)")
        }
    }, confirmButton = { Button(onClick = { onConfirm(cause, date, amount.toDoubleOrNull() ?: 0.0, bk, ac, br, h, nt.takeIf { it.isNotBlank() }) }, enabled = bk.isNotBlank() && ac.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Submit Claim") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun AddBeneficiaryDialog(beneficiary: Beneficiary?, onDismiss: () -> Unit, onConfirm: (String, String, String, String, Boolean) -> Unit) {
    var n by remember { mutableStateOf(beneficiary?.fullName ?: "") }; var id by remember { mutableStateOf(beneficiary?.idNumber ?: "") }
    var r by remember { mutableStateOf(beneficiary?.relationship ?: "") }; var d by remember { mutableStateOf(beneficiary?.dateOfBirth ?: "") }; var i by remember { mutableStateOf(beneficiary?.isOver65 ?: false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (beneficiary == null) "Add Beneficiary" else "Update Beneficiary") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SanibonaniTextField(n, { n = it }, "Full Name")
            SanibonaniTextField(id, { id = it }, "ID Number", visualTransformation = IDNumberTransformation())
            SanibonaniTextField(r, { r = it }, "Relationship")
            SanibonaniDatePickerField(
                label = "Date of Birth",
                value = d,
                onValueChange = { d = it }
            )
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(i, { i = it }); Text("Over 65 Years Old") }
        }
    }, confirmButton = { SanibonaniButton(text = if (beneficiary == null) "Add" else "Update", onClick = { if (n.isNotBlank()) onConfirm(n, id, r, d, i) }, modifier = Modifier.fillMaxWidth()) }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}


@Composable
fun MemberDocumentsTab(
    member: Member?, 
    group: Group?, 
    vm: MemberViewModel,
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()
    var activeIdx by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) vm.uploadDocument(activeIdx, bytes, getFileNameFromUri(context.contentResolver, it), "Document")
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        SectionHeader("Your Documents")
        
        // ── Standard Indexed Documents ──────────────────────────────────────────
        val docs = listOf(
            "Identity Document (ID)" to member?.document1Url,
            "Proof of Residence" to member?.document2Url,
            "Beneficiary Documents" to member?.document3Url,
            "Marriage Certificate" to member?.document4Url,
            "Signed Constitution" to member?.document5Url
        )
        docs.forEachIndexed { i, (name, url) ->
            DocumentUploadCard(
                name = name,
                isUploaded = !url.isNullOrBlank(),
                status = when(i+1) {
                    1 -> member?.document1Status ?: DocumentStatus.PENDING
                    2 -> member?.document2Status ?: DocumentStatus.PENDING
                    3 -> member?.document3Status ?: DocumentStatus.PENDING
                    4 -> member?.document4Status ?: DocumentStatus.PENDING
                    5 -> member?.document5Status ?: DocumentStatus.PENDING
                    else -> DocumentStatus.PENDING
                },
                onUpload = { activeIdx = i + 1; launcher.launch("*/*") },
                onDownload = if (!url.isNullOrBlank()) {
                    {
                        val h = vm.getDownloadParams(url ?: "")
                        val ext = (url ?: "").substringAfterLast(".", "pdf").substringBefore("?")
                        val fileName = "${name.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                        onFileAction(url ?: "", fileName, h)
                    }
                } else null
            )
        }

        // ── Relational Documents (Group Specific) ──────────────────────────────
        if (state.documents.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Group Specific Documents")
            state.documents.forEach { doc ->
                val relationalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    uri?.let {
                        val bytes = context.contentResolver.openInputStream(it)?.readBytes()
                        if (bytes != null) vm.uploadRelationalDocument(doc.label, bytes, getFileNameFromUri(context.contentResolver, it))
                    }
                }

                DocumentUploadCard(
                    name = doc.label,
                    isUploaded = true,
                    status = doc.status,
                    onUpload = { relationalLauncher.launch("*/*") },
                    onDownload = {
                        val h = vm.getDownloadParams(doc.documentUrl)
                        val ext = doc.documentUrl.substringAfterLast(".", "pdf").substringBefore("?")
                        val fileName = "${doc.label.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                        onFileAction(doc.documentUrl, fileName, h)
                    }
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        ModernNavigationLink(
            title = "Group Constitution",
            subtitle = "Read the latest group rules and policy",
            icon = Icons.Default.Description,
            onClick = { 
                val officialUrl = group?.constitutionUrl
                if (!officialUrl.isNullOrBlank()) {
                    val h = vm.getDownloadParams(officialUrl)
                    val fileName = "Group_Constitution_${group?.name?.replace(" ", "_")}.pdf"
                    onFileAction(officialUrl, fileName, h)
                } else {
                    vm.downloadGroupConstitution()
                }
            },
            accentColor = Forest
        )
        
        Spacer(Modifier.height(24.dp))
        InfoBox("Documents are encrypted and only accessible by admins.", InfoType.INFO)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun MemberProfileTab(member: Member?, group: Group?, vm: MemberViewModel, profileImageVersion: Long) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { handleProfilePhotoSelection(context, vm, it) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(Modifier.size(100.dp), shape = CircleShape, color = Forest.copy(alpha = 0.1f), border = BorderStroke(2.dp, Forest)) {
                if (!member?.profilePhotoUrl.isNullOrBlank()) AsyncImage(model = ImageRequest.Builder(context).data(member?.profilePhotoUrl).memoryCacheKey("${member?.profilePhotoUrl}-$profileImageVersion").crossfade(true).build(), contentDescription = "Photo", contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, modifier = Modifier.size(48.dp).align(Alignment.Center), tint = Forest)
            }
            SmallFloatingActionButton(onClick = { launcher.launch("image/*") }, containerColor = Forest, contentColor = Color.White, modifier = Modifier.size(32.dp), shape = CircleShape) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(24.dp))
        DetailSection("Account Information") { 
            DetailRow("Full Name", member?.fullName ?: "N/A")
            DetailRow("Email", member?.email ?: "N/A")
            DetailRow("Phone", member?.phone ?: "N/A") 
        }
        DetailSection("Membership") { 
            DetailRow("Group", group?.name ?: "N/A")
            DetailRow("Status", member?.status?.displayName ?: "N/A")
            DetailRow("Member Since", member?.joinedAt?.substringBefore("T") ?: "N/A") 
        }

        DetailSection("Security") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Biometric Login", fontWeight = FontWeight.Bold)
                    Text("Use fingerprint or face to sign in faster.", style = MaterialTheme.typography.labelSmall, color = MidGray)
                }
                Switch(
                    checked = state.biometricEnabled,
                    onCheckedChange = { vm.toggleBiometric(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Forest, checkedTrackColor = Forest.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
fun MemberLoansTab(
    loans: List<Loan>, 
    repayments: List<LoanRepayment>, 
    group: Group?, 
    vm: MemberViewModel,
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    var showRequest by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Surface(color = Forest.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Emergency Loans", fontWeight = FontWeight.Bold, color = Forest); Text("Apply for a loan based on group rules.", style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { showRequest = true }, colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Apply") }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loans.isEmpty()) item { EmptyState("🏦", "No loans", "You haven't requested any loans yet.") }
            else items(loans) { loan -> 
                LoanItem(
                    loan = loan, 
                    repayments = repayments.filter { r -> r.loanId == loan.id },
                    onDownloadContract = { 
                        val url = loan.contractUrl
                        if (!url.isNullOrBlank()) {
                            val headers = vm.getDownloadParams(url)
                            val fileName = "Loan_Agreement_${loan.id?.take(5)}_${System.currentTimeMillis()}.pdf"
                            onFileAction(url, fileName, headers)
                        }
                    }
                ) 
            }
        }
    }
    if (showRequest) LoanRequestDialog(group, { showRequest = false }, { a, m, p -> vm.requestLoan(a, m, p); showRequest = false })
}

@Composable
fun LoanItem(loan: Loan, repayments: List<LoanRepayment>, onDownloadContract: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("R${loan.amount} Loan", fontWeight = FontWeight.Bold)
                    Text("Requested on ${loan.createdAt?.substringBefore("T") ?: "N/A"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!loan.contractUrl.isNullOrBlank() && onDownloadContract != null) {
                        val context = LocalContext.current
                        IconButton(onClick = onDownloadContract) {
                            Icon(Icons.Default.Description, "Download Agreement", tint = Forest)
                        }
                    }
                    StatusChip(status = loan.status.toMemberStatus(), label = loan.status.displayName)
                }
            }
            Spacer(Modifier.height(12.dp))
            DetailRow("Total Repayable", formatZAR(loan.totalToRepay))
            DetailRow("Remaining", formatZAR(loan.balanceRemaining))
            if (loan.status == LoanStatus.ACTIVE || loan.status == LoanStatus.PARTIALLY_PAID) {
                Spacer(Modifier.height(12.dp))
                val progress = if (loan.totalToRepay > 0) (loan.totalRepaid / loan.totalToRepay).toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progress }, 
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), 
                    color = Forest,
                    trackColor = Forest.copy(alpha = 0.1f)
                )
                Text("${(progress * 100).toInt()}% Repaid", style = MaterialTheme.typography.labelSmall, color = MidGray, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun LoanStatus.toMemberStatus(): MemberStatus = when(this) {
    LoanStatus.ACTIVE, LoanStatus.COMPLETED, LoanStatus.PARTIALLY_PAID, LoanStatus.APPROVED -> MemberStatus.ACTIVE
    LoanStatus.PENDING -> MemberStatus.PENDING_PAYMENT
    LoanStatus.REJECTED, LoanStatus.OVERDUE, LoanStatus.CANCELLED -> MemberStatus.SUSPENDED
}

private fun ContributionStatus.toMemberStatus(): MemberStatus = when(this) {
    ContributionStatus.PAID, ContributionStatus.PARTIAL -> MemberStatus.ACTIVE
    ContributionStatus.DUE -> MemberStatus.PENDING_PAYMENT
    ContributionStatus.OVERDUE -> MemberStatus.SUSPENDED
}

@Composable
fun LoanRequestDialog(group: Group?, onDismiss: () -> Unit, onConfirm: (Double, Int, String) -> Unit) {
    var a by remember { mutableStateOf("") }; var m by remember { mutableStateOf("3") }; var p by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Request Loan") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SanibonaniTextField(a, { a = it }, "Amount (R)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            SanibonaniTextField(m, { m = it }, "Months to Repay", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            SanibonaniTextField(p, { p = it }, "Purpose of Loan")
            InfoBox("Interest Rate: ${group?.loanInterestRate ?: 0}%", InfoType.INFO)
        }
    }, confirmButton = { Button(onClick = { onConfirm(a.toDoubleOrNull() ?: 0.0, m.toIntOrNull() ?: 3, p) }, enabled = a.isNotBlank()) { Text("Submit") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun MemberNotificationsTab(notifications: List<AppNotification>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (notifications.isEmpty()) item { EmptyState("🔔", "No notifications", "You're all caught up!") }
        else items(notifications) { NotificationItem(it) }
    }
}

@Composable
fun NotificationItem(n: AppNotification) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(Forest.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp), tint = Forest) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(n.message, style = MaterialTheme.typography.bodyMedium); Text(n.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
        }
    }
}

@Composable
fun MemberMessagesTab(messages: List<AppNotification>, vm: MemberViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionHeader("Messages from Admin")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) item { EmptyState("✉️", "No messages", "Admins haven't sent any messages yet.") }
            else items(messages) { MessageItem(it) }
        }
    }
}

@Composable
fun MessageItem(m: AppNotification) {
    Card(colors = CardDefaults.cardColors(containerColor = Forest.copy(0.05f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(m.message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(m.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun ContributionItem(c: Contribution) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Forest.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Text(if (c.type == "joining_fee") "🎟️" else "💰") }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(if (c.type == "joining_fee") "Joining Fee" else "Monthly Contribution", fontWeight = FontWeight.Bold)
                Text(c.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatZAR(c.amount), fontWeight = FontWeight.ExtraBold, color = Forest)
                StatusChip(status = c.status.toMemberStatus(), label = c.status.name)
            }
        }
    }
}

@Composable
fun DetailSection(title: String, content: @Composable (ColumnScope.() -> Unit)) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Forest)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), content = content)
        }
    }
}


@Composable
fun StatCard(icon: String, label: String, value: String, subtitle: String, accentColor: Color) {
    Card(modifier = Modifier.width(160.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 18.sp); Spacer(Modifier.width(8.dp)); Text(label, style = MaterialTheme.typography.labelSmall, color = MidGray) }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accentColor)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MidGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun getFileNameFromUri(cr: ContentResolver, uri: Uri): String {
    var name = ""
    cr.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx != -1 && cursor.moveToFirst()) name = cursor.getString(idx)
    }
    return if (name.isBlank()) "upload_${System.currentTimeMillis()}" else name
}

@Composable
fun MemberInsightsTab(state: MemberUiState) {
    val insight = state.businessInsight
    
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader("Group Insights")
        
        when (insight) {
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Rosca -> RoscaInsights(insight.schedule)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.InvestmentClub -> InvestmentClubInsights(insight.valuation)
            is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Stokvel -> StokvelInsights(insight.projection)
            else -> {
                EmptyState(
                    icon = "📊",
                    title = "No specialized insights",
                    description = "Specialized business tools are available for ROSCA, Investment Club, and Stokvel group types."
                )
            }
        }
    }
}

fun handleProfilePhotoSelection(context: Context, vm: MemberViewModel, uri: Uri?) {
    uri?.let {
        runCatching {
            val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
            if (bytes != null) vm.uploadDocument(0, bytes, getFileNameFromUri(context.contentResolver, it), "Profile Photo")
        }
    }
}

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
            onMemberRegistered(state.joiningFee)
        }
    }

    Scaffold(
        topBar = { SanibonaniTopBar("Member Registration", onBack = onBack) },
        containerColor = Cream
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Join Group", style = MaterialTheme.typography.headlineMedium, color = Forest)
            Text("Please provide your details to join this group.", color = MidGray)

            SanibonaniTextField(
                value = state.fullName,
                onValueChange = { vm.onFieldChange("fullName", it) },
                label = "Full Name",
                placeholder = "John Doe",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            SanibonaniTextField(
                value = state.idNumber,
                onValueChange = { if (it.length <= 13) vm.onFieldChange("idNumber", it) },
                label = "SA ID Number",
                placeholder = "YYMMDD SSSS CAZ",
                visualTransformation = IDNumberTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            SanibonaniTextField(
                value = state.phone,
                onValueChange = { if (it.length <= 10) vm.onFieldChange("phone", it) },
                label = "Phone Number",
                placeholder = "071 234 5678",
                visualTransformation = PhoneNumberTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            SanibonaniTextField(
                value = state.email,
                onValueChange = { vm.onFieldChange("email", it) },
                label = "Email Address",
                placeholder = "you@example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            HorizontalDivider(color = Forest.copy(alpha = 0.1f))
            Text("Residential Address", style = MaterialTheme.typography.titleMedium, color = Forest)

            AutoCompleteTextField(
                value = state.street,
                onValueChange = { vm.onFieldChange("street", it) },
                label = "Street Address",
                suggestions = state.addressSuggestions,
                onSuggestionClick = { vm.onAddressSelected(it) },
                isLoading = state.isSearchingAddress
            )

            SanibonaniTextField(
                value = state.suburb,
                onValueChange = { vm.onFieldChange("suburb", it) },
                label = "Suburb",
                placeholder = "Soweto"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = state.city,
                    onValueChange = { vm.onFieldChange("city", it) },
                    label = "City",
                    modifier = Modifier.weight(1f)
                )
                
                Box(modifier = Modifier.weight(1f)) {
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = state.province,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Province") },
                        trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        SA_PROVINCES.forEach { p ->
                            DropdownMenuItem(text = { Text(p) }, onClick = { vm.onFieldChange("province", p); expanded = false })
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.error != null) {
                InfoBox(state.error!!, InfoType.ERROR)
            }

            SanibonaniButton(
                text = if (state.isSubmitting) "Processing..." else "Register & Pay Joining Fee",
                onClick = { vm.submit(groupId, null) },
                enabled = state.canSubmit && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )

            InfoBox(
                "Your information is stored securely and only shared with group administrators for membership purposes.",
                InfoType.INFO
            )
            
            Spacer(Modifier.height(40.dp))
        }
    }
}
