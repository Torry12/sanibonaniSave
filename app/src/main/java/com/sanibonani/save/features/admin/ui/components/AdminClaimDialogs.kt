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
