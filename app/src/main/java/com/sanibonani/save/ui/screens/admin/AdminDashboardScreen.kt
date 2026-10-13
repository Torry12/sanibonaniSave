package com.sanibonani.save.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.data.utils.PaymentCalculation
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.*
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.viewmodel.AdminViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@Suppress("DEPRECATION")
data class AdminMemberPortalAccess(
    val enabled: Boolean,
    val groupId: String?,
    val memberId: String?,
    val title: String,
    val description: String,
    val buttonLabel: String
)

internal fun resolveAdminMemberPortalAccess(state: AdminUiState, supportActive: Boolean): AdminMemberPortalAccess {
    val groupId = state.group?.id
        ?: state.currentGroupId
        ?: state.managedGroups.firstOrNull { !it.id.isNullOrBlank() }?.id

    if (groupId.isNullOrBlank()) {
        return AdminMemberPortalAccess(
            enabled = false,
            groupId = null,
            memberId = null,
            title = if (supportActive) "Member portal unavailable" else "Switch to Member View",
            description = "Group data not yet synchronized.",
            buttonLabel = "Switch to Member Portal"
        )
    }

    if (!supportActive) {
        return AdminMemberPortalAccess(
            enabled = true,
            groupId = groupId,
            memberId = null,
            title = "Switch to Member View",
            description = "Access your own contributions and profile as a group member.",
            buttonLabel = "Switch to Member Portal"
        )
    }

    // Support mode logic
    val selectedMember = state.selectedMember
    if (selectedMember != null) {
        val memberName = selectedMember.fullName.ifBlank { "selected member" }
        return AdminMemberPortalAccess(
            enabled = true,
            groupId = groupId,
            memberId = selectedMember.id,
            title = "Impersonate $memberName",
            description = "View the portal exactly as this member sees it.",
            buttonLabel = "Impersonate Member"
        )
    }

    return AdminMemberPortalAccess(
        enabled = false,
        groupId = groupId,
        memberId = null,
        title = "Member portal unavailable",
        description = "In support mode, please select a member from the list first.",
        buttonLabel = "Switch to Member Portal"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onNavigateToPayment: (type: String, amount: String, groupId: String) -> Unit,
    onNavigateToMemberPortal: (groupId: String, memberId: String?) -> Unit,
    onNavigateToHealthScore: (groupId: String) -> Unit,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    isSupportMode: Boolean = false,
    vm: AdminViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val adminTabs = remember {
        listOf<Pair<Int, String>>(
            0 to "Overview",
            1 to "Members",
            2 to "Alerts",
            3 to "Messaging",
            4 to "Viability",
            5 to "Account",
            6 to "Settings",
            7 to "Payouts",
            8 to "Loans",
            9 to "Insights",
            10 to "Ledger"
        )
    }

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { adminTabs.size })
    
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clickDebouncer = rememberClickDebouncer()

    // Sync PagerState with ViewModel State (Logical selection)
    LaunchedEffect(state.selectedTab) {
        val targetPos = adminTabs.indexOfFirst { it.first == state.selectedTab }
        if (targetPos == -1) {
            vm.setTab(adminTabs.first().first)
        } else if (pagerState.currentPage != targetPos) {
            pagerState.animateScrollToPage(targetPos)
        }
    }

    // Sync ViewModel State with PagerState (Swipe)
    LaunchedEffect(pagerState.currentPage) {
        val logicalIndex = adminTabs.getOrNull(pagerState.currentPage)?.first
        if (logicalIndex != null && state.selectedTab != logicalIndex) {
            vm.setTab(logicalIndex)
        }
    }

    DisposableEffect(Unit) {
        vm.setActive(true)
        onDispose {
            vm.setActive(false)
        }
    }

    var showFileActionDialog by remember { mutableStateOf<Triple<String, String, Map<String, String>>?>(null) }
    var viewFileData by remember { mutableStateOf<Triple<String, String, Map<String, String>>?>(null) }

    val portalGroupId = state.group?.id ?: state.currentGroupId
    val memberPortalAccess = remember(
        state.group?.id,
        state.currentGroupId,
        state.managedGroups,
        state.selectedMember?.id,
        state.selectedMember?.fullName,
        isSupportMode
    ) {
        resolveAdminMemberPortalAccess(state, isSupportMode)
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSuccessMessage()
        }
    }

    LaunchedEffect(state.loadingMessage) {
        state.loadingMessage?.let {
            ToastUtils.showProcessing(context, it)
            vm.clearLoadingMessage()
        }
    }

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
            vm.clearExportFile()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (isSupportMode) "Admin View (Support)" else "Admin Dashboard",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            state.group?.name?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForestMid
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { clickDebouncer.processClick(onNavigateBack) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                            IconButton(
                                onClick = {
                                    clickDebouncer.processClick {
                                        val gid = memberPortalAccess.groupId
                                        if (!gid.isNullOrBlank() && memberPortalAccess.enabled) {
                                            onNavigateToMemberPortal(gid, memberPortalAccess.memberId)
                                        }
                                    }
                                },
                                enabled = memberPortalAccess.enabled
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = memberPortalAccess.buttonLabel,
                                    tint = if (memberPortalAccess.enabled) Forest else MidGray
                                )
                            }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )

                state.group?.takeIf { state.feeStatus == AdminFeeState.PENDING_ACTIVATION }?.let { group ->
                    AdminFeeBanner(
                        group = group,
                        status = state.feeStatus,
                        daysOverdue = state.daysOverdue,
                        onPayClick = {
                            clickDebouncer.processClick {
                                group.id?.let { id ->
                                    onNavigateToPayment("registration", group.registrationFee.toString(), id)
                                }
                            }
                        }
                    )
                }

                if (state.managedGroups.size > 1) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Forest.copy(0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                        ) {
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
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.fillMaxWidth(0.9f).background(Color.White)
                        ) {
                            state.managedGroups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.name, fontWeight = if (group.id == state.currentGroupId) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        group.id?.let { vm.selectGroup(it) }
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (group.id == state.currentGroupId) Icons.Default.CheckCircle else Icons.Default.Circle,
                                            contentDescription = null,
                                            tint = if (group.id == state.currentGroupId) Forest else LightGray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Tab Row - moved to middle of screen (below banner/group switcher)
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp,
                    color = Color.White
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.White,
                        contentColor = Forest,
                        edgePadding = 0.dp,
                        divider = {},
                        indicator = { tabPositions ->
                            if (pagerState.currentPage < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                    color = Forest
                                )
                            }
                        }
                    ) {
                        adminTabs.forEachIndexed { i, tab ->
                            Tab(
                                selected = pagerState.currentPage == i,
                                onClick = { 
                                    scope.launch {
                                        pagerState.animateScrollToPage(i)
                                        vm.setTab(tab.first)
                                    }
                                },
                                text = { CompactTabText(tab.second) }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!portalGroupId.isNullOrBlank()) {
                Surface(shadowElevation = 8.dp, tonalElevation = 2.dp, color = Color.White) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .navigationBarsPadding()
                            .imePadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                clickDebouncer.processClick {
                                    val gid = memberPortalAccess.groupId
                                    if (!gid.isNullOrBlank() && memberPortalAccess.enabled) {
                                        onNavigateToMemberPortal(gid, memberPortalAccess.memberId)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = memberPortalAccess.enabled,
                            colors = ButtonDefaults.buttonColors(containerColor = Forest),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = memberPortalAccess.buttonLabel,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isSupportMode && !memberPortalAccess.enabled) {
                            Text(
                                text = memberPortalAccess.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MidGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
                beyondViewportPageCount = 1
            ) { page ->
                val logicalIndex = adminTabs.getOrNull(page)?.first ?: 0
                when (logicalIndex) {
                    0 -> OverviewTab(state, vm, 
                        onPayClick = { amount -> 
                            onNavigateToPayment("registration", amount.toString(), state.group?.id ?: state.currentGroupId ?: "")
                        },
                        memberPortalEnabled = memberPortalAccess.enabled,
                        memberPortalTitle = memberPortalAccess.title,
                        memberPortalDescription = memberPortalAccess.description,
                        onMemberPortalClick = {
                            val gid = memberPortalAccess.groupId
                            if (!gid.isNullOrBlank() && memberPortalAccess.enabled) {
                                onNavigateToMemberPortal(gid, memberPortalAccess.memberId)
                            }
                        },
                        onNavigateToHealthScore = {
                            val gid = state.group?.id ?: state.currentGroupId
                            if (!gid.isNullOrBlank()) {
                                onNavigateToHealthScore(gid)
                            }
                        },
                        onFileAction = { url, name, headers -> showFileActionDialog = Triple(url, name, headers) },
                        clickDebouncer = clickDebouncer
                    )
                    1 -> MembersTab(
                        state = state,
                        vm = vm,
                        isSupportMode = isSupportMode,
                        onEnterPortal = { memberId ->
                            clickDebouncer.processClick {
                                portalGroupId?.let { gid ->
                                    onNavigateToMemberPortal(gid, memberId)
                                }
                            }
                        },
                        onFileAction = { url, name, headers -> showFileActionDialog = Triple(url, name, headers) }
                    )
                    2 -> NotificationsTab(state)
                    3 -> MessagingTab(state, vm)
                    4 -> ViabilityPlanningTab(state, vm)
                    5 -> AccountTab(state, state.group, onLogout)
                    6 -> SettingsTab(state, vm)
                    7 -> PayoutTab(state, vm)
                    8 -> LoansTab(state, vm, onFileAction = { url, name, headers -> showFileActionDialog = Triple(url, name, headers) })
                    9 -> InsightsTab(state)
                    10 -> LedgerTab(state, vm)
                }
            }

            if (state.isLoading && state.group == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Forest)
                }
            } else if (state.group == null && state.error != null) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(0.1f)),
                        border = BorderStroke(1.dp, ErrorRed)
                    ) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Group not found or no access", fontWeight = FontWeight.Bold)
                            Text(state.error!!, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    state.selectedLedgerEntry?.let { entry ->
        LedgerEntryDetailDialog(
            entry = entry,
            onDismiss = { vm.selectLedgerEntry(null) }
        )
    }

    state.selectedMember?.let { member ->
        val id = member.id ?: ""
        MemberDetailDialog(
            member = member,
            beneficiaries = state.selectedMemberBeneficiaries,
            documents = state.selectedMemberDocuments,
            calculation = state.selectedMemberCalculation,
            isEligibleForLoan = state.isEligibleForLoan,
            loanIneligibilityReason = state.loanIneligibilityReason,
            isSupportMode = isSupportMode,
            onDismiss = { vm.selectMember(null) },
            onEnterPortal = { 
                clickDebouncer.processClick {
                    portalGroupId?.let { gid ->
                        onNavigateToMemberPortal(gid, id)
                    }
                }
            },
            onVerifyDoc = { idx, approve ->
                vm.verifyDocument(id, idx, approve)
            },
            onVerifyRelDoc = { docId, approve -> vm.verifyRelationalDocument(docId, approve) },
            messageText = state.messageText,
            onUpdateMessageText = { msg -> vm.updateMessageText(msg) },
            onSendMessage = { vm.sendMessageToMember(id) },
            isSending = state.isSendingMessage,
            messageSentSuccess = state.messageSentSuccess,
            onBroadcast = { vm.broadcastMessage() },
            onUpdateStatus = { status -> vm.updateMemberStatus(id, status) },
            whatsAppTestResult = state.whatsAppTestResult,
            isSendingWhatsAppTest = state.isSendingWhatsAppTest,
            onExportStatement = { vm.exportMemberStatement(member, pdf = false) },
            onEditBeneficiary = { vm.startEditBeneficiary(it) },
            onVerifyClaim = { claimId, approve -> vm.verifyClaim(claimId, approve) },
            onEscalateClaim = { claimId -> vm.escalateClaim(claimId) },
            burialClaims = state.burialClaims,
            vm = vm,
            onFileAction = { url, name, headers -> showFileActionDialog = Triple(url, name, headers) }
        )
    }

    state.editingBeneficiary?.let { beneficiary ->
        BeneficiaryEditDialog(
            beneficiary = beneficiary,
            isSaving = state.isSavingBeneficiary,
            onDismiss = { vm.startEditBeneficiary(null) },
            onSave = { vm.saveBeneficiary() },
            onUpdate = { update -> vm.updateEditingBeneficiary { update } }
        )
    }

    if (state.error != null && state.group != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text("Error") },
            text = { Text(state.error!!) },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("OK") } }
        )
    }

    // File Action Dialog
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
fun LedgerTab(state: AdminUiState, vm: AdminViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle("📜 Group Ledger", "Immutable audit trail")
            IconButton(onClick = { vm.exportGroupLedger(pdf = false) }, enabled = !state.isExporting) {
                if (state.isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Share, "Export CSV", tint = Forest)
                }
            }
        }
        
        Text("Transparency First: This ledger records all contributions and withdrawals. Use these records for independent verification.", style = MaterialTheme.typography.bodyMedium, color = MidGray)

        if (state.ledger.isEmpty()) {
            EmptyState(icon = "🧾", title = "No transactions", description = "Once payments start, they will be logged here.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(state.ledger) { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White), 
                        border = BorderStroke(1.dp, LightGray.copy(0.3f)),
                        modifier = Modifier.clickable { vm.selectLedgerEntry(entry) }
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${entry.createdAt?.take(16)?.replace("T", " ")} • ${entry.category.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val color = if (entry.amount >= 0) SuccessGreen else Color.Red
                                val prefix = if (entry.amount >= 0) "+" else ""
                                Text("$prefix${formatZAR(entry.amount)}", color = color, fontWeight = FontWeight.Black)
                                Text("Bal: ${formatZAR(entry.balanceAfter)}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerEntryDetailDialog(
    entry: com.sanibonani.save.domain.model.LedgerEntry,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ledger Verification Portal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoBox(
                    message = "This entry represents an immutable accounting record. Use the Transaction ID below to verify the source proof.",
                    type = InfoType.INFO
                )
                
                DetailRow("Description", entry.description)
                DetailRow("Category", entry.category.uppercase())
                DetailRow("Amount", formatZAR(entry.amount))
                DetailRow("Balance After", formatZAR(entry.balanceAfter))
                DetailRow("Date/Time", entry.createdAt?.replace("T", " ") ?: "N/A")
                
                HorizontalDivider(color = LightGray.copy(alpha = 0.3f))
                
                Column {
                    Text("Transaction Reference", style = MaterialTheme.typography.labelSmall, color = MidGray)
                    Text(
                        entry.transactionId ?: "INTERNAL_ADJUSTMENT",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                
                Text(
                    "Account Principles Applied: Double-entry consistency verified. Balance reflects post-transaction state.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ForestMid
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Forest) }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    val isCompact = LocalConfiguration.current.screenWidthDp <= 360
    Text(
        text = text,
        modifier = modifier,
        style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
        color = Forest,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CompactTabText(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun ViabilityPlanningTab(state: AdminUiState, vm: AdminViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("📈 Viability Planning")
        Text(
            "Set your group's financial goals and we'll calculate the required contributions to make it sustainable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MidGray
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, Forest.copy(0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SanibonaniTextField(
                    value = state.settings.goalAmount,
                    onValueChange = { vm.updateSetting("goalAmount", it) },
                    label = "Target Goal Amount (R)",
                    placeholder = "e.g. 10000",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    prefix = { Text("R ", color = Forest, fontWeight = FontWeight.Bold) }
                )
                
                SanibonaniTextField(
                    value = state.settings.periodMonths,
                    onValueChange = { vm.updateSetting("periodMonths", it) },
                    label = "Target Time Period (Months)",
                    placeholder = "e.g. 12",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text(" Months", color = MidGray) }
                )

                SanibonaniButton(
                    text = "Calculate Strategy",
                    onClick = { vm.calculateViability() },
                    isLoading = state.isCalculatingViability,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        state.viabilityPlan?.let { plan ->
            ViabilityResultCard(
                plan = plan,
                groupType = state.group?.type ?: GroupType.OTHER,
                onApply = { vm.applySuggestedContribution() }
            )
        }
    }
}

@Composable
fun ViabilityResultCard(plan: ViabilityPlan, groupType: GroupType, onApply: () -> Unit) {
    var showAllFactors by remember(plan) { mutableStateOf(false) }
    var explainFactor by remember(plan) { mutableStateOf<ViabilityFactorUi?>(null) }
    val factors = remember(plan, showAllFactors, groupType) {
        plan.toViabilityFactors(groupType = groupType, includeNeutral = showAllFactors)
    }
    val panels = remember(plan, showAllFactors, groupType) {
        plan.toViabilityPanels(groupType = groupType, includeNeutral = showAllFactors)
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Forest.copy(0.05f)),
        border = BorderStroke(1.dp, Forest.copy(0.2f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Suggested Strategy", style = MaterialTheme.typography.titleMedium, color = Forest, fontWeight = FontWeight.Bold)
            
            DetailRow("Required Monthly Contribution", formatZAR(plan.suggestedMonthlyContribution))
            DetailRow("Initial Upfront Payment", formatZAR(plan.initialContribution))
            DetailRow("Projected Value", formatZAR(plan.projectedValue))

            if (panels.isNotEmpty()) {
                HorizontalDivider(color = Forest.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Projection Factors", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (showAllFactors) {
                                "Showing all ${factors.size} factors, including neutral multipliers."
                            } else {
                                "Showing ${factors.size} active factors for ${groupType.displayName}. Neutral multipliers are hidden."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MidGray
                        )
                    }
                    FilterChip(
                        selected = showAllFactors,
                        onClick = { showAllFactors = !showAllFactors },
                        label = {
                            Text(if (showAllFactors) "All Factors" else "Active Only")
                        }
                    )
                }
                panels.forEach { panel ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            panel.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = Forest,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            panel.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MidGray
                        )
                        panel.factors.forEach { factor ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { explainFactor = factor }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(factor.label, style = MaterialTheme.typography.bodyMedium, color = MidGray)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = viabilityFactorIcon(factor.trend()),
                                        contentDescription = null,
                                        tint = viabilityFactorColor(factor.trend()),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = formatFactorMultiplier(factor.value),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = viabilityFactorColor(factor.trend()),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            if (plan.messages.isNotEmpty()) {
                HorizontalDivider(color = Forest.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))
                plan.messages.forEach { msg ->
                    InfoBox(msg, InfoType.INFO)
                }
            }

            Spacer(Modifier.height(8.dp))
            SanibonaniButton(
                text = "Apply Suggested Contribution",
                onClick = onApply,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Applying this will update the group's monthly contribution for all members. This action should be discussed with the group.",
                style = MaterialTheme.typography.labelSmall,
                color = MidGray,
                textAlign = TextAlign.Center
            )
        }
    }

    explainFactor?.let { factor ->
        AlertDialog(
            onDismissRequest = { explainFactor = null },
            title = { Text(factor.label) },
            text = { Text(factor.description()) },
            confirmButton = {
                TextButton(onClick = { explainFactor = null }) { Text("OK") }
            }
        )
    }
}

private fun formatFactorMultiplier(value: Double): String {
    return if (value >= 1.0) "+${((value - 1.0) * 100).toInt()}%" else "-${((1.0 - value) * 100).toInt()}%"
}

private fun viabilityFactorIcon(trend: ViabilityFactorTrend): ImageVector {
    return when (trend) {
        ViabilityFactorTrend.LIFT -> Icons.Default.TrendingUp
        ViabilityFactorTrend.HAIRCUT -> Icons.Default.TrendingDown
        else -> Icons.Default.HorizontalRule
    }
}

private fun viabilityFactorColor(trend: ViabilityFactorTrend): Color {
    return when (trend) {
        ViabilityFactorTrend.LIFT -> SuccessGreen
        ViabilityFactorTrend.HAIRCUT -> ErrorRed
        else -> MidGray
    }
}

@Composable
private fun ViabilityFactorsBarChart(factors: List<ViabilityFactorUi>) {
    val maxAbs = factors.maxOfOrNull { abs(it.value - 1.0) }?.coerceAtLeast(0.05) ?: 0.05
    
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var selectedFactorKey by remember(factors) { mutableStateOf(factors.firstOrNull()?.key) }
        
        factors.forEach { factor ->
            val trend = factor.trend()
            val isSelected = selectedFactorKey == factor.key
            val weight = (abs(factor.value - 1.0) / maxAbs).toFloat()
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { selectedFactorKey = factor.key }
                    .background(if (isSelected) viabilityFactorColor(trend).copy(alpha = 0.08f) else Color.Transparent)
                    .padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(factor.label, style = MaterialTheme.typography.labelMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    Text(formatFactorMultiplier(factor.value), style = MaterialTheme.typography.labelSmall, color = viabilityFactorColor(trend), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LightGray.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(weight.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .align(if (factor.value >= 1.0) Alignment.CenterStart else Alignment.CenterEnd)
                            .background(viabilityFactorColor(trend))
                    )
                }
                if (isSelected) {
                    Text(
                        factor.description(),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MidGray
                    )
                }
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ViabilityLegendItem(SuccessGreen, "Positive Impact")
            ViabilityLegendItem(ErrorRed, "Negative Impact")
            ViabilityLegendItem(MidGray, "Neutral")
        }
    }
}

@Composable
private fun ViabilityLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MidGray)
    }
}

@Composable
fun AdminFeeBanner(group: Group, status: AdminFeeState, daysOverdue: Int, onPayClick: () -> Unit) {
    val formattedMonthlyFee = formatZAR(group.platformFeeAmount)
    
    val (bgColor, textColor, message) = when (status) {
        AdminFeeState.PAID -> Triple(Forest, Color.White, "✅ Platform fee paid — next due soon")
        AdminFeeState.DUE -> Triple(WarningYellow, Color.Black, "⚠️ Platform fee due — please pay $formattedMonthlyFee")
        AdminFeeState.OVERDUE -> Triple(ErrorRed, Color.White, "🚨 Platform fee overdue ($daysOverdue days) — $formattedMonthlyFee due")
        AdminFeeState.WARNING -> Triple(WarningYellow, Color.Black, "⚠️ Platform fee due soon — please pay $formattedMonthlyFee")
        AdminFeeState.SUSPENDED -> Triple(Color.Black, Color.White, "🚫 Account suspended — pay $formattedMonthlyFee to restore access")
        AdminFeeState.PENDING_ACTIVATION -> Triple(ForestMid, Color.White, "🚀 Onboarding — Pay ${formatZAR(group.registrationFee)} to activate group")
    }

    Surface(
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPayClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.Login,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun OverviewTab(
    state: AdminUiState,
    vm: AdminViewModel,
    onPayClick: (Double) -> Unit,
    memberPortalEnabled: Boolean,
    memberPortalTitle: String,
    memberPortalDescription: String,
    onMemberPortalClick: () -> Unit,
    onNavigateToHealthScore: () -> Unit,
    onFileAction: (String, String, Map<String, String>) -> Unit,
    clickDebouncer: ClickDebouncer
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Activation/Onboarding Card
        if (state.feeStatus == AdminFeeState.PENDING_ACTIVATION) {
            GlassCard(accentColor = Forest) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RocketLaunch, null, tint = Forest, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Activate Group", style = MaterialTheme.typography.titleMedium, color = Forest, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(12.dp))
                Text("Your group is ready for takeoff. Pay the registration fee to unlock all management tools.", style = MaterialTheme.typography.bodyMedium, color = MidGray)
                Spacer(Modifier.height(20.dp))
                SanibonaniButton(
                    text = "Pay Registration Fee (${formatZAR(state.group?.registrationFee ?: 700.0)})",
                    onClick = { clickDebouncer.processClick { onPayClick(state.group?.registrationFee ?: 700.0) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Member Portal Link
        ModernNavigationLink(
            title = memberPortalTitle,
            subtitle = memberPortalDescription,
            icon = Icons.Default.Person,
            onClick = { if (memberPortalEnabled) clickDebouncer.processClick(onMemberPortalClick) },
            accentColor = if (memberPortalEnabled) Forest else MidGray,
            containerColor = if (memberPortalEnabled) Forest.copy(0.04f) else LightGray.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth().alpha(if (memberPortalEnabled) 1f else 0.7f)
        )

        // Main Metric - Large Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(1.dp, Forest.copy(0.06f))
        ) {
            Column(Modifier.padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Forest.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("💰", fontSize = 28.sp)
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text("Total Group Balance", style = MaterialTheme.typography.labelMedium, color = MidGray)
                        Text(
                            formatZAR(state.group?.balance ?: 0.0),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = Charcoal
                        )
                    }
                }
            }
        }

        // Action Cards Grid
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                icon = "👥",
                label = "Active Members",
                value = "${state.group?.currentMembers ?: 0}",
                subtitle = "Enrolled",
                accentColor = Forest,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = "✅",
                label = "Collection Rate",
                value = "${state.metrics.paymentRatePct.toInt()}%",
                subtitle = "Current month",
                accentColor = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
        }

        // Account Status / Restore Button
        if (state.group?.isPlatformSuspended == true) {
            GlassCard(accentColor = ErrorRed) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = ErrorRed)
                        Spacer(Modifier.width(8.dp))
                        Text("ACCOUNT SUSPENDED", color = ErrorRed, fontWeight = FontWeight.Black)
                    }
                    Text("Operations are restricted due to overdue platform fees. Clear the balance to resume.", style = MaterialTheme.typography.bodySmall)
                    
                    if (state.restoreRequested) {
                        InfoBox("Restoration request pending platform review.", InfoType.INFO)
                    } else {
                        Button(
                            onClick = { vm.requestRestore() },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("REQUEST RESTORATION")
                        }
                    }
                }
            }
        }

        SectionTitle("Quick Actions")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(icon = Icons.Default.Share, label = "Export", modifier = Modifier.weight(1f)) { vm.exportGroupStatement() }
            ActionButton(icon = Icons.Default.PictureAsPdf, label = "Report", modifier = Modifier.weight(1f)) { vm.downloadPdfStatement() }
            ActionButton(icon = Icons.Default.Description, label = "Rules", modifier = Modifier.weight(1f)) {
                val officialUrl = state.group?.constitutionUrl
                if (!officialUrl.isNullOrBlank()) {
                    onFileAction(officialUrl, "Group_Rules.pdf", vm.getDownloadParams(officialUrl))
                } else {
                    vm.downloadGroupConstitution()
                }
            }
        }

        ModernNavigationLink(
            title = "Actuarial Health Score",
            subtitle = "Detailed risk and solvency analytics",
            icon = Icons.Default.Shield,
            onClick = { clickDebouncer.processClick(onNavigateToHealthScore) },
            badgeCount = if (state.healthScore != null && state.healthScore!!.zone != RiskZone.GREEN) 1 else 0,
            accentColor = InfoBlue
        )

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Forest.copy(alpha = 0.15f)),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = ForestMid)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ForestMid)
        }
    }
}

