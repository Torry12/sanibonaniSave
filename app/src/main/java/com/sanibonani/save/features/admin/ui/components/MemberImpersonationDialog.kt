package com.sanibonani.save.features.admin.ui.components

import android.content.Context
import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.data.utils.FileDownloader
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.admin.presentation.*
import com.sanibonani.save.features.admin.presentation.state.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog


@Composable
fun MemberImpersonationDialog(
    groups: List<Group>,
    state: PlatformSupportUiState,
    vm: PlatformSupportViewModel,
    onDismiss: () -> Unit,
    onMemberSelected: (String, String) -> Unit
) {
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var groupQuery by remember { mutableStateOf("") }
    var memberQuery by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SupportAgent, null, tint = Navy)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.ui_support_manager))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = Navy,
                    divider = {}
                ) {
                    Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }) {
                        Text(stringResource(R.string.ui_by_group), modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }) {
                        Text(stringResource(R.string.ui_global_search), modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (selectedTabIndex == 0) {
                    // --- EXISTING GROUP FLOW ---
                    if (selectedGroup == null) {
                        Text(stringResource(R.string.ui_step_1_select_group), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = groupQuery,
                            onValueChange = { groupQuery = it },
                            label = { Text(stringResource(R.string.ui_search_group_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, null) }
                        )
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(
                                groups.filter { it.name.contains(groupQuery, ignoreCase = true) },
                                key = { it.id ?: it.name }
                            ) { group ->
                                DropdownMenuItem(
                                    text = { Text(group.name) },
                                    onClick = {
                                        selectedGroup = group
                                        vm.selectImpersonationGroup(group.id ?: "")
                                    }
                                )
                            }
                        }
                    } else {
                        val group = selectedGroup
                        if (group != null) {
                            Text(stringResource(R.string.ui_step_2_select_member_from, group.name), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                        if (state.isLoadingImpersonationMembers) {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Navy)
                            }
                        } else {
                            OutlinedTextField(
                                value = memberQuery,
                                onValueChange = { memberQuery = it },
                                label = { Text(stringResource(R.string.ui_search_member_name_id)) },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )
                            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                                items(
                                    state.impersonationMembers.filter { it.fullName.contains(memberQuery, ignoreCase = true) },
                                    key = { it.id ?: it.fullName }
                                ) { member ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(member.fullName)
                                                Text(member.idNumber ?: stringResource(R.string.ui_no_id), style = MaterialTheme.typography.labelSmall)
                                            }
                                        },
                                        onClick = {
                                            vm.triggerImpersonationAudit(member.id ?: "", group?.id ?: "")
                                            onMemberSelected(member.id ?: "", group?.id ?: "")
                                        }
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { selectedGroup = null }) { Text(stringResource(R.string.ui_back_to_group_selection)) }
                    }
                } else {
                    // --- GLOBAL SEARCH FLOW ---
                    Text(stringResource(R.string.ui_search_across_platform), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { vm.searchAllMembers(it); vm.updateSearchQuery(it) },
                        label = { Text(stringResource(R.string.ui_search_member_by_name_or_id)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text(stringResource(R.string.ui_min_3_characters)) }
                    )

                    if (state.searchQuery.length >= 3) {
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(state.memberBehaviorInsights, key = { "${it.groupId}_${it.memberId}" }) { insight ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(insight.memberName.ifBlank { "ID: ${insight.memberId.take(8)}..." })
                                            Text("Group ID: ${insight.groupId.take(8)}...", style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                    onClick = {
                                        vm.triggerImpersonationAudit(insight.memberId, insight.groupId)
                                        onMemberSelected(insight.memberId, insight.groupId)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
