package com.sanibonani.save.ui.screens.admin

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.viewmodel.PlatformAdminUiState
import com.sanibonani.save.viewmodel.PlatformAdminViewModel

@Composable
fun PlatformAdminScreen(
    onNavigateToCreateAdmin: () -> Unit,
    onLogout: () -> Unit,
    onImpersonateGroupAdmin: (groupId: String) -> Unit,
    onImpersonateMember: (memberId: String, groupId: String) -> Unit,
    onOpenMemberPortalFromDisbursement: (groupId: String, payoutId: String?) -> Unit,
    vm: PlatformAdminViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val pendingLoanRequests = state.loanRequestsByGroup.values.flatten().count { it.status == LoanStatus.PENDING }
    val disbursementBacklogCount = state.payouts.size + state.escalatedClaims.size + pendingLoanRequests
    val highRiskMembersCount = state.memberBehaviorInsights.count { it.riskBand == "High" }
    val tabs = listOf(
        "Platform Analytics",
        "All Groups",
        if (disbursementBacklogCount > 0) "Disbursements ($disbursementBacklogCount)" else "Disbursements",
        "Financial Ledger",
        "Fee Management",
        if (highRiskMembersCount > 0) "Maintenance ($highRiskMembersCount)" else "Maintenance"
    )

    Scaffold(
        topBar = {
            SanibonaniTopBar(
                title = "Platform Administration",
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = Forest)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(Cream).padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = Color.White,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = state.selectedTab == i,
                        onClick = { vm.setTab(i) },
                        text = { Text(t, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Forest)
                }
            } else {
                when (state.selectedTab) {
                    0 -> PlatformAnalyticsTab(state.analytics)
                    1 -> AllGroupsTab(state.groups, vm, state)
                    2 -> DisbursementsTab(
                        payouts = state.payouts,
                        groups = state.groups,
                        claims = state.escalatedClaims,
                        loanRequestsByGroup = state.loanRequestsByGroup,
                        loanMemberNames = state.loanMemberNames,
                        memberBehaviorInsights = state.filteredMemberBehaviorInsights,
                        selectedRiskFilter = state.selectedRiskFilter,
                        isLoadingLoanRequests = state.isLoadingLoanRequests,
                        isProcessingLoanRequest = state.isProcessingLoanRequest,
                        vm = vm,
                        onOpenMemberPortal = onOpenMemberPortalFromDisbursement,
                        onVerifyMember = onImpersonateMember,
                        onRiskFilterChanged = vm::setRiskFilter
                    )
                    3 -> PlatformLedgerTab(state)
                    4 -> FeeManagementTab(state, vm)
                    5 -> MaintenanceTab(state, vm, onNavigateToCreateAdmin, onLogout, onImpersonateGroupAdmin, onImpersonateMember)
                    else -> CenterPlaceholder("Unknown Tab")
                }
            }
        }
    }
}

