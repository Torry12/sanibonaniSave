package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.admin.presentation.state.AdminMemberUiState

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MembersTab(
    state: AdminMemberUiState,
    windowSizeClass: WindowSizeClass,
    onMemberClick: (Member) -> Unit,
    onRefresh: () -> Unit
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Member>()

    LaunchedEffect(state.selectedMember) {
        if (state.selectedMember != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, state.selectedMember)
        } else {
            // 🛡️ Navigation Hardening: If selection is cleared, pop back to the list view
            // to prevent getting stuck in an empty Detail pane on small screens.
            if (navigator.canNavigateBack()) {
                navigator.navigateBack()
            }
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            MemberListSection(state, onMemberClick, onRefresh)
        },
        detailPane = {
            state.selectedMember?.let { member ->
                MemberDetailSection(member, state.memberCalculations[member.id ?: ""])
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a member to view details")
            }
        }
    )
}

@Composable
fun MemberListSection(
    state: AdminMemberUiState,
    onMemberClick: (Member) -> Unit,
    onRefresh: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredMembers = state.members.filter {
        it.fullName.contains(searchQuery, ignoreCase = true) || (it.idNumber?.contains(searchQuery) == true)
    }

    val membersCountDiscrepancy = (state.group?.currentMembers ?: 0) != state.members.size

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (membersCountDiscrepancy && state.members.isEmpty()) {
             OfflineBanner(isOnline = false, message = "⚠️ Member list may be out of sync. Try refreshing.")
        }

        SanibonaniSectionHeader(
            title = stringResource(R.string.ui_members),
            subtitle = "${state.members.size} enrolled in this group",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.ui_search_by_name_or_id)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp)
        )

        if (filteredMembers.isEmpty()) {
            EmptyState(
                icon = "👥",
                title = "No members found",
                description = if (searchQuery.isNotBlank()) "Try a different search term." else "This group doesn't have any members yet.",
                actionLabel = "Refresh List",
                onAction = { onRefresh() }
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredMembers, key = { it.id ?: it.hashCode().toString() }) { member ->
                    val calculation = state.memberCalculations[member.id ?: ""]
                    MemberItem(
                        member = member,
                        calculation = calculation,
                        onClick = { onMemberClick(member) }
                    )
                }
            }
        }
    }
}

@Composable
fun MemberDetailSection(member: Member, calculation: PaymentCalculation?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
    ) {
        Text(member.fullName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Status: ${member.status.displayName}", color = if (calculation?.isOverdue == true) ErrorRed else Navy)

        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Financial Summary", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Paid:")
                    Text(member.totalPaid.toString(), fontWeight = FontWeight.Bold)
                }
                if (calculation != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Due Now:")
                        Text(calculation.totalDueNow.toString(), color = if (calculation.isOverdue) ErrorRed else Navy)
                    }
                }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LightGray.copy(alpha = 0.4f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Navy.copy(0.1f)), contentAlignment = Alignment.Center) {
                Text(member.fullName.take(1).uppercase())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(member.fullName, fontWeight = FontWeight.Bold)
                Text(member.status.displayName, style = MaterialTheme.typography.labelSmall, color = when(member.status) {
                    MemberStatus.ACTIVE -> if (calculation?.isOverdue == true) ErrorRed else Navy
                    MemberStatus.PENDING_PAYMENT -> WarningAmber
                    else -> MidGray
                })
            }
            Icon(Icons.Default.ChevronRight, null, tint = LightGray)
        }
    }
}
