package com.sanibonani.save.ui.screens.admin

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.viewmodel.PlatformAdminUiState
import com.sanibonani.save.viewmodel.PlatformAdminViewModel

@Composable
fun PlatformAdminScreen(
    onNavigateToCreateAdmin: () -> Unit,
    onLogout: () -> Unit,
    vm: PlatformAdminViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val tabs = listOf("Platform Analytics", "All Groups", "Fee Management", "Disbursements", "Maintenance")

    Scaffold(
        topBar = {
            SanibonaniTopBar(
                title = "Platform Administration",
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = Color.White)
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

        val errorMsg = state.error
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Forest)
            }
        } else if (errorMsg != null && state.groups.isEmpty() && state.analytics.totalMembers == 0) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("⚠️", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Platform Data Unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val currentError = state.error
                if (currentError != null) {
                    InfoBox(currentError, InfoType.ERROR)
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { vm.loadData() },
                    colors = ButtonDefaults.buttonColors(containerColor = Forest),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Retry Connection")
                }
            }
        } else {
            when (state.selectedTab) {
                0 -> PlatformAnalyticsTab(state.analytics)
                1 -> AllGroupsTab(state.groups, vm, state)
                2 -> FeeManagementTab(state, vm)
                3 -> DisbursementsTab(state.payouts, state.groups, vm)
                4 -> MaintenanceTab(vm, onNavigateToCreateAdmin)
                else -> CenterPlaceholder("Unknown Tab")
            }
        }
        }
    }
}

@Composable
private fun MaintenanceTab(
    vm: PlatformAdminViewModel,
    onNavigateToCreateAdmin: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("System Maintenance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Administrative tools for system-level operations.", style = MaterialTheme.typography.bodySmall, color = MidGray)

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Forest.copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("👤 Platform Admin Management", style = MaterialTheme.typography.titleSmall, color = Forest, fontWeight = FontWeight.Bold)
                Text(
                    "Create additional platform administrator accounts to help manage the system.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MidGray
                )
                
                Button(
                    onClick = onNavigateToCreateAdmin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest)
                ) {
                    Text("CREATE NEW PLATFORM ADMIN")
                }
            }
        }

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
                        border = BorderStroke(1.dp, ErrorRed)
                    ) {
                        Text("RESET LOCAL DATA")
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
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PlatformAnalyticsTab(analytics: com.sanibonani.save.domain.model.PlatformAnalytics) {
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
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(40.dp).background(Forest, RoundedCornerShape(8.dp)),
                                        Alignment.Center
                                    ) { Text(group.logoEmoji) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(group.name, fontWeight = FontWeight.Bold)
                                        Text("${group.type.displayName} • ${group.city}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(formatZAR(group.balance), fontWeight = FontWeight.Bold, color = Forest)
                                        AdminFeeChip(group.feeStatus)
                                    }
                                }
                                
                                Spacer(Modifier.height(12.dp))
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { 
                                            showMetricsFor = group
                                            vm.fetchGroupMetrics(group.id ?: "")
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("VIEW METRICS", style = MaterialTheme.typography.labelSmall)
                                    }

                                    if (group.isPlatformSuspended) {
                                        Button(
                                            onClick = { vm.unsuspendGroup(group.id ?: "") },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("LIFT SUSPENSION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = { groupToSuspend = group },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("SUSPEND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        groupToSuspend?.let { group ->
        var reason by remember { mutableStateOf("") }
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
                    enabled = reason.isNotBlank()
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
private fun DisbursementsTab(payouts: List<com.sanibonani.save.domain.model.PayoutRequest>, groups: List<Group>, vm: PlatformAdminViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Pending Disbursements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Payout requests from groups to their local bank accounts.", style = MaterialTheme.typography.labelSmall, color = MidGray)
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
                            com.sanibonani.save.domain.model.PayoutStatus.PENDING -> MidGray
                            com.sanibonani.save.domain.model.PayoutStatus.PROCESSING -> Forest
                            com.sanibonani.save.domain.model.PayoutStatus.COMPLETED -> SuccessGreen
                            com.sanibonani.save.domain.model.PayoutStatus.FAILED -> ErrorRed
                            com.sanibonani.save.domain.model.PayoutStatus.CANCELLED -> MidGray
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
                    
                    if (payout.status == com.sanibonani.save.domain.model.PayoutStatus.PENDING || 
                        payout.status == com.sanibonani.save.domain.model.PayoutStatus.PROCESSING) {
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (payout.status == com.sanibonani.save.domain.model.PayoutStatus.PENDING) {
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
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = Forest)
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
        
        val currentError = state.error
        if (currentError != null) {
            if (state.groups.isNotEmpty() || state.analytics.totalMembers > 0) {
                InfoBox(currentError, InfoType.ERROR)
            }
        }
    }
}
