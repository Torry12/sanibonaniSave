package com.sanibonani.save.features.member.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.disbursement.ValidateBurialClaimEligibilityUseCase
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.viewmodel.disbursement.MemberBeneficiaryViewModel
import kotlinx.coroutines.launch

@Composable
fun LoanItem(loan: Loan, onDownloadContract: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${formatZAR(loan.amount)} Loan", fontWeight = FontWeight.Bold)
                    Text("Requested on ${loan.createdAt?.substringBefore("T") ?: "N/A"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!loan.contractUrl.isNullOrBlank() && (onDownloadContract != null)) {
                        IconButton(onClick = onDownloadContract) {
                            Icon(Icons.Default.Description, "Download Agreement", tint = Navy)
                        }
                    }
                    StatusChip(status = loan.status.toMemberStatus(), customLabel = loan.status.displayName)
                }
            }
            Spacer(Modifier.height(12.dp))
            DetailRow("Total Repayable", formatZAR(loan.totalToRepay))
            DetailRow("Remaining", formatZAR(loan.balanceRemaining))
            if (loan.status == LoanStatus.ACTIVE || loan.status == LoanStatus.PARTIALLY_PAID) {
                Spacer(Modifier.height(12.dp))
                val progress = if (loan.totalToRepay.isPositive) (loan.totalRepaid / loan.totalToRepay).toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.onSurface,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                Text("${(progress * 100).toInt()}% Repaid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

fun LoanStatus.toMemberStatus(): MemberStatus = when(this) {
    LoanStatus.ACTIVE, LoanStatus.COMPLETED, LoanStatus.PARTIALLY_PAID, LoanStatus.APPROVED -> MemberStatus.ACTIVE
    LoanStatus.PENDING -> MemberStatus.PENDING_PAYMENT
    LoanStatus.REJECTED, LoanStatus.OVERDUE, LoanStatus.CANCELLED, LoanStatus.DEFAULTED -> MemberStatus.SUSPENDED
}

fun ContributionStatus.toMemberStatus(): MemberStatus = when(this) {
    ContributionStatus.PAID, ContributionStatus.PARTIAL -> MemberStatus.ACTIVE
    ContributionStatus.DUE -> MemberStatus.PENDING_PAYMENT
    ContributionStatus.OVERDUE, ContributionStatus.MISSED -> MemberStatus.SUSPENDED
}

@Composable
fun NotificationItem(n: AppNotification) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(Navy.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp), tint = Navy) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(n.message, style = MaterialTheme.typography.bodyMedium); Text(n.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
        }
    }
}

@Composable
fun MessageItem(m: AppNotification) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(0.05f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(m.message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(m.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun ContributionItem(c: Contribution, onClick: (Contribution) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable { onClick(c) },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Navy.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Text(if (c.type == "joining_fee") "🎟️" else "💰") }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    text = if (c.type == "joining_fee") "Joining Fee" else "Monthly Contribution",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(c.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatZAR(c.amount), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                StatusChip(status = c.status.toMemberStatus(), customLabel = c.status.name)
            }
        }
    }
}

