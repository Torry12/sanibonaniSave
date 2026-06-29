package com.sanibonani.save.ui.screens.admin.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.ModernNavigationLink
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.ui.theme.ErrorRed
import com.sanibonani.save.ui.theme.Forest
import com.sanibonani.save.ui.theme.WarningYellow
import com.sanibonani.save.viewmodel.AdminUiState
import com.sanibonani.save.viewmodel.AdminViewModel
import com.sanibonani.save.data.utils.PaymentCalculation

@Composable
fun MembersTab(
    state: AdminUiState,
    vm: AdminViewModel,
    isSupportMode: Boolean = false,
    onEnterPortal: (String) -> Unit = {},
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
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
                MemberItem(
                    member = member,
                    calculation = calculation,
                    isSupportMode = isSupportMode,
                    onEnterPortal = { member.id?.let { onEnterPortal(it) } },
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
    isSupportMode: Boolean = false,
    onEnterPortal: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val statusColor = when (member.status) {
        MemberStatus.ACTIVE -> if (calculation?.isOverdue == true) ErrorRed else Forest
        MemberStatus.PROBATION -> WarningYellow
        MemberStatus.PENDING_PAYMENT -> Color.Gray
        else -> ErrorRed
    }

    ModernNavigationLink(
        title = member.fullName,
        subtitle = member.idNumber ?: "No ID captured",
        icon = Icons.Default.Person,
        onClick = onClick,
        accentColor = statusColor,
        badgeCount = if (calculation?.isOverdue == true) 1 else 0,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
