package com.sanibonani.save.features.admin.ui.components

import android.content.Context
import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

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
import com.sanibonani.save.domain.utils.FileDownloader
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.features.admin.presentation.*
import com.sanibonani.save.features.admin.presentation.state.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


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
        title = { Text(stringResource(R.string.ui_edit_beneficiary)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = beneficiary.fullName,
                    onValueChange = { text -> onUpdate(beneficiary.copy(fullName = text)) },
                    label = stringResource(R.string.ui_full_name)
                )
                SanibonaniTextField(
                    value = beneficiary.idNumber ?: "",
                    onValueChange = { text -> onUpdate(beneficiary.copy(idNumber = text)) },
                    label = stringResource(R.string.ui_id_number)
                )
                SanibonaniTextField(
                    value = beneficiary.relationship ?: "",
                    onValueChange = { text -> onUpdate(beneficiary.copy(relationship = text)) },
                    label = stringResource(R.string.ui_relationship)
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isSaving) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(R.string.ui_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        }
    )
}

@Composable
fun AdminDashboardDialogs(
    overviewState: AdminOverviewUiState,
    memberState: AdminMemberUiState,
    financialState: AdminFinancialUiState,
    overviewVm: AdminOverviewViewModel,
    memberVm: AdminMemberViewModel,
    financialVm: AdminFinancialViewModel,
    onNavigateToMemberPortal: (String, String?) -> Unit,
    isSupportMode: Boolean,
    context: Context
) {
    var showFileActionDialog by remember { mutableStateOf<Triple<String, String, Map<String, String>>?>(null) }
    var viewFileData by remember { mutableStateOf<Triple<String, String, Map<String, String>>?>(null) }

    memberState.selectedMember?.let { member ->
        MemberDetailDialog(
            member = member,
            group = overviewState.group,
            beneficiaries = memberState.selectedMemberBeneficiaries,
            documents = memberState.selectedMemberDocuments,
            calculation = memberState.selectedMemberCalculation,
            isEligibleForLoan = memberState.isEligibleForLoan,
            loanIneligibilityReason = memberState.loanIneligibilityReason,
            onDismiss = { memberVm.selectMember(null) },
            isSupportMode = isSupportMode,
            onEnterPortal = {
                overviewState.group?.id?.let { gid ->
                    onNavigateToMemberPortal(gid, member.id)
                }
            },
            isExporting = memberState.isExporting,
            onVerifyDoc = { idx, approve ->
                member.id?.let { memberVm.verifyDocument(it, idx, approve) }
            },
            onVerifyRelDoc = { docId, approve -> memberVm.verifyRelationalDocument(docId, approve) },
            onSendMessage = { msg, id -> memberVm.sendMessage(msg, id) },
            messageText = memberState.messageText,
            onMessageChange = { memberVm.updateMessageText(it) },
            selectedNotifChannel = memberState.selectedNotifChannel,
            onNotifChannelChange = { memberVm.updateNotifChannel(it) },
            onBroadcast = {},
            isSending = memberState.isSendingMessage,
            messageSuccess = memberState.messageSentSuccess,
            onEditBeneficiary = {},
            onExportMemberStatement = { m, pdf -> memberVm.exportMemberStatement(m, pdf) },
            getDownloadParams = { memberVm.getDownloadParams(it) },
            downloadMemberDocument = { url, label -> memberVm.downloadMemberDocument(url, label) },
            onFileAction = { url, name, headers -> showFileActionDialog = Triple(url, name, headers) },
            onSaveBankingDetails = { bankName, accountNo, branchCode ->
                member.id?.let { memberVm.updateBankingDetails(it, bankName, accountNo, branchCode) }
            }
        )
    }

    overviewState.error?.let { error ->
        AdminErrorDialog(error, onDismiss = { overviewVm.clearError() })
    }

    financialState.error?.let { error ->
        AdminErrorDialog(error, onDismiss = { financialVm.clearError() })
    }

    memberState.error?.let { error ->
        AdminErrorDialog(error, onDismiss = { memberVm.clearError() })
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
                FileDownloader.downloadFile(context, url, name, mimeType, headers)
            },
            fileName = name
        )
    }

    viewFileData?.let { (url, _, headers) ->
        FileViewerDialog(
            url = url,
            type = if (url.contains(".pdf", ignoreCase = true)) "pdf" else "image",
            headers = headers,
            onDismiss = { viewFileData = null }
        )
    }
}

@Composable
private fun AdminErrorDialog(error: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_error)) },
        text = { Text(error) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_ok)) } }
    )
}

@Composable
fun GroupSelectionDialog(
    groups: List<Group>,
    onDismiss: () -> Unit,
    onGroupSelected: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, groups) {
        groups.filter { it.name.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Group") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search Group") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(filtered) { group ->
                        DropdownMenuItem(
                            text = { Text(group.name) },
                            onClick = { onGroupSelected(group.id ?: "") }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MemberImpersonationDialog(
    state: PlatformAdminUiState,
    vm: PlatformAdminViewModel,
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
                Text("Support Manager")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = Navy,
                    divider = {}
                ) {
                    Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }) {
                        Text("By Group", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }) {
                        Text("Global Search", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (selectedTabIndex == 0) {
                    // --- EXISTING GROUP FLOW ---
                    if (selectedGroup == null) {
                        Text("Step 1: Select Group", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = groupQuery,
                            onValueChange = { groupQuery = it },
                            label = { Text("Search Group Name") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, null) }
                        )
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(state.groups.filter { it.name.contains(groupQuery, ignoreCase = true) }) { group ->
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
                        val group = selectedGroup!!
                        Text("Step 2: Select Member from ${group.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        if (state.isLoadingImpersonationMembers) {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Navy)
                            }
                        } else {
                            OutlinedTextField(
                                value = memberQuery,
                                onValueChange = { memberQuery = it },
                                label = { Text("Search Member Name/ID") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )
                            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                                items(state.impersonationMembers.filter { it.fullName.contains(memberQuery, ignoreCase = true) }) { member ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(member.fullName)
                                                Text(member.idNumber ?: "No ID", style = MaterialTheme.typography.labelSmall)
                                            }
                                        },
                                        onClick = {
                                            vm.triggerImpersonationAudit(member.id ?: "", group.id ?: "")
                                            onMemberSelected(member.id ?: "", group.id ?: "")
                                        }
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { selectedGroup = null }) { Text("Back to Group Selection") }
                    }
                } else {
                    // --- GLOBAL SEARCH FLOW ---
                    Text("Search Across Platform", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { vm.searchAllMembers(it); vm.updateSearchQuery(it) },
                        label = { Text("Search Member by Name or ID Number") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Min 3 characters...") }
                    )

                    if (state.searchQuery.length >= 3) {
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(state.memberBehaviorInsights) { insight ->
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