@Composable
fun BeneficiaryItem(
    beneficiary: Beneficiary,
    isBurial: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpload: (ByteArray, String) -> Unit,
    onClaim: () -> Unit,
    onDownload: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            val bytes = context.contentResolver.openInputStream(selectedUri)?.use { s -> s.readBytes() }
            if (bytes != null) {
                onUpload(bytes, getFileNameFromUri(context.contentResolver, selectedUri))
            }
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Navy.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Navy.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = Navy, modifier = Modifier.size(24.dp))
                }

                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = beneficiary.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isBurial) {
                            Spacer(Modifier.width(8.dp))
                            StatusBadge(
                                text = if (beneficiary.isOver65) "Over 65" else "Standard",
                                color = if (beneficiary.isOver65) WarningAmber else SuccessGreen
                            )
                        }
                    }
                    Text(
                        text = "${beneficiary.relationship} • ${beneficiary.idNumber ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, tint = Navy, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                }
            }

            if (isBurial) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Navy.copy(alpha = 0.05f))
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val hasDoc = !beneficiary.documentUrl.isNullOrBlank()
                    OutlinedButton(
                        onClick = {
                            if (hasDoc && onDownload != null) {
                                onDownload(beneficiary.documentUrl!!, beneficiary.fullName)
                            } else {
                                launcher.launch("image/*")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (hasDoc) SuccessGreen else Navy
                        ),
                        border = BorderStroke(1.dp, (if (hasDoc) SuccessGreen else Navy).copy(alpha = 0.3f))
                    ) {
                        Icon(
                            imageVector = if (hasDoc) Icons.Default.CheckCircle else Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (hasDoc) "Identity Verified" else "Upload ID",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Button(
                        onClick = onClaim,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Icon(Icons.Default.MedicalServices, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Initiate Claim", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            color = color
        )
    }
}

@Composable
fun LoanRequestDialog(group: Group?, onDismiss: () -> Unit, onConfirm: (Double, Int, String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var m by remember { mutableStateOf("3") }
    var p by remember { mutableStateOf("") }

    val amountError = remember(a) {
        if (a.isEmpty()) null
        else if (a.toDoubleOrNull() == null) "Invalid amount"
        else if ((a.toDoubleOrNull() ?: 0.0) <= 0.0) "Must be greater than 0"
        else null
    }

    val monthsError = remember(m) {
        if (m.isEmpty()) null
        else if (m.toIntOrNull() == null) "Invalid number"
        else if ((m.toIntOrNull() ?: 0) < 1) "Minimum 1 month"
        else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_request_loan), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = a,
                    onValueChange = { a = it },
                    label = "Amount (R) *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountError != null,
                    errorMessage = amountError
                )
                SanibonaniTextField(
                    value = m,
                    onValueChange = { m = it },
                    label = "Months to Repay *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = monthsError != null,
                    errorMessage = monthsError
                )
                SanibonaniTextField(
                    value = p,
                    onValueChange = { p = it },
                    label = "Purpose of Loan",
                    placeholder = "e.g. Medical emergency"
                )
                InfoBox("Interest Rate: ${group?.loanInterestRate ?: 0}%", InfoType.INFO)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(a.toDoubleOrNull() ?: 0.0, m.toIntOrNull() ?: 3, p) },
                enabled = a.isNotBlank() && m.isNotBlank() && amountError == null && monthsError == null
            ) { Text(stringResource(R.string.ui_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        }
    )
}

@Composable
fun ClaimBeneficiaryDialog(
    beneficiary: Beneficiary,
    vm: MemberBeneficiaryViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var cause by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("5000.0") }
    var nt by remember { mutableStateOf("") }

    // Uploaded URLs
    var beneficiaryIdUrl by remember { mutableStateOf<String?>(null) }
    var deathCertUrl by remember { mutableStateOf<String?>(null) }
    var memberIdUrl by remember { mutableStateOf<String?>(null) }

    // Upload status
    var isUploadingBeneficiaryId by remember { mutableStateOf(false) }
    var isUploadingDeathCert by remember { mutableStateOf(false) }
    var isUploadingMemberId by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    val eligibility = remember(cause, date) {
        if (cause.isNotBlank() && date.isNotBlank()) {
            vm.checkEligibility(cause, date)
        } else null
    }

    val isEligible = eligibility is ValidateBurialClaimEligibilityUseCase.EligibilityResult.Eligible
    val amountError = if (amount.toDoubleOrNull() == null) "Invalid amount" else if ((amount.toDoubleOrNull() ?: 0.0) <= 0.0) "Greater than 0" else null
    val canGoToDocs = isEligible && amountError == null
    val canSubmit = beneficiaryIdUrl != null && deathCertUrl != null && memberIdUrl != null && !state.isSubmittingClaim

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.ui_submit_payout_claim), fontWeight = FontWeight.Bold)
                Text("Step $step of 3", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (step) {
                    1 -> ClaimEligibilityStep(
                        beneficiaryName = beneficiary.fullName,
                        cause = cause,
                        onCauseChange = { cause = it },
                        date = date,
                        onDateChange = { date = it },
                        amount = amount,
                        onAmountChange = { amount = it },
                        amountError = amountError,
                        eligibility = eligibility
                    )
                    2 -> ClaimDocumentUploadStep(
                        beneficiaryIdUrl = beneficiaryIdUrl,
                        isUploadingBeneficiaryId = isUploadingBeneficiaryId,
                        onBeneficiaryIdSelect = { uri ->
                            scope.launch {
                                isUploadingBeneficiaryId = true
                                context.contentResolver.openInputStream(uri)?.use { s ->
                                    vm.uploadClaimDocument(getFileNameFromUri(context.contentResolver, uri), s.readBytes()).onSuccess { url -> beneficiaryIdUrl = url }
                                }
                                isUploadingBeneficiaryId = false
                            }
                        },
                        deathCertUrl = deathCertUrl,
                        isUploadingDeathCert = isUploadingDeathCert,
                        onDeathCertSelect = { uri ->
                            scope.launch {
                                isUploadingDeathCert = true
                                context.contentResolver.openInputStream(uri)?.use { s ->
                                    vm.uploadClaimDocument(getFileNameFromUri(context.contentResolver, uri), s.readBytes()).onSuccess { url -> deathCertUrl = url }
                                }
                                isUploadingDeathCert = false
                            }
                        },
                        memberIdUrl = memberIdUrl,
                        isUploadingMemberId = isUploadingMemberId,
                        onMemberIdSelect = { uri ->
                            scope.launch {
                                isUploadingMemberId = true
                                context.contentResolver.openInputStream(uri)?.use { s ->
                                    vm.uploadClaimDocument(getFileNameFromUri(context.contentResolver, uri), s.readBytes()).onSuccess { url -> memberIdUrl = url }
                                }
                                isUploadingMemberId = false
                            }
                        }
                    )
                    3 -> ClaimReviewStep(
                        beneficiaryName = beneficiary.fullName,
                        cause = cause,
                        date = date,
                        amount = Money(amount.toDoubleOrNull() ?: 0.0),
                        notes = nt,
                        onNotesChange = { nt = it }
                    )
                }
            }
        },
        confirmButton = {
            if (step < 3) {
                Button(
                    onClick = { step++ },
                    enabled = if (step == 1) canGoToDocs else (beneficiaryIdUrl != null && deathCertUrl != null && memberIdUrl != null)
                ) { Text("Next") }
            } else {
                Button(
                    onClick = {
                        vm.submitClaim(
                            beneficiary = beneficiary,
                            amount = Money(amount.toDoubleOrNull() ?: 0.0),
                            cause = cause,
                            date = date,
                            bid = beneficiaryIdUrl!!,
                            dc = deathCertUrl!!,
                            mid = memberIdUrl!!
                        )
                        onDismiss()
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    if (state.isSubmittingClaim) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    else Text("Submit Claim")
                }
            }
        },
        dismissButton = {
            if (step > 1) {
                TextButton(onClick = { step-- }) { Text("Back") }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
            }
        }
    )
}