@Composable
private fun PlatformAnalyticsTab(analytics: PlatformAnalytics) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Network-Wide KPIs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("🏢", "Total Groups", "${analytics.totalGroups}", "Active across SA", accentColor = Forest, modifier = Modifier.weight(1f))
                StatCard("👥", "Total Members", "${analytics.totalMembers}", "Enrolled users", accentColor = Forest, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("💰", "Network Balance", formatZAR(analytics.totalBalance), "Group holdings", accentColor = Gold, modifier = Modifier.weight(1f))
                StatCard("📈", "Platform Revenue", formatZAR(analytics.totalPlatformFees), "From monthly fees", accentColor = SuccessGreen, modifier = Modifier.weight(1f))
            }
        }
        
        item {
            StatCard("🛡️", "Avg Risk Score", "${analytics.averageRiskScore.toInt()}/100", "Actuarial platform health", accentColor = InfoBlue)
        }

        item {
            SectionTitle("Group Distribution", "By province")
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    analytics.provinceDistribution.forEach { (prov, count) ->
                        DistributionRow(prov ?: "Unknown", count)
                    }
                }
            }
        }

        item {
            SectionTitle("Market Segments", "By group type")
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    analytics.groupTypeDistribution.forEach { (type, count) ->
                        DistributionRow(type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, count)
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionRow(label: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text("$count groups", fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(thickness = 0.5.dp, color = LightGray)
}

@Composable
private fun AllGroupsTab(groups: List<Group>, vm: PlatformAdminViewModel, state: PlatformAdminUiState) {
    var groupToSuspend by remember { mutableStateOf<Group?>(null) }
    var showMetricsFor by remember { mutableStateOf<Group?>(null) }
    
    val filteredGroups = remember(groups, state.searchQuery) {
        if (state.searchQuery.isBlank()) groups
        else groups.filter { it.name.contains(state.searchQuery, ignoreCase = true) || it.province?.contains(state.searchQuery, ignoreCase = true) == true }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { vm.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search groups by name or province...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Box(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredGroups, key = { it.id ?: it.hashCode() }) { group ->
                    ModernNavigationLink(
                        title = group.name,
                        subtitle = "${group.type.displayName} • ${group.city}",
                        icon = Icons.Default.Groups,
                        onClick = { 
                            showMetricsFor = group
                            vm.fetchGroupMetrics(group.id ?: "")
                        },
                        accentColor = if (group.isPlatformSuspended) ErrorRed else Forest,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }

    groupToSuspend?.let { group ->
        var reason by remember(group.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { groupToSuspend = null },
            title = { Text("Suspend Group: ${group.name}") },
            text = {
                Column {
                    Text("Provide a reason for suspension. This will be sent to the group admin.")
                    Spacer(Modifier.height(8.dp))
                    SanibonaniTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = "Suspension Reason",
                        placeholder = "e.g. Non-payment of fees"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.suspendGroup(group.id ?: "", reason)
                        groupToSuspend = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    enabled = reason.isNotBlank() && !state.isSuspending && !state.isSaving
                ) { Text("Confirm Suspension") }
            },
            dismissButton = {
                TextButton(onClick = { groupToSuspend = null }) { Text("Cancel") }
            }
        )
    }

    showMetricsFor?.let { group ->
        AlertDialog(
            onDismissRequest = { showMetricsFor = null },
            title = { Text("Actuarial Health: ${group.name}") },
            text = {
                state.selectedGroupMetrics?.let { metrics ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricRow("Risk Score", "${metrics.compositeRiskScore}/100", 
                            if (metrics.compositeRiskScore < 40) SuccessGreen else ErrorRed)
                        MetricRow("Reserve Adequacy", formatPct(metrics.reserveAdequacyPct), Forest)
                        MetricRow("Solvency Margin", formatPct(metrics.solvencyMarginPct), Forest)
                        MetricRow("Expected Claims (Ann)", formatZAR(metrics.expectedAnnualClaims), Charcoal)
                    }
                } ?: CircularProgressIndicator(color = Forest)
            },
            confirmButton = {
                TextButton(onClick = { showMetricsFor = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MidGray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun PlatformLedgerTab(state: PlatformAdminUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("📈 Platform Revenue Ledger")
        Text("Central record of registration fees and monthly member charges.", style = MaterialTheme.typography.bodyMedium, color = MidGray)

        if (state.platformLedger.isEmpty()) {
            EmptyState(
                icon = "🧾",
                title = "No transactions",
                description = "Platform-wide financial movements will appear here."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.platformLedger) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, LightGray.copy(0.5f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("${entry.createdAt?.take(16)} • ${entry.category}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val color = if (entry.amount >= 0) SuccessGreen else Color.Red
                                val prefix = if (entry.amount >= 0) "+" else ""
                                Text("$prefix${formatZAR(entry.amount)}", color = color, fontWeight = FontWeight.Black)
                                Text("Total: ${formatZAR(entry.balanceAfter)}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeeManagementTab(state: PlatformAdminUiState, vm: PlatformAdminViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Global Fee Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Update the fees charged by the platform for all groups.", style = MaterialTheme.typography.bodySmall, color = MidGray)

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SanibonaniTextField(
                    value = state.memberCharge,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateMemberCharge(it)
                        }
                    },
                    label = "Monthly Charge Per Member",
                    prefix = { Text("R ") },
                    placeholder = "e.g. 10.0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                
                SanibonaniTextField(
                    value = state.registrationFee,
                    onValueChange = { 
                        if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                            vm.updateRegistrationFee(it)
                        }
                    },
                    label = "One-time Group Registration Fee",
                    prefix = { Text("R ") },
                    placeholder = "e.g. 700.0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }

        if (state.isSaving) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Forest)
            }
        } else {
            SanibonaniButton(
                text = "Update Global Fees",
                onClick = { vm.saveGlobalFees() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.saveSuccess) {
            InfoBox("Global fees updated successfully! Changes will apply to all groups immediately.", InfoType.SUCCESS)
        }
        
        state.error?.let { error ->
            if (state.groups.isNotEmpty() || state.analytics.totalMembers > 0) {
                InfoBox(error, InfoType.ERROR)
            }
        }
    }
}

@Composable
private fun DisbursementsTab(
    payouts: List<PayoutRequest>,
    groups: List<Group>,
    claims: List<BeneficiaryPayoutClaim>,
    loanRequestsByGroup: Map<String, List<Loan>>,
    loanMemberNames: Map<String, String>,
    memberBehaviorInsights: List<MemberBehaviorInsight>,
    selectedRiskFilter: String,
    isLoadingLoanRequests: Boolean,
    isProcessingLoanRequest: Boolean,
    vm: PlatformAdminViewModel,
    onOpenMemberPortal: (groupId: String, payoutId: String?) -> Unit,
    onVerifyMember: (memberId: String, groupId: String) -> Unit,
    onRiskFilterChanged: (String) -> Unit
) {
    var rejectLoanTarget by remember { mutableStateOf<Loan?>(null) }
    var rejectLoanReason by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Escalated Disbursements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Validated payout requests escalated by group admins.", style = MaterialTheme.typography.labelSmall, color = MidGray)
        }
        
        items(payouts) { payout ->
            val group = groups.find { it.id == payout.groupId }
            val groupName = group?.name ?: "Unknown Group"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LightGray)
            ) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(groupName, fontWeight = FontWeight.Bold)
                            Text("Amount: R${payout.amount}", color = Forest, fontWeight = FontWeight.ExtraBold)
                            Text("Requested: ${payout.createdAt}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                        }
                        
                        val statusColor = when(payout.status) {
                            PayoutStatus.PENDING -> MidGray
                            PayoutStatus.GROUP_APPROVED -> InfoBlue
                            PayoutStatus.PROCESSING -> Forest
                            PayoutStatus.COMPLETED -> SuccessGreen
                            PayoutStatus.FAILED -> ErrorRed
                            PayoutStatus.CANCELLED -> MidGray
                        }
                        
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
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = LightGray.copy(0.5f))

                    Text("Bank: ${payout.bankName}", style = MaterialTheme.typography.bodySmall)
                    Text("Account: ${payout.accountNo}", style = MaterialTheme.typography.bodySmall)
                    Text("Branch: ${payout.branchCode}", style = MaterialTheme.typography.bodySmall)

                    OutlinedButton(
                        onClick = {
                            vm.logAudit(
                                action = "OPEN_MEMBER_PORTAL_FROM_DISBURSEMENT",
                                targetGroupId = payout.groupId,
                                details = mapOf(
                                    "payoutId" to (payout.id ?: "unknown"),
                                    "status" to payout.status.name,
                                    "groupName" to groupName
                                )
                            )
                            onOpenMemberPortal(payout.groupId, payout.id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Open Related Member Portal")
                    }
                    
                    if (payout.status == PayoutStatus.GROUP_APPROVED ||
                        payout.status == PayoutStatus.PROCESSING) {
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (payout.status == PayoutStatus.GROUP_APPROVED) {
                                OutlinedButton(
                                    onClick = { payout.id?.let { vm.approvePayout(it, payout.groupId) } },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Approve")
                                }
                                
                                Button(
                                    onClick = { payout.id?.let { vm.rejectPayout(it, payout.groupId) } },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Reject")
                                }
                            } else {
                                Button(
                                    onClick = { payout.id?.let { vm.completePayout(it, payout.groupId) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Forest),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Mark as Completed")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (payouts.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No disbursement requests found.", color = MidGray)
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text("Member Loan Requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Loan applications grouped by group for platform verification and triage.", style = MaterialTheme.typography.labelSmall, color = MidGray)
            TextButton(
                onClick = { vm.refreshLoanRequests() },
                enabled = !isLoadingLoanRequests && !isProcessingLoanRequest,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (isLoadingLoanRequests) "Refreshing..." else "Refresh Loan Requests")
            }
        }

        if (isLoadingLoanRequests) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Forest)
                }
            }
        } else if (loanRequestsByGroup.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No member loan requests requiring platform review.", color = MidGray)
                }
            }
        } else {
            loanRequestsByGroup
                .toList()
                .sortedBy { (groupId, _) -> groups.find { it.id == groupId }?.name ?: "" }
                .forEach { (groupId, loans) ->
                    item(key = "loan_group_header_$groupId") {
                        val groupName = groups.find { it.id == groupId }?.name ?: "Unknown Group"
                        Text(
                            text = "$groupName (${loans.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Forest,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(loans, key = { it.id ?: "${it.groupId}_${it.memberId}_${it.createdAt}" }) { loan ->
                        val memberName = loanMemberNames[loan.memberId] ?: "Member ${loan.memberId.take(8)}"
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, LightGray)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(memberName, fontWeight = FontWeight.Bold)
                                        Text("Amount: ${formatZAR(loan.amount)}", color = Forest, fontWeight = FontWeight.ExtraBold)
                                        Text("Purpose: ${loan.purpose ?: "Not provided"}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                                        Text("Requested: ${loan.createdAt?.take(16) ?: "Unknown"}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                                    }

                                    val statusColor = when (loan.status) {
                                        LoanStatus.PENDING -> InfoBlue
                                        LoanStatus.APPROVED -> Forest
                                        LoanStatus.ACTIVE -> SuccessGreen
                                        LoanStatus.PARTIALLY_PAID -> Gold
                                        LoanStatus.OVERDUE -> ErrorRed
                                        LoanStatus.COMPLETED -> SuccessGreen
                                        LoanStatus.REJECTED -> ErrorRed
                                        LoanStatus.CANCELLED -> MidGray
                                    }
                                    Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp)) {
                                        Text(
                                            loan.status.displayName,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor
                                        )
                                    }
                                }

                                Text(
                                    "Outstanding: ${formatZAR(loan.balanceRemaining)} • Progress: ${(loan.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MidGray
                                )

                                OutlinedButton(
                                    onClick = {
                                        vm.logAudit(
                                            action = "PLATFORM_VERIFY_LOAN_MEMBER",
                                            targetMemberId = loan.memberId,
                                            targetGroupId = loan.groupId,
                                            details = mapOf("loanId" to (loan.id ?: "unknown"))
                                        )
                                        onVerifyMember(loan.memberId, loan.groupId)
                                    },
                                    enabled = !isProcessingLoanRequest,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Verify Member Profile")
                                }

                                if (loan.status == LoanStatus.PENDING) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = { vm.approveLoanRequest(loan) },
                                            enabled = !isProcessingLoanRequest,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Forest)
                                        ) {
                                            Text("Approve")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                rejectLoanTarget = loan
                                                rejectLoanReason = ""
                                            },
                                            enabled = !isProcessingLoanRequest,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                            border = BorderStroke(1.dp, ErrorRed)
                                        ) {
                                            Text("Reject")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text("Member Behavior Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Risk-focused summary based on member loan request and repayment behavior.", style = MaterialTheme.typography.labelSmall, color = MidGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf("All", "High", "Elevated", "Watch", "Stable").forEach { riskBand ->
                    FilterChip(
                        selected = selectedRiskFilter == riskBand,
                        onClick = { onRiskFilterChanged(riskBand) },
                        label = { Text(riskBand) }
                    )
                }
            }
        }

        if (memberBehaviorInsights.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No member behavior insights available yet.", color = MidGray)
                }
            }
        } else {
            items(memberBehaviorInsights.take(15), key = { it.memberId }) { insight ->
                val riskColor = when (insight.riskBand) {
                    "High" -> ErrorRed
                    "Elevated" -> Gold
                    "Stable" -> SuccessGreen
                    else -> InfoBlue
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LightGray)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(insight.memberName, fontWeight = FontWeight.Bold)
                            Text(insight.riskBand, color = riskColor, fontWeight = FontWeight.Bold)
                        }
                        Text("Loan Requests: ${insight.totalLoanRequests} • Pending: ${insight.pendingRequests} • Overdue: ${insight.overdueLoans}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                        Text("Requested: ${formatZAR(insight.totalRequestedAmount)} • Outstanding: ${formatZAR(insight.outstandingAmount)}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                        Text("Completion: ${(insight.completionRatio * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MidGray)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text("Burial Payout Claims", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Death benefit claims for burial society beneficiaries.", style = MaterialTheme.typography.labelSmall, color = MidGray)
        }

        if (claims.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No escalated burial claims.", color = MidGray)
                }
            }
        }

        items(claims) { claim ->
            val group = groups.find { it.id == claim.groupId }
            val groupName = group?.name ?: "Unknown Group"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LightGray)
            ) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(groupName, style = MaterialTheme.typography.labelSmall, color = MidGray)
                            Text("For: ${claim.beneficiaryName}", fontWeight = FontWeight.Bold)
                            Text("Claim: R${claim.claimAmount}", color = Color.Red, fontWeight = FontWeight.ExtraBold)
                        }
                        Surface(
                            color = Forest.copy(0.1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                claim.status.displayName,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Forest,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    HorizontalDivider(color = LightGray.copy(0.5f))
                    
                    Text("Banking: ${claim.bankName} (${claim.accountNo})", style = MaterialTheme.typography.bodySmall)
                    Text("Holder: ${claim.accountHolder}", style = MaterialTheme.typography.bodySmall)
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (claim.status == BeneficiaryClaimStatus.ESCALATED) {
                            Button(
                                onClick = { claim.id?.let { vm.approveBurialClaim(it, "Approved by Platform") } },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Forest)
                            ) {
                                Text("Approve", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (claim.status == BeneficiaryClaimStatus.APPROVED) {
                            Button(
                                onClick = { claim.id?.let { vm.payBurialClaim(it, "Paid Out") } },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Text("Mark as Paid", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedButton(
                            onClick = { claim.id?.let { vm.rejectBurialClaim(it, "Rejected by Platform") } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed)
                        ) {
                            Text("Reject", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    rejectLoanTarget?.let { loan ->
        AlertDialog(
            onDismissRequest = { rejectLoanTarget = null },
            title = { Text("Reject Loan Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Provide a reason for rejecting this loan request.")
                    SanibonaniTextField(
                        value = rejectLoanReason,
                        onValueChange = { rejectLoanReason = it },
                        label = "Rejection Reason",
                        placeholder = "e.g. Contribution history insufficient"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.rejectLoanRequest(loan, rejectLoanReason)
                        rejectLoanTarget = null
                    },
                    enabled = rejectLoanReason.isNotBlank() && !isProcessingLoanRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectLoanTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MaintenanceTab(
    state: PlatformAdminUiState,
    vm: PlatformAdminViewModel,
    onNavigateToCreateAdmin: () -> Unit,
    onLogout: () -> Unit,
    onImpersonateGroupAdmin: (groupId: String) -> Unit,
    onImpersonateMember: (memberId: String, groupId: String) -> Unit
) {
    val filteredGroups = remember(state.groups, state.searchQuery) {
        if (state.searchQuery.isBlank()) state.groups
        else state.groups.filter {
            it.name.contains(state.searchQuery, ignoreCase = true) ||
                    it.province?.contains(state.searchQuery, ignoreCase = true) == true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Forest.copy(alpha = 0.2f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("System Maintenance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Charcoal)
                    Text("Administrative controls for support, audits, impersonation, and live operations.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Groups: ${filteredGroups.size}") }
                        )
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Audit Logs: ${state.auditLogs.size}") }
                        )
                    }
                }
            }
        }

        item {
            AuditLogsCard(
                auditLogs = state.auditLogs,
                isLoading = state.isLoadingAuditLogs
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Forest.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("👤 Administrator Management", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                    Text(
                        "Create additional group administrator accounts to help manage the system.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MidGray
                    )

                    Button(
                        onClick = onNavigateToCreateAdmin,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Forest)
                    ) {
                        Text("CREATE NEW ADMIN")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🕵️ Impersonate Group or Member", style = MaterialTheme.typography.titleSmall, color = InfoBlue, fontWeight = FontWeight.Bold)
                    Text(
                        "Access any group or member portal for maintenance or support. All actions will be logged for audit compliance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MidGray
                    )

                    state.error?.let { error ->
                        InfoBox(error, InfoType.ERROR)
                    }

                    OutlinedButton(
                        onClick = { vm.refreshMaintenanceData() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading && !state.isLoadingImpersonationMembers
                    ) {
                        Text(if (state.isLoading) "Refreshing..." else "Refresh Platform Data")
                    }

                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { vm.updateSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Filter groups...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    state.whatsAppTestResult?.let { result ->
                        InfoBox(result, if (result.contains("successfully")) InfoType.SUCCESS else InfoType.ERROR)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📢 Platform-Wide Broadcast", style = MaterialTheme.typography.titleSmall, color = Gold, fontWeight = FontWeight.Bold)
                    Text("Send a system-wide message to ALL members in ALL groups across the platform.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                    
                    SanibonaniTextField(
                        value = state.broadcastMessage,
                        onValueChange = { vm.updateBroadcastMessage(it) },
                        label = "Broadcast Message",
                        placeholder = "Enter message for all users..."
                    )
                    
                    if (state.broadcastSuccess) {
                        InfoBox("Broadcast sent successfully to all groups!", InfoType.SUCCESS)
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(3000)
                            vm.clearBroadcastSuccess()
                        }
                    }
                    
                    Button(
                        onClick = { vm.broadcastMessage() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold),
                        enabled = state.broadcastMessage.isNotBlank() && !state.isBroadcasting
                    ) {
                        Text(if (state.isBroadcasting) "SENDING..." else "SEND BROADCAST")
                    }
                }
            }
        }

        // Groups and Members Section
        items(filteredGroups, key = { it.id ?: it.hashCode() }) { group ->
            val groupId = group.id?.takeIf { it.isNotBlank() }
            val isSelected = state.impersonationGroupId == groupId

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSelected) InfoBlue.copy(0.05f) else Color.White),
                border = if (isSelected) BorderStroke(2.dp, InfoBlue) else BorderStroke(1.dp, LightGray.copy(0.5f))
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Forest.copy(alpha = 0.08f)
                        ) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Groups, contentDescription = null, tint = Forest, modifier = Modifier.size(20.dp))
                            }
                        }

                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                group.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Charcoal,
                                maxLines = 2
                            )
                            Text(
                                "${group.type.displayName} • ${group.province ?: "N/A"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MidGray
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = when {
                                group.isPlatformSuspended -> ErrorRed.copy(alpha = 0.12f)
                                isSelected -> InfoBlue.copy(alpha = 0.12f)
                                else -> Forest.copy(alpha = 0.08f)
                            }
                        ) {
                            Text(
                                text = when {
                                    group.isPlatformSuspended -> "Suspended"
                                    isSelected -> "Loaded"
                                    else -> "Ready"
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    group.isPlatformSuspended -> ErrorRed
                                    isSelected -> InfoBlue
                                    else -> Forest
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                groupId?.let {
                                    vm.logAudit(
                                        action = "IMPERSONATE_GROUP_ADMIN",
                                        targetGroupId = it,
                                        details = mapOf("groupName" to group.name)
                                    )
                                    onImpersonateGroupAdmin(it)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = groupId != null && !state.isLoading,
                            border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = InfoBlue)
                        ) {
                            Text("Admin Portal", fontWeight = FontWeight.SemiBold)
                        }

                        IconButton(
                            onClick = { groupId?.let { vm.sendWhatsAppTestToAdmin(it) } },
                            enabled = groupId != null && !state.isSendingWhatsAppTest,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(1.dp, LightGray.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        ) {
                            Text("💬", fontSize = 16.sp)
                        }

                        Button(
                            onClick = { groupId?.let { vm.selectImpersonationGroup(it, forceReload = true) } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) InfoBlue else MidGray),
                            enabled = groupId != null && !state.isLoadingImpersonationMembers
                        ) {
                            Text(
                                if (state.isLoadingImpersonationMembers && isSelected) "Loading..." else "Load Members",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Inline Members List
                    if (isSelected) {
                        HorizontalDivider(thickness = 0.5.dp, color = LightGray.copy(0.5f))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (state.isLoadingImpersonationMembers) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = InfoBlue, strokeWidth = 2.dp)
                                }
                            } else if (state.impersonationMembers.isEmpty()) {
                                Text("No members found in this group.", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = MidGray)
                            } else {
                                state.impersonationMembers.forEach { member ->
                                    val memberId = member.id
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(Color.White, RoundedCornerShape(10.dp))
                                            .border(1.dp, LightGray.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(member.fullName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            Text(member.status.name, style = MaterialTheme.typography.labelSmall, color = if (member.status == MemberStatus.ACTIVE) SuccessGreen else MidGray)
                                        }

                                        TextButton(
                                            onClick = {
                                                if (memberId != null && groupId != null) {
                                                    vm.logAudit(
                                                        action = "IMPERSONATE_MEMBER",
                                                        targetMemberId = memberId,
                                                        targetGroupId = groupId,
                                                        details = mapOf("memberName" to member.fullName)
                                                    )
                                                    onImpersonateMember(memberId, groupId)
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Forest),
                                            enabled = memberId != null && groupId != null
                                        ) {
                                            Text("IMPERSONATE", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⚠️ Reset Local Database", style = MaterialTheme.typography.titleSmall, color = ErrorRed, fontWeight = FontWeight.Bold)
                    Text(
                        "Wipes all cached data on this device. Use this if you encounter sync inconsistencies or schema conflicts. This does NOT affect remote data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MidGray
                    )

                    var showConfirm by remember { mutableStateOf(false) }

                    if (!showConfirm) {
                        OutlinedButton(
                            onClick = { showConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed),
                            enabled = !state.isLoading
                        ) {
                            Text(if (state.isLoading) "RESETTING..." else "RESET LOCAL DATA")
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showConfirm = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MidGray)
                            ) {
                                Text("CANCEL")
                            }
                            Button(
                                onClick = {
                                    vm.resetLocalData()
                                    showConfirm = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                            ) {
                                Text("CONFIRM RESET")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            SanibonaniButton(
                text = "LOGOUT",
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                contentColor = ErrorRed
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AuditLogsCard(
    auditLogs: List<AuditLog>,
    isLoading: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Forest.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("📜 Platform Audit Logs", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
            Text("The last 50 administrative actions taken on the platform.", style = MaterialTheme.typography.bodySmall, color = MidGray)

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            } else if (auditLogs.isEmpty()) {
                Text("No audit logs found.", style = MaterialTheme.typography.labelSmall, color = MidGray)
            } else {
                auditLogs.forEach { log ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(log.action, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(log.createdAt?.take(16)?.replace("T", " ") ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray)
                        }
                        Text("By: ${log.actorId}", style = MaterialTheme.typography.labelSmall, color = Forest)
                        if (!log.targetGroupId.isNullOrBlank()) {
                            Text("Group: ${log.targetGroupId}", style = MaterialTheme.typography.labelSmall)
                        }
                        log.details?.let { details ->
                            Text(details.toString(), style = MaterialTheme.typography.labelSmall, color = MidGray)
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = LightGray.copy(0.3f))
                    }
                }
            }
        }
    }
}

