package com.sanibonani.save.features.admin.ui.components

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
import com.sanibonani.save.ui.components.DetailRow
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.ui.theme.*
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
fun SectionHeaderCard(
    title: String,
    subtitle: String,
    icon: String,
    count: Int? = null,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Navy.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (count != null) {
                Surface(
                    color = Navy.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.6f)
            )
        }
    }
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
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isSubHeader) 0.dp else 12.dp,
                vertical = if (isSubHeader) 4.dp else 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isSubHeader) 18.dp else 22.dp)
                )
                Spacer(Modifier.width(12.dp))
            }

            Text(
                text = if (isSubHeader) title.uppercase() else title,
                style = if (isSubHeader) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSubHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (count > 0) {
                Text(
                    text = if (label.isEmpty()) count.toString() else "$count $label",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (isSubHeader) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (isSubHeader) 16.dp else 20.dp)
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Navy.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(stringResource(R.string.ui_payout_request), style = MaterialTheme.typography.labelSmall, color = Navy)
                    Text("Requested ${payout.createdAt?.substringBefore("T") ?: "recently"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(status = payout.status.name)
            }

            Text(formatZAR(payout.amount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Navy)

            HorizontalDivider(color = Navy.copy(0.05f))

            DetailRow("Bank", payout.bankName, isBold = true)
            DetailRow("Account", maskAccountNumberForDisplay(payout.accountNo), isBold = true)
            DetailRow("Branch", payout.branchCode, isBold = true)

            HorizontalDivider(color = Navy.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))

            // Primary Actions Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (payout.memberId != null) {
                    OutlinedButton(
                        onClick = onViewSummary,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy),
                        border = BorderStroke(1.dp, Navy.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.PersonSearch, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Summary", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedButton(
                    onClick = onVerifyProfile,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.SupportAgent, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Impersonate", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Decision Actions Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (payout.status == PayoutStatus.PENDING || payout.status == PayoutStatus.GROUP_APPROVED) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(0.8f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isProcessing
                    ) {
                        Text(stringResource(R.string.ui_reject_1), style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        else Text(if (payout.status == PayoutStatus.PENDING) "APPROVE" else "PROCESS", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (payout.status == PayoutStatus.PROCESSING) {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        else Text("MARK AS COMPLETED", style = MaterialTheme.typography.labelSmall)
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
        border = BorderStroke(1.dp, Navy.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(memberName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text("Group: $groupName", style = MaterialTheme.typography.labelSmall, color = Navy)
                    Text("Requested ${loan.createdAt?.substringBefore("T") ?: "recently"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }

                StatusChip(status = loan.status.name)
            }

            DetailRow("Requested Amount", formatZAR(loan.amount))
            DetailRow("Interest Rate", "${loan.interestRate}%")
            DetailRow("Purpose", loan.purpose ?: "Not specified")

            HorizontalDivider(color = Navy.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))

            // Primary Actions Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onViewSummary,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy),
                    border = BorderStroke(1.dp, Navy.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.PersonSearch, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Summary", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onVerifyProfile,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.SupportAgent, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Impersonate", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Decision Actions Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(0.8f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isProcessing && (loan.status == LoanStatus.PENDING || loan.status == LoanStatus.APPROVED)
                ) {
                    Text(stringResource(R.string.ui_reject_1), style = MaterialTheme.typography.labelSmall)
                }

                if (loan.status == LoanStatus.PENDING) {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        else Text(stringResource(R.string.ui_approve_1), style = MaterialTheme.typography.labelSmall)
                    }
                } else if (loan.status == LoanStatus.APPROVED) {
                    Button(
                        onClick = onDisburse,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = InfoBlue),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        else Text(stringResource(R.string.ui_disburse_funds), style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Spacer(Modifier.weight(1.2f))
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
        border = BorderStroke(1.dp, Navy.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(claim.beneficiaryName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text("Group: $groupName", style = MaterialTheme.typography.labelSmall, color = Navy)
                    Text("Submitted ${claim.createdAt?.substringBefore("T") ?: "recently"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }

                StatusChip(status = claim.status.name)
            }

            DetailRow("Claim Amount", formatZAR(claim.claimAmount))
            DetailRow("Cause", claim.causeOfDeath ?: "Not specified")

            HorizontalDivider(color = Navy.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))

            // Primary Actions Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onViewSummary,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy),
                    border = BorderStroke(1.dp, Navy.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.PersonSearch, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Summary", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onVerifyProfile,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.SupportAgent, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Impersonate", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Decision Actions Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (claim.status == BeneficiaryClaimStatus.SUBMITTED || claim.status == BeneficiaryClaimStatus.UNDER_REVIEW || claim.status == BeneficiaryClaimStatus.ESCALATED) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(0.8f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        Text(stringResource(R.string.ui_reject_1), style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        else Text("APPROVE CLAIM", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (claim.status == BeneficiaryClaimStatus.APPROVED) {
                    Button(
                        onClick = onPay,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        else Text("MARK AS PAID", style = MaterialTheme.typography.labelSmall)
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
        color = color.copy(0.1f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BehaviorInsightCard(insight: MemberBehaviorInsight, onClick: () -> Unit = {}) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(insight.memberName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
            DetailRow("Risk Band", insight.riskBand)
            DetailRow("Overdue Loans", insight.overdueLoans.toString())
            DetailRow("Completion", "${(insight.completionRatio * 100).toInt()}%")
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
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun HeaderWithIcon(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Navy, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