@Composable
fun MembersTab(
    state: AdminUiState,
    vm: AdminViewModel,
    isSupportMode: Boolean = false,
    onEnterPortal: ((String) -> Unit)? = null,
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredMembers = state.members.filter {
        it.fullName.contains(searchQuery, ignoreCase = true) || it.idNumber?.contains(searchQuery) == true
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search by name or ID...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredMembers) { member ->
                val calculation = state.memberCalculations[member.id ?: ""]
                MemberItem(
                    member = member,
                    calculation = calculation,
                    onClick = { vm.selectMember(member) }
                )
            }
        }
    }
}

@Composable
fun MemberItem(
    member: Member,
    calculation: PaymentCalculation?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LightGray.copy(alpha = 0.4f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Forest.copy(0.1f)), contentAlignment = Alignment.Center) {
                Text(member.fullName.take(1).uppercase())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(member.fullName, fontWeight = FontWeight.Bold)
                Text(member.status.displayName, style = MaterialTheme.typography.labelSmall, color = when(member.status) {
                    MemberStatus.ACTIVE -> if (calculation?.isOverdue == true) ErrorRed else Forest
                    MemberStatus.PENDING_PAYMENT -> WarningAmber
                    else -> MidGray
                })
            }
            Icon(Icons.Default.ChevronRight, null, tint = LightGray)
        }
    }
}

