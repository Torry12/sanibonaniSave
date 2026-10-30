package com.sanibonani.save.features.admin.ui.components

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.*
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
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.components.AutoFitText
import com.sanibonani.save.ui.utils.*


@Composable
fun PayoutCard(
    payout: PayoutRequest,
    groupName: String,
    isProcessing: Boolean = false,
    onViewSummary: () -> Unit = {},
    onVerifyProfile: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    AutoFitText(
                        text = groupName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(stringResource(R.string.ui_payout_request), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_requested_on, payout.createdAt?.substringBefore("T") ?: "recently"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
                Spacer(Modifier.width(12.dp))
                StatusChip(status = payout.status.name)
            }

            Text(formatZAR(payout.amount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow("Bank", payout.bankName, isBold = true)
                DetailRow("Account", maskAccountNumberForDisplay(payout.accountNo), isBold = true)
                DetailRow("Branch", payout.branchCode, isBold = true)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

            // Primary Actions Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onVerifyProfile,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.SupportAgent, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_impersonate), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }

                if (payout.memberId != null) {
                    OutlinedButton(
                        onClick = onViewSummary,
                        modifier = Modifier.weight(0.8f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.PersonSearch, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_view_summary), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Decision Actions Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (payout.status == PayoutStatus.PENDING || payout.status == PayoutStatus.GROUP_APPROVED || payout.status == PayoutStatus.FAILED) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        Text(stringResource(R.string.ui_reject_1), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1.2f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            val btnText = when(payout.status) {
                                PayoutStatus.PENDING -> stringResource(R.string.ui_approve_payout)
                                PayoutStatus.GROUP_APPROVED -> stringResource(R.string.ui_process_payout)
                                PayoutStatus.FAILED -> stringResource(R.string.ui_reprocess_payout)
                                else -> stringResource(R.string.ui_process_payout)
                            }
                            Text(btnText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                        }
                    }
                } else if (payout.status == PayoutStatus.PROCESSING) {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.ui_mark_as_completed), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun BurialClaimCard(
    claim: BeneficiaryPayoutClaim,
    groupName: String,
    isProcessing: Boolean = false,
    onViewSummary: () -> Unit = {},
    onVerifyProfile: () -> Unit = {},
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onPay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AutoFitText(
                        text = claim.beneficiaryName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(stringResource(R.string.ui_group_label, groupName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_submitted_on, claim.createdAt?.substringBefore("T") ?: "recently"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }

                StatusChip(status = claim.status.name)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow("Claim Amount", formatZAR(claim.claimAmount), isBold = true)
                DetailRow("Cause", claim.causeOfDeath ?: "Not specified")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

            // Primary Actions Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onViewSummary,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.PersonSearch, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_view_summary), style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onVerifyProfile,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.SupportAgent, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.6.dp))
                    Text(stringResource(R.string.ui_impersonate), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Decision Actions Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (claim.status == BeneficiaryClaimStatus.SUBMITTED || claim.status == BeneficiaryClaimStatus.UNDER_REVIEW || claim.status == BeneficiaryClaimStatus.ESCALATED) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        Text(stringResource(R.string.ui_reject_1), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.ui_approve_claim), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                } else if (claim.status == BeneficiaryClaimStatus.APPROVED) {
                    Button(
                        onClick = onPay,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.ui_mark_as_paid), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
