package com.sanibonani.save.features.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.DetailRow
import com.sanibonani.save.ui.components.SanibonaniLoadingScreen
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.admin.presentation.BehaviorTrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehaviorTrackingDetailScreen(
    memberId: String,
    onBack: () -> Unit,
    vm: BehaviorTrackingViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(memberId) {
        vm.loadTrack(memberId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Behavior Insights", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            SanibonaniLoadingScreen("Analyzing member behavior...")
        } else if (state.track != null) {
            BehaviorDetailContent(
                track = state.track!!,
                modifier = Modifier.padding(padding),
                onFlag = { vm.flagMember(memberId, it) },
                onUnflag = { vm.unflagMember(memberId) },
                onSuspend = { vm.suspendMember(memberId, it) },
                onUnsuspend = { vm.unsuspendMember(memberId) }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Behavior data not found for this member.")
            }
        }
    }
}

@Composable
private fun BehaviorDetailContent(
    track: MemberBehaviorTrack,
    modifier: Modifier,
    onFlag: (String) -> Unit,
    onUnflag: () -> Unit,
    onSuspend: (String) -> Unit,
    onUnsuspend: () -> Unit
) {
    var showFlagDialog by remember { mutableStateOf(false) }
    var showSuspendDialog by remember { mutableStateOf(false) }
    var actionReason by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MemberHeader(track)
        }

        item {
            ScoreCards(track)
        }

        item {
            MetricSection("Payment Performance", Icons.Default.Payments) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("On-Time Payments", track.onTimeContributions.toString())
                    DetailRow("Late Payments", track.lateContributions.toString())
                    DetailRow("Overdue Now", track.overdueCount.toString())
                    DetailRow("Current Streak", "${track.currentPaymentStreak} months")
                    DetailRow("Total Contributed", formatZAR(track.totalAmountContributed))
                }
            }
        }

        item {
            MetricSection("Loan History", Icons.AutoMirrored.Filled.TrendingUp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Loans Requested", track.totalLoansRequested.toString())
                    DetailRow("Loans Approved", track.totalLoansApproved.toString())
                    DetailRow("Loans Completed", track.totalLoansCompleted.toString())
                    DetailRow("Active Loans", track.activeLoans.toString())
                    DetailRow("Completion Rate", "${track.loanCompletionRate.toInt()}%")
                }
            }
        }

        item {
            MetricSection("Fraud Indicators", Icons.Default.Shield) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Duplicate Transactions", track.duplicateTransactionCount.toString())
                    DetailRow("Suspicious Activities", track.suspiciousActivityCount.toString())
                    DetailRow("Velocity Check Failures", if (track.velocityCheckFailed) "YES" else "NO")
                    DetailRow("Rapid Disbursements", track.rapidDisbursementAttempts.toString())
                    DetailRow("Unusual Pattern Detected", if (track.unusualPaymentPatterns) "YES" else "NO")
                }
            }
        }

        item {
            AdminActionSection(
                track = track,
                onFlag = { showFlagDialog = true },
                onUnflag = onUnflag,
                onSuspend = { showSuspendDialog = true },
                onUnsuspend = onUnsuspend
            )
        }
    }

    if (showFlagDialog) {
        AlertDialog(
            onDismissRequest = { showFlagDialog = false },
            title = { Text("Flag Member for Review") },
            text = {
                OutlinedTextField(
                    value = actionReason,
                    onValueChange = { actionReason = it },
                    label = { Text("Reason for flagging") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onFlag(actionReason)
                    showFlagDialog = false
                    actionReason = ""
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showFlagDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSuspendDialog) {
        AlertDialog(
            onDismissRequest = { showSuspendDialog = false },
            title = { Text("Suspend Member") },
            text = {
                OutlinedTextField(
                    value = actionReason,
                    onValueChange = { actionReason = it },
                    label = { Text("Reason for suspension") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSuspend(actionReason)
                        showSuspendDialog = false
                        actionReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Suspend") }
            },
            dismissButton = { TextButton(onClick = { showSuspendDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MemberHeader(track: MemberBehaviorTrack) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = Navy,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(track.memberId.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text("Member ID: ${track.memberIdNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("In group for ${track.monthsInGroup} months", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun ScoreCards(track: MemberBehaviorTrack) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScoreCard("Behavior", track.behaviorScore.toInt(), Modifier.weight(1f), Navy)
        ScoreCard("Fraud Risk", track.fraudScore.toInt(), Modifier.weight(1f), MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ScoreCard(label: String, score: Int, modifier: Modifier, color: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f))) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
            Text(score.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = color)
            LinearProgressIndicator(
                progress = { score / 100f },
                color = color,
                trackColor = color.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth().height(4.dp).background(color.copy(alpha = 0.1f), CircleShape)
            )
        }
    }
}

@Composable
private fun MetricSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Navy, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
        ) {
            Box(Modifier.padding(12.dp)) { content() }
        }
    }
}

@Composable
private fun AdminActionSection(
    track: MemberBehaviorTrack,
    onFlag: () -> Unit,
    onUnflag: () -> Unit,
    onSuspend: () -> Unit,
    onUnsuspend: () -> Unit
) {
    Column {
        Text("Administrative Actions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (track.isFlaggedForReview) {
                Button(onClick = onUnflag, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("Unflag Member")
                }
            } else {
                OutlinedButton(onClick = onFlag, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("Flag for Review")
                }
            }

            if (track.isSuspended) {
                Button(
                    onClick = onUnsuspend,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) {
                    Text("Unsuspend")
                }
            } else {
                Button(
                    onClick = onSuspend,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Suspend")
                }
            }
        }
    }
}