@Composable
fun MemberDetailDialog(
    member: Member,
    beneficiaries: List<Beneficiary> = emptyList(),
    documents: List<MemberDocument> = emptyList(),
    calculation: PaymentCalculation?,
    isEligibleForLoan: Boolean,
    loanIneligibilityReason: String?,
    isSupportMode: Boolean,
    onDismiss: () -> Unit,
    onEnterPortal: () -> Unit,
    onVerifyDoc: (Int, Boolean) -> Unit,
    onVerifyRelDoc: (String, Boolean) -> Unit,
    messageText: String,
    onUpdateMessageText: (String) -> Unit,
    onSendMessage: () -> Unit,
    isSending: Boolean,
    messageSentSuccess: Boolean,
    onBroadcast: () -> Unit,
    onUpdateStatus: (MemberStatus) -> Unit,
    whatsAppTestResult: String?,
    isSendingWhatsAppTest: Boolean,
    onExportStatement: () -> Unit,
    onEditBeneficiary: (Beneficiary) -> Unit,
    onVerifyClaim: (String, Boolean) -> Unit,
    onEscalateClaim: (String) -> Unit,
    burialClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    vm: AdminViewModel?,
    onFileAction: (String, String, Map<String, String>) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                SanibonaniTopBar(
                    title = member.fullName,
                    onBack = onDismiss,
                    actions = {
                        IconButton(onClick = onExportStatement) { Icon(Icons.Default.Share, "Export") }
                    }
                )
            },
            containerColor = Cream
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Photo and Basic Info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(member.profilePhotoUrl)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = "Photo",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(member.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        StatusChip(member.status, member.status.displayName)
                    }
                }

                GlassCard(accentColor = Forest) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Loan Eligibility", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            val (color, text) = if (isEligibleForLoan) {
                                Forest to "✅ QUALIFIED"
                            } else {
                                ErrorRed to "❌ NOT QUALIFIED"
                            }
                            Text(text, color = color, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                        
                        loanIneligibilityReason?.let { reason ->
                            InfoBox(reason, InfoType.WARNING)
                        }
                        
                        DetailRow("Total Contributions", "${member.totalContributions}")
                        DetailRow("Total Paid", formatZAR(member.totalPaid ?: 0.0))
                        calculation?.let {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Arrears / Balance", style = MaterialTheme.typography.bodySmall, color = MidGray)
                                Text(formatZAR(it.totalDueNow), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (it.isOverdue) ErrorRed else SuccessGreen)
                            }
                        }
                    }
                }

                DetailSection("Profile Details") {
                    DetailRow("ID Number", member.idNumber ?: "Not captured")
                    DetailRow("Phone", member.phone.ifBlank { "Not captured" })
                    DetailRow("Joined", member.joinedAt?.substringBefore("T") ?: "N/A")
                    
                    val addressParts = listOfNotNull(
                        member.street,
                        member.suburb,
                        member.city,
                        member.province
                    ).filter { it.isNotBlank() }
                    
                    if (addressParts.isNotEmpty()) {
                        DetailRow("Address", addressParts.joinToString(", "))
                    }
                }

                // Beneficiaries
                DetailSection("Beneficiaries (${beneficiaries.size})") {
                    beneficiaries.forEach { b ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, LightGray.copy(alpha = 0.2f))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(b.fullName, fontWeight = FontWeight.Bold)
                                        Text("${b.relationship} • ${b.idNumber ?: "N/A"}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (b.documentUrl != null) {
                                        IconButton(onClick = { 
                                            val dUrl = b.documentUrl!!
                                            val headers = vm?.getDownloadParams(dUrl) ?: emptyMap()
                                            val ext = dUrl.substringAfterLast(".", "pdf").substringBefore("?")
                                            val fileName = "Beneficiary_ID_${b.fullName.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                                            onFileAction(dUrl, fileName, headers)
                                        }) {
                                            Icon(Icons.Default.Description, "View ID", tint = Forest)
                                        }
                                    }
                                    IconButton(onClick = { onEditBeneficiary(b) }) { Icon(Icons.Default.Edit, "Edit", tint = MidGray, modifier = Modifier.size(20.dp)) }
                                }
                                
                                val bClaim = burialClaims.find { it.beneficiaryId == b.id }
                                if (bClaim != null) {
                                    Spacer(Modifier.height(8.dp))
                                    ClaimAdminCard(bClaim, onVerify = { approve -> onVerifyClaim(bClaim.id!!, approve) }, onEscalate = { onEscalateClaim(bClaim.id!!) })
                                }
                            }
                        }
                    }
                    if (beneficiaries.isEmpty()) {
                        Text("No beneficiaries registered.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                    }
                }

                // Documents
                DetailSection("Compliance Documents") {
                    // Indexed ones (1-5)
                    for (i in 1..5) {
                        val label = when(i) {
                            1 -> "Identity Document (ID)"
                            2 -> "Proof of Residence"
                            3 -> "Marriage/Other Doc"
                            4 -> "Member Policy Sign-off"
                            5 -> "Beneficiary ID Docs"
                            else -> "Doc $i"
                        }
                        val url = when(i) { 1 -> member.document1Url; 2 -> member.document2Url; 3 -> member.document3Url; 4 -> member.document4Url; 5 -> member.document5Url; else -> null }
                        val status = when(i) { 1 -> member.document1Status; 2 -> member.document2Status; 3 -> member.document3Status; 4 -> member.document4Status; 5 -> member.document5Status; else -> DocumentStatus.PENDING }
                        
                        DocumentAdminCard(
                            label = label,
                            url = url,
                            status = status,
                            onVerify = { approve -> onVerifyDoc(i, approve) },
                            onDownload = { dUrl, dLabel ->
                                    val (dlUrl, headers) = vm?.downloadMemberDocument(dUrl, dLabel) ?: Pair(dUrl, emptyMap())
                                    val ext = dlUrl.substringAfterLast(".", "pdf").substringBefore("?")
                                    val fileName = "${dLabel.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                                    onFileAction(dlUrl, fileName, headers)
                            }
                        )
                    }
                    
                    // Relational ones
                    documents.forEach { doc ->
                        DocumentAdminCard(
                            label = doc.label,
                            url = doc.documentUrl,
                            status = doc.status,
                            onVerify = { approve -> onVerifyRelDoc(doc.id!!, approve) },
                            onDownload = { dUrl, dLabel ->
                                val (dlUrl, headers) = vm?.downloadMemberDocument(dUrl, dLabel) ?: Pair(dUrl, emptyMap())
                                val ext = dlUrl.substringAfterLast(".", "pdf").substringBefore("?")
                                val fileName = "${dLabel.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                                onFileAction(dlUrl, fileName, headers)
                            }
                        )
                    }
                }

                // Messaging
                DetailSection("Send Direct Message") {
                    SanibonaniTextField(
                        value = messageText,
                        onValueChange = onUpdateMessageText,
                        label = "New Message",
                        placeholder = "e.g. Please update your ID document",
                        singleLine = false,
                        modifier = Modifier.height(100.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onSendMessage,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = messageText.isNotBlank() && !isSending,
                        colors = ButtonDefaults.buttonColors(containerColor = Forest)
                    ) {
                        if (isSending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("SEND NOTIFICATION")
                    }
                    if (messageSentSuccess) InfoBox("Message sent!", InfoType.SUCCESS)
                    
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm?.sendWhatsAppTestToSelectedMember() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSendingWhatsAppTest
                    ) {
                        if (isSendingWhatsAppTest) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("SEND SMOKE-TEST WHATSAPP")
                    }
                    
                    whatsAppTestResult?.let { result ->
                        val resultType = if (result.contains("success", ignoreCase = true)) InfoType.SUCCESS else InfoType.INFO
                        InfoBox(result, resultType)
                    }
                }

                DetailSection("Member Governance") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onUpdateStatus(MemberStatus.ACTIVE) }, modifier = Modifier.weight(1f)) { Text("SET ACTIVE", fontSize = 10.sp) }
                        OutlinedButton(onClick = { onUpdateStatus(MemberStatus.SUSPENDED) }, modifier = Modifier.weight(1f)) { Text("SUSPEND", fontSize = 10.sp) }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ClaimAdminCard(
    claim: BeneficiaryPayoutClaim,
    onVerify: (Boolean) -> Unit,
    onEscalate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Burying Claim", fontWeight = FontWeight.Bold, color = ErrorRed)
                Text(formatZAR(claim.claimAmount), fontWeight = FontWeight.Black)
            }
            Text("Reason: ${claim.causeOfDeath}", style = MaterialTheme.typography.labelSmall)
            Text("Date: ${claim.dateOfDeath}", style = MaterialTheme.typography.labelSmall)
            
            HorizontalDivider(color = ErrorRed.copy(alpha = 0.1f))
            
            Text("Bank: ${claim.bankName} (${claim.accountNo})", style = MaterialTheme.typography.labelSmall)
            
            if (claim.status == BeneficiaryClaimStatus.SUBMITTED) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onVerify(true) }, colors = ButtonDefaults.buttonColors(containerColor = Forest), modifier = Modifier.weight(1f)) { Text("Verify", fontSize = 11.sp) }
                    OutlinedButton(onClick = onEscalate, modifier = Modifier.weight(1f)) { Text("Escalate", fontSize = 11.sp) }
                    IconButton(onClick = { onVerify(false) }) { Icon(Icons.Default.Close, null, tint = ErrorRed) }
                }
            } else {
                StatusChip(status = when(claim.status) {
                    BeneficiaryClaimStatus.APPROVED -> MemberStatus.ACTIVE
                    BeneficiaryClaimStatus.PAID -> MemberStatus.ACTIVE
                    BeneficiaryClaimStatus.REJECTED -> MemberStatus.SUSPENDED
                    BeneficiaryClaimStatus.ESCALATED -> MemberStatus.PENDING_PAYMENT
                    else -> MemberStatus.PENDING_PAYMENT
                }, label = claim.status.displayName)
            }
        }
    }
}

