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
        val claimId = claim.id ?: return@let
        ClaimDetailDialog(
            claim = claim,
            onDismiss = { financialVm.selectClaim(null) },
            onVerifyDoc = { key, approve -> financialVm.verifyClaimDocument(claimId, key, approve) },
            onUpdateStatus = { status -> financialVm.verifyClaimDocument(claimId, "status", status == BeneficiaryClaimStatus.APPROVED) }
        )
    }

    financialState.selectedPayout?.let { payout ->
        val payoutId = payout.id ?: return@let
        PayoutDetailDialog(
            payout = payout,
            onDismiss = { financialVm.selectPayout(null) },
            onVerifyDoc = { approve -> financialVm.verifyPayoutDocument(payoutId, approve) }
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
