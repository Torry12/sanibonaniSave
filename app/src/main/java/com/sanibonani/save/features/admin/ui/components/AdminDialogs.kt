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
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.admin.presentation.*
import com.sanibonani.save.features.admin.presentation.state.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
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
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
fun ClaimDetailDialog(
    claim: BeneficiaryPayoutClaim,
    onDismiss: () -> Unit,
    onVerifyDoc: (String, Boolean) -> Unit,
    onUpdateStatus: (BeneficiaryClaimStatus) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_claim_details_label, claim.beneficiaryName)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(stringResource(R.string.ui_requisite_documents), style = MaterialTheme.typography.titleSmall)
                }

                item {
                    DocumentAdminCard(
                        label = "Face Photo",
                        url = claim.facePhotoUrl,
                        status = claim.facePhotoStatus,
                        onVerify = { onVerifyDoc("face_photo", it) },
                        onDownload = { _, _ -> } // Handled by global downloader if needed
                    )
                }

                item {
                    DocumentAdminCard(
                        label = "Beneficiary ID",
                        url = claim.beneficiaryIdUrl,
                        status = claim.beneficiaryIdStatus,
                        onVerify = { onVerifyDoc("beneficiary_id", it) },
                        onDownload = { _, _ -> }
                    )
                }

                item {
                    DocumentAdminCard(
                        label = "Death Certificate",
                        url = claim.deathCertificateUrl,
                        status = claim.deathCertificateStatus,
                        onVerify = { onVerifyDoc("death_certificate", it) },
                        onDownload = { _, _ -> }
                    )
                }

                item {
                    DocumentAdminCard(
                        label = "Member ID",
                        url = claim.memberIdUrl,
                        status = claim.memberIdStatus,
                        onVerify = { onVerifyDoc("member_id", it) },
                        onDownload = { _, _ -> }
                    )
                }

                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.ui_financial_details), style = MaterialTheme.typography.titleSmall)
                    DetailRow(stringResource(R.string.ui_amount_to_pay), formatZAR(claim.claimAmount))
                    DetailRow("Bank", claim.bankName)
                    DetailRow("Account", claim.accountNo)
                }
            }
        },
        confirmButton = {
            if (claim.status == BeneficiaryClaimStatus.SUBMITTED) {
                Button(onClick = { onUpdateStatus(BeneficiaryClaimStatus.APPROVED) }) {
                    Text(stringResource(R.string.ui_approve_claim_btn))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close)) }
        }
    )
}

@Composable
fun PayoutDetailDialog(
    payout: PayoutRequest,
    onDismiss: () -> Unit,
    onVerifyDoc: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_payout_request_details)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(stringResource(R.string.ui_amount_to_pay), formatZAR(payout.amount), isBold = true)
                DetailRow("Type", payout.type.name)
                DetailRow("Status", payout.status.name)

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text("Disbursement Banking Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                DetailRow("Bank", payout.bankName)
                DetailRow("Account Number", payout.accountNo)
                DetailRow("Branch Code", payout.branchCode)

                if (!payout.supportingDocumentUrl.isNullOrBlank()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.ui_requisite_documents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    DocumentAdminCard(
                        label = "Supporting Document",
                        url = payout.supportingDocumentUrl,
                        status = payout.supportingDocumentStatus,
                        onVerify = onVerifyDoc,
                        onDownload = { _, _ -> }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
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

    financialState.selectedClaim?.let { claim ->
        ClaimDetailDialog(
            claim = claim,
            onDismiss = { financialVm.selectClaim(null) },
            onVerifyDoc = { key, approve -> financialVm.verifyClaimDocument(claim.id!!, key, approve) },
            onUpdateStatus = { status -> financialVm.verifyClaimDocument(claim.id!!, "status", status == BeneficiaryClaimStatus.APPROVED) }
        )
    }

    financialState.selectedPayout?.let { payout ->
        PayoutDetailDialog(
            payout = payout,
            onDismiss = { financialVm.selectPayout(null) },
            onVerifyDoc = { approve -> financialVm.verifyPayoutDocument(payout.id!!, approve) }
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
            fileName = "Document",
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
        title = { Text(stringResource(R.string.ui_select_group)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.ui_search_group)) },
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
                            items(groups.filter { it.name.contains(groupQuery, ignoreCase = true) }) { group ->
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
                                items(state.impersonationMembers.filter { it.fullName.contains(memberQuery, ignoreCase = true) }) { member ->
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

@Composable
fun ActuarialHealthDialog(
    group: Group,
    metrics: ActuarialMetrics?,
    healthScore: GroupHealthScore?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onNavigateToHealthScore: (String) -> Unit,
    onDeleteGroup: ((String) -> Unit)? = null
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete Group from Platform?", color = ErrorRed, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete \"${group.name}\"? This action will remove the group and all its records from the platform.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDelete = false
                        onDismiss()
                        onDeleteGroup?.invoke(group.id ?: "")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Actuarial Health: ${group.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (onDeleteGroup != null) {
                    TextButton(
                        onClick = { showConfirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                    ) {
                        Text("Delete Group", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                metrics?.let { m ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricRow(
                            label = "Actuarial Health Score",
                            value = "${m.compositeRiskScore}/100",
                            color = when {
                                m.compositeRiskScore >= 80 -> SuccessGreen
                                m.compositeRiskScore >= 55 -> WarningAmber
                                else -> ErrorRed
                            }
                        )

                        if (m.keyMetrics.isNotEmpty()) {
                            m.keyMetrics.forEach { metric ->
                                MetricRow(
                                    label = metric.title,
                                    value = metric.resultText,
                                    color = when (metric.status) {
                                        "OK", "OPTIMAL" -> SuccessGreen
                                        "WARNING", "LOW" -> WarningAmber
                                        "CRITICAL" -> ErrorRed
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        } else {
                            MetricRow("Reserve Adequacy", "${m.reserveAdequacyPct}%", MaterialTheme.colorScheme.primary)
                            MetricRow("Solvency Margin", "${m.solvencyMarginPct}%", MaterialTheme.colorScheme.primary)
                            MetricRow("Expected Claims (Ann)", formatZAR(m.expectedAnnualClaims), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                } else {
                    healthScore?.let { score ->
                        HorizontalDivider(color = LightGray.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(stringResource(R.string.ui_composite_health_score), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Text("${score.overallScore}/100", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (score.zone == RiskZone.GREEN) SuccessGreen else if (score.zone == RiskZone.YELLOW) WarningAmber else ErrorRed)
                            }

                            Surface(
                                color = (if (score.zone == RiskZone.GREEN) SuccessGreen else if (score.zone == RiskZone.YELLOW) WarningAmber else ErrorRed).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    score.zone.name,
                                    modifier = Modifier.padding(8.dp, 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (score.zone == RiskZone.GREEN) SuccessGreen else if (score.zone == RiskZone.YELLOW) WarningAmber else ErrorRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onNavigateToHealthScore(group.id ?: "")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.ui_view_full_health_report))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_close))
            }
        }
    )
}