private fun BeneficiaryClaimStatus.toStatusColor(): Color = when(this) {
    BeneficiaryClaimStatus.SUBMITTED -> MidGray
    BeneficiaryClaimStatus.APPROVED -> SuccessGreen
    BeneficiaryClaimStatus.PAID -> Forest
    BeneficiaryClaimStatus.REJECTED -> ErrorRed
    BeneficiaryClaimStatus.ESCALATED -> InfoBlue
    else -> MidGray
}

@Composable
fun DocumentRow(label: String, status: DocumentStatus, onVerify: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(status.name, style = MaterialTheme.typography.labelSmall, color = when(status) {
                DocumentStatus.VERIFIED -> SuccessGreen
                DocumentStatus.REJECTED -> ErrorRed
                else -> MidGray
            })
        }
        if (status == DocumentStatus.PENDING) {
            Row {
                IconButton(onClick = { onVerify(true) }) { Icon(Icons.Default.Check, null, tint = SuccessGreen) }
                IconButton(onClick = { onVerify(false) }) { Icon(Icons.Default.Close, null, tint = ErrorRed) }
            }
        }
    }
}

@Composable
fun InsightsTab(state: AdminUiState) {
    val insight = state.businessInsight
    
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("Group Business Insights")
        
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
        
        // Actuarial stats
        GlassCard(accentColor = Gold) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Group Health Overview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                DashboardMetricRow("Composite Risk", "${state.metrics.compositeRiskScore}/100", if (state.metrics.compositeRiskScore < 40) SuccessGreen else WarningAmber)
                DashboardMetricRow("Solvency Margin", formatPct(state.metrics.solvencyMarginPct), Forest)
                DashboardMetricRow("Reserve Adequacy", formatPct(state.metrics.reserveAdequacyPct), Forest)
            }
        }
    }
}