@Composable
private fun ClaimEligibilityStep(
    beneficiaryName: String,
    cause: String,
    onCauseChange: (String) -> Unit,
    date: String,
    onDateChange: (String) -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    amountError: String?,
    eligibility: ValidateBurialClaimEligibilityUseCase.EligibilityResult?
) {
    Text("Beneficiary: $beneficiaryName", fontWeight = FontWeight.Bold)
    SanibonaniTextField(cause, onCauseChange, "Cause of Death (e.g. Natural, Accidental)")
    Spacer(Modifier.height(4.dp))
    SanibonaniTextField(date, onDateChange, "Date of Death (YYYY-MM-DD)", placeholder = "2024-01-01")

    if (eligibility != null) {
        InfoBox(
            message = when (eligibility) {
                is ValidateBurialClaimEligibilityUseCase.EligibilityResult.Eligible -> "Member is eligible for this claim."
                is ValidateBurialClaimEligibilityUseCase.EligibilityResult.Ineligible -> eligibility.reason
            },
            type = if (eligibility is ValidateBurialClaimEligibilityUseCase.EligibilityResult.Eligible) InfoType.SUCCESS else InfoType.ERROR
        )
    }

    SanibonaniTextField(
        value = amount,
        onValueChange = onAmountChange,
        label = "Claim Amount (R)",
        isError = amountError != null,
        errorMessage = amountError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun ClaimDocumentUploadStep(
    beneficiaryIdUrl: String?,
    isUploadingBeneficiaryId: Boolean,
    onBeneficiaryIdSelect: (android.net.Uri) -> Unit,
    deathCertUrl: String?,
    isUploadingDeathCert: Boolean,
    onDeathCertSelect: (android.net.Uri) -> Unit,
    memberIdUrl: String?,
    isUploadingMemberId: Boolean,
    onMemberIdSelect: (android.net.Uri) -> Unit
) {
    val beneficiaryIdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onBeneficiaryIdSelect) }
    val deathCertLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onDeathCertSelect) }
    val memberIdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onMemberIdSelect) }

    Text("Upload Supporting Documents", fontWeight = FontWeight.Bold)
    Text("All documents are mandatory for processing.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

    DocumentUploadRow("Beneficiary ID Card", beneficiaryIdUrl != null, isUploadingBeneficiaryId) { beneficiaryIdLauncher.launch("*/*") }
    DocumentUploadRow("Death Certificate", deathCertUrl != null, isUploadingDeathCert) { deathCertLauncher.launch("*/*") }
    DocumentUploadRow("Member ID Card", memberIdUrl != null, isUploadingMemberId) { memberIdLauncher.launch("*/*") }
}

