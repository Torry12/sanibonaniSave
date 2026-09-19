package com.sanibonani.save.ui.screens.admin.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberSummaryDialog(
    summary: MemberSummary?,
    onDismiss: () -> Unit
) {
    if (summary == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── HEADER ──
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = summary.member.profilePhotoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Navy.copy(alpha = 0.1f)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = summary.member.fullName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        StatusChip(status = summary.member.status)
                    }
                }
            }

            // ── PERSONAL INFO ──
            item {
                SummarySection(title = "Personal Details", icon = Icons.Default.Person) {
                    InfoRow("ID Number", summary.member.idNumber)
                    InfoRow("Phone", summary.member.phone)
                    InfoRow("Email", summary.member.email ?: "N/A")
                    InfoRow("Address", "${summary.member.street}, ${summary.member.suburb}, ${summary.member.city}")
                }
            }

            // ── FINANCIAL BEHAVIOR ──
            item {
                SummarySection(title = "Payment Behavior", icon = Icons.Default.Analytics) {
                    summary.behavior?.let { b ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BehaviorStat("On-Time", b.onTimeContributions.toString(), SuccessGreen)
                            BehaviorStat("Late", b.lateContributions.toString(), WarningAmber)
                            BehaviorStat("Missed", b.missedContributions.toString(), ErrorRed)
                        }
                        Spacer(Modifier.height(12.dp))
                        InfoRow("Total Contributed", formatZAR(b.totalAmountContributed))
                        InfoRow("Current Streak", "${b.currentPaymentStreak} months")
                        InfoRow("Consistency Score", "${b.paymentConsistencyScore.toInt()}%")
                    } ?: Text("No behavior data available.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            // ── LOAN HISTORY (Past 6 Months) ──
            item {
                Text(
                    "Loan History (Past 6 Months)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
            }

            if (summary.recentLoans.isEmpty()) {
                item {
                    Text("No recent loans found.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            } else {
                items(summary.recentLoans) { loan ->
                    LoanHistoryItem(loan)
                }
            }
        }
    }
}

@Composable
private fun SummarySection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Navy, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Navy)
        }
        content()
        HorizontalDivider(color = Navy.copy(alpha = 0.05f))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BehaviorStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
private fun LoanHistoryItem(loan: Loan) {
    Surface(
        color = Navy.copy(alpha = 0.03f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(formatZAR(loan.amount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(loan.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Text(
                loan.status.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = when(loan.status) {
                    LoanStatus.COMPLETED -> SuccessGreen
                    LoanStatus.OVERDUE -> ErrorRed
                    else -> Navy
                }
            )
        }
    }
}

@Composable
private fun StatusChip(status: MemberStatus) {
    Surface(
        color = when(status) {
            MemberStatus.ACTIVE -> SuccessGreen.copy(0.1f)
            else -> WarningAmber.copy(0.1f)
        },
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            status.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = when(status) {
                MemberStatus.ACTIVE -> SuccessGreen
                else -> WarningAmber
            }
        )
    }
}