@Composable
fun AnalyticsTab(state: AdminUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionTitle("Group Analytics") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("💰", "Balance", formatZAR(state.group?.balance ?: 0.0), accentColor = Forest, modifier = Modifier.weight(1f))
                StatCard("👥", "Members", "${state.group?.currentMembers ?: 0}", accentColor = Forest, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("📈", "Payment Rate", "${state.metrics.paymentRatePct.toInt()}%", accentColor = SuccessGreen, modifier = Modifier.weight(1f))
                StatCard("🛡️", "Risk Score", "${state.metrics.compositeRiskScore.toInt()}/100", accentColor = if (state.metrics.compositeRiskScore < 40) SuccessGreen else WarningAmber, modifier = Modifier.weight(1f))
            }
        }
        
        item {
            GlassCard(accentColor = Gold) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Actuarial Health", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    DashboardMetricRow("Solvency Margin", formatPct(state.metrics.solvencyMarginPct), Forest)
                    DashboardMetricRow("Reserve Adequacy", formatPct(state.metrics.reserveAdequacyPct), Forest)
                    DashboardMetricRow("Expected Annual Claims", formatZAR(state.metrics.expectedAnnualClaims), Charcoal)
                }
            }
        }
    }
}

@Composable
fun DashboardMetricRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MidGray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun MessagingTab(state: AdminUiState, vm: AdminViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("Group Broadcast", "Send a message to ALL members")
        
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Forest.copy(alpha = 0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = state.messageText,
                    onValueChange = { vm.updateMessageText(it) },
                    label = "Message Content",
                    placeholder = "e.g. Meeting next Sunday at 2 PM",
                    singleLine = false,
                    modifier = Modifier.height(120.dp)
                )
                
                if (state.messageSentSuccess) {
                    InfoBox("Broadcast sent successfully!", InfoType.SUCCESS)
                }
                
                SanibonaniButton(
                    text = "Send Broadcast",
                    onClick = { vm.broadcastMessage() },
                    isLoading = state.isSendingMessage,
                    enabled = state.messageText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        SectionTitle("History")
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.memberMessages) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Forest.copy(alpha = 0.05f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(msg.message, style = MaterialTheme.typography.bodyMedium, color = Charcoal)
                        Text(msg.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray)
                    }
                }
            }
            if (state.memberMessages.isEmpty()) {
                item { EmptyState("✉️", "No message history", "Your previous broadcasts will appear here.") }
            }
        }
    }
}

