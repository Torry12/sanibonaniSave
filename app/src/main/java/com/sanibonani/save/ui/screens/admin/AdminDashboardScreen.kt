package com.sanibonani.save.ui.screens.admin

import com.sanibonani.save.BuildConfig
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.data.utils.PaymentCalculation
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.viewmodel.AdminViewModel

@Composable
fun AdminDashboardScreen(
    onNavigateToPayment: (type: String, amount: String, groupId: String) -> Unit,
    onNavigateToMemberPortal: (groupId: String) -> Unit,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    vm: AdminViewModel
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val portalGroupId = state.group?.id
        ?: state.currentGroupId
        ?: state.managedGroups.firstOrNull { !it.id.isNullOrBlank() }?.id
    val portalGroupName = state.group?.name
        ?: state.managedGroups.firstOrNull { it.id == portalGroupId }?.name
    val adminTabs = listOf(
        0 to "Overview",
        1 to "Members",
        3 to "Messaging",
        7 to "Payouts",
        8 to "Loans",
        4 to "Viability",
        5 to "Account",
        2 to "Alerts",
        6 to "Settings"
    )
    val selectedTabPosition = adminTabs.indexOfFirst { (logicalIndex, _) -> logicalIndex == state.selectedTab }
        .takeIf { it >= 0 }
        ?: 0

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
            val chooserTitle = if (isPdf) "Open/Share Group PDF" else "Share Group Statement"
            context.startActivity(android.content.Intent.createChooser(intent, chooserTitle))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            if (state.group != null) {
                                Text(
                                    state.group!!.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (state.isLoading) {
                                Text("Loading...", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                "Group Admin Portal",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                            IconButton(
                                onClick = {
                                    val gid = state.group?.id ?: state.currentGroupId
                                    if (!gid.isNullOrBlank()) onNavigateToMemberPortal(gid)
                                }
                            ) {
                                Icon(Icons.Default.Person, contentDescription = "Member Portal")
                            }
                        IconButton(onClick = { vm.setTab(2) }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = if (state.notifications.isNotEmpty()) ErrorRed else Forest)
                        }
                        IconButton(onClick = { vm.setTab(5) }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Account")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                
                // Admin Fee Banner
                state.group?.takeIf { state.feeStatus == AdminFeeState.PENDING_ACTIVATION }?.let { group ->
                    AdminFeeBanner(
                        group = group,
                        status = state.feeStatus,
                        daysOverdue = state.daysOverdue,
                        onPayClick = { 
                            group.id?.let { id ->
                                onNavigateToPayment("registration", group.registrationFee.toString(), id)
                            }
                        }
                    )
                }

                // Group Switcher
                if (state.managedGroups.size > 1) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Forest.copy(0.3f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SwapHoriz, null, tint = Forest, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Switch Group: ${state.group?.name ?: ""}",
                                        color = Forest,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                    text = { 
                                        Column {
                                            Text(group.name, fontWeight = if (group.id == state.currentGroupId) FontWeight.Bold else FontWeight.Normal)
                                            Text(group.type.displayName, style = MaterialTheme.typography.labelSmall, color = MidGray)
                                        }
                                    },
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
                        selectedTabIndex = selectedTabPosition,
                        containerColor = Color.White,
                        contentColor = Forest,
                        edgePadding = 0.dp,
                        divider = {},
                        indicator = { tabPositions ->
                            if (selectedTabPosition < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabPosition]),
                                    color = Forest
                                )
                            }
                        }
                    ) {
                        adminTabs.forEach { (logicalIndex, label) ->
                            Tab(
                                selected = state.selectedTab == logicalIndex,
                                onClick = { vm.setTab(logicalIndex) },
                                text = { CompactTabText(label) }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!portalGroupId.isNullOrBlank()) {
                Surface(shadowElevation = 8.dp, tonalElevation = 2.dp, color = Color.White) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .navigationBarsPadding()
                            .imePadding(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { onNavigateToMemberPortal(portalGroupId) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Forest),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            val label = if (!portalGroupName.isNullOrBlank()) {
                                "Switch to Member Portal (${portalGroupName})"
                            } else {
                                "Switch to Member Portal"
                            }
                            Text(
                                text = label,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (state.selectedTab) {
                0 -> OverviewTab(state, vm, 
                    onPayClick = { amount -> 
                        onNavigateToPayment("registration", amount.toString(), state.group?.id ?: state.currentGroupId ?: "")
                    },
                    onMemberPortalClick = { (state.group?.id ?: state.currentGroupId)?.let { onNavigateToMemberPortal(it) } }
                )
                1 -> MembersTab(state, vm)
                2 -> NotificationsTab(state)
                3 -> MessagingTab(state, vm)
                4 -> ViabilityPlanningTab(state, vm)
                5 -> AccountTab(state, state.group, onLogout)
                6 -> SettingsTab(state, vm)
                7 -> PayoutTab(state, vm)
                8 -> LoansTab(state, vm)
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
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Something went wrong", fontWeight = FontWeight.Bold, color = ErrorRed)
                            Spacer(Modifier.height(8.dp))
                            Text(state.error ?: "Failed to load dashboard data.", textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { vm.selectGroup(state.currentGroupId ?: "") }) {
                                Text("Retry Connection")
                            }
                        }
                    }
                }
            }

            val isLocked = (state.feeStatus == AdminFeeState.SUSPENDED || state.feeStatus == AdminFeeState.PENDING_ACTIVATION) && 
                          state.selectedTab != 0 && state.selectedTab != 5
            
            if (isLocked) {
                val lockMessage = when (state.feeStatus) {
                    AdminFeeState.PENDING_ACTIVATION -> "Onboarding Incomplete. Please pay the registration fee on the Overview tab to activate your group."
                    AdminFeeState.SUSPENDED -> if (state.restoreRequested) 
                        "Suspension lift requested. Awaiting platform admin approval." 
                    else 
                        "Account Suspended. Please contact Platform Admin."
                    else -> ""
                }
                LockedTabOverlay(message = lockMessage)
            }
        }
    }

    // Member Details Dialog
    state.selectedMember?.let { member ->
        MemberDetailDialog(
            member = member,
            group = state.group,
            beneficiaries = state.selectedMemberBeneficiaries,
            documents = state.selectedMemberDocuments,
            calculation = state.selectedMemberCalculation,
            isEligibleForLoan = state.isEligibleForLoan,
            loanIneligibilityReason = state.loanIneligibilityReason,
            onDismiss = { vm.selectMember(null) },
            onVerifyDoc = { idx, approve -> 
                member.id?.let { id ->
                    vm.verifyDocument(id, idx, approve)
                }
            },
            onVerifyRelDoc = { docId, approve -> vm.verifyRelationalDocument(docId, approve) },
            onSendMessage = { msg, memberId -> 
                vm.updateMessageText(msg)
                vm.sendMessageToMember(memberId) 
            },
            messageText = state.messageText,
            onMessageChange = { vm.updateMessageText(it) },
            onBroadcast = { vm.broadcastMessage() },
            isSending = state.isSendingMessage,
            messageSuccess = state.messageSentSuccess,
            isSendingWhatsAppTest = state.isSendingWhatsAppTest,
            whatsAppTestResult = state.whatsAppTestResult,
            onEditBeneficiary = { vm.startEditBeneficiary(it) },
            vm = vm
        )
    }

    // Beneficiary Edit Dialog
    state.editingBeneficiary?.let { beneficiary ->
        BeneficiaryEditDialog(
            beneficiary = beneficiary,
            onDismiss = { vm.startEditBeneficiary(null) },
            onSave = { vm.saveBeneficiary() },
            onUpdate = { vm.updateEditingBeneficiary(it) },
            isSaving = state.isSavingBeneficiary
        )
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text("Error") },
            text = { Text(state.error ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) { Text("OK") }
            }
        )
    }
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
            ViabilityResultCard(plan, onApply = { vm.applySuggestedContribution() })
        }
    }
}