@Composable
private fun ClaimReviewStep(
    beneficiaryName: String,
    cause: String,
    date: String,
    amount: Money,
    notes: String,
    onNotesChange: (String) -> Unit
) {
    Text("Final Review", fontWeight = FontWeight.Bold)
    ReviewRow("Beneficiary", beneficiaryName)
    ReviewRow("Cause", cause)
    ReviewRow("Date", date)
    ReviewRow("Amount", formatZAR(amount))
    SanibonaniTextField(notes, onNotesChange, "Additional Notes (Optional)")
}

@Composable
private fun DocumentUploadRow(label: String, isUploaded: Boolean, isUploading: Boolean, onUpload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (isUploaded) Text("Uploaded ✓", style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
        }
        if (isUploading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onUpload) {
                Icon(
                    if (isUploaded) Icons.Default.CheckCircle else Icons.Default.FileUpload,
                    null,
                    tint = if (isUploaded) SuccessGreen else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ReviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LoanSummaryCard(loans: List<Loan>) {
    val activeLoans = loans.filter { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID || it.status == LoanStatus.OVERDUE }
    if (activeLoans.isEmpty()) return

    val totalRemaining = activeLoans.fold(Money.ZERO) { acc, l -> acc + l.balanceRemaining }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CreditCard, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Running Loans", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("${activeLoans.size} Active Loan${if (activeLoans.size > 1) "s" else ""}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                Text(formatZAR(totalRemaining), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun PayoutSummaryItem(claim: BeneficiaryPayoutClaim? = null, payout: PayoutRequest? = null) {
    val name = claim?.beneficiaryName ?: payout?.bankName ?: "Pending Payout"
    val amount = claim?.claimAmount ?: payout?.amount ?: Money.ZERO
    val status = claim?.status?.displayName ?: payout?.status?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Pending"

    val isBurial = claim != null

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (isBurial) ErrorRed.copy(0.1f) else SuccessGreen.copy(0.1f),
            shape = CircleShape,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (isBurial) "🕊️" else "💰", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, style = MaterialTheme.typography.labelSmall, color = if (status.contains("Approved", true) || status.contains("Paid", true)) SuccessGreen else WarningAmber)
        }
        Text(formatZAR(amount), fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun BankingDetailsDialog(
    initialBank: String = "",
    initialAccount: String = "",
    initialBranch: String = "",
    onDismiss: () -> Unit,
    onConfirm: (bank: String, account: String, branch: String) -> Unit
) {
    var bank by remember { mutableStateOf(initialBank) }
    var account by remember { mutableStateOf(initialAccount) }
    var branch by remember { mutableStateOf(initialBranch) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Banking Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter the account where you want to receive payouts.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                BankingDetailsFields(
                    bankName = bank,
                    accountNo = account,
                    branchCode = branch,
                    onBankNameChange = { bank = it },
                    onAccountNoChange = { account = it },
                    onBranchCodeChange = { branch = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(bank, account, branch) },
                enabled = bank.isNotBlank() && account.isNotBlank() && branch.isNotBlank()
            ) {
                Text("Save Details")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        }
    )
}

@Composable
fun WithdrawalRequestDialog(
    group: Group?,
    type: PayoutType,
    member: Member? = null,
    onDismiss: () -> Unit,
    onConfirm: (Money) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val title = if (type == PayoutType.EMERGENCY) "Request Emergency Funds" else "Request Disinvestment"
    val description = if (type == PayoutType.EMERGENCY)
        "Emergency withdrawals are subject to a 50% liquidity cap and admin approval."
    else
        "Disinvesting your equity may take up to 7 days to process based on group rules."

    val hasBanking = member?.accountNo?.isNotBlank() == true && member.bankName?.isNotBlank() == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!hasBanking) {
                    InfoBox("You must configure your banking details in the Profile tab before requesting a withdrawal.", InfoType.ERROR)
                }

                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                SanibonaniTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) amount = it },
                    label = "Amount (R)",
                    enabled = hasBanking,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (type == PayoutType.EMERGENCY && group != null) {
                    InfoBox("Current Group Liquidity: ${formatZAR(group.balance)}", InfoType.INFO)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = amount.toDoubleOrNull() ?: 0.0
                    if (a > 0) onConfirm(Money(a))
                },
                enabled = hasBanking && amount.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Submit Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        }
    )
}

