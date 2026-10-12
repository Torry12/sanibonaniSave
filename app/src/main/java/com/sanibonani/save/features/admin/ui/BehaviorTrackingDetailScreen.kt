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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
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
        vm.loadMemberData(memberId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_behavior_insights), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        val track = state.track
        if (state.isLoading) {
            SanibonaniLoadingScreen(stringResource(R.string.ui_analyzing_member_behavior))
        } else if (track != null) {
            BehaviorDetailContent(
                track = track,
                modifier = Modifier.padding(padding),
                onFlag = { vm.flagMember(memberId, it) },
                onUnflag = { vm.unflagMember(memberId) },
                onSuspend = { vm.suspendMember(memberId, it) },
                onUnsuspend = { vm.unsuspendMember(memberId) }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ui_behavior_data_not_found))
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
            MetricSection(stringResource(R.string.ui_payment_performance), Icons.Default.Payments) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(stringResource(R.string.ui_on_time_payments), track.onTimeContributions.toString())
                    DetailRow(stringResource(R.string.ui_late_payments), track.lateContributions.toString())
                    DetailRow(stringResource(R.string.ui_overdue_now), track.overdueCount.toString())
                    DetailRow(stringResource(R.string.ui_current_streak), stringResource(R.string.ui_current_streak_months, track.currentPaymentStreak.toString()))
                    DetailRow(stringResource(R.string.ui_total_contributed), formatZAR(track.totalAmountContributed))
                }
            }
        }

        item {
            MetricSection(stringResource(R.string.ui_loan_history), Icons.AutoMirrored.Filled.TrendingUp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(stringResource(R.string.ui_loans_requested), track.totalLoansRequested.toString())
                    DetailRow(stringResource(R.string.ui_loans_approved), track.totalLoansApproved.toString())
                    DetailRow(stringResource(R.string.ui_loans_completed), track.totalLoansCompleted.toString())
                    DetailRow(stringResource(R.string.ui_active_loans), track.activeLoans.toString())
                    DetailRow(stringResource(R.string.ui_completion_rate), "${track.loanCompletionRate.toInt()}%")
                }
            }
        }

        item {
            MetricSection(stringResource(R.string.ui_fraud_indicators), Icons.Default.Shield) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(stringResource(R.string.ui_duplicate_transactions), track.duplicateTransactionCount.toString())
                    DetailRow(stringResource(R.string.ui_suspicious_activities), track.suspiciousActivityCount.toString())
                    DetailRow(stringResource(R.string.ui_velocity_check_failures), if (track.velocityCheckFailed) "YES" else "NO")
                    DetailRow(stringResource(R.string.ui_rapid_disbursements), track.rapidDisbursementAttempts.toString())
                    DetailRow(stringResource(R.string.ui_unusual_pattern_detected), if (track.unusualPaymentPatterns) "YES" else "NO")
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
            title = { Text(stringResource(R.string.ui_flag_member_for_review)) },
            text = {
                OutlinedTextField(
                    value = actionReason,
                    onValueChange = { actionReason = it },
                    label = { Text(stringResource(R.string.ui_reason_for_flagging)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onFlag(actionReason)
                    showFlagDialog = false
                    actionReason = ""
                }) { Text(stringResource(R.string.ui_ok)) }
            },
            dismissButton = { TextButton(onClick = { showFlagDialog = false }) { Text(stringResource(R.string.ui_cancel)) } }
        )
    }

    if (showSuspendDialog) {
        AlertDialog(
            onDismissRequest = { showSuspendDialog = false },
            title = { Text(stringResource(R.string.ui_suspend_member)) },
            text = {
                OutlinedTextField(
                    value = actionReason,
                    onValueChange = { actionReason = it },
                    label = { Text(stringResource(R.string.ui_reason_for_suspension)) },
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
                ) { Text(stringResource(R.string.ui_suspend)) }
            },
            dismissButton = { TextButton(onClick = { showSuspendDialog = false }) { Text(stringResource(R.string.ui_cancel)) } }
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
            Text(stringResource(R.string.ui_member_id_label, track.memberIdNumber), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ui_tenure_months, track.monthsInGroup), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun ScoreCards(track: MemberBehaviorTrack) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScoreCard(stringResource(R.string.ui_behavior), track.behaviorScore.toInt(), Modifier.weight(1f), Navy)
        ScoreCard(stringResource(R.string.ui_fraud_risk), track.fraudScore.toInt(), Modifier.weight(1f), MaterialTheme.colorScheme.error)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
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
        Text(stringResource(R.string.ui_administrative_actions), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (track.isFlaggedForReview) {
                Button(onClick = onUnflag, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.ui_unflag_member))
                }
            } else {
                OutlinedButton(onClick = onFlag, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.ui_flag_for_review))
                }
            }

            if (track.isSuspended) {
                Button(
                    onClick = onUnsuspend,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) {
                    Text(stringResource(R.string.ui_unsuspend))
                }
            } else {
                Button(
                    onClick = onSuspend,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ui_suspend))
                }
            }
        }
    }
}
