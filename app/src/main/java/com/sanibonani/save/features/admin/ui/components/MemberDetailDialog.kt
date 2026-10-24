package com.sanibonani.save.features.admin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.ui.components.InitialsAvatar
import com.sanibonani.save.ui.theme.Navy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailDialog(
    member: Member,
    beneficiaries: List<Beneficiary>,
    documents: List<MemberDocument>,
    calculation: PaymentCalculation?,
    isEligibleForLoan: Boolean = false,
    loanIneligibilityReason: String? = null,
    onDismiss: () -> Unit,
    onVerifyDoc: (Int, Boolean) -> Unit,
    onVerifyRelDoc: (String, Boolean) -> Unit,
    onSendMessage: (String, String) -> Unit,
    messageText: String,
    onMessageChange: (String) -> Unit,
    selectedNotifChannel: NotifChannel = NotifChannel.BOTH,
    onNotifChannelChange: (NotifChannel) -> Unit = {},
    isSending: Boolean = false,
    messageSuccess: Boolean = false,
    onEditBeneficiary: (Beneficiary) -> Unit,
    onExportMemberStatement: (Member, Boolean) -> Unit = { _, _ -> },
    getDownloadParams: (String) -> Map<String, String> = { emptyMap() },
    downloadMemberDocument: (String, String) -> Pair<String, Map<String, String>> = { url, _ -> Pair(url, emptyMap()) },
    isSupportMode: Boolean = false,
    onEnterPortal: (() -> Unit)? = null,
    isExporting: Boolean = false,
    burialClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    onVerifyClaim: (String, Boolean) -> Unit = { _, _ -> },
    onEscalateClaim: (String) -> Unit = {},
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> },
    onSaveBankingDetails: (String, String, String) -> Unit = { _, _, _ -> }
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InitialsAvatar(
                                initials = member.fullName,
                                size = 32.dp,
                                background = Navy,
                                photoUrl = member.profilePhotoUrl
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(member.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                    },
                    actions = {
                        if (isSupportMode && (onEnterPortal != null)) {
                            TextButton(onClick = onEnterPortal) {
                                Icon(Icons.AutoMirrored.Filled.Login, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.ui_enter_portal), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MemberSummaryCard(
                        member = member,
                        calculation = calculation,
                        isEligibleForLoan = isEligibleForLoan,
                        loanIneligibilityReason = loanIneligibilityReason,
                        isExporting = isExporting,
                        onExportMemberStatement = onExportMemberStatement
                    )

                    MemberPersonalInfoSection(member)

                    MemberBankingSection(
                        member = member,
                        onSaveBankingDetails = onSaveBankingDetails
                    )

                    MemberBeneficiariesSection(
                        beneficiaries = beneficiaries,
                        getDownloadParams = getDownloadParams,
                        onEditBeneficiary = onEditBeneficiary,
                        onFileAction = onFileAction
                    )

                    MemberBurialClaimsSection(
                        burialClaims = burialClaims,
                        onVerifyClaim = onVerifyClaim,
                        onEscalateClaim = onEscalateClaim
                    )

                    MemberDocumentsSection(
                        member = member,
                        documents = documents,
                        onVerifyDoc = onVerifyDoc,
                        onVerifyRelDoc = onVerifyRelDoc,
                        downloadMemberDocument = downloadMemberDocument,
                        onFileAction = onFileAction
                    )

                    MemberMessageSection(
                        member = member,
                        messageText = messageText,
                        onMessageChange = onMessageChange,
                        selectedNotifChannel = selectedNotifChannel,
                        onNotifChannelChange = onNotifChannelChange,
                        isSending = isSending,
                        messageSuccess = messageSuccess,
                        onSendMessage = onSendMessage
                    )
                }
            }
        }
    }
}