@Composable
fun AddBeneficiaryDialog(beneficiary: Beneficiary?, onDismiss: () -> Unit, onConfirm: (String, String, String, String, Boolean) -> Unit) {
    var n by remember { mutableStateOf(beneficiary?.fullName ?: "") }
    var id by remember { mutableStateOf(beneficiary?.idNumber ?: "") }
    var r by remember { mutableStateOf(beneficiary?.relationship ?: "") }
    var d by remember { mutableStateOf(beneficiary?.dateOfBirth ?: "") }
    var i by remember { mutableStateOf(beneficiary?.isOver65 ?: false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (beneficiary == null) "Add Beneficiary" else "Update Beneficiary") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SanibonaniTextField(n, { n = it }, "Full Name")
            SanibonaniTextField(id, { id = it }, "ID Number", visualTransformation = com.sanibonani.save.ui.utils.IDNumberTransformation())
            SanibonaniTextField(r, { r = it }, "Relationship")
            SanibonaniDatePickerField(
                label = stringResource(R.string.ui_date_of_birth),
                selectedDate = d,
                onDateSelected = { d = it }
            )
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(i, { i = it }); Text(stringResource(R.string.ui_over_65_years_old)) }
        }
    }, confirmButton = { SanibonaniButton(text = if (beneficiary == null) "Add" else "Update", onClick = { if (n.isNotBlank()) onConfirm(n, id, r, d, i) }, modifier = Modifier.fillMaxWidth()) }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } })
}

@Composable
fun LedgerItem(entry: LedgerEntry, onClick: () -> Unit) {
    val isInflow = entry.amount.isPositive
    val color = if (isInflow) SuccessGreen else ErrorRed

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LightGray.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(4.dp, 32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.description,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.createdAt?.take(16)?.replace("T", " ")} • ${entry.category.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isInflow) "+" else "") + formatZAR(entry.amount),
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    text = "Bal: " + formatZAR(entry.balanceAfter ?: Money.ZERO),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun LedgerDetailDialog(
    entry: LedgerEntry,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaction Verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoBox(
                    message = "This transaction is immutable and recorded on the permanent group ledger.",
                    type = InfoType.INFO
                )

                DetailRow("Description", entry.description)
                DetailRow("Category", entry.category.uppercase())
                DetailRow("Amount", formatZAR(entry.amount))
                DetailRow("Running Balance", formatZAR(entry.balanceAfter ?: Money.ZERO))
                DetailRow("Date", entry.createdAt?.replace("T", " ") ?: "N/A")

                HorizontalDivider(color = LightGray.copy(alpha = 0.3f))

                Column {
                    Text("Digital Audit Reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(
                        entry.transactionId ?: entry.id ?: "SYSTEM_VERIFIED",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close)) }
        }
    )
}
