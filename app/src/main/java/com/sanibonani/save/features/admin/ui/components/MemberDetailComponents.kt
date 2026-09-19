package com.sanibonani.save.features.admin.ui.components

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.DetailRow
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.ui.theme.*

@Composable
fun DocumentAdminCard(
    label: String,
    url: String?,
    status: DocumentStatus,
    onVerify: (Boolean) -> Unit,
    onDownload: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LightGray.copy(0.5f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(status.name, style = MaterialTheme.typography.labelSmall, color = when(status) {
                    DocumentStatus.VERIFIED -> Navy
                    DocumentStatus.REJECTED -> ErrorRed
                    else -> WarningYellow
                })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (url != null) {
                    IconButton(onClick = { onDownload(url, label) }) {
                        Icon(Icons.Default.Download, "Download", tint = Navy)
                    }
                }

                if (status == DocumentStatus.PENDING) {
                    IconButton(onClick = { onVerify(false) }) { Icon(Icons.Default.Close, null, tint = ErrorRed) }
                    IconButton(onClick = { onVerify(true) }) { Icon(Icons.Default.Check, null, tint = Navy) }
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
        colors = CardDefaults.cardColors(containerColor = Navy.copy(0.05f)),
        border = BorderStroke(1.dp, Navy.copy(0.1f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(claim.beneficiaryName, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_burial_payout_claim), style = MaterialTheme.typography.labelSmall, color = Navy)
                }
                Surface(
                    color = when(claim.status) {
                        BeneficiaryClaimStatus.SUBMITTED -> Color.Black.copy(alpha = 0.6f)
                        BeneficiaryClaimStatus.UNDER_REVIEW -> InfoBlue
                        BeneficiaryClaimStatus.ESCALATED -> NavyMid
                        BeneficiaryClaimStatus.APPROVED -> Navy
                        BeneficiaryClaimStatus.PAID -> Navy
                        BeneficiaryClaimStatus.REJECTED -> ErrorRed
                        else -> Color.Black.copy(alpha = 0.6f)
                    }.copy(0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        claim.status.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = when(claim.status) {
                            BeneficiaryClaimStatus.SUBMITTED -> Color.Black.copy(alpha = 0.6f)
                            BeneficiaryClaimStatus.UNDER_REVIEW -> InfoBlue
                            BeneficiaryClaimStatus.ESCALATED -> NavyMid
                            BeneficiaryClaimStatus.APPROVED -> Navy
                            BeneficiaryClaimStatus.PAID -> Navy
                            BeneficiaryClaimStatus.REJECTED -> ErrorRed
                            else -> Color.Black.copy(alpha = 0.6f)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            DetailRow("Cause of Death", claim.causeOfDeath)
            DetailRow("Date of Death", claim.dateOfDeath)
            DetailRow("Claim Amount", formatZAR(claim.claimAmount))

            HorizontalDivider(color = Navy.copy(0.1f))
            Text(stringResource(R.string.ui_banking_details), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                        Text(stringResource(R.string.ui_reject_1), style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { onVerify(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)
                    ) {
                        Text(stringResource(R.string.ui_verify_details), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (claim.status == BeneficiaryClaimStatus.UNDER_REVIEW) {
                Button(
                    onClick = onEscalate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) {
                    Text(stringResource(R.string.ui_escalate_to_platform), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
