package com.sanibonani.save.features.admin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR

@Composable
internal fun MemberSummaryCard(
    member: Member,
    calculation: PaymentCalculation?,
    isEligibleForLoan: Boolean,
    loanIneligibilityReason: String?,
    isExporting: Boolean,
    onExportMemberStatement: (Member, Boolean) -> Unit
) {
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
                    Navy to "✅ QUALIFIED"
                } else {
                    ErrorRed to "❌ NOT QUALIFIED"
                }
                Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
            loanIneligibilityReason?.let { reason ->
                Text(reason, style = MaterialTheme.typography.labelSmall, color = ErrorRed, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun MemberPersonalInfoSection(member: Member) {
    DetailSection("Personal Information") {
        DetailRow(stringResource(R.string.ui_id_number), member.idNumber?.takeIf { it.isNotBlank() } ?: "Not captured")
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
}

@Composable
internal fun MemberBankingSection(
    member: Member,
    onSaveBankingDetails: (String, String, String) -> Unit
) {
    var isEditingBanking by remember { mutableStateOf(false) }
    var bankName by remember(member.bankName) { mutableStateOf(member.bankName ?: "") }
    var accountNo by remember(member.accountNo) { mutableStateOf(member.accountNo ?: "") }
    var branchCode by remember(member.branchCode) { mutableStateOf(member.branchCode ?: "") }

    DetailSection(stringResource(R.string.ui_banking_details)) {
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
                TextButton(onClick = { isEditingBanking = false }) { Text(stringResource(R.string.ui_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    onSaveBankingDetails(bankName, accountNo, branchCode)
                    isEditingBanking = false
                }) { Text(stringResource(R.string.ui_save)) }
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
}

@Composable
internal fun MemberBeneficiariesSection(
    beneficiaries: List<Beneficiary>,
    getDownloadParams: (String) -> Map<String, String>,
    onEditBeneficiary: (Beneficiary) -> Unit,
    onFileAction: (String, String, Map<String, String>) -> Unit
) {
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
                        Text("${b.relationship} • ${b.idNumber ?: "N/A"}", style = MaterialTheme.typography.labelSmall)

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
                        Icon(Icons.Default.Edit, stringResource(R.string.ui_edit_beneficiary), tint = Navy, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (beneficiaries.isEmpty()) {
            Text(stringResource(R.string.ui_no_beneficiaries_registered), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
internal fun MemberBurialClaimsSection(
    burialClaims: List<BeneficiaryPayoutClaim>,
    onVerifyClaim: (String, Boolean) -> Unit,
    onEscalateClaim: (String) -> Unit
) {
    if (burialClaims.isEmpty()) return

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

@Composable
internal fun MemberDocumentsSection(
    member: Member,
    documents: List<MemberDocument>,
    onVerifyDoc: (Int, Boolean) -> Unit,
    onVerifyRelDoc: (String, Boolean) -> Unit,
    downloadMemberDocument: (String, String) -> Pair<String, Map<String, String>>,
    onFileAction: (String, String, Map<String, String>) -> Unit
) {
    DetailSection("Documents") {
        var hasAnyDoc = false
        // Core KYC Documents (Indexed 1-4)
        for (i in 1..4) {
            val url = when (i) {
                1 -> member.document1Url
                2 -> member.document2Url
                3 -> member.document3Url
                else -> member.document4Url
            }
            val status = when (i) {
                1 -> member.document1Status
                2 -> member.document2Status
                3 -> member.document3Status
                else -> member.document4Status
            }
            val label = when (i) {
                1 -> "Identity Document (ID)"
                2 -> "Proof of Residence"
                3 -> "Beneficiary Form / Documents"
                else -> "Marriage Certificate"
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemberMessageSection(
    member: Member,
    messageText: String,
    onMessageChange: (String) -> Unit,
    selectedNotifChannel: NotifChannel,
    onNotifChannelChange: (NotifChannel) -> Unit,
    isSending: Boolean,
    messageSuccess: Boolean,
    onSendMessage: (String, String) -> Unit
) {
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
