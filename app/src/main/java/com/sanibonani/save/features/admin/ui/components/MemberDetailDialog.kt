package com.sanibonani.save.features.admin.ui.components

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val context = LocalContext.current
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
                    // Status Overview
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.ui_financial_summary), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onExportMemberStatement(member, false) },
                                        modifier = Modifier.size(24.dp),
                                        enabled = !isExporting
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.List, "Export CSV", tint = Navy)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { onExportMemberStatement(member, true) },
                                        modifier = Modifier.size(24.dp),
                                        enabled = !isExporting
                                    ) {
                                        if (isExporting) {
                                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                        } else {
                                            Icon(Icons.Default.PictureAsPdf, "Export PDF", tint = Navy)
                                        }
                                    }
                                }
                            }
                            DetailRow("Status", member.status.displayName, isBold = true, color = if (member.status == MemberStatus.SUSPENDED) ErrorRed else Navy)
                            DetailRow("Total Paid", formatZAR(member.totalPaid))
                            calculation?.let {
                                DetailRow("Shortfall", formatZAR(it.shortfall), color = if (it.isOverdue) ErrorRed else Navy)
                                DetailRow("Overpayment", formatZAR(it.overpayment), color = SuccessGreen)
                                DetailRow("Next Due", it.nextDueDate)
                                if (it.isOverdue) {
                                    DetailRow("Total Due Now", formatZAR(it.totalDueNow), isBold = true, color = ErrorRed)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Navy.copy(0.1f))

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.ui_loan_eligibility), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                val (color, text) = if (isEligibleForLoan) {
                                    Navy to "âœ… QUALIFIED"
                                } else {
                                    ErrorRed to "âŒ NOT QUALIFIED"
                                }
                                Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            loanIneligibilityReason?.let { reason ->
                                Text(reason, style = MaterialTheme.typography.labelSmall, color = ErrorRed, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    DetailSection("Personal Information") {
                        DetailRow("ID Number", member.idNumber?.takeIf { it.isNotBlank() } ?: "Not captured")
                        DetailRow("Phone", member.phone.ifBlank { "Not captured" })
                        DetailRow("Joined", member.joinedAt?.substringBefore("T") ?: "N/A")

                        val addressParts = listOfNotNull(
                            member.street?.takeIf { it.isNotBlank() },
                            member.suburb?.takeIf { it.isNotBlank() },
                            member.city?.takeIf { it.isNotBlank() },
                            member.province?.takeIf { it.isNotBlank() }
                        )
                        DetailRow("Address", if (addressParts.isEmpty()) "Not captured" else addressParts.joinToString(", "))
                    }

                    // Banking Details
                    var isEditingBanking by remember { mutableStateOf(false) }
                    var bankName by remember(member.bankName) { mutableStateOf(member.bankName ?: "") }
                    var accountNo by remember(member.accountNo) { mutableStateOf(member.accountNo ?: "") }
                    var branchCode by remember(member.branchCode) { mutableStateOf(member.branchCode ?: "") }

                    DetailSection("Banking Details") {
                        if (isEditingBanking) {
                            OutlinedTextField(
                                value = bankName,
                                onValueChange = { bankName = it },
                                label = { Text("Bank Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = accountNo,
                                onValueChange = { accountNo = it },
                                label = { Text("Account Number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = branchCode,
                                onValueChange = { branchCode = it },
                                label = { Text("Branch Code") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { isEditingBanking = false }) { Text("Cancel") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = {
                                    onSaveBankingDetails(bankName, accountNo, branchCode)
                                    isEditingBanking = false
                                }) { Text("Save") }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    DetailRow("Bank", member.bankName?.takeIf { it.isNotBlank() } ?: "Not captured")
                                    DetailRow("Account No", member.accountNo?.takeIf { it.isNotBlank() } ?: "Not captured")
                                    DetailRow("Branch", member.branchCode?.takeIf { it.isNotBlank() } ?: "Not captured")
                                }
                                IconButton(onClick = { isEditingBanking = true }) {
                                    Icon(Icons.Default.Edit, "Edit Banking Details", tint = Navy, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    // Beneficiaries
                    DetailSection("Beneficiaries (${beneficiaries.size})") {
                        beneficiaries.forEach { b ->
                            Surface(
                                onClick = { onEditBeneficiary(b) },
                                shape = RoundedCornerShape(8.dp),
                                color = Navy.copy(0.05f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(b.fullName, fontWeight = FontWeight.Bold)
                                        Text("${b.relationship} â€¢ ${b.idNumber ?: "N/A"}", style = MaterialTheme.typography.labelSmall)

                                        if (b.documentUrl != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                                Icon(Icons.Default.CheckCircle, null, tint = Navy, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.ui_id_document_uploaded), style = MaterialTheme.typography.labelSmall, color = Navy)
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (b.documentUrl != null) {
                                            IconButton(onClick = {
                                                val url = b.documentUrl ?: return@IconButton
                                                val headers = getDownloadParams(url)
                                                val ext = url.substringAfterLast(".", "pdf").substringBefore("?")
                                                val fileName = "Beneficiary_ID_${b.fullName.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                                                onFileAction(url, fileName, headers)
                                            }) {
                                                Icon(Icons.Default.PictureAsPdf, "Download ID", tint = Navy)
                                            }
                                        }
                                        Icon(Icons.Default.Edit, null, tint = Navy, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (beneficiaries.isEmpty()) {
                            Text(stringResource(R.string.ui_no_beneficiaries_registered), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }

                    // Burial Claims
                    if (burialClaims.isNotEmpty()) {
                        DetailSection("Burial Claims (${burialClaims.size})") {
                            burialClaims.forEach { claim ->
                                ClaimAdminCard(
                                    claim = claim,
                                    onVerify = { approve: Boolean -> claim.id?.let { onVerifyClaim(it, approve) } },
                                    onEscalate = { claim.id?.let { onEscalateClaim(it) } }
                                )
                            }
                        }
                    }

                    // Documents
                    DetailSection("Documents") {
                        var hasAnyDoc = false
                        // Core KYC Documents (Indexed 1-4)
                        for (i in 1..4) {
                            val url = when(i) {
                                1 -> member.document1Url; 2 -> member.document2Url; 3 -> member.document3Url; 4 -> member.document4Url; else -> null
                            }
                            val status = when(i) {
                                1 -> member.document1Status; 2 -> member.document2Status; 3 -> member.document3Status; 4 -> member.document4Status; else -> DocumentStatus.PENDING
                            }
                            val label = when(i) {
                                1 -> "Identity Document (ID)"
                                2 -> "Proof of Residence"
                                3 -> "Beneficiary Form / Documents"
                                4 -> "Marriage Certificate"
                                else -> ""
                            }

                            if (url != null) {
                                hasAnyDoc = true
                                DocumentAdminCard(
                                    label = label,
                                    url = url,
                                    status = status,
                                    onVerify = { approve ->
                                        onVerifyDoc(i, approve)
                                    },
                                    onDownload = { dUrl, dLabel ->
                                        val (dlUrl, headers) = downloadMemberDocument(dUrl, dLabel)
                                        val extension = dlUrl.substringAfterLast(".", "pdf")
                                        val fileName = "${dLabel.replace(" ", "_")}_${System.currentTimeMillis()}.$extension"
                                        onFileAction(dlUrl, fileName, headers)
                                    }
                                )
                            }
                        }

                        // New Relational Documents
                        documents.forEach { doc ->
                            val docId = doc.id
                            if (docId != null) {
                                hasAnyDoc = true
                                DocumentAdminCard(
                                    label = doc.label,
                                    url = doc.documentUrl,
                                    status = doc.status,
                                    onVerify = { approve -> onVerifyRelDoc(docId, approve) },
                                    onDownload = { dUrl, dLabel ->
                                        val (dlUrl, headers) = downloadMemberDocument(dUrl, dLabel)
                                        val extension = dlUrl.substringAfterLast(".", "pdf")
                                        val fileName = "${dLabel.replace(" ", "_")}_${System.currentTimeMillis()}.$extension"
                                        onFileAction(dlUrl, fileName, headers)
                                    }
                                )
                            }
                        }

                        if (!hasAnyDoc) {
                            Text(stringResource(R.string.ui_no_documents_uploaded_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }

                    // Direct Message
                    DetailSection("Send Direct Message") {
                        SanibonaniTextField(
                            value = messageText,
                            onValueChange = onMessageChange,
                            label = stringResource(R.string.ui_message_text),
                            singleLine = false,
                            modifier = Modifier.height(100.dp)
                        )

                        Text(
                            "Send Via:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NotifChannel.entries.forEach { channel ->
                                val isSelected = selectedNotifChannel == channel
                                val (label, icon) = when (channel) {
                                    NotifChannel.PUSH -> "In-App" to Icons.Default.NotificationsActive
                                    NotifChannel.EMAIL -> "Email" to Icons.Default.Email
                                    NotifChannel.SMS -> "SMS" to Icons.Default.Sms
                                    NotifChannel.WHATSAPP -> "WhatsApp" to Icons.AutoMirrored.Filled.Chat
                                    NotifChannel.BOTH -> "Both" to Icons.Default.AllInclusive
                                }

                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onNotifChannelChange(channel) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = Color.Transparent,
                                        selectedBorderColor = Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        if (messageSuccess) {
                            InfoBox("Message sent successfully!", type = InfoType.SUCCESS)
                        }

                        Spacer(Modifier.height(4.dp))

                        SanibonaniButton(
                            text = "Send Message",
                            onClick = {
                                member.id?.let { id ->
                                    onSendMessage(messageText, id)
                                }
                            },
                            isLoading = isSending,
                            enabled = messageText.isNotBlank() && !isSending,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