@Composable
fun ViabilityResultCard(plan: ViabilityPlan, onApply: () -> Unit) {
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
            
            if (plan.messages.isNotEmpty()) {
                HorizontalDivider(color = Forest.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))
                plan.messages.forEach { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MidGray)
                }
            }

            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Forest)
            ) {
                Text("Apply Suggested Contribution")
            }
        }
    }
}

@Composable
fun AdminFeeBanner(group: Group, status: AdminFeeState, daysOverdue: Int, onPayClick: () -> Unit) {
    val (bgColor, textColor, message) = when (status) {
        AdminFeeState.PAID -> Triple(Forest, Color.White, "✅ Platform fee paid — next due soon")
        AdminFeeState.DUE -> Triple(WarningYellow, Color.Black, "⚠️ Platform fee due — please pay R150.00")
        AdminFeeState.OVERDUE -> Triple(ErrorRed, Color.White, "🚨 Platform fee overdue ($daysOverdue days)!")
        AdminFeeState.WARNING -> Triple(WarningYellow, Color.Black, "⚠️ Platform fee due soon — please pay R150.00")
        AdminFeeState.SUSPENDED -> Triple(Color.Black, Color.White, "🚫 Account Suspended — Pay R150.00 to restore access")
        AdminFeeState.PENDING_ACTIVATION -> Triple(ForestMid, Color.White, "🚀 Onboarding — Pay R${group.registrationFee.toInt()} to activate group")
    }

    Surface(
        color = bgColor,
        onClick = if (status != AdminFeeState.PAID) onPayClick else ({})
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(message, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            if (status != AdminFeeState.PAID) {
                Text("PAY NOW", color = textColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun OverviewTab(state: AdminUiState, vm: AdminViewModel, onPayClick: (Double) -> Unit, onMemberPortalClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Activation/Onboarding Card
        if (state.feeStatus == AdminFeeState.PENDING_ACTIVATION) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Forest.copy(0.1f)),
                border = BorderStroke(1.dp, Forest)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RocketLaunch, null, tint = Forest, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Activate Your Group", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                    }
                    Text("Your group is almost ready! Pay the one-time registration fee to start onboarding members, capturing details, and managing funds.", style = MaterialTheme.typography.bodySmall)
                    
                    Button(
                        onClick = { 
                            val fee = state.group?.registrationFee ?: 700.0
                            onPayClick(fee) 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Forest),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Pay Registration Fee (R${state.group?.registrationFee?.toInt() ?: 700})")
                    }
                }
            }
        }

        // Member Portal Link
        Card(
            onClick = onMemberPortalClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Forest.copy(0.05f)),
            border = BorderStroke(1.dp, Forest.copy(0.2f))
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Forest.copy(0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, tint = Forest, modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Switch to Member View", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Forest)
                    Text("Manage your personal contributions and beneficiaries", style = MaterialTheme.typography.labelSmall, color = MidGray)
                }
                Icon(Icons.Default.ChevronRight, null, tint = Forest)
            }
        }

        // Balance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = WarningYellow.copy(0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("💰", fontSize = 20.sp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Balance", style = MaterialTheme.typography.labelMedium, color = MidGray)
                        Text(formatZAR(state.group?.balance ?: 0.0), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Forest)
                        Text("As of today", style = MaterialTheme.typography.labelSmall, color = WarningYellow)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Collection Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Surface(shape = CircleShape, color = Forest.copy(0.1f), modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.TrendingUp, "", tint = Forest, modifier = Modifier.size(16.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Collection", style = MaterialTheme.typography.labelMedium, color = MidGray)
                    Text("${state.metrics.paymentRatePct.toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Forest)
                    Text("This month", style = MaterialTheme.typography.labelSmall, color = Forest)
                }
            }

            // Members Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Surface(shape = CircleShape, color = Color(0xFFE3F2FD), modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, "", tint = Color(0xFF1976D2), modifier = Modifier.size(16.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Members", style = MaterialTheme.typography.labelMedium, color = MidGray)
                    Text("${state.group?.currentMembers ?: 0}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Active now", style = MaterialTheme.typography.labelSmall, color = MidGray)
                }
            }
        }

        if (state.feeStatus == AdminFeeState.SUSPENDED) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(0.1f)),
                border = BorderStroke(1.dp, ErrorRed)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🚨 Account Suspended", style = MaterialTheme.typography.titleSmall, color = ErrorRed, fontWeight = FontWeight.Bold)
                    Text("Your admin panel features are limited until the platform fee is paid.", style = MaterialTheme.typography.bodySmall)
                    
                    if (state.restoreRequested) {
                        Text("Lifting request sent to platform admin.", color = Forest, fontWeight = FontWeight.Bold)
                    } else {
                        Button(
                            onClick = { vm.requestRestore() },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Request Suspension Lift")
                        }
                    }
                }
            }
        }

        Text("Quick Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                icon = Icons.Default.Share,
                label = "Export CSV",
                modifier = Modifier.weight(1f)
            ) { vm.exportGroupStatement() }
            ActionButton(
                icon = Icons.Default.PictureAsPdf,
                label = "Download PDF",
                modifier = Modifier.weight(1f)
            ) { vm.downloadPdfStatement() }
            ActionButton(
                icon = Icons.Filled.Message,
                label = "Broadcast",
                modifier = Modifier.weight(1f)
            ) { vm.setTab(3) }
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .background(Color.White, CircleShape)
                .clip(CircleShape)
        ) {
            Icon(icon, label, tint = Forest)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MidGray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MembersTab(state: AdminUiState, vm: AdminViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredMembers = state.members.filter { 
        it.fullName.contains(searchQuery, ignoreCase = true) || 
        (it.idNumber?.contains(searchQuery) == true)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(16.dp)) {
            SanibonaniTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Search members...",
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(filteredMembers) { member ->
                val calculation = state.memberCalculations[member.id ?: ""]
                MemberItem(member, calculation) { vm.selectMember(member) }
            }
        }
    }
}

