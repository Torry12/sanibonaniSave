package com.sanibonani.save.ui.screens.admin.components

import androidx.compose.animation.*
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
import com.sanibonani.save.ui.components.formatZAR
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Forest.copy(alpha = 0.1f),
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
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MidGray,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (count != null) {
                Surface(
                    color = Forest.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Forest
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MidGray
            )
        }
    }
}

@Composable
fun ProvinceHeaderCard(
    province: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                contentDescription = null,
                tint = Forest
            )
            Text(
                province,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$count groups",
                style = MaterialTheme.typography.labelSmall,
                color = MidGray
            )
        }
    }
}

@Composable
fun PayoutCard(
    payout: PayoutRequest,
    groupName: String,
    onOpenPortal: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onComplete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Forest.copy(alpha = 0.1f))
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
                    Text("Payout Request", style = MaterialTheme.typography.labelSmall, color = Forest)
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = getPayoutStatusColor(payout.status).copy(0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        payout.status.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = getPayoutStatusColor(payout.status),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Text(formatZAR(payout.amount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Forest)
            
            HorizontalDivider(color = Forest.copy(0.05f))
            
            // Protected bank account information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                border = BorderStroke(1.dp, Color(0xFFFFB74D))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔒", fontSize = 16.sp)
                    Column {
                        Text(
                            "Registered Account (Protected)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            "These details cannot be changed for security",
                            style = MaterialTheme.typography.labelSmall,
                            color = MidGray
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            DetailRow("Bank", payout.bankName, isProtected = true)
            DetailRow("Account", maskAccountNumberForDisplay(payout.accountNo), isProtected = true)
            DetailRow("Branch", payout.branchCode, isProtected = true)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenPortal, modifier = Modifier.weight(1f)) {
                    Text("View Portal", style = MaterialTheme.typography.labelSmall)
                }
                
                if (payout.status == PayoutStatus.PENDING) {
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)) {
                        Text("Reject", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(onClick = onApprove, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                        Text("Approve", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (payout.status == PayoutStatus.GROUP_APPROVED) {
                    Button(onClick = onComplete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)) {
                        Text("Mark as Paid", style = MaterialTheme.typography.labelSmall)
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
    isProcessing: Boolean,
    onVerifyProfile: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDisburse: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Forest.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(memberName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text("Requested ${loan.createdAt?.substringBefore("T") ?: "recently"}", style = MaterialTheme.typography.labelSmall, color = MidGray)
                }
                
                val statusColor = when(loan.status) {
                    LoanStatus.PENDING -> InfoBlue
                    LoanStatus.APPROVED -> SuccessGreen
                    else -> MidGray
                }
                
                Surface(
                    color = statusColor.copy(0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = loan.status.name, 
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), 
                        color = statusColor, 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            DetailRow("Requested Amount", formatZAR(loan.amount))
            DetailRow("Interest Rate", "${loan.interestRate}%")
            DetailRow("Purpose", loan.purpose ?: "Not specified")
            
            HorizontalDivider(color = Forest.copy(0.1f), modifier = Modifier.padding(vertical = 4.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onVerifyProfile,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Forest),
                    border = BorderStroke(1.dp, Forest)
                ) {
                    Text("Verify Profile", style = MaterialTheme.typography.labelSmall)
                }
                
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(0.7f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = BorderStroke(1.dp, ErrorRed),
                    enabled = !isProcessing && loan.status == LoanStatus.PENDING
                ) {
                    Text("Reject", style = MaterialTheme.typography.labelSmall)
                }
                
                if (loan.status == LoanStatus.PENDING) {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(0.7f),
                        colors = ButtonDefaults.buttonColors(containerColor = Forest),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        else Text("Approve", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (loan.status == LoanStatus.APPROVED) {
                    Button(
                        onClick = onDisburse,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = InfoBlue),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        else Text("Disburse Funds", style = MaterialTheme.typography.labelSmall)
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
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onPay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Forest.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = claim.beneficiaryName,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Group: $groupName",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            DetailRow("Amount", formatZAR(claim.claimAmount))
            
            if (claim.status == BeneficiaryClaimStatus.ESCALATED) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)) {
                        Text("Reject")
                    }
                    Button(onClick = onApprove, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                        Text("Approve Payout")
                    }
                }
            } else if (claim.status == BeneficiaryClaimStatus.APPROVED) {
                Button(onClick = onPay, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)) {
                    Text("Record Disbursement")
                }
            }
        }
    }
}

@Composable
fun BehaviorInsightCard(insight: MemberBehaviorInsight) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(insight.memberName, fontWeight = FontWeight.Bold)
            DetailRow("Risk Band", insight.riskBand)
            DetailRow("Overdue Loans", insight.overdueLoans.toString())
            DetailRow("Completion", "${(insight.completionRatio * 100).toInt()}%")
        }
    }
}

@Composable
fun EmptyStateSmall(message: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MidGray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DistributionRow(label: String, count: Int, pct: Float, color: Color) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("$count (${(pct * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
fun MetricRow(label: String, value: String, color: Color = Charcoal) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MidGray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun HeaderWithIcon(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Forest, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

fun getPayoutStatusColor(status: PayoutStatus): Color = when(status) {
    PayoutStatus.PENDING -> MidGray
    PayoutStatus.GROUP_APPROVED -> InfoBlue
    PayoutStatus.PROCESSING -> ForestMid
    PayoutStatus.COMPLETED -> Forest
    PayoutStatus.FAILED -> ErrorRed
    PayoutStatus.CANCELLED -> MidGray
}