@Composable
fun NotificationsTab(state: AdminUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("Group Alerts") }
        items(state.notifications) { notif ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Forest.copy(alpha = 0.05f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).background(Forest.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Notifications, null, tint = Forest, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(notif.message, style = MaterialTheme.typography.bodySmall)
                        Text(notif.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray)
                    }
                }
            }
        }
        if (state.notifications.isEmpty()) {
            item { EmptyState("🔔", "All caught up!", "No new alerts for this group.") }
        }
    }
}

@Composable
fun AccountTab(state: AdminUiState, group: Group?, onLogout: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = Forest.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(64.dp), tint = Forest)
                }
            }
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(group?.name ?: "Group Admin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(group?.city ?: "Sanibonani Platform", style = MaterialTheme.typography.bodyMedium, color = MidGray)
        }
        
        GlassCard(accentColor = Forest) {
            SectionTitle("Group Details")
            DetailRow("Type", group?.type?.displayName ?: "N/A")
            DetailRow("Province", group?.province ?: "N/A")
            DetailRow("Joined", group?.createdAt?.substringBefore("T") ?: "N/A")
        }
        
        GlassCard(accentColor = Gold) {
            SectionTitle("Subscription")
            DetailRow("Status", state.feeStatus.name)
            DetailRow("Monthly Fee", formatZAR(group?.platformFeeAmount ?: 0.0))
        }

        Spacer(Modifier.height(24.dp))
        LogoutButton(onClick = onLogout, style = LogoutButtonStyle.Outlined)
    }
}

