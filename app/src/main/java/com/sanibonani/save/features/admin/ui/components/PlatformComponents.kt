package com.sanibonani.save.features.admin.ui.components

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.components.AutoFitText
import com.sanibonani.save.ui.utils.*

/**
 * Masks account number for security display in payout cards.
 * Shows only last 4 digits.
 * Example: "1234567890" → "****7890"
 */
fun maskAccountNumberForDisplay(accountNumber: String): String = when {
    accountNumber.length <= 4 -> accountNumber
    else -> "*".repeat(accountNumber.length - 4) + accountNumber.takeLast(4)
}

@Composable
fun AccordionHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    label: String = "",
    icon: ImageVector? = null,
    isSubHeader: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isSubHeader) 8.dp else 12.dp,
                vertical = if (isSubHeader) 8.dp else 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isSubHeader) 20.dp else 24.dp)
                )
                Spacer(Modifier.width(12.dp))
            }

            Text(
                text = if (isSubHeader) title.uppercase() else title,
                style = if (isSubHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black,
                color = if (isSubHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                letterSpacing = if (isSubHeader) 1.sp else 0.sp
            )

            if (count > 0) {
                Text(
                    text = if (label.isEmpty()) count.toString() else "$count $label",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (isSubHeader) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (isSubHeader) 20.dp else 24.dp)
            )
        }
    }
}

@Composable
fun ProvinceHeaderCard(
    province: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    label: String = "groups"
) {
    AccordionHeader(
        title = province,
        count = count,
        isExpanded = isExpanded,
        onClick = onClick,
        label = label,
        isSubHeader = true
    )
}

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
fun LoanRequestCard(
    loan: Loan,
    memberName: String,
    groupName: String,
    isProcessing: Boolean,
    onViewSummary: () -> Unit,
    onVerifyProfile: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDisburse: () -> Unit
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
                        text = memberName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(stringResource(R.string.ui_group_label, groupName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_requested_on, loan.createdAt?.substringBefore("T") ?: "recently"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }

                StatusChip(status = loan.status.name)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow("Requested Amount", formatZAR(loan.amount), isBold = true)
                DetailRow("Interest Rate", "${loan.interestRate}%", isBold = true)
                DetailRow("Purpose", loan.purpose ?: "Not specified")
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
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isProcessing && (loan.status == LoanStatus.PENDING || loan.status == LoanStatus.APPROVED)
                ) {
                    Text(stringResource(R.string.ui_reject_1), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                if (loan.status == LoanStatus.PENDING) {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.ui_approve_1), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                } else if (loan.status == LoanStatus.APPROVED) {
                    Button(
                        onClick = onDisburse,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = InfoBlue),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.ui_disburse_funds), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
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

@Composable
private fun StatusChip(status: String) {
    val upperStatus = status.uppercase()
    val color = when {
        upperStatus == "PAID" || upperStatus == "COMPLETED" || upperStatus == "APPROVED" || upperStatus == "ACTIVE" -> SuccessGreen
        upperStatus == "PENDING" || upperStatus.contains("REVIEW") || upperStatus == "ESCALATED" || upperStatus == "GROUP_APPROVED" || upperStatus == "PROBATION" -> WarningAmber
        upperStatus == "FAILED" || upperStatus == "REJECTED" || upperStatus == "CANCELLED" || upperStatus == "OVERDUE" || upperStatus == "SUSPENDED" -> ErrorRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = status.replace("_", " "),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BehaviorInsightCard(insight: MemberBehaviorInsight, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AutoFitText(
                    text = insight.memberName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }

            DetailRow("Risk Band", insight.riskBand, isBold = true)
            DetailRow("Overdue Loans", insight.overdueLoans.toString(), isBold = true, color = if (insight.overdueLoans > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            DetailRow("Behavior Score", "${insight.behaviorScore.toInt()}/100", isBold = true, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun EmptyStateSmall(message: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface, // Use full onSurface for visibility
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DistributionRow(label: String, count: Int, pct: Float, color: Color) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AutoFitText(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                minFontSize = 10.sp
            )
            Spacer(Modifier.width(8.dp))
            Text("$count (${(pct * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
    }
}

@Composable
fun MetricRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun HeaderWithIcon(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 0.5.sp
        )
    }
}
