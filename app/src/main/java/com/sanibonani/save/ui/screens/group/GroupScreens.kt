package com.sanibonani.save.ui.screens.group

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.SA_PROVINCES
import com.sanibonani.save.domain.model.SA_BANKS
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.IDNumberTransformation
import com.sanibonani.save.domain.model.PlatformFees
import com.sanibonani.save.ui.screens.payment.PaymentScreen
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.viewmodel.GroupViewModel
import com.sanibonani.save.viewmodel.PaymentViewModel
import com.sanibonani.save.viewmodel.RegisterGroupState

@Composable
fun GroupListScreen(vm: GroupViewModel, onGroupClick: (String) -> Unit) {
    val state by vm.listState.collectAsState()
    val context = LocalContext.current
    var showMapView by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        if (state.error?.contains("internet", ignoreCase = true) == true || 
            state.error?.contains("connection", ignoreCase = true) == true) {
            Toast.makeText(context, state.error, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = { 
            SanibonaniTopBar(
                title = "Savings Groups",
                actions = {
                    IconButton(onClick = { showMapView = !showMapView }) {
                        Icon(
                            imageVector = if (showMapView) Icons.Default.List else Icons.Default.Map,
                            contentDescription = if (showMapView) "Show List View" else "Show Map View",
                            tint = Forest
                        )
                    }
                }
            ) 
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Handled in NavGraph */ }, containerColor = com.sanibonani.save.ui.theme.Forest, contentColor = Color.White) {
                Icon(Icons.Default.Add, "Register Group")
            }
        }
    ) { padding ->
        if (state.isLoading && state.filteredGroups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = com.sanibonani.save.ui.theme.Forest) }
        } else if (showMapView) {
            // Map View
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Search bar for map view
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { vm.updateFilter(query = it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search groups...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Forest,
                        unfocusedBorderColor = LightGray
                    )
                )
                
                // Map
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val groupsWithLocation = state.filteredGroups.filter { 
                        it.latitude != null && it.longitude != null 
                    }
                    
                    if (groupsWithLocation.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            EmptyState(
                                icon = "🗺️",
                                title = "No Groups on Map",
                                description = "No groups with location data available. Groups will appear here once they have coordinates set."
                            )
                        }
                    } else {
                        SaOsmMap(
                            groups = groupsWithLocation,
                            onMarker = { groupId -> onGroupClick(groupId) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Groups count indicator
                Surface(
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Forest, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        val groupsWithLoc = state.filteredGroups.count { it.latitude != null && it.longitude != null }
                        Text(
                            text = "$groupsWithLoc of ${state.filteredGroups.size} groups have location data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MidGray
                        )
                    }
                }
            }
        } else {
            // List View
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item { Spacer(Modifier.height(16.dp)) }
                items(state.filteredGroups) { group ->
                    GroupListItem(group) { onGroupClick(group.id ?: "") }
                    Spacer(Modifier.height(12.dp))
                }
                
                // Empty state for list view
                if (state.filteredGroups.isEmpty()) {
                    item {
                        EmptyState(
                            icon = "👥",
                            title = "No Groups Found",
                            description = "No savings groups available. Be the first to create one!"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupListItem(group: Group, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Text(group.logoEmoji, fontSize = 24.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("${group.type.displayName} • ${group.city}", color = MidGray, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MidGray)
        }
    }
}

@Composable
fun GroupProfileScreen(
    groupId: String,
    onJoinGroup: () -> Unit,
    onBack: () -> Unit,
    vm: GroupViewModel = hiltViewModel()
) {
    val state by vm.detail.collectAsState()

    LaunchedEffect(groupId) {
        vm.loadGroup(groupId)
    }

    Scaffold(
        topBar = { SanibonaniTopBar("Group Profile", onBack = onBack) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Forest)
            }
        } else if (state.error != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EmptyState("❌", "Failed to load group", state.error!!)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.loadGroup(groupId) }, colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                    Text("Retry")
                }
            }
        } else {
            state.group?.let { group ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Cream)
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ── Header Section ──────────────────────────────────────────
                    Surface(
                        color = Color.White,
                        shadowElevation = 2.dp,
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .background(Forest.copy(0.05f), CircleShape)
                                    .border(2.dp, Forest.copy(0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(group.logoEmoji, fontSize = 48.sp)
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Text(
                                group.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Charcoal,
                                textAlign = TextAlign.Center
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(color = Forest.copy(0.1f), shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        group.type.displayName,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Forest,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("•", color = MidGray)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${group.city}, ${group.province}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MidGray
                                )
                            }
                        }
                    }

                    Column(Modifier.padding(16.dp)) {
                        // ── Stats Row ───────────────────────────────────────────
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                icon = "👥",
                                label = "Members",
                                value = "${group.currentMembers}/${group.maxMembers}",
                                subtitle = "Capacity",
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                icon = "💰",
                                label = "Fund Value",
                                value = formatZARShort(group.balance),
                                subtitle = "Total Balance",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        // ── About Section ───────────────────────────────────────
                        SectionTitle("About this Group")
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, LightGray.copy(0.5f))
                        ) {
                            Text(
                                group.description?.ifBlank { "No description provided for this group." } ?: "No description provided for this group.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Charcoal,
                                modifier = Modifier.padding(16.dp),
                                lineHeight = 20.sp
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        // ── Financial Rules ─────────────────────────────────────
                        SectionTitle("Financial Requirements", "Monthly commitments and rules")
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                InfoRow("Joining Fee", formatZAR(group.joiningFee))
                                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Cream)
                                InfoRow("Monthly Contribution", formatZAR(group.monthlyContribution))
                                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Cream)
                                InfoRow("Late Payment Fine", formatZAR(group.lateFee))
                                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Cream)
                                InfoRow("Grace Period", "${group.lateFeeGraceDays} days")
                                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Cream)
                                InfoRow("Probation Period", "${group.probationMonths} months")
                            }
                        }

                        if (group.type == GroupType.BURIAL_SOCIETY) {
                            Spacer(Modifier.height(24.dp))
                            SectionTitle("Burial Benefits")
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    InfoRow("Max Beneficiaries", "${group.maxBeneficiaries ?: "Unlimited"}")
                                    group.beneficiaryIncreasePct?.let {
                                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Cream)
                                        InfoRow("Cost per Extra Beneficiary", "+${formatPct(it)}")
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(32.dp))

                        // ── Action ──────────────────────────────────────────────
                        if (group.currentMembers < group.maxMembers) {
                            SanibonaniButton(
                                text = "Apply to Join Group",
                                onClick = onJoinGroup,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(disabledContainerColor = MidGray)
                            ) {
                                Text("Group is Full")
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        InfoBox(
                            "Joining this group requires paying the R${formatDecimal(group.joiningFee)} fee upon registration.",
                            InfoType.INFO
                        )

                        Spacer(Modifier.height(40.dp))
                    }
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                EmptyState("🔍", "Group not found", "The group you are looking for might have been removed.") 
            }
        }
    }
}