@Composable
fun SettingsTab(state: AdminUiState, vm: AdminViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SectionTitle("Group Configuration", "Manage rules and parameters")
        
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Forest.copy(alpha = 0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SanibonaniTextField(
                    value = state.settings.monthlyContribution,
                    onValueChange = { vm.updateSetting("monthlyContribution", it) },
                    label = "Standard Monthly Contribution (R)",
                    prefix = { Text("R ", color = Forest, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                
                SanibonaniTextField(
                    value = state.settings.joiningFee,
                    onValueChange = { vm.updateSetting("joiningFee", it) },
                    label = "Joining Fee (R)",
                    prefix = { Text("R ", color = Forest, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                
                HorizontalDivider(color = Forest.copy(alpha = 0.05f))

                SanibonaniTextField(
                    value = state.settings.loanInterestRate,
                    onValueChange = { vm.updateSetting("loanInterestRate", it) },
                    label = "Loan Interest Rate (%)",
                    suffix = { Text("%", color = MidGray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                SanibonaniTextField(
                    value = state.settings.lateFeeGraceDays,
                    onValueChange = { vm.updateSetting("lateFeeGraceDays", it.filter { c -> c.isDigit() }) },
                    label = "Late Fee Grace Period (Days)",
                    suffix = { Text(" Days", color = MidGray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
        
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Forest.copy(alpha = 0.1f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Operational Rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Partial Payments", style = MaterialTheme.typography.bodyMedium)
                        Text("Allow members to pay less than the full monthly amount", style = MaterialTheme.typography.labelSmall, color = MidGray)
                    }
                    Switch(
                        checked = state.settings.allowPartialPayment,
                        onCheckedChange = { vm.updateSetting("allowPartialPayment", it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Forest)
                    )
                }
            }
        }

        if (state.isSaving) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = Forest) }
        } else {
            SanibonaniButton(text = "Save Settings", onClick = { vm.saveSettings() }, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = { vm.generateAndUploadStandardConstitution() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isUploading
        ) {
            if (state.isUploading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else {
                Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generate Standard Constitution")
            }
        }

        if (state.saveSuccess) {
            InfoBox("Settings updated successfully.", InfoType.SUCCESS)
        }
        
        // Danger zone
        Card(
            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.05f)),
            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.2f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Danger Zone", style = MaterialTheme.typography.titleSmall, color = ErrorRed, fontWeight = FontWeight.Bold)
                Text("Maintenance operations that clear local caches. Only use if synced data appears incorrect.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { vm.resetLocalData() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = BorderStroke(1.dp, ErrorRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RESET LOCAL DATA")
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun BeneficiaryEditDialog(
    beneficiary: Beneficiary,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onUpdate: (Beneficiary) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Beneficiary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = beneficiary.fullName,
                    onValueChange = { text -> onUpdate(beneficiary.copy(fullName = text)) },
                    label = "Full Name"
                )
                SanibonaniTextField(
                    value = beneficiary.idNumber ?: "",
                    onValueChange = { text -> onUpdate(beneficiary.copy(idNumber = text)) },
                    label = "ID Number"
                )
                SanibonaniTextField(
                    value = beneficiary.relationship ?: "",
                    onValueChange = { text -> onUpdate(beneficiary.copy(relationship = text)) },
                    label = "Relationship"
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isSaving) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                else Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun LockedTabOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.8f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, LightGray.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, null, tint = MidGray, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Feature Locked", fontWeight = FontWeight.Bold)
                Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MidGray)
            }
        }
    }
}

@Composable
fun PayoutTab(state: AdminUiState, vm: AdminViewModel) {
    var showHistory by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(if (showHistory) "Payout History" else "Request Group Payout", "Withdraw group funds")
            TextButton(onClick = { showHistory = !showHistory }) {
                Text(if (showHistory) "NEW REQUEST" else "VIEW HISTORY")
            }
        }

        if (showHistory) {
            if (state.payouts.isEmpty()) {
                EmptyState("🧾", "No payout history", "Your previous payout requests will appear here.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.payouts.sortedByDescending { it.createdAt }) { payout ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, LightGray.copy(0.3f))
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatZAR(payout.amount), fontWeight = FontWeight.Black, fontSize = 18.sp)
                                    StatusChip(status = when(payout.status) {
                                        PayoutStatus.COMPLETED -> MemberStatus.ACTIVE
                                        PayoutStatus.FAILED -> MemberStatus.SUSPENDED
                                        PayoutStatus.CANCELLED -> MemberStatus.SUSPENDED
                                        else -> MemberStatus.PENDING_PAYMENT
                                    }, label = payout.status.name)
                                }
                                Text("Bank: ${payout.bankName} (${payout.accountNo})", style = MaterialTheme.typography.labelSmall, color = MidGray)
                                Text("Requested: ${payout.createdAt?.substringBefore("T")}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                                
                                if (payout.status == PayoutStatus.PENDING) {
                                    Spacer(Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { vm.approveAndEscalatePayoutRequest(payout.id ?: "") },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Forest)
                                        ) { Text("Approve & Escalate", fontSize = 10.sp) }
                                        OutlinedButton(
                                            onClick = { vm.cancelPayoutRequest(payout.id ?: "") },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Cancel", fontSize = 10.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Forest.copy(alpha = 0.1f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SanibonaniTextField(
                        value = state.payoutAmount,
                        onValueChange = { vm.updatePayoutAmount(it) },
                        label = "Amount to Withdraw (R)",
                        prefix = { Text("R ", color = Forest, fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Text("Destination Bank Account", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    
                    SanibonaniTextField(
                        value = state.payoutBankName,
                        onValueChange = { vm.updatePayoutBank(it) },
                        label = "Bank Name"
                    )
                    SanibonaniTextField(
                        value = state.payoutAccountNo,
                        onValueChange = { if (it.all { c -> c.isDigit() }) vm.updatePayoutAccount(it) },
                        label = "Account Number",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    SanibonaniTextField(
                        value = state.payoutBranchCode,
                        onValueChange = { if (it.all { c -> c.isDigit() }) vm.updatePayoutBranch(it) },
                        label = "Branch Code",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    if (state.payoutRequestSuccess) {
                        InfoBox("Payout request submitted successfully and escalated for platform approval.", InfoType.SUCCESS)
                    }

                    SanibonaniButton(
                        text = "Submit Payout Request",
                        onClick = { vm.submitPayoutRequest() },
                        isLoading = state.isRequestingPayout,
                        enabled = (state.payoutAmount.toDoubleOrNull() ?: 0.0) > 0 && state.payoutAccountNo.length >= 7,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            InfoBox("Note: All payouts must be approved by the platform administrators after group approval. Processing takes 1-2 business days.", InfoType.INFO)
        }
    }
}

@Composable
fun LoansTab(
    state: AdminUiState,
    vm: AdminViewModel,
    onFileAction: (String, String, Map<String, String>) -> Unit
) {
    var showHistory by remember { mutableStateOf(false) }
    
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(if (showHistory) "Loan History" else "Pending Loan Requests", "Manage member borrowing")
            TextButton(onClick = { showHistory = !showHistory }) {
                Text(if (showHistory) "NEW REQUESTS" else "VIEW ALL")
            }
        }

        if (showHistory) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.groupLoans.sortedByDescending { it.createdAt }) { loan ->
                    HistoryLoanCard(loan, state)
                }
                if (state.groupLoans.isEmpty()) {
                    item { EmptyState("🏦", "No loan history", "Records will appear here once loans are requested.") }
                }
            }
        } else {
            val pendingLoans = state.groupLoans.filter { it.status == LoanStatus.PENDING }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(pendingLoans) { loan ->
                    LoanRequestCard(loan, state, vm)
                }
                if (pendingLoans.isEmpty()) {
                    item { EmptyState("✅", "All caught up!", "No pending loan requests for this group.") }
                }
            }
        }
        
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun LoanRequestCard(loan: Loan, state: AdminUiState, vm: AdminViewModel) {
    val member = state.members.find { it.id == loan.memberId }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Forest.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(member?.fullName ?: "Unknown Member", fontWeight = FontWeight.Bold)
                    Text(formatZAR(loan.amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Forest)
                }
                Surface(color = Forest.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Text(loan.status.displayName, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Forest)
                }
            }
            
            HorizontalDivider(color = LightGray.copy(alpha = 0.2f))
            
            InfoRow("Purpose", loan.purpose ?: "Not specified")
            InfoRow("Date", loan.createdAt?.substringBefore("T") ?: "")

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.approveLoan(loan.id ?: "") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest),
                    enabled = !state.isProcessingLoan
                ) { Text("APPROVE") }
                
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = BorderStroke(1.dp, ErrorRed),
                    enabled = !state.isProcessingLoan
                ) { Text("REJECT") }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject Loan Request") },
            text = {
                SanibonaniTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = "Reason for rejection",
                    placeholder = "e.g. History of late payments"
                )
            },
            confirmButton = {
                Button(
                    onClick = { 
                        vm.rejectLoan(loan.id ?: "", rejectReason)
                        showRejectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("REJECT") }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
fun ActiveLoanCard(loan: Loan, state: AdminUiState) {
    val member = state.members.find { it.id == loan.memberId }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Forest.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(member?.fullName ?: "Member", fontWeight = FontWeight.Bold)
                Text(formatZAR(loan.amount), fontWeight = FontWeight.Black)
            }
            val progress = if (loan.totalToRepay > 0) (loan.totalRepaid / loan.totalToRepay).toFloat() else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Forest, trackColor = Forest.copy(alpha = 0.1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Repaid: ${formatZAR(loan.totalRepaid)}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Forest, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HistoryLoanCard(loan: Loan, state: AdminUiState) {
    val member = state.members.find { it.id == loan.memberId }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LightGray.copy(0.3f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(member?.fullName ?: "Unknown", fontWeight = FontWeight.Bold)
                Text("${formatZAR(loan.amount)} • ${loan.status.displayName}", style = MaterialTheme.typography.labelSmall, color = MidGray)
            }
            Text(loan.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray)
        }
    }
}
