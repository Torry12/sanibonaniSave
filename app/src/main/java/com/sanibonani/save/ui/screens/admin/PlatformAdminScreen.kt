package com.sanibonani.save.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
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
import com.sanibonani.save.ui.utils.uiLabel
import com.sanibonani.save.viewmodel.PlatformAdminUiState
import com.sanibonani.save.viewmodel.PlatformAdminViewModel

/**
 * Main Platform Administration Screen.
 * Provides a central dashboard for platform-wide analytics, group management, 
 * disbursements, ledger tracking, fee configuration, and system maintenance.
 */
@Composable
fun PlatformAdminScreen(
    onNavigateToCreateAdmin: () -> Unit,
    onNavigateToSandbox: () -> Unit,
    onLogout: () -> Unit,
    onImpersonateGroupAdmin: (groupId: String) -> Unit,
    onImpersonateMember: (memberId: String, groupId: String) -> Unit,
    onOpenMemberPortalFromDisbursement: (groupId: String, payoutId: String?) -> Unit,
    onNavigateToHealthScore: (groupId: String) -> Unit,
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
                    OutlinedButton(
                        onClick = onLogout,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, LightGray.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MidGray),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = MidGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Log out", style = MaterialTheme.typography.labelMedium)
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
                edgePadding = 0.dp,
                divider = {}
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = state.selectedTab == i,
                        onClick = { vm.setTab(i) },
                        text = { 
                            Text(
                                t, 
                                style = if (state.selectedTab == i) MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold) 
                                        else MaterialTheme.typography.labelSmall
                            ) 
                        }
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Forest)
                }
            } else {
                Crossfade(targetState = state.selectedTab, label = "TabTransition") { tabIndex ->
                    when (tabIndex) {
                        0 -> PlatformAnalyticsTab(state.analytics)
                        1 -> AllGroupsTab(state.groups, vm, state, onNavigateToHealthScore)
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
                        5 -> MaintenanceTab(state, vm, onNavigateToCreateAdmin, onNavigateToSandbox, onLogout, onImpersonateGroupAdmin, onImpersonateMember)
                        else -> CenterPlaceholder("Unknown Tab")
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// TABS IMPLEMENTATION
// ══════════════════════════════════════════════════════════════════════════

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
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, LightGray.copy(0.3f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    analytics.provinceDistribution.toList().sortedByDescending { it.second }.forEach { (prov, count) ->
                        val pct = if (analytics.totalGroups > 0) count.toFloat() / analytics.totalGroups else 0f
                        DistributionRow(prov ?: "Unknown", count, pct, Forest)
                    }
                }
            }
        }

        item {
            SectionTitle("Market Segments", "By group type")
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, LightGray.copy(0.3f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    analytics.groupTypeDistribution.toList().sortedByDescending { it.second }.forEach { (type, count) ->
                        val pct = if (analytics.totalGroups > 0) count.toFloat() / analytics.totalGroups else 0f
                        DistributionRow(type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, count, pct, InfoBlue)
                    }
                }
            }
        }
        
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun AllGroupsTab(
    groups: List<Group>,
    vm: PlatformAdminViewModel,
    state: PlatformAdminUiState,
    onNavigateToHealthScore: (groupId: String) -> Unit
) {
    var showMetricsFor by remember { mutableStateOf<Group?>(null) }
    
    val filteredGroups = remember(groups, state.searchQuery) {
        if (state.searchQuery.isBlank()) groups
        else groups.filter { it.name.contains(state.searchQuery, ignoreCase = true) || it.province?.contains(state.searchQuery, ignoreCase = true) == true }
    }

    val groupsByProvince = remember(filteredGroups) {
        filteredGroups.groupBy { it.province ?: "Other" }.toList().sortedBy { it.first }
    }
    val expandedProvinces = remember { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { vm.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search groups by name or province...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Forest,
                unfocusedBorderColor = LightGray,
                focusedLeadingIconColor = Forest,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            )
        )

        Box(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupsByProvince.forEach { (province, provinceGroups) ->
                    item(key = "all_groups_province_$province") {
                        ProvinceHeaderCard(
                            province = province,
                            count = provinceGroups.size,
                            isExpanded = expandedProvinces[province] == true,
                            onClick = { expandedProvinces[province] = !(expandedProvinces[province] ?: false) }
                        )
                    }

                    if (expandedProvinces[province] == true) {
                        items(provinceGroups, key = { it.id ?: it.hashCode() }) { group ->
                            ModernNavigationLink(
                                title = group.name,
                                subtitle = "${group.type.displayName} • ${group.city}",
                                icon = Icons.Default.Groups,
                                onClick = { 
                                    showMetricsFor = group
                                    vm.fetchGroupMetrics(group.id ?: "")
                                },
                                accentColor = if (group.isPlatformSuspended) ErrorRed else Forest,
                                modifier = Modifier.padding(start = 12.dp).padding(vertical = 4.dp)
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    showMetricsFor?.let { group ->
        AlertDialog(
            onDismissRequest = { showMetricsFor = null },
            title = { Text("Actuarial Health: ${group.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    state.selectedGroupMetrics?.let { metrics ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricRow("Risk Score", "${metrics.compositeRiskScore}/100", 
                                if (metrics.compositeRiskScore < 40) SuccessGreen else ErrorRed)
                            MetricRow("Reserve Adequacy", formatPct(metrics.reserveAdequacyPct), Forest)
                            MetricRow("Solvency Margin", formatPct(metrics.solvencyMarginPct), Forest)
                            MetricRow("Expected Claims (Ann)", formatZAR(metrics.expectedAnnualClaims), Charcoal)
                        }
                    }

                    if (state.isLoadingHealthScore) {
                        Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            CircularProgressIndicator(color = Forest, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        state.selectedGroupHealthScore?.let { healthScore ->
                            HorizontalDivider(color = LightGray.copy(alpha = 0.3f))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Composite Health Score", style = MaterialTheme.typography.labelSmall, color = MidGray)
                                    Text("${healthScore.overallScore}/100", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = healthScore.zone.backgroundColor())
                                }
                                
                                Surface(
                                    color = healthScore.zone.backgroundColor().copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        healthScore.zone.label(),
                                        modifier = Modifier.padding(8.dp, 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = healthScore.zone.backgroundColor(),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Button(
                                onClick = { 
                                    showMetricsFor = null
                                    onNavigateToHealthScore(group.id ?: "")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Forest),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("View Full Health Report")
                            }
                        }
                    }
                    
                    if (state.selectedGroupMetrics == null && !state.isLoadingHealthScore && state.selectedGroupHealthScore == null) {
                         Box(Modifier.fillMaxWidth(), Alignment.Center) {
                             Text("No data available.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                         }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMetricsFor = null }) { Text("Close", color = Forest) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
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
    
    var isPayoutsSectionExpanded by remember { mutableStateOf(true) }
    var isLoansSectionExpanded by remember { mutableStateOf(true) }
    var isClaimsSectionExpanded by remember { mutableStateOf(true) }
    var isAnalysisSectionExpanded by remember { mutableStateOf(false) }

    val expandedPayoutProvinces = remember { mutableStateMapOf<String, Boolean>() }
    val expandedLoanProvinces = remember { mutableStateMapOf<String, Boolean>() }
    val expandedClaimProvinces = remember { mutableStateMapOf<String, Boolean>() }

    val provinceMap = remember(groups) { groups.associateBy({ it.id }, { it.province ?: "Other" }) }

    val payoutsByProvince = remember(payouts, provinceMap) {
        payouts.groupBy { provinceMap[it.groupId] ?: "Other" }.toList().sortedBy { it.first }
    }

    val loansByProvince = remember(loanRequestsByGroup, provinceMap) {
        loanRequestsByGroup.entries.groupBy { (groupId, _) ->
            provinceMap[groupId] ?: "Other"
        }.toList().sortedBy { it.first }
    }

    val claimsByProvince = remember(claims, provinceMap) {
        claims.groupBy { claim ->
            provinceMap[claim.groupId] ?: "Other"
        }.toList().sortedBy { it.first }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Escalated Disbursements Section ---
        item {
            SectionHeaderCard(
                icon = "💰",
                title = "Escalated Disbursements",
                subtitle = "Payout requests awaiting final approval.",
                count = payouts.size,
                isExpanded = isPayoutsSectionExpanded,
                onClick = { isPayoutsSectionExpanded = !isPayoutsSectionExpanded }
            )
        }

        if (isPayoutsSectionExpanded) {
            if (payouts.isEmpty()) {
                item { EmptyStateSmall("No disbursement requests found.") }
            } else {
                payoutsByProvince.forEach { (province, provincePayouts) ->
                    item(key = "payout_province_$province") {
                        ProvinceHeaderCard(
                            province = province,
                            count = provincePayouts.size,
                            isExpanded = expandedPayoutProvinces[province] == true,
                            onClick = { expandedPayoutProvinces[province] = !(expandedPayoutProvinces[province] ?: false) }
                        )
                    }

                    if (expandedPayoutProvinces[province] == true) {
                        items(provincePayouts) { payout ->
                            val group = groups.find { it.id == payout.groupId }
                            val groupName = group?.name ?: "Unknown Group"
                            PayoutCard(
                                payout = payout,
                                groupName = groupName,
                                onOpenPortal = {
                                    vm.logAudit(action = "OPEN_MEMBER_PORTAL_FROM_DISBURSEMENT", targetGroupId = payout.groupId, details = mapOf("payoutId" to (payout.id ?: "unknown"), "groupName" to groupName))
                                    onOpenMemberPortal(payout.groupId, payout.id)
                                },
                                onApprove = { payout.id?.let { vm.approvePayout(it, payout.groupId) } },
                                onReject = { payout.id?.let { vm.rejectPayout(it, payout.groupId) } },
                                onComplete = { payout.id?.let { vm.completePayout(it, payout.groupId) } }
                            )
                        }
                    }
                }
            }
        }

        // --- 2. Member Loan Requests Section ---
        item {
            SectionHeaderCard(
                icon = "🏥",
                title = "Member Loan Requests",
                subtitle = "Loan applications requiring platform review.",
                count = loanRequestsByGroup.values.flatten().size,
                isExpanded = isLoansSectionExpanded,
                onClick = { isLoansSectionExpanded = !isLoansSectionExpanded }
            )
        }

        if (isLoansSectionExpanded) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.refreshLoanRequests() }, enabled = !isLoadingLoanRequests && !isProcessingLoanRequest) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isLoadingLoanRequests) "Refreshing..." else "Refresh List", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (isLoadingLoanRequests) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Forest, modifier = Modifier.size(24.dp)) } }
            } else if (loanRequestsByGroup.isEmpty()) {
                item { EmptyStateSmall("No loan requests requiring review.") }
            } else {
                loansByProvince.forEach { (province, provinceEntries) ->
                    item(key = "loan_province_$province") {
                        ProvinceHeaderCard(
                            province = province,
                            count = provinceEntries.sumOf { it.value.size },
                            isExpanded = expandedLoanProvinces[province] == true,
                            onClick = { expandedLoanProvinces[province] = !(expandedLoanProvinces[province] ?: false) }
                        )
                    }

                    if (expandedLoanProvinces[province] == true) {
                        provinceEntries.sortedBy { (groupId, _) -> groups.find { it.id == groupId }?.name ?: "" }.forEach { (groupId, loans) ->
                            item(key = "loan_group_header_$groupId") {
                                val groupName = groups.find { it.id == groupId }?.name ?: "Unknown Group"
                                Text(text = groupName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ForestMid, modifier = Modifier.padding(top = 8.dp, start = 24.dp))
                            }

                            items(loans) { loan ->
                                val memberName = loanMemberNames[loan.memberId] ?: "Member ${loan.memberId.take(8)}"
                                LoanRequestCard(
                                    loan = loan,
                                    memberName = memberName,
                                    isProcessing = isProcessingLoanRequest,
                                    onVerifyProfile = {
                                        vm.logAudit(action = "PLATFORM_VERIFY_LOAN_MEMBER", targetMemberId = loan.memberId, targetGroupId = loan.groupId, details = mapOf("loanId" to (loan.id ?: "unknown")))
                                        onVerifyMember(loan.memberId, loan.groupId)
                                    },
                                    onApprove = { vm.approveLoanRequest(loan) },
                                    onReject = { rejectLoanTarget = loan; rejectLoanReason = "" }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Burial Society Claims Section ---
        item {
            SectionHeaderCard(
                icon = "🕊️",
                title = "Burial Payout Claims",
                subtitle = "Escalated death benefit claims.",
                count = claims.size,
                isExpanded = isClaimsSectionExpanded,
                onClick = { isClaimsSectionExpanded = !isClaimsSectionExpanded }
            )
        }

        if (isClaimsSectionExpanded) {
            if (claims.isEmpty()) {
                item { EmptyStateSmall("No escalated burial claims.") }
            } else {
                claimsByProvince.forEach { (province, provinceClaims) ->
                    item(key = "claim_province_$province") {
                        ProvinceHeaderCard(
                            province = province,
                            count = provinceClaims.size,
                            isExpanded = expandedClaimProvinces[province] == true,
                            onClick = { expandedClaimProvinces[province] = !(expandedClaimProvinces[province] ?: false) }
                        )
                    }

                    if (expandedClaimProvinces[province] == true) {
                        items(provinceClaims) { claim ->
                            val group = groups.find { it.id == claim.groupId }
                            val groupName = group?.name ?: "Unknown Group"
                            BurialClaimCard(
                                claim = claim,
                                groupName = groupName,
                                onApprove = { claim.id?.let { vm.approveBurialClaim(it, "Approved by Platform") } },
                                onPay = { claim.id?.let { vm.payBurialClaim(it, "Paid Out") } },
                                onReject = { claim.id?.let { vm.rejectBurialClaim(it, "Rejected by Platform") } }
                            )
                        }
                    }
                }
            }
        }

        // --- 4. Member Behavior Analysis Section ---
        item {
            SectionHeaderCard(
                icon = "📊",
                title = "Member Behavior Analysis",
                subtitle = "Risk-focused summary of loan behavior.",
                count = null,
                isExpanded = isAnalysisSectionExpanded,
                onClick = { isAnalysisSectionExpanded = !isAnalysisSectionExpanded }
            )
        }

        if (isAnalysisSectionExpanded) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 12.dp)) {
                    listOf("All", "High", "Elevated", "Watch", "Stable").forEach { riskBand ->
                        FilterChip(
                            selected = selectedRiskFilter == riskBand,
                            onClick = { onRiskFilterChanged(riskBand) },
                            label = { Text(riskBand) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Forest.copy(0.1f), selectedLabelColor = Forest)
                        )
                    }
                }
            }

            if (memberBehaviorInsights.isEmpty()) {
                item { EmptyStateSmall("No behavior insights available.") }
            } else {
                items(memberBehaviorInsights.take(15)) { insight ->
                    BehaviorInsightCard(insight)
                }
            }
        }
        
        item { Spacer(Modifier.height(40.dp)) }
    }

    rejectLoanTarget?.let { loan ->
        AlertDialog(
            onDismissRequest = { rejectLoanTarget = null },
            title = { Text("Reject Loan Request", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Provide a reason for rejecting this loan request.")
                    SanibonaniTextField(value = rejectLoanReason, onValueChange = { rejectLoanReason = it }, label = "Rejection Reason", placeholder = "e.g. Contribution history insufficient")
                }
            },
            confirmButton = {
                Button(onClick = { vm.rejectLoanRequest(loan, rejectLoanReason); rejectLoanTarget = null }, enabled = rejectLoanReason.isNotBlank() && !isProcessingLoanRequest, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)) { Text("Reject") }
            },
            dismissButton = {
                TextButton(onClick = { rejectLoanTarget = null }) { Text("Cancel", color = MidGray) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}

@Composable
fun PlatformLedgerTab(state: PlatformAdminUiState) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("📈 Platform Revenue Ledger")
        Text("Central record of registration fees and monthly member charges.", style = MaterialTheme.typography.bodyMedium, color = MidGray)

        if (state.platformLedger.isEmpty()) {
            EmptyState(icon = "🧾", title = "No transactions", description = "Platform-wide financial movements will appear here.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.platformLedger) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, LightGray.copy(0.3f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("${entry.createdAt?.take(16)?.replace("T", " ")} • ${entry.category}", style = MaterialTheme.typography.labelSmall, color = MidGray)
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
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun FeeManagementTab(state: PlatformAdminUiState, vm: PlatformAdminViewModel) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Column {
            Text("Platform Global Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Configure core fees and operational constraints for the entire ecosystem.", style = MaterialTheme.typography.bodySmall, color = MidGray)
        }

        // --- Core Revenue Settings ---
        HeaderWithIcon(Icons.Default.Payments, "CORE PLATFORM FEES")
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, LightGray.copy(0.3f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SanibonaniTextField(
                    value = state.memberCharge,
                    onValueChange = { vm.updateMemberCharge(it) },
                    label = "Monthly Service Fee (Per Member)",
                    prefix = { Text("R ", style = MaterialTheme.typography.bodyMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                
                SanibonaniTextField(
                    value = state.registrationFee,
                    onValueChange = { vm.updateRegistrationFee(it) },
                    label = "Group Onboarding/Registration Fee",
                    prefix = { Text("R ", style = MaterialTheme.typography.bodyMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }

        // --- Transactional Factors ---
        HeaderWithIcon(Icons.Default.Receipt, "TRANSACTIONAL & SERVICE FACTORS")
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, LightGray.copy(0.3f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SanibonaniTextField(
                    value = state.payoutFee,
                    onValueChange = { vm.updatePayoutFee(it) },
                    label = "Payout Processing Fee (Flat)",
                    prefix = { Text("R ", style = MaterialTheme.typography.bodyMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                SanibonaniTextField(
                    value = state.whatsappFee,
                    onValueChange = { vm.updateWhatsappFee(it) },
                    label = "WhatsApp Notification Cost (Per msg)",
                    prefix = { Text("R ", style = MaterialTheme.typography.bodyMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }

        // --- Governance & Compliance ---
        HeaderWithIcon(Icons.Default.Gavel, "GOVERNANCE & COMPLIANCE")
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, LightGray.copy(0.3f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SanibonaniTextField(
                    value = state.lateFeePercent,
                    onValueChange = { vm.updateLateFeePercent(it) },
                    label = "Default Late Fee Penalty",
                    suffix = { Text("%", style = MaterialTheme.typography.bodyMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                SanibonaniTextField(
                    value = state.autoSuspensionDays,
                    onValueChange = { vm.updateAutoSuspensionDays(it) },
                    label = "Auto-Suspension Inactivity Threshold",
                    suffix = { Text(" Days", style = MaterialTheme.typography.bodyMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.isSaving) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Forest) }
        } else {
            SanibonaniButton(text = "Apply Global Changes", onClick = { vm.saveGlobalFees() }, modifier = Modifier.fillMaxWidth())
        }

        if (state.saveSuccess) { InfoBox("Platform settings synchronized successfully across all group instances.", InfoType.SUCCESS) }
        state.error?.let { error -> InfoBox(error, InfoType.ERROR) }
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun MaintenanceTab(
    state: PlatformAdminUiState,
    vm: PlatformAdminViewModel,
    onNavigateToCreateAdmin: () -> Unit,
    onNavigateToSandbox: () -> Unit,
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

    val groupsByProvince = remember(filteredGroups) {
        filteredGroups.groupBy { it.province ?: "Other" }.toList().sortedBy { it.first }
    }
    val expandedProvinces = remember { mutableStateMapOf<String, Boolean>() }
    var isResetConfirmVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeaderCard(
                icon = "🔧",
                title = "System Maintenance",
                subtitle = "Administrative controls and live operations.",
                count = null,
                isExpanded = true,
                onClick = {}
            )
        }

        item {
            AuditLogsCard(
                auditLogs = state.auditLogs,
                isLoading = state.isLoadingAuditLogs
            )
        }

        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Forest.copy(alpha = 0.3f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("👤 Administrator Management", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                    Text("Create additional group administrator accounts to help manage the system.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                    Button(onClick = onNavigateToCreateAdmin, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("CREATE NEW ADMIN") }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Terra.copy(alpha = 0.3f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("💸 Payment Sandbox", style = MaterialTheme.typography.titleSmall, color = Terra, fontWeight = FontWeight.Bold)
                    Text("Test payment gateway integrations (Stitch, PayFast) in a sandbox environment.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                    Button(onClick = onNavigateToSandbox, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Terra)) { Text("OPEN PAYMENT SANDBOX") }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.3f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🕵️ support Tools", style = MaterialTheme.typography.titleSmall, color = InfoBlue, fontWeight = FontWeight.Bold)
                    Text("Access any group or member portal for maintenance or support. Actions are logged.", style = MaterialTheme.typography.bodySmall, color = MidGray)

                    HorizontalDivider(color = LightGray.copy(0.3f))

                    Text("🧪 Direct WhatsApp Smoke Test", style = MaterialTheme.typography.labelMedium, color = Charcoal, fontWeight = FontWeight.Bold)
                    SanibonaniTextField(value = state.whatsAppTestPhone, onValueChange = vm::updateWhatsAppTestPhone, label = "WhatsApp Number", placeholder = "0713459563", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), visualTransformation = PhoneNumberTransformation())
                    SanibonaniTextField(value = state.whatsAppTestMessage, onValueChange = vm::updateWhatsAppTestMessage, label = "Test Message", placeholder = "Enter message...", singleLine = false, modifier = Modifier.height(90.dp))

                    Button(
                        onClick = { vm.sendDirectWhatsAppTest() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.whatsAppTestPhone.isNotBlank() && !state.isSendingWhatsAppTest,
                        colors = ButtonDefaults.buttonColors(containerColor = Forest)
                    ) {
                        if (state.isSendingWhatsAppTest) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("Send WhatsApp Test")
                    }

                    state.whatsAppTestResult?.let { result -> InfoBox(result, if (result.contains("successfully")) InfoType.SUCCESS else InfoType.ERROR) }

                    OutlinedButton(onClick = { vm.refreshMaintenanceData() }, modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading && !state.isLoadingImpersonationMembers) {
                        Text(if (state.isLoading) "Refreshing..." else "Refresh Platform Data")
                    }

                    OutlinedTextField(
                        value = state.searchQuery, 
                        onValueChange = { vm.updateSearchQuery(it) }, 
                        modifier = Modifier.fillMaxWidth(), 
                        placeholder = { Text("Filter groups...") }, 
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, 
                        singleLine = true, 
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = InfoBlue,
                            unfocusedBorderColor = LightGray,
                            focusedLeadingIconColor = InfoBlue,
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        )
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📢 Platform-Wide Broadcast", style = MaterialTheme.typography.titleSmall, color = Gold, fontWeight = FontWeight.Bold)
                    Text("Send a system-wide message to ALL members in ALL groups.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                    SanibonaniTextField(value = state.broadcastMessage, onValueChange = { vm.updateBroadcastMessage(it) }, label = "Broadcast Message", placeholder = "Enter message...")
                    if (state.broadcastSuccess) { InfoBox("Broadcast sent successfully!", InfoType.SUCCESS) }
                    Button(onClick = { vm.broadcastMessage() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold), enabled = state.broadcastMessage.isNotBlank() && !state.isBroadcasting) {
                        Text(if (state.isBroadcasting) "SENDING..." else "SEND BROADCAST")
                    }
                }
            }
        }

        // Groups and Members Section
        groupsByProvince.forEach { (province, provinceGroups) ->
            item(key = "province_header_$province") {
                ProvinceHeaderCard(province = province, count = provinceGroups.size, isExpanded = expandedProvinces[province] == true, onClick = { expandedProvinces[province] = !(expandedProvinces[province] ?: false) })
            }

            if (expandedProvinces[province] == true) {
                items(provinceGroups, key = { it.id ?: it.hashCode() }) { group ->
                    val groupId = group.id?.takeIf { it.isNotBlank() }
                    val isSelected = state.impersonationGroupId == groupId

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) InfoBlue.copy(0.05f) else Color.White),
                        border = if (isSelected) BorderStroke(2.dp, InfoBlue) else BorderStroke(1.dp, LightGray.copy(0.5f)),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(shape = RoundedCornerShape(10.dp), color = Forest.copy(alpha = 0.08f)) {
                                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, contentDescription = null, tint = Forest, modifier = Modifier.size(20.dp)) }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Charcoal, maxLines = 2)
                                    Text("${group.type.displayName} • ${group.city}", style = MaterialTheme.typography.labelMedium, color = MidGray)
                                }
                                Surface(shape = RoundedCornerShape(999.dp), color = if (group.isPlatformSuspended) ErrorRed.copy(alpha = 0.12f) else Forest.copy(alpha = 0.08f)) {
                                    Text(text = if (group.isPlatformSuspended) "Suspended" else if (isSelected) "Loaded" else "Ready", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (group.isPlatformSuspended) ErrorRed else if (isSelected) InfoBlue else Forest, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { groupId?.let { vm.logAudit(action = "IMPERSONATE_GROUP_ADMIN", targetGroupId = it, details = mapOf("groupName" to group.name)); onImpersonateGroupAdmin(it) } }, modifier = Modifier.weight(1f), enabled = groupId != null && !state.isLoading, border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = InfoBlue)) { Text("Admin Portal", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) }
                                IconButton(onClick = { groupId?.let { vm.sendWhatsAppTestToAdmin(it) } }, enabled = groupId != null && !state.isSendingWhatsAppTest, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, LightGray.copy(alpha = 0.6f), RoundedCornerShape(10.dp))) { Text("💬", fontSize = 16.sp) }
                                Button(onClick = { groupId?.let { vm.selectImpersonationGroup(it, forceReload = true) } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) InfoBlue else MidGray), enabled = groupId != null && !state.isLoadingImpersonationMembers) { Text(if (state.isLoadingImpersonationMembers && isSelected) "..." else "Load Members", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                            }

                            if (isSelected) {
                                HorizontalDivider(thickness = 0.5.dp, color = LightGray.copy(0.5f))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (state.isLoadingImpersonationMembers) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = InfoBlue, strokeWidth = 2.dp) } }
                                    else if (state.impersonationMembers.isEmpty()) { Text("No members found.", style = MaterialTheme.typography.bodySmall, color = MidGray) }
                                    else {
                                        state.impersonationMembers.forEach { member ->
                                            val memberId = member.id
                                            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).border(1.dp, LightGray.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(member.fullName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                    Text(member.status.name, style = MaterialTheme.typography.labelSmall, color = if (member.status == MemberStatus.ACTIVE) SuccessGreen else MidGray)
                                                }
                                                TextButton(onClick = { if (memberId != null && groupId != null) { vm.logAudit(action = "IMPERSONATE_MEMBER", targetMemberId = memberId, targetGroupId = groupId, details = mapOf("memberName" to member.fullName)); onImpersonateMember(memberId, groupId) } }, colors = ButtonDefaults.textButtonColors(contentColor = Forest), enabled = memberId != null && groupId != null) { Text("IMPERSONATE", fontSize = 10.sp) }
                                            }
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
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⚠️ Danger Zone", style = MaterialTheme.typography.titleSmall, color = ErrorRed, fontWeight = FontWeight.Bold)
                    Text("System-wide resets or dangerous operations.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                    if (!isResetConfirmVisible) {
                        OutlinedButton(onClick = { isResetConfirmVisible = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed), border = BorderStroke(1.dp, ErrorRed), enabled = !state.isLoading) { Text("RESET LOCAL CACHE") }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { isResetConfirmVisible = false }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MidGray)) { Text("CANCEL") }
                            Button(onClick = { vm.resetLocalData(); isResetConfirmVisible = false }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)) { Text("CONFIRM") }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun AuditLogsCard(auditLogs: List<AuditLog>, isLoading: Boolean) {
    var isMainExpanded by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Forest.copy(alpha = 0.3f))) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { isMainExpanded = !isMainExpanded }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("📜 Platform Audit Logs", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                    Text("The last administrative actions.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Forest.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp)) {
                        Text("${auditLogs.size}", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Forest, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(imageVector = if (isMainExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = Forest)
                }
            }
            
            AnimatedVisibility(visible = isMainExpanded) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    if (isLoading) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Forest) } }
                    else if (auditLogs.isEmpty()) { Text("No audit logs found.", style = MaterialTheme.typography.labelSmall, color = MidGray, modifier = Modifier.padding(vertical = 12.dp)) }
                    else {
                        auditLogs.forEach { log ->
                            val isExpanded = expandedId == log.id
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { expandedId = if (isExpanded) null else log.id }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(log.action.replace("_", " "), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isExpanded) Forest else Charcoal)
                                        Text(log.createdAt?.take(16)?.replace("T", " ") ?: "", style = MaterialTheme.typography.labelSmall, color = MidGray, fontSize = 10.sp)
                                    }
                                    Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = if (isExpanded) Forest else MidGray, modifier = Modifier.size(20.dp))
                                }
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp)) {
                                        AuditDetailRow(Forest, "Actor", log.actorId)
                                        log.targetGroupId?.takeIf { it.isNotBlank() }?.let {
                                            AuditDetailRow(Gold, "Group", it)
                                        }
                                        log.targetMemberId?.takeIf { it.isNotBlank() }?.let {
                                            AuditDetailRow(Terra, "Member", it)
                                        }
                                        log.details?.let { details ->
                                            Spacer(Modifier.height(8.dp))
                                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Cream2), shape = RoundedCornerShape(4.dp)) {
                                                Column(Modifier.padding(8.dp)) {
                                                    details.forEach { (key, value) ->
                                                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                                            Text("$key:", Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ForestMid)
                                                            Text(value, style = MaterialTheme.typography.labelSmall, color = Charcoal)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!isExpanded) HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = LightGray.copy(0.3f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditDetailRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(6.dp).background(color, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(8.dp))
        Text("$label: $value", style = MaterialTheme.typography.labelSmall, color = MidGray)
    }
}

// ══════════════════════════════════════════════════════════════════════════
// REUSABLE UI COMPONENTS
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeaderCard(icon: String, title: String, subtitle: String, count: Int?, isExpanded: Boolean, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isExpanded) Forest.copy(0.02f) else Color.White), border = BorderStroke(1.dp, Forest.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("$icon $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Forest)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MidGray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                count?.let {
                    Surface(shape = RoundedCornerShape(16.dp), color = Forest.copy(alpha = 0.1f)) {
                        Text("$it", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Forest, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = Forest)
            }
        }
    }
}

@Composable
private fun ProvinceHeaderCard(province: String, count: Int, isExpanded: Boolean, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isExpanded) Forest.copy(0.05f) else Color.White), border = BorderStroke(1.dp, if (isExpanded) Forest.copy(alpha = 0.5f) else LightGray.copy(0.5f)), modifier = Modifier.fillMaxWidth().padding(start = 12.dp).clickable { onClick() }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Forest, modifier = Modifier.size(18.dp))
                Text(province, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = Charcoal)
                Surface(shape = RoundedCornerShape(16.dp), color = Forest.copy(alpha = 0.1f)) {
                    Text("$count", Modifier.padding(horizontal = 8.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = Forest, fontWeight = FontWeight.Bold)
                }
            }
            Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = Forest, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PayoutCard(payout: PayoutRequest, groupName: String, onOpenPortal: () -> Unit, onApprove: () -> Unit, onReject: () -> Unit, onComplete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, LightGray.copy(0.5f)), modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(groupName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text(formatZAR(payout.amount), color = Forest, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodySmall)
                }
                Surface(color = getPayoutStatusColor(payout.status).copy(0.1f), shape = RoundedCornerShape(16.dp)) {
                    Text(payout.status.uiLabel, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = getPayoutStatusColor(payout.status), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = LightGray.copy(0.3f))
            Text("Bank: ${payout.bankName} • Acc: ${payout.accountNo}", style = MaterialTheme.typography.labelSmall, color = MidGray)
            OutlinedButton(onClick = onOpenPortal, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(32.dp), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text("Open Related Portal", style = MaterialTheme.typography.labelSmall) }
            if (payout.status == PayoutStatus.GROUP_APPROVED || payout.status == PayoutStatus.PROCESSING) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (payout.status == PayoutStatus.GROUP_APPROVED) {
                        OutlinedButton(onClick = onApprove, modifier = Modifier.weight(1f).height(32.dp), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text("Approve", style = MaterialTheme.typography.labelSmall) }
                        Button(onClick = onReject, modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text("Reject", style = MaterialTheme.typography.labelSmall) }
                    } else {
                        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text("Mark as Completed", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanRequestCard(loan: Loan, memberName: String, isProcessing: Boolean, onVerifyProfile: () -> Unit, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, LightGray.copy(0.5f)), modifier = Modifier.padding(start = 36.dp, bottom = 4.dp)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(memberName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text(formatZAR(loan.amount), color = Forest, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodySmall)
                }
                Surface(color = InfoBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp)) {
                    Text(loan.status.displayName, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = InfoBlue, fontSize = 9.sp)
                }
            }
            OutlinedButton(onClick = onVerifyProfile, enabled = !isProcessing, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(32.dp), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text("Verify Profile", style = MaterialTheme.typography.labelSmall) }
            if (loan.status == LoanStatus.PENDING) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Button(onClick = onApprove, enabled = !isProcessing, modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text("Approve", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(onClick = onReject, enabled = !isProcessing, modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed), border = BorderStroke(1.dp, ErrorRed), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text("Reject", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun BurialClaimCard(claim: BeneficiaryPayoutClaim, groupName: String, onApprove: () -> Unit, onPay: () -> Unit, onReject: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, LightGray.copy(0.5f)), modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(claim.beneficiaryName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text(groupName, style = MaterialTheme.typography.labelSmall, color = MidGray)
                    Text(formatZAR(claim.claimAmount), color = Color.Red, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodySmall)
                }
                Surface(color = Forest.copy(0.1f), shape = RoundedCornerShape(16.dp)) {
                    Text(claim.status.displayName, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Forest, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (claim.status == BeneficiaryClaimStatus.ESCALATED) Button(onClick = onApprove, modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text("Approve", style = MaterialTheme.typography.labelSmall) }
                if (claim.status == BeneficiaryClaimStatus.APPROVED) Button(onClick = onPay, modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text("Pay", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed), border = BorderStroke(1.dp, ErrorRed), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) { Text("Reject", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun BehaviorInsightCard(insight: MemberBehaviorInsight) {
    val riskColor = when (insight.riskBand) { "High" -> ErrorRed; "Elevated" -> Gold; "Stable" -> SuccessGreen; else -> InfoBlue }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, LightGray.copy(0.5f)), modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(insight.memberName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text(insight.riskBand, color = riskColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            Text("Loans: ${insight.totalLoanRequests} • O/S: ${formatZAR(insight.outstandingAmount)}", style = MaterialTheme.typography.labelSmall, color = MidGray)
        }
    }
}

@Composable
private fun EmptyStateSmall(message: String) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MidGray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DistributionRow(label: String, count: Int, percentage: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text("$count", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(LightGray.copy(alpha = 0.3f))
        ) {
            Box(
                Modifier.fillMaxWidth(percentage).fillMaxHeight().background(color)
            )
        }
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
private fun HeaderWithIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = Charcoal, modifier = Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Charcoal)
    }
}

private fun getPayoutStatusColor(status: PayoutStatus): Color = when(status) {
    PayoutStatus.PENDING -> MidGray
    PayoutStatus.GROUP_APPROVED -> InfoBlue
    PayoutStatus.PROCESSING -> Forest
    PayoutStatus.COMPLETED -> SuccessGreen
    PayoutStatus.FAILED -> ErrorRed
    PayoutStatus.CANCELLED -> MidGray
}
