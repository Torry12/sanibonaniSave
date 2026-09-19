package com.sanibonani.save.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.viewmodel.GroupViewModel

@Composable
fun GroupProfileScreen(
    groupId: String,
    onJoinGroup: () -> Unit,
    onBack: () -> Unit,
    vm: GroupViewModel = hiltViewModel()
) {
    val state by vm.detail.collectAsStateWithLifecycle()
    val clickDebouncer = rememberClickDebouncer()

    DisposableEffect(Unit) {
        vm.setActive(true)
        onDispose {
            vm.setActive(false)
        }
    }

    LaunchedEffect(groupId) {
        vm.loadGroup(groupId)
    }

    Scaffold(
        topBar = { SanibonaniTopBar("Group Profile", onBack = { clickDebouncer.processClick(onBack) }) }
    ) { padding ->
        if (state.isLoading && state.group == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Navy)
            }
        } else if (state.error != null && state.group == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(icon = "❌", title = "Error", description = state.error!!)
            }
        } else {
            state.group?.let { group ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Cream)
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Navy.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(group.logoEmoji, fontSize = 40.sp)
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text(group.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(group.type.displayName, color = Color.Black.copy(alpha = 0.6f))
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    SectionTitle("About Group")
                    Text(group.description ?: "No description provided.", style = MaterialTheme.typography.bodyLarge)

                    Spacer(Modifier.height(24.dp))

                    SectionTitle("Financials")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(title = "Monthly", value = group.monthlyContribution.format(), label = "Subscription", modifier = Modifier.weight(1f))
                        StatCard(title = "Joining Fee", value = group.joiningFee.format(), label = "One-time", modifier = Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(title = "Balance", value = group.balance.format(), label = "Total Savings", modifier = Modifier.weight(1f))
                        StatCard(title = "Members", value = "${group.currentMembers}/${group.maxMembers}", label = "Occupancy", modifier = Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionTitle("Location")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Navy, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${group.township ?: ""}, ${group.city}, ${group.province}")
                    }

                    Spacer(Modifier.height(40.dp))

                    SanibonaniButton(
                        text = "Apply to Join Group",
                        onClick = { clickDebouncer.processClick(onJoinGroup) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
