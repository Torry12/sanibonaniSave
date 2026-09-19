package com.sanibonani.save.ui.screens.member

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.DetailRow
import com.sanibonani.save.ui.components.InfoBox
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.components.StatusChip
import com.sanibonani.save.ui.theme.LightGray
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.ui.utils.formatZAR

@Composable
fun LoanItem(loan: Loan, onDownloadContract: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${formatZAR(loan.amount)} Loan", fontWeight = FontWeight.Bold)
                    Text("Requested on ${loan.createdAt?.substringBefore("T") ?: "N/A"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!loan.contractUrl.isNullOrBlank() && onDownloadContract != null) {
                        IconButton(onClick = onDownloadContract) {
                            Icon(Icons.Default.Description, "Download Agreement", tint = Navy)
                        }
                    }
                    StatusChip(status = loan.status.toMemberStatus(), label = loan.status.displayName)
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
                    color = Color.Black,
                    trackColor = Color.Black.copy(alpha = 0.1f)
                )
                Text("${(progress * 100).toInt()}% Repaid", style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

fun LoanStatus.toMemberStatus(): MemberStatus = when(this) {
    LoanStatus.ACTIVE, LoanStatus.COMPLETED, LoanStatus.PARTIALLY_PAID, LoanStatus.APPROVED -> MemberStatus.ACTIVE
    LoanStatus.PENDING -> MemberStatus.PENDING_PAYMENT
    LoanStatus.REJECTED, LoanStatus.OVERDUE, LoanStatus.CANCELLED -> MemberStatus.SUSPENDED
}

fun ContributionStatus.toMemberStatus(): MemberStatus = when(this) {
    ContributionStatus.PAID, ContributionStatus.PARTIAL -> MemberStatus.ACTIVE
    ContributionStatus.DUE -> MemberStatus.PENDING_PAYMENT
    ContributionStatus.OVERDUE -> MemberStatus.SUSPENDED
}

@Composable
fun NotificationItem(n: AppNotification) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(Navy.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp), tint = Navy) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(n.message, style = MaterialTheme.typography.bodyMedium); Text(n.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
        }
    }
}

@Composable
fun MessageItem(m: AppNotification) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.05f)), modifier = Modifier.fillMaxWidth()) {
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                Text(formatZAR(c.amount), fontWeight = FontWeight.ExtraBold, color = Color.Black)
                StatusChip(status = c.status.toMemberStatus(), label = c.status.name)
            }
        }
    }
}

@Composable
fun ContributionDetailDialog(
    contribution: Contribution,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_transaction_proof), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoBox(
                    message = "This record is verified on the digital ledger. Use the Transaction ID below for external verification.",
                    type = InfoType.INFO
                )

                DetailRow("Type", if (contribution.type == "joining_fee") "Joining Fee" else "Monthly Contribution")
                DetailRow("Amount", formatZAR(contribution.amount))
                DetailRow("Due Date", contribution.dueDate)
                DetailRow("Paid Date", contribution.paidAt?.substringBefore("T") ?: "N/A")
                DetailRow("Status", contribution.status.displayName)

                HorizontalDivider(color = LightGray.copy(alpha = 0.3f))

                Column {
                    Text(stringResource(R.string.ui_transaction_reference), style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(alpha = 0.6f))
                    Text(
                        contribution.transactionId ?: "PENDING_VERIFICATION",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close), color = Color.Black) }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}