@Composable
fun MemberItem(member: Member, calculation: PaymentCalculation?, onClick: () -> Unit) {
    val statusColor = when (member.status) {
        MemberStatus.ACTIVE -> if (calculation?.isOverdue == true) ErrorRed else Forest
        MemberStatus.PROBATION -> WarningYellow
        MemberStatus.PENDING_PAYMENT -> Color.Gray
        else -> ErrorRed
    }

    Surface(
        onClick = onClick,
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = statusColor.copy(0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(member.fullName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = statusColor)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(member.fullName, fontWeight = FontWeight.Bold)
                Text(member.idNumber ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(member.status.displayName, color = statusColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                calculation?.let {
                    if (it.shortfall > 0) {
                        Text("Due: ${formatZAR(it.totalDueNow)}", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                    } else if (it.overpayment > 0) {
                        Text("Credit: ${formatZAR(it.overpayment)}", color = Forest, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun MemberDetailDialog(
    member: Member,
    group: Group?,
    beneficiaries: List<Beneficiary>,
    documents: List<MemberDocument>,
    calculation: PaymentCalculation?,
    isEligibleForLoan: Boolean = false,
    loanIneligibilityReason: String? = null,
    onDismiss: () -> Unit,
    onVerifyDoc: (Int, Boolean) -> Unit,
    onVerifyRelDoc: (String, Boolean) -> Unit,
    onSendMessage: (String, String) -> Unit,
    messageText: String,
    onMessageChange: (String) -> Unit,
    onBroadcast: () -> Unit,
    isSending: Boolean,
    messageSuccess: Boolean,
    isSendingWhatsAppTest: Boolean,
    whatsAppTestResult: String?,
    onEditBeneficiary: (Beneficiary) -> Unit,
    vm: AdminViewModel? = null
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val profileUrl = member.profilePhotoUrl
                            val profileRequest = remember(profileUrl) {
                                if (profileUrl.isNullOrBlank()) null
                                else ImageRequest.Builder(context)
                                    .data(profileUrl)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(true)
                                    .build()
                            }
                            val personPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Person)

                            AsyncImage(
                                model = profileRequest,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Forest.copy(alpha = 0.2f), CircleShape),
                                contentScale = ContentScale.Crop,
                                placeholder = personPainter,
                                error = personPainter,
                                fallback = personPainter
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(member.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status Overview
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Financial Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            DetailRow("Status", member.status.displayName)
                            DetailRow("Total Paid", formatZAR(member.totalPaid ?: 0.0))
                            calculation?.let {
                                DetailRow("Shortfall", formatZAR(it.shortfall))
                                DetailRow("Overpayment", formatZAR(it.overpayment))
                                DetailRow("Next Due", it.nextDueDate)
                                if (it.isOverdue) {
                                    DetailRow("Total Due Now", formatZAR(it.totalDueNow))
                                }
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Forest.copy(0.1f))
                            
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Loan Eligibility", style = MaterialTheme.typography.bodySmall, color = MidGray)
                                val (color, text) = if (isEligibleForLoan) {
                                    Forest to "✅ QUALIFIED"
                                } else {
                                    ErrorRed to "❌ NOT QUALIFIED"
                                }
                                Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            loanIneligibilityReason?.let { reason ->
                                Text(reason, style = MaterialTheme.typography.labelSmall, color = ErrorRed, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                        DetailSection("Personal Information") {
                        DetailRow("ID Number", member.idNumber ?: "N/A")
                        DetailRow("Phone", member.phone)
                        DetailRow("Joined", member.joinedAt?.substringBefore("T") ?: "N/A")
                        DetailRow("Address", "${member.street}, ${member.suburb}, ${member.city}, ${member.province}")
                    }

                    // Beneficiaries
                    DetailSection("Beneficiaries (${beneficiaries.size})") {
                        beneficiaries.forEach { b ->
                            Surface(
                                onClick = { onEditBeneficiary(b) },
                                shape = RoundedCornerShape(8.dp),
                                color = Forest.copy(0.05f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(b.fullName, fontWeight = FontWeight.Bold)
                                        Text("${b.relationship} • ${b.idNumber ?: "N/A"}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Icon(Icons.Default.Edit, null, tint = Forest, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (beneficiaries.isEmpty()) {
                            Text("No beneficiaries registered.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                        }
                    }

                    // Documents
                    DetailSection("Documents") {
                        // Legacy Documents (Indexed 1-5)
                        for (i in 1..5) {
                            val url = when(i) {
                                1 -> member.document1Url; 2 -> member.document2Url; 3 -> member.document3Url; 4 -> member.document4Url; 5 -> member.document5Url; else -> null
                            }
                            val status = when(i) {
                                1 -> member.document1Status; 2 -> member.document2Status; 3 -> member.document3Status; 4 -> member.document4Status; 5 -> member.document5Status; else -> DocumentStatus.PENDING
                            }
                            val label = when(i) {
                                1 -> "Identity Document (ID)"
                                2 -> "Proof of Residence"
                                3 -> "Beneficiary Form / Documents"
                                4 -> "Marriage Certificate"
                                5 -> "Group Constitution (Signed)"
                                else -> ""
                            }

                            if (url != null) {
                                DocumentAdminCard(
                                    label = label,
                                    url = url,
                                    status = status,
                                    onVerify = { approve -> onVerifyDoc(i, approve) },
                                    onDownload = { dUrl, dLabel -> 
                                        val (url, headers) = vm!!.downloadMemberDocument(dUrl, dLabel)
                                        val extension = url.substringAfterLast(".", "pdf")
                                        val fileName = "${dLabel.replace(" ", "_")}_${System.currentTimeMillis()}.$extension"
                                        val mimeType = when (extension.lowercase()) {
                                            "pdf" -> "application/pdf"
                                            "jpg", "jpeg" -> "image/jpeg"
                                            "png" -> "image/png"
                                            else -> "application/octet-stream"
                                        }
                                        com.sanibonani.save.domain.utils.FileDownloader.downloadFile(context, url, fileName, mimeType, headers)
                                    }
                                )
                            }
                        }
                        
                        // New Relational Documents
                        documents.forEach { doc ->
                            val docId = doc.id
                            if (docId != null) {
                                DocumentAdminCard(
                                    label = doc.label,
                                    url = doc.documentUrl,
                                    status = doc.status,
                                    onVerify = { approve -> onVerifyRelDoc(docId, approve) },
                                    onDownload = { dUrl, dLabel -> 
                                        val (url, headers) = vm!!.downloadMemberDocument(dUrl, dLabel)
                                        val extension = url.substringAfterLast(".", "pdf")
                                        val fileName = "${dLabel.replace(" ", "_")}_${System.currentTimeMillis()}.$extension"
                                        val mimeType = when (extension.lowercase()) {
                                            "pdf" -> "application/pdf"
                                            "jpg", "jpeg" -> "image/jpeg"
                                            "png" -> "image/png"
                                            else -> "application/octet-stream"
                                        }
                                        com.sanibonani.save.domain.utils.FileDownloader.downloadFile(context, url, fileName, mimeType, headers)
                                    }
                                )
                            }
                        }
                    }

                    // Direct Message
                    DetailSection("Send Direct Message") {
                        SanibonaniTextField(
                            value = messageText,
                            onValueChange = onMessageChange,
                            label = "Message text...",
                            modifier = Modifier.height(100.dp)
                        )
                        if (messageSuccess) {
                            Text("Message sent!", color = Forest, style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { 
                                member.id?.let { id ->
                                    onSendMessage(messageText, id)
                                }
                            },
                            enabled = messageText.isNotBlank() && !isSending,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Forest)
                        ) {
                            if (isSending) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            else Text("Send WhatsApp & Push")
                        }

                        if (BuildConfig.DEBUG) {
                            OutlinedButton(
                                onClick = { vm?.sendWhatsAppTestToSelectedMember() },
                                enabled = !isSendingWhatsAppTest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isSendingWhatsAppTest) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Debug: Send WhatsApp Test")
                                }
                            }

                            whatsAppTestResult?.let { result ->
                                val resultType = if (result.contains("success", ignoreCase = true)) InfoType.SUCCESS else InfoType.INFO
                                InfoBox(result, resultType)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentRow(label: String, status: DocumentStatus, onVerify: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(status.name, style = MaterialTheme.typography.labelSmall, color = when(status) {
                DocumentStatus.VERIFIED -> Forest
                DocumentStatus.REJECTED -> ErrorRed
                else -> WarningYellow
            })
        }
        
        if (status == DocumentStatus.PENDING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { onVerify(false) }) { Icon(Icons.Default.Close, null, tint = ErrorRed) }
                IconButton(onClick = { onVerify(true) }) { Icon(Icons.Default.Check, null, tint = Forest) }
            }
        }
    }
}

@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Forest)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodySmall,
            color = MidGray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AnalyticsTab(state: AdminUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("📊 Financial Analytics", style = MaterialTheme.typography.headlineSmall, color = Forest, fontWeight = FontWeight.Bold)
        
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardMetricRow("Expected Annual Claims", formatZAR(state.metrics.expectedAnnualClaims), Forest)
                DashboardMetricRow("Pure Premium (per member)", formatZAR(state.metrics.purePremium), Forest)
                DashboardMetricRow("Gross Premium (incl. admin)", formatZAR(state.metrics.grossPremium), Forest)
                HorizontalDivider()
                DashboardMetricRow("Solvency Margin", "${state.metrics.solvencyMarginPct.toInt()}%", if (state.metrics.solvencyMarginPct > 100) Forest else ErrorRed)
                DashboardMetricRow("Reserve Adequacy", "${state.metrics.reserveAdequacyPct.toInt()}%", if (state.metrics.reserveAdequacyPct > 100) Forest else WarningYellow)
            }
        }

        // Potential Growth
        Card(colors = CardDefaults.cardColors(containerColor = Forest.copy(0.05f))) {
            Column(Modifier.padding(16.dp)) {
                Text("Group Health Score", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { (state.metrics.paymentRatePct / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                    color = if (state.metrics.paymentRatePct > 80) Forest else WarningYellow,
                    trackColor = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your group has a historical payment rate of ${state.metrics.paymentRatePct.toInt()}% based on current reserves.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun DashboardMetricRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(0.65f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MessagingTab(state: AdminUiState, vm: AdminViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("📢 Broadcast Message")
        Text("Send a message to all members via App Notifications and WhatsApp.", style = MaterialTheme.typography.bodyMedium, color = MidGray)

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = state.messageText,
                    onValueChange = { vm.updateMessageText(it) },
                    label = "Your message...",
                    modifier = Modifier.height(150.dp)
                )

                if (state.messageSentSuccess) {
                    InfoBox("Message broadcasted successfully!", com.sanibonani.save.ui.components.InfoType.SUCCESS)
                }

                SanibonaniButton(
                    text = "Broadcast to All Members",
                    onClick = { vm.broadcastMessage() },
                    isLoading = state.isSendingMessage,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text("Recent Broadcasts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f)) {
            items(state.memberMessages) { msg ->
                Card(
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.5f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(msg.message, style = MaterialTheme.typography.bodyMedium)
                        Text(msg.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsTab(state: AdminUiState) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SectionHeading("🔔 System Notifications")
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.notifications) { notif ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Forest.copy(0.1f), modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Notifications, null, tint = Forest, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(notif.message, style = MaterialTheme.typography.bodyMedium)
                            Text(notif.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray)
                        }
                    }
                }
            }
            if (state.notifications.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No new notifications", color = MidGray)
                    }
                }
            }
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("👤 Group Profile")

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("Group Name", group?.name ?: "")
                InfoRow("Group Type", group?.type?.name ?: "")
                InfoRow("Location", "${group?.city}, ${group?.province}")
                InfoRow("Members", "${group?.currentMembers} / ${group?.maxMembers}")
            }
        }

        Text("🏦 Banking Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("Bank", group?.bankName ?: "Not set")
                InfoRow("Account Number", group?.accountNumber ?: "Not set")
                InfoRow("Branch Code", group?.branchCode ?: "Not set")
                InfoRow("Account Type", group?.accountType ?: "Not set")
            }
        }
        
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
            border = BorderStroke(1.dp, ErrorRed)
        ) {
            Text("Log Out")
        }
    }
}

@Composable
fun SettingsTab(state: AdminUiState, vm: AdminViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("⚙️ Group Settings")

        // General Section
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📝 General", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                
                SanibonaniTextField(
                    value = state.group?.name ?: "",
                    onValueChange = { /* Disabled for now */ },
                    label = "Group Name (Read-only)",
                    enabled = false
                )
            }
        }

        // Fees Section
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("💰 Fee Structure", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                
                SanibonaniTextField(
                    value = state.settings.joiningFee,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateSetting("joiningFee", it)
                        }
                    },
                    label = "Joining Fee (R)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                SanibonaniTextField(
                    value = state.settings.monthlyContribution,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateSetting("monthlyContribution", it)
                        }
                    },
                    label = "Monthly Contribution (R)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                SanibonaniTextField(
                    value = state.settings.lateFee,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateSetting("lateFee", it)
                        }
                    },
                    label = "Late Fee (R)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                SanibonaniTextField(
                    value = state.settings.lateFeeGraceDays,
                    onValueChange = { vm.updateSetting("lateFeeGraceDays", it.filter { c -> c.isDigit() }) },
                    label = "Grace Days",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (state.group?.type == GroupType.BURIAL_SOCIETY) {
                    HorizontalDivider(color = Forest.copy(0.1f))
                    Text("🕊️ Burial Society Settings", style = MaterialTheme.typography.labelLarge, color = Forest)
                    
                    SanibonaniTextField(
                        value = state.settings.maxBeneficiaries,
                        onValueChange = { vm.updateSetting("maxBeneficiaries", it.filter { c -> c.isDigit() }) },
                        label = "Max Beneficiaries per Member",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    SanibonaniTextField(
                        value = state.settings.beneficiaryIncreasePct,
                        onValueChange = { 
                            if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                                vm.updateSetting("beneficiaryIncreasePct", it)
                            }
                        },
                        label = "Increase % for Beneficiaries > 65",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        }

        // Governance Section
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("⚖️ Governance", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                
                SanibonaniTextField(
                    value = state.settings.maxMembers,
                    onValueChange = { vm.updateSetting("maxMembers", it.filter { c -> c.isDigit() }) },
                    label = "Maximum Members",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Surface(
                    onClick = { vm.updateSetting("allowPartialPayment", !state.settings.allowPartialPayment) },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.settings.allowPartialPayment,
                            onCheckedChange = { vm.updateSetting("allowPartialPayment", it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Allow Partial Payments", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("If disabled, members must pay the full monthly amount to stay active.", style = MaterialTheme.typography.labelSmall, color = MidGray)
                        }
                    }
                }

                SanibonaniTextField(
                    value = state.settings.probationMonths,
                    onValueChange = { 
                        vm.updateSetting("probationMonths", it.filter { c -> c.isDigit() })
                    },
                    label = "Probation Period (Months)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        // Viability Goals Section
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🎯 Viability Goals", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                
                SanibonaniTextField(
                    value = state.settings.goalAmount,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateSetting("goalAmount", it)
                        }
                    },
                    label = "Target Goal Amount (R)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                SanibonaniTextField(
                    value = state.settings.periodMonths,
                    onValueChange = { 
                        val filtered = it.filter { c -> c.isDigit() }
                        vm.updateSetting("periodMonths", filtered)
                    },
                    label = "Target Time Period (Months)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        
        if (state.isSaving) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = Forest)
        } else {
            SanibonaniButton(
                text = "Save All Changes",
                onClick = { vm.saveSettings() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.saveSuccess) {
            InfoBox("Settings saved successfully!", InfoType.SUCCESS)
        }

        // Sync Section
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Reset Local Cache", style = MaterialTheme.typography.titleSmall, color = ErrorRed, fontWeight = FontWeight.Bold)
                Text(
                    "Clears all locally stored data. Use this if you encounter sync issues. This will not delete data from the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MidGray
                )
                
                var showConfirm by remember { mutableStateOf(false) }
                
                if (!showConfirm) {
                    OutlinedButton(
                        onClick = { showConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        border = BorderStroke(1.dp, ErrorRed)
                    ) {
                        Text("Reset Local Data")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showConfirm = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MidGray)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { 
                                vm.resetLocalData()
                                showConfirm = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                        ) {
                            Text("Confirm Reset")
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun BeneficiaryEditDialog(
    beneficiary: Beneficiary,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onUpdate: ((Beneficiary) -> Beneficiary) -> Unit,
    isSaving: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (beneficiary.id == null) "Add Beneficiary" else "Edit Beneficiary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = beneficiary.fullName,
                    onValueChange = { text -> onUpdate { it.copy(fullName = text) } },
                    label = "Full Name"
                )
                SanibonaniTextField(
                    value = beneficiary.idNumber ?: "",
                    onValueChange = { text -> onUpdate { it.copy(idNumber = text) } },
                    label = "ID Number"
                )
                SanibonaniTextField(
                    value = beneficiary.relationship ?: "",
                    onValueChange = { text -> onUpdate { it.copy(relationship = text) } },
                    label = "Relationship"
                )
                DatePickerField(
                    label = "Date of Birth",
                    value = beneficiary.dateOfBirth ?: "",
                    onValueChange = { text -> onUpdate { it.copy(dateOfBirth = text) } }
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isSaving) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DatePickerField(label: String, value: String, onValueChange: (String) -> Unit) {
    // Basic implementation for now
    SanibonaniTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = "YYYY-MM-DD"
    )
}

@Composable
fun LockedTabOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp), tint = ErrorRed)
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PayoutTab(state: AdminUiState, vm: AdminViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeading("Request Payout")
        Text("Request disbursement of group funds from the platform to the group's bank account.", style = MaterialTheme.typography.bodyMedium, color = MidGray)
        
        Spacer(Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                val amountVal = state.payoutAmount.toDoubleOrNull() ?: 0.0
                val balanceVal = state.group?.balance ?: 0.0
                OutlinedTextField(
                    value = state.payoutAmount,
                    onValueChange = { vm.updatePayoutAmount(it) },
                    label = { Text("Amount (R)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountVal > balanceVal,
                    supportingText = {
                        if (amountVal > balanceVal) {
                            Text("Insufficient balance. Current: R$balanceVal", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Current Balance: R$balanceVal")
                        }
                    }
                )
                
                Spacer(Modifier.height(16.dp))
                Text("Banking Details", fontWeight = FontWeight.Bold)
                
                OutlinedTextField(
                    value = state.payoutBankName,
                    onValueChange = { vm.updatePayoutBank(it) },
                    label = { Text("Bank Name") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = state.payoutBankName.isBlank() && state.error?.contains("Bank Name") == true
                )
                
                OutlinedTextField(
                    value = state.payoutAccountNo,
                    onValueChange = { if (it.all { c -> c.isDigit() }) vm.updatePayoutAccount(it) },
                    label = { Text("Account Number") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.error?.contains("Account Number") == true,
                    supportingText = {
                        if (state.error?.contains("Account Number") == true) {
                            Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("7-13 digits")
                        }
                    }
                )
                
                OutlinedTextField(
                    value = state.payoutBranchCode,
                    onValueChange = { if (it.all { c -> c.isDigit() }) vm.updatePayoutBranch(it) },
                    label = { Text("Branch Code") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.error?.contains("Branch Code") == true,
                    supportingText = {
                        if (state.error?.contains("Branch Code") == true) {
                            Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("6 digits")
                        }
                    }
                )
                
                Spacer(Modifier.height(24.dp))
                
                Button(
                    onClick = { vm.submitPayoutRequest() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isRequestingPayout && 
                             state.payoutAmount.isNotEmpty() && 
                             (state.payoutAmount.toDoubleOrNull() ?: 0.0) <= (state.group?.balance ?: 0.0) &&
                             (state.payoutAmount.toDoubleOrNull() ?: 0.0) > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Forest)
                ) {
                    if (state.isRequestingPayout) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Submit Request")
                    }
                }
                
                if (state.payoutRequestSuccess) {
                    Text("Request submitted successfully!", color = Forest, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        SectionHeading("Payout History")

        if (state.payouts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No payout requests found.", color = MidGray)
                TextButton(onClick = { vm.refreshPayouts() }) {
                    Text("Refresh", color = Forest)
                }
            }
        } else {
            state.payouts.sortedByDescending { it.createdAt }.forEach { payout ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LightGray)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("R${payout.amount}", fontWeight = FontWeight.Bold)
                            Text("Requested: ${payout.createdAt}", style = MaterialTheme.typography.labelSmall)
                        }
                        
                        val statusColor = when(payout.status) {
                            PayoutStatus.PENDING -> MidGray

                            PayoutStatus.GROUP_APPROVED -> InfoBlue
                            PayoutStatus.PROCESSING -> Forest
                            PayoutStatus.COMPLETED -> Forest
                            PayoutStatus.FAILED -> ErrorRed
                            PayoutStatus.CANCELLED -> MidGray
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                color = statusColor.copy(0.1f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    payout.status.name,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            
                            when (payout.status) {
                                PayoutStatus.PENDING -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { vm.approveAndEscalatePayoutRequest(payout.id ?: "") },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Approve & Escalate", color = Forest, style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(
                                            onClick = { vm.cancelPayoutRequest(payout.id ?: "") },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Cancel", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                PayoutStatus.GROUP_APPROVED -> {
                                    Text(
                                        "Escalated to platform",
                                        color = InfoBlue,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoansTab(state: AdminUiState, vm: AdminViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeading("Loan Management")
        Text(
            "Review and manage member loan requests.",
            style = MaterialTheme.typography.bodyMedium,
            color = MidGray
        )

        val pendingLoans = state.groupLoans.filter { it.status == LoanStatus.PENDING }
        val activeLoans = state.groupLoans.filter { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID || it.status == LoanStatus.OVERDUE }
        val completedLoans = state.groupLoans.filter { it.status == LoanStatus.COMPLETED || it.status == LoanStatus.REJECTED }

        if (pendingLoans.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Pending Requests", fontWeight = FontWeight.Bold, color = Forest)
            pendingLoans.forEach { loan ->
                LoanRequestCard(loan, state, vm)
            }
        }

        if (activeLoans.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Active Loans", fontWeight = FontWeight.Bold, color = Forest)
            activeLoans.forEach { loan ->
                ActiveLoanCard(loan, state)
            }
        }

        if (completedLoans.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("History", fontWeight = FontWeight.Bold, color = MidGray)
            completedLoans.forEach { loan ->
                HistoryLoanCard(loan, state)
            }
        }

        if (state.groupLoans.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No loan records found", color = MidGray)
            }
        }
    }
}

@Composable
fun LoanRequestCard(loan: Loan, state: AdminUiState, vm: AdminViewModel) {
    val member = state.members.find { it.id == loan.memberId }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LightGray)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(member?.fullName ?: "Unknown Member", fontWeight = FontWeight.Bold)
                    Text("Requested R${loan.amount}", style = MaterialTheme.typography.titleMedium, color = Forest)
                }
                Surface(
                    color = Forest.copy(0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "PENDING",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Forest,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            InfoRow("Total Repayable", "R${loan.totalToRepay}")
            InfoRow("Monthly Repayment", "R${loan.monthlyRepayment}")
            InfoRow("Purpose", loan.purpose ?: "Not specified")
            InfoRow("Date", loan.createdAt?.take(10) ?: "")

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = BorderStroke(1.dp, ErrorRed)
                ) {
                    Text("Reject")
                }
                Button(
                    onClick = { vm.approveLoan(loan.id ?: "") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest),
                    enabled = !state.isProcessingLoan
                ) {
                    if (state.isProcessingLoan) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Approve")
                    }
                }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { 
                showRejectDialog = false
                vm.clearLoanProcessing()
            },
            title = { Text("Reject Loan Request") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("Reason for rejection") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.rejectLoan(loanId = loan.id ?: "", reason = rejectReason)
                        showRejectDialog = false
                    },
                    enabled = rejectReason.isNotBlank() && !state.isProcessingLoan
                ) {
                    Text("Confirm Rejection", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showRejectDialog = false
                    vm.clearLoanProcessing()
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ActiveLoanCard(loan: Loan, state: AdminUiState) {
    val member = state.members.find { it.id == loan.memberId }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LightGray)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(member?.fullName ?: "Unknown Member", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Balance: R${loan.balanceRemaining}", style = MaterialTheme.typography.titleMedium, color = Forest, modifier = Modifier.weight(1f))
                Surface(
                    color = when(loan.status) {
                        LoanStatus.OVERDUE -> ErrorRed.copy(0.1f)
                        else -> Forest.copy(0.1f)
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        loan.status.displayName.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (loan.status == LoanStatus.OVERDUE) ErrorRed else Forest,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { loan.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Forest,
                trackColor = LightGray,
            )
            Spacer(Modifier.height(4.dp))
            Text("Repaid: R${loan.totalRepaid} / R${loan.totalToRepay}", style = MaterialTheme.typography.labelSmall, color = MidGray)
        }
    }
}

@Composable
fun HistoryLoanCard(loan: Loan, state: AdminUiState) {
    val member = state.members.find { it.id == loan.memberId }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = LightGray.copy(0.3f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(member?.fullName ?: "Unknown Member", style = MaterialTheme.typography.bodyMedium)
                Text("R${loan.amount} - ${loan.status.displayName}", style = MaterialTheme.typography.labelSmall, color = MidGray)
            }
            Text(loan.createdAt?.take(10) ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodySmall,
            color = MidGray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
