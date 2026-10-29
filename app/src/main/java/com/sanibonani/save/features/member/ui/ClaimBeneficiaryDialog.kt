package com.sanibonani.save.features.member.ui


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.member.ValidateBurialClaimEligibilityUseCase
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.member.presentation.MemberBeneficiaryViewModel
import java.math.BigDecimal
import kotlinx.coroutines.launch

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
    val amountError = if (amount.toBigDecimalOrNull() == null) "Invalid amount" else if ((amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) <= java.math.BigDecimal.ZERO) "Greater than 0" else null
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
                        amount = Money(amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO),
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
                        val claim = BeneficiaryPayoutClaim(
                            beneficiaryId = beneficiary.id ?: "",
                            beneficiaryName = beneficiary.fullName,
                            claimAmount = Money(amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO),
                            causeOfDeath = cause,
                            dateOfDeath = date,
                            beneficiaryIdUrl = beneficiaryIdUrl,
                            deathCertificateUrl = deathCertUrl,
                            memberIdUrl = memberIdUrl,
                            notes = nt
                        )
                        vm.submitClaim(claim)
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
        supportingText = amountError,
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
