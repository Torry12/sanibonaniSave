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

@Composable
fun SectionHeaderCard(
    title: String,
    subtitle: String,
    icon: String,
    count: Int? = null,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
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
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MidGray)
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
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
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
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onViewLedger: () -> Unit,
    onViewMember: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Forest.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(groupName, fontWeight = FontWeight.Bold)
                    Text("Payout Request", style = MaterialTheme.typography.labelSmall, color = Forest)
                }
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
            
            DetailRow("Bank", payout.bankName)
            DetailRow("Account", payout.accountNo)
            DetailRow("Branch", payout.branchCode)

            if (payout.status == PayoutStatus.GROUP_APPROVED) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)) {
                        Text("Reject")
                    }
                    Button(onClick = onApprove, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                        Text("Approve & Pay")
                    }
                }
            }
        }
    }
}

@Composable
fun PlatformLoanRequestCard(
    loan: Loan,
    memberName: String,
    isDisbursed: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onViewMember: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(memberName, fontWeight = FontWeight.Bold)
            DetailRow("Amount", formatZAR(loan.amount))
            DetailRow("Interest", "${loan.interestRate}%")
            
            if (loan.status == LoanStatus.PENDING) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text("Reject") }
                    Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text("Approve") }
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
    onViewMember: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(claim.beneficiaryName, fontWeight = FontWeight.Bold)
            Text("Group: $groupName", style = MaterialTheme.typography.labelSmall)
            DetailRow("Amount", formatZAR(claim.claimAmount))
            
            if (claim.status == BeneficiaryClaimStatus.ESCALATED) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text("Reject") }
                    Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text("Approve Payout") }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
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
