package com.sanibonani.save.ui.screens.admin.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailDialog(
    member: Member,
    group: Group?,
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
    onBroadcast: () -> Unit,
    isSending: Boolean,
    messageSuccess: Boolean,
    isSendingWhatsAppTest: Boolean,
    whatsAppTestResult: String?,
    onEditBeneficiary: (Beneficiary) -> Unit,
    vm: AdminViewModel? = null,
    isSupportMode: Boolean = false,
    onEnterPortal: (() -> Unit)? = null,
    isExporting: Boolean = false,
    burialClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    onVerifyClaim: (String, Boolean) -> Unit = { _, _ -> },
    onEscalateClaim: (String) -> Unit = {},
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
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
                            val profileUrl = member.profilePhotoUrl
                            val profileRequest = remember(profileUrl) {
                                if (profileUrl.isNullOrBlank()) null
                                else ImageRequest.Builder(context)
                                    .data(profileUrl)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(true)
                                    .build()
                            }
                            val personPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Person)

                            AsyncImage(
                                model = profileRequest,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Forest.copy(alpha = 0.2f), CircleShape),
                                contentScale = ContentScale.Crop,
                                placeholder = personPainter,
                                error = personPainter,
                                fallback = personPainter
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(member.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                    },
                    actions = {
                        if (isSupportMode && onEnterPortal != null) {
                            TextButton(onClick = onEnterPortal) {
                                Icon(Icons.AutoMirrored.Filled.Login, null, tint = Forest)
                                Spacer(Modifier.width(4.dp))
                                Text("Enter Portal", color = Forest, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status Overview
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Financial Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { vm?.exportMemberStatement(member, pdf = false) },
                                        modifier = Modifier.size(24.dp),
                                        enabled = !isExporting
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.List, "Export CSV", tint = Forest)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { vm?.exportMemberStatement(member, pdf = true) },
                                        modifier = Modifier.size(24.dp),
                                        enabled = !isExporting
                                    ) {
                                        if (isExporting) {
                                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                        } else {
                                            Icon(Icons.Default.PictureAsPdf, "Export PDF", tint = Forest)
                                        }
                                    }
                                }
                            }
                            DetailRow("Status", member.status.displayName)
                            DetailRow("Total Paid", formatZAR(member.totalPaid ?: 0.0))
                            calculation?.let {
                                DetailRow("Shortfall", formatZAR(it.shortfall))
                                DetailRow("Overpayment", formatZAR(it.overpayment))
                                DetailRow("Next Due", it.nextDueDate)
                                if (it.isOverdue) {
                                    DetailRow("Total Due Now", formatZAR(it.totalDueNow))
                                }
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Forest.copy(0.1f))
                            
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Loan Eligibility", style = MaterialTheme.typography.bodySmall, color = MidGray)
                                val (color, text) = if (isEligibleForLoan) {
                                    Forest to "✅ QUALIFIED"
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

                    // Beneficiaries
                    DetailSection("Beneficiaries (${beneficiaries.size})") {
                        beneficiaries.forEach { b ->
                            Surface(
                                onClick = { onEditBeneficiary(b) },
                                shape = RoundedCornerShape(8.dp),
                                color = Forest.copy(0.05f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(b.fullName, fontWeight = FontWeight.Bold)
                                        Text("${b.relationship} • ${b.idNumber ?: "N/A"}", style = MaterialTheme.typography.labelSmall)
                                        
                                        if (b.documentUrl != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                                Icon(Icons.Default.CheckCircle, null, tint = Forest, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("ID Document Uploaded", style = MaterialTheme.typography.labelSmall, color = Forest)
                                            }
                                        }
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (b.documentUrl != null) {
                                            IconButton(onClick = {
                                                val url = b.documentUrl ?: return@IconButton
                                                val headers = vm?.getDownloadParams(url) ?: emptyMap()
                                                val ext = url.substringAfterLast(".", "pdf").substringBefore("?")
                                                val fileName = "Beneficiary_ID_${b.fullName.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                                                onFileAction(url, fileName, headers)
                                            }) {
                                                Icon(Icons.Default.PictureAsPdf, "Download ID", tint = Forest)
                                            }
                                        }
                                        Icon(Icons.Default.Edit, null, tint = Forest, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (beneficiaries.isEmpty()) {
                            Text("No beneficiaries registered.", style = MaterialTheme.typography.bodySmall, color = MidGray)
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
                        // Legacy Documents (Indexed 1-5)
                        for (i in 1..5) {
                            val url = when(i) {
                                1 -> member.document1Url; 2 -> member.document2Url; 3 -> member.document3Url; 4 -> member.document4Url; 5 -> member.document5Url; else -> null
                            }
                            val status = when(i) {
                                1 -> member.document1Status; 2 -> member.document2Status; 3 -> member.document3Status; 4 -> member.document4Status; 5 -> member.document5Status; else -> DocumentStatus.PENDING
                            }
                            val label = when(i) {
                                1 -> "Identity Document (ID)"
                                2 -> "Proof of Residence"
                                3 -> "Beneficiary Form / Documents"
                                4 -> "Marriage Certificate"
                                5 -> "Group Constitution (Signed)"
                                else -> ""
                            }

                            if (url != null) {
                                hasAnyDoc = true
                                DocumentAdminCard(
                                    label = label,
                                    url = url,
                                    status = status,
                                    onVerify = { approve -> onVerifyDoc(i, approve) },
                                    onDownload = { dUrl, dLabel -> 
                                        val (dlUrl, headers) = vm?.downloadMemberDocument(dUrl, dLabel) ?: Pair(dUrl, emptyMap())
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
                                        val (dlUrl, headers) = vm?.downloadMemberDocument(dUrl, dLabel) ?: Pair(dUrl, emptyMap())
                                        val extension = dlUrl.substringAfterLast(".", "pdf")
                                        val fileName = "${dLabel.replace(" ", "_")}_${System.currentTimeMillis()}.$extension"
                                        onFileAction(dlUrl, fileName, headers)
                                    }
                                )
                            }
                        }

                        if (!hasAnyDoc) {
                            Text("No documents uploaded yet.", style = MaterialTheme.typography.bodySmall, color = MidGray)
                        }
                    }

                    // Direct Message
                    DetailSection("Send Direct Message") {
                        SanibonaniTextField(
                            value = messageText,
                            onValueChange = onMessageChange,
                            label = "Message text...",
                            modifier = Modifier.height(100.dp)
                        )
                        if (messageSuccess) {
                            Text("Message sent!", color = Forest, style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { 
                                member.id?.let { id ->
                                    onSendMessage(messageText, id)
                                }
                            },
                            enabled = messageText.isNotBlank() && !isSending,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Forest)
                        ) {
                            if (isSending) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            else Text("Send WhatsApp & Push")
                        }

                        if (BuildConfig.DEBUG) {
                            OutlinedButton(
                                onClick = { vm?.sendWhatsAppTestToSelectedMember() },
                                enabled = !isSendingWhatsAppTest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isSendingWhatsAppTest) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Debug: Send WhatsApp Test")
                                }
                            }

                            whatsAppTestResult?.let { result ->
                                val resultType = if (result.contains("success", ignoreCase = true)) InfoType.SUCCESS else InfoType.INFO
                                InfoBox(result, resultType)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClaimAdminCard(
    claim: BeneficiaryPayoutClaim,
    onVerify: (Boolean) -> Unit,
    onEscalate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Forest.copy(0.05f)),
        border = BorderStroke(1.dp, Forest.copy(0.1f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(claim.beneficiaryName, fontWeight = FontWeight.Bold)
                    // Note: actual relationship isn't in claim model but beneficiary name is
                    Text("Burial Payout Claim", style = MaterialTheme.typography.labelSmall, color = Forest)
                }
                Surface(
                    color = when(claim.status) {
                        BeneficiaryClaimStatus.SUBMITTED -> MidGray
                        BeneficiaryClaimStatus.UNDER_REVIEW -> InfoBlue
                        BeneficiaryClaimStatus.ESCALATED -> ForestMid
                        BeneficiaryClaimStatus.APPROVED -> Forest
                        BeneficiaryClaimStatus.PAID -> Forest
                        BeneficiaryClaimStatus.REJECTED -> ErrorRed
                        else -> MidGray
                    }.copy(0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        claim.status.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = when(claim.status) {
                            BeneficiaryClaimStatus.SUBMITTED -> MidGray
                            BeneficiaryClaimStatus.UNDER_REVIEW -> InfoBlue
                            BeneficiaryClaimStatus.ESCALATED -> ForestMid
                            BeneficiaryClaimStatus.APPROVED -> Forest
                            BeneficiaryClaimStatus.PAID -> Forest
                            BeneficiaryClaimStatus.REJECTED -> ErrorRed
                            else -> MidGray
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            DetailRow("Cause of Death", claim.causeOfDeath)
            DetailRow("Date of Death", claim.dateOfDeath)
            DetailRow("Claim Amount", formatZAR(claim.claimAmount))
            
            HorizontalDivider(color = Forest.copy(0.1f))
            Text("Banking Details", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            DetailRow("Bank", claim.bankName)
            DetailRow("Account", claim.accountNo)
            DetailRow("Account Holder", claim.accountHolder)

            if (claim.status == BeneficiaryClaimStatus.SUBMITTED) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onVerify(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        border = BorderStroke(1.dp, ErrorRed)
                    ) {
                        Text("Reject", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { onVerify(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)
                    ) {
                        Text("Verify Details", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (claim.status == BeneficiaryClaimStatus.UNDER_REVIEW) {
                Button(
                    onClick = onEscalate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest)
                ) {
                    Text("Escalate to Platform", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun DocumentRow(label: String, status: DocumentStatus, onVerify: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(status.name, style = MaterialTheme.typography.labelSmall, color = when(status) {
                DocumentStatus.VERIFIED -> Forest
                DocumentStatus.REJECTED -> ErrorRed
                else -> WarningYellow
            })
        }
        
        if (status == DocumentStatus.PENDING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { onVerify(false) }) { Icon(Icons.Default.Close, null, tint = ErrorRed) }
                IconButton(onClick = { onVerify(true) }) { Icon(Icons.Default.Check, null, tint = Forest) }
            }
        }
    }
}