@Composable
fun GroupRegistrationScreen(
    onSuccess: (String) -> Unit,
    onBack: () -> Unit,
    vm: GroupViewModel = hiltViewModel()
) {
    val state by vm.registerState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.success, state.createdGroupId) {
        if (state.success) {
            val gid = state.createdGroupId
            if (gid != null) {
                Toast.makeText(context, "Group registered successfully!", Toast.LENGTH_LONG).show()
                onSuccess(gid)
                vm.resetNavigation() // Reset state after navigation
            }
        }
    }

    if (state.needsPayment) {
        val payVm: PaymentViewModel = hiltViewModel()
        val payState by payVm.state.collectAsState()

        PaymentScreen(
            paymentType = "registration",
            amount = PlatformFees.REGISTRATION,
            groupId = state.createdGroupId ?: "new_group",
            onPaymentComplete = { 
                vm.finalizeRegistrationAfterPayment(payState.transactionId) 
            },
            onBack = { vm.updateField("needsPayment", false) },
            vm = payVm
        )
    } else {
        Scaffold(
            topBar = {
                SanibonaniTopBar(
                    title = "Register Group",
                    onBack = if (state.success) null else {
                        {
                            if (state.currentStep > 1) {
                                vm.prevStep()
                            } else {
                                onBack()
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Cream)
                    .padding(padding)
                    .imePadding()
                    .padding(24.dp)
            ) {
                if (state.success) {
                    // Success screen
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Forest, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Group Created!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("You will be redirected shortly.", color = MidGray)
                        }
                    }
                } else {
                    // Registration steps
                    LinearProgressIndicator(
                        progress = { state.currentStep / state.totalSteps.toFloat() },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = com.sanibonani.save.ui.theme.Forest,
                        trackColor = com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f)
                    )
                    Spacer(Modifier.height(24.dp))

                    Column(Modifier.weight(1f)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            when (state.currentStep) {
                                1 -> RegStep1(state, vm)
                                2 -> RegStep2(state, vm)
                                3 -> RegStep3(state, vm)
                                4 -> RegStep4(state, vm)
                                5 -> RegStepConstitution(state, vm)
                                6 -> RegStep5(state, vm)
                            }
                        }
                    }

                    if (state.error != null) {
                        InfoBox(state.error!!, InfoType.ERROR)
                        Spacer(Modifier.height(16.dp))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.currentStep > 1) {
                            OutlinedButton(onClick = { vm.prevStep() }, modifier = Modifier.weight(1f)) { Text("Back") }
                        }
                        SanibonaniButton(
                            text = if (state.currentStep == 6) "Pay Registration Fee" else "Next",
                            onClick = { if (state.currentStep == 6) vm.submitGroup() else vm.nextStep() },
                            modifier = Modifier.weight(1f),
                            isLoading = state.isSubmitting
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterGroupScreen(onGroupCreated: (String) -> Unit, onBack: () -> Unit) {
    GroupRegistrationScreen(onSuccess = onGroupCreated, onBack = onBack)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegStep1(s: RegisterGroupState, vm: GroupViewModel) {
    Text("Basic Info", style = MaterialTheme.typography.headlineMedium, color = com.sanibonani.save.ui.theme.Forest)
    Text("Tell us the name and type of your group.", style = MaterialTheme.typography.bodySmall, color = MidGray)
    Spacer(Modifier.height(16.dp))
    
    SanibonaniTextField(s.name, { vm.updateField("name", it) }, "Group Name *")
    Spacer(Modifier.height(16.dp))
    
    SanibonaniTextField(s.adminEmail, { vm.updateField("adminEmail", it) }, "Group Email *")
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.adminPhone,
        onValueChange = { if (it.length <= 10) vm.updateField("adminPhone", it) },
        label = "WhatsApp Number *",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        visualTransformation = PhoneNumberTransformation()
    )
    Spacer(Modifier.height(16.dp))

    Text("Group Type", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    GroupType.entries.forEach { type ->
        Row(
            Modifier.fillMaxWidth().clickable { vm.updateField("type", type) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = s.type == type, onClick = { vm.updateField("type", type) })
            Spacer(Modifier.width(8.dp))
            Column {
                Text(type.displayName, fontWeight = FontWeight.Medium)
                Text(getDesc(type), style = MaterialTheme.typography.bodySmall, color = MidGray)
            }
        }
    }
    
    Spacer(Modifier.height(24.dp))
    Text("Group Icon", style = MaterialTheme.typography.labelLarge)
    Text("Choose an emoji to represent your group.", style = MaterialTheme.typography.bodySmall, color = MidGray)
    Spacer(Modifier.height(12.dp))
    
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(64.dp).background(com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(s.logoEmoji, fontSize = 32.sp)
        }
        
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("🤝", "💰", "🏠", "📈", "🕊️", "🛡️", "🇿🇦", "🔥", "💎", "🌟").forEach { emoji ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { vm.updateField("logoEmoji", emoji) }
                        .background(if (s.logoEmoji == emoji) com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.2f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

private fun getDesc(t: GroupType) = when(t) {
    GroupType.STOKVEL -> "Traditional savings and social event support."
    GroupType.BURIAL_SOCIETY -> "Dedicated funds for funeral expenses."
    GroupType.INVESTMENT_CLUB -> "Pooling capital for long-term growth."
    GroupType.ROSCA -> "Rotating Savings and Credit Association."
    GroupType.EMERGENCY_FUND -> "Savings for unexpected costs."
    GroupType.COMMUNITY_SAVINGS -> "General community savings group."
    GroupType.TONTINE -> "Investment system where participants share a common fund."
    GroupType.OTHER -> "Other type of savings group."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegStep2(s: RegisterGroupState, vm: GroupViewModel) {
    Text("Location", style = MaterialTheme.typography.headlineMedium, color = com.sanibonani.save.ui.theme.Forest)
    Text("Where is your group based?", style = MaterialTheme.typography.bodySmall, color = MidGray)
    Spacer(Modifier.height(16.dp))

    AutoCompleteTextField(
        value = s.city,
        onValueChange = { vm.updateField("city", it) },
        label = "City / Town / Township *",
        suggestions = s.addressSuggestions,
        onSuggestionClick = { vm.onAddressSelected(it) },
        isLoading = s.isSearchingAddress
    )
    Spacer(Modifier.height(16.dp))

    Text("Province *", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SA_PROVINCES.forEach { p ->
            FilterChip(
                selected = s.province == p,
                onClick = { vm.updateField("province", p) },
                label = { Text(p) }
            )
        }
    }
}

@Composable
private fun RegStep3(s: RegisterGroupState, vm: GroupViewModel) {
    Text("Rules & Fees", style = MaterialTheme.typography.headlineMedium, color = com.sanibonani.save.ui.theme.Forest)
    Text("Define the financial commitments.", style = MaterialTheme.typography.bodySmall, color = MidGray)
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.joiningFee,
        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.updateField("joiningFee", it) },
        label = "Registration / Joining Fee *",
        placeholder = "e.g. 500.00",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
    Spacer(Modifier.height(16.dp))
    
    SanibonaniTextField(
        value = s.monthlyContribution,
        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.updateField("monthlyContribution", it) },
        label = "Monthly Contribution *",
        placeholder = "e.g. 200.00",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.maxMembers,
        onValueChange = { vm.updateField("maxMembers", it.filter { c -> c.isDigit() }) },
        label = "Maximum Members *",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(16.dp))
    
    SanibonaniTextField(
        value = s.lateFee,
        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.updateField("lateFee", it) },
        label = "Late Payment Fine",
        placeholder = "e.g. 50.00",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.lateFeeGraceDays,
        onValueChange = { vm.updateField("lateFeeGraceDays", it.filter { c -> c.isDigit() }) },
        label = "Grace Period (Days)",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.probationMonths,
        onValueChange = { vm.updateField("probationMonths", it.filter { c -> c.isDigit() }) },
        label = "Probation Period (Months)",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.paymentDueDay,
        onValueChange = { vm.updateField("paymentDueDay", it.filter { c -> c.isDigit() }) },
        label = "Payment Due Day of Month",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(16.dp))

    if (s.type == GroupType.STOKVEL || s.type == GroupType.INVESTMENT_CLUB) {
        SanibonaniTextField(
            value = s.goalAmount,
            onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.updateField("goalAmount", it) },
            label = "Total Savings Goal (R)",
            placeholder = "e.g. 50000.00",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(16.dp))

        SanibonaniTextField(
            value = s.periodMonths,
            onValueChange = { vm.updateField("periodMonths", it.filter { c -> c.isDigit() }) },
            label = "Savings Period (Months)",
            placeholder = "e.g. 12",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(16.dp))
    }

    if (s.type == GroupType.BURIAL_SOCIETY) {
        SanibonaniTextField(
            value = s.maxBeneficiaries,
            onValueChange = { vm.updateField("maxBeneficiaries", it.filter { c -> c.isDigit() }) },
            label = "Max Beneficiaries per Member *",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(16.dp))

        SanibonaniTextField(
            value = s.beneficiaryIncreasePct,
            onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) vm.updateField("beneficiaryIncreasePct", it) },
            label = "Monthly Increase % per Extra Beneficiary",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(16.dp))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = s.allowPartialPayment,
            onCheckedChange = { vm.updateField("allowPartialPayment", it) }
        )
        Column {
            Text("Allow Partial Payments", style = MaterialTheme.typography.bodyLarge)
            Text(
                "If disabled, members must pay the full monthly amount.",
                style = MaterialTheme.typography.bodySmall,
                color = MidGray
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = s.termsAccepted, onCheckedChange = { vm.updateField("termsAccepted", it) })
        Text("I accept the terms and conditions.")
    }
}

@Composable
private fun RegStep4(s: RegisterGroupState, vm: GroupViewModel) {
    Text("Banking", style = MaterialTheme.typography.headlineMedium, color = com.sanibonani.save.ui.theme.Forest)
    Text("Where should group funds be deposited?", style = MaterialTheme.typography.bodySmall, color = MidGray)
    Spacer(Modifier.height(16.dp))
    
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = s.bankName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Bank Name *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SA_BANKS.forEach { b ->
                DropdownMenuItem(text = { Text(b) }, onClick = { vm.updateField("bankName", b); expanded = false })
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SanibonaniTextField(s.accountNumber, { vm.updateField("accountNumber", it.filter { c -> c.isDigit() }) }, "Account Number *", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    Spacer(Modifier.height(12.dp))
    SanibonaniTextField(s.branchCode, { vm.updateField("branchCode", it.filter { c -> c.isDigit() }) }, "Branch Code *", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun RegStepConstitution(s: RegisterGroupState, vm: GroupViewModel) {
    Text("Constitution", style = MaterialTheme.typography.headlineMedium, color = com.sanibonani.save.ui.theme.Forest)
    Text("Upload your group's constitution or rules of operation.", style = MaterialTheme.typography.bodySmall, color = MidGray)
    Spacer(Modifier.height(16.dp))

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes()
                val fileName = "constitution_${System.currentTimeMillis()}.pdf"
                if (bytes != null) {
                    if (bytes.size > com.sanibonani.save.data.FileUploadLimits.MAX_FILE_SIZE_BYTES) {
                        ToastUtils.showError(context, "File too large. Maximum 3MB allowed.")
                    } else {
                        vm.uploadConstitution(bytes, fileName)
                    }
                }
            } catch (e: Exception) {
                ToastUtils.showError(context, "Failed to read file: ${e.message}")
            }
        }
    }

    DocumentUploadCard(
        name = "Group Constitution",
        isUploaded = s.constitutionUrl != null,
        status = s.constitutionStatus,
        onUpload = { launcher.launch("application/pdf") }
    )
    
    if (s.isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Gold)
    }

    Spacer(Modifier.height(24.dp))
    Text("Why is this needed?", style = MaterialTheme.typography.titleSmall)
    Text(
        "A constitution helps resolve disputes and ensures all members are aligned with the group's goals and rules.",
        style = MaterialTheme.typography.bodySmall,
        color = MidGray
    )
}

@Composable
private fun RegStep5(s: RegisterGroupState, vm: GroupViewModel) {
    Text("Admin Account", style = MaterialTheme.typography.headlineMedium, color = com.sanibonani.save.ui.theme.Forest)
    Text("Create your group administrator credentials.", style = MaterialTheme.typography.bodySmall, color = MidGray)
    Spacer(Modifier.height(16.dp))

    SanibonaniTextField(
        value = s.adminFullName,
        onValueChange = { vm.updateField("adminFullName", it) },
        label = "Admin Full Name *",
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
    )
    Spacer(Modifier.height(12.dp))

    SanibonaniTextField(
        value = s.adminIdNumber,
        onValueChange = { if (it.length <= 13 && it.all { c -> c.isDigit() }) vm.updateField("adminIdNumber", it) },
        label = "SA ID Number *",
        placeholder = "13-digit SA ID",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = IDNumberTransformation(),
        isError = s.adminIdNumber.isNotEmpty() && s.adminIdNumber.length != 13,
        supportingText = if (s.adminIdNumber.isNotEmpty() && s.adminIdNumber.length != 13) "ID Number must be 13 digits" else null
    )
    Spacer(Modifier.height(12.dp))

    SanibonaniTextField(
        value = s.adminEmail, 
        onValueChange = { vm.updateField("adminEmail", it) }, 
        label = "Admin Email (Primary Login) *", 
        enabled = !s.isLoggedIn
    )
    Spacer(Modifier.height(12.dp))
    
    if (!s.isLoggedIn) {
        var passwordVisible by remember { mutableStateOf(false) }
        SanibonaniTextField(
            value = s.adminPassword,
            onValueChange = { vm.updateField("adminPassword", it) },
            label = "Admin Password *",
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(image, description)
                }
            }
        )
    } else {
        InfoBox("You are already logged in. This group will be linked to your existing account.", InfoType.INFO)
    }

    Spacer(Modifier.height(24.dp))
    InfoBox(
        "As the group creator, you will automatically be registered as the first member. " +
        "Your R${formatDecimal(PlatformFees.REGISTRATION)} platform registration fee will be credited as your first contribution.",
        InfoType.INFO
    )

    Spacer(Modifier.height(16.dp))
    Text("Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.05f))) {
        Column(Modifier.padding(12.dp)) {
            Text("Name: ${s.name}")
            Text("Type: ${s.type.displayName}")
            Text("Location: ${s.city}, ${s.province}")
            Text("Monthly Fee: ${formatZAR(s.monthlyContribution.toDoubleOrNull() ?: 0.0)}")
            
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = com.sanibonani.save.ui.theme.Forest.copy(alpha = 0.1f))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Platform Registration Fee:", style = MaterialTheme.typography.bodySmall, color = MidGray)
                Text(formatZAR(PlatformFees.REGISTRATION), fontWeight = FontWeight.Bold, color = com.sanibonani.save.ui.theme.Forest)
            }
            Text("This is a once-off fee to activate your group.", style = MaterialTheme.typography.labelSmall, color = MidGray)
        }
    }
}
