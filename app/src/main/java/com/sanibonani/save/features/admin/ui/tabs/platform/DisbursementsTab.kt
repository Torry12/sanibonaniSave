package com.sanibonani.save.features.admin.ui.tabs.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.features.admin.ui.components.*
import com.sanibonani.save.features.admin.presentation.PlatformDisbursementsViewModel
import com.sanibonani.save.ui.components.SanibonaniRefreshButton
import com.sanibonani.save.ui.theme.ErrorRed
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.ui.utils.formatZAR

data class DisbursementsTabState(
    val payoutRequests: List<PayoutRequest>,
    val loanEscalations: List<Loan>,
    val claimEscalations: List<BeneficiaryPayoutClaim>,
    val behaviorInsights: List<MemberBehaviorInsight>,
    val groupNames: Map<String, String>,
    val loanMemberNames: Map<String, String>,
    val groupProvinces: Map<String, String>,
    val isProcessingPayout: Boolean = false,
    val isProcessingLoan: Boolean = false,
    val isProcessingClaim: Boolean = false,
    val processingIds: Set<String> = emptySet(),
    val selectedMemberSummary: MemberSummary? = null,
    val isLoadingMemberSummary: Boolean = false,
    // Pre-computed groupings
    val payoutsByProvince: List<Pair<String, List<PayoutRequest>>> = emptyList(),
    val loansByProvince: List<Pair<String, List<Loan>>> = emptyList(),
    val claimsByProvince: List<Pair<String, List<BeneficiaryPayoutClaim>>> = emptyList()
)

@Composable
fun DisbursementsTab(
    state: DisbursementsTabState,
    vm: PlatformDisbursementsViewModel,
    onOpenMemberPortalFromDisbursement: (groupId: String, payoutId: String?) -> Unit,
    onImpersonateMember: (memberId: String, groupId: String) -> Unit,
    onNavigateToHealthScore: (groupId: String) -> Unit,
    onNavigateToBehaviorTracking: (memberId: String) -> Unit
) {
    var expandedPayouts by rememberSaveable { mutableStateOf(true) }
    var expandedLoans by rememberSaveable { mutableStateOf(true) }
    var expandedClaims by rememberSaveable { mutableStateOf(true) }
    var expandedWatchlist by rememberSaveable { mutableStateOf(false) }

    val expandedPayoutProvinces = remember { mutableStateMapOf<String, Boolean>() }
    val expandedLoanProvinces = remember { mutableStateMapOf<String, Boolean>() }
    val expandedClaimProvinces = remember { mutableStateMapOf<String, Boolean>() }

    var payoutToConfirm by remember { mutableStateOf<Pair<PayoutRequest, PayoutStatus>?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.ui_financial_escalations),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                SanibonaniRefreshButton(
                    onRefresh = { vm.load() },
                    text = stringResource(R.string.ui_refresh)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- 1. PAYOUT REQUESTS ---
                item {
                    AccordionHeader(
                        title = stringResource(R.string.ui_payout_requests),
                        icon = Icons.Default.Payments,
                        count = state.payoutRequests.size,
                        label = stringResource(R.string.ui_requests_count),
                        isExpanded = expandedPayouts,
                        onClick = { expandedPayouts = !expandedPayouts }
                    )
                }

                if (expandedPayouts) {
                    if (state.payoutRequests.isEmpty()) {
                        item { EmptyStateSmall("No disbursement requests found.") }
                    } else {
                        state.payoutsByProvince.forEach { (province, payouts) ->
                            val isExp = expandedPayoutProvinces[province] != false
                            item {
                                ProvinceHeaderCard(
                                    province = province,
                                    count = payouts.size,
                                    isExpanded = isExp,
                                    onClick = { expandedPayoutProvinces[province] = !isExp },
                                    label = stringResource(R.string.ui_requests_count)
                                )
                            }
                            if (isExp) {
                                items(payouts, key = { it.id ?: it.hashCode().toString() }) { payout ->
                                    PayoutCard(
                                        payout = payout,
                                        groupName = state.groupNames[payout.groupId] ?: "Unknown Group",
                                        isProcessing = state.processingIds.contains(payout.id),
                                        onViewSummary = { vm.fetchMemberSummary(payout.memberId ?: "", payout.groupId) },
                                        onVerifyProfile = { onOpenMemberPortalFromDisbursement(payout.groupId, payout.id) },
                                        onApprove = { payoutToConfirm = payout to PayoutStatus.GROUP_APPROVED },
                                        onReject = { payoutToConfirm = payout to PayoutStatus.REJECTED },
                                        onComplete = { payoutToConfirm = payout to PayoutStatus.COMPLETED }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // --- 2. LOAN ESCALATIONS ---
                item {
                    AccordionHeader(
                        title = stringResource(R.string.ui_escalated_loan_requests),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        count = state.loanEscalations.size,
                        label = stringResource(R.string.ui_loans_count),
                        isExpanded = expandedLoans,
                        onClick = { expandedLoans = !expandedLoans }
                    )
                }

                if (expandedLoans) {
                    if (state.loanEscalations.isEmpty()) {
                        item { EmptyStateSmall("No loan requests requiring review.") }
                    } else {
                        state.loansByProvince.forEach { (province, loans) ->
                            val isExp = expandedLoanProvinces[province] != false
                            item {
                                ProvinceHeaderCard(
                                    province = province,
                                    count = loans.size,
                                    isExpanded = isExp,
                                    onClick = { expandedLoanProvinces[province] = !isExp },
                                    label = stringResource(R.string.ui_loans_count)
                                )
                            }
                            if (isExp) {
                                items(loans, key = { it.id ?: it.hashCode().toString() }) { loan ->
                                    LoanRequestCard(
                                        loan = loan,
                                        memberName = state.loanMemberNames[loan.memberId] ?: "Member ${loan.memberId.takeLast(4)}",
                                        groupName = state.groupNames[loan.groupId] ?: "Unknown Group",
                                        isProcessing = state.processingIds.contains(loan.id),
                                        onViewSummary = { vm.fetchMemberSummary(loan.memberId, loan.groupId) },
                                        onVerifyProfile = { onImpersonateMember(loan.memberId, loan.groupId) },
                                        onApprove = { vm.approveLoanRequest(loan) },
                                        onReject = { vm.rejectLoanRequest(loan, "Rejected by Platform Admin") },
                                        onDisburse = { vm.disburseLoan(loan) }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // --- 3. BURIAL CLAIM ESCALATIONS ---
                item {
                    AccordionHeader(
                        title = stringResource(R.string.ui_burial_claim_escalations),
                        icon = Icons.Default.Shield,
                        count = state.claimEscalations.size,
                        label = stringResource(R.string.ui_claims_count),
                        isExpanded = expandedClaims,
                        onClick = { expandedClaims = !expandedClaims }
                    )
                }

                if (expandedClaims) {
                    if (state.claimEscalations.isEmpty()) {
                        item { EmptyStateSmall("No escalated burial claims.") }
                    } else {
                        state.claimsByProvince.forEach { (province, claims) ->
                            val isExp = expandedClaimProvinces[province] != false
                            item {
                                ProvinceHeaderCard(
                                    province = province,
                                    count = claims.size,
                                    isExpanded = isExp,
                                    onClick = { expandedClaimProvinces[province] = !isExp },
                                    label = stringResource(R.string.ui_claims_count)
                                )
                            }
                            if (isExp) {
                                items(claims, key = { it.id ?: it.hashCode().toString() }) { claim ->
                                    BurialClaimCard(
                                        claim = claim,
                                        groupName = state.groupNames[claim.groupId] ?: "Unknown Group",
                                        isProcessing = state.processingIds.contains(claim.id),
                                        onViewSummary = { vm.fetchMemberSummary(claim.memberId, claim.groupId) },
                                        onVerifyProfile = { onImpersonateMember(claim.memberId, claim.groupId) },
                                        onApprove = { vm.approveBurialClaim(claim.id ?: "", "Approved by Platform Admin") },
                                        onReject = { vm.rejectBurialClaim(claim.id ?: "", "Rejected by Platform Admin") },
                                        onPay = { vm.payBurialClaim(claim.id ?: "", "Paid by Platform Admin") }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // --- 4. BEHAVIOR ANALYTICS (RISK WATCHLIST) ---
                item {
                    AccordionHeader(
                        title = stringResource(R.string.ui_high_risk_watchlist),
                        icon = Icons.Default.Warning,
                        count = state.behaviorInsights.size,
                        label = stringResource(R.string.ui_members_count),
                        isExpanded = expandedWatchlist,
                        onClick = { expandedWatchlist = !expandedWatchlist }
                    )
                }

                if (expandedWatchlist) {
                    if (state.behaviorInsights.isEmpty()) {
                        item { EmptyStateSmall("No behavior insights available.") }
                    } else {
                        items(state.behaviorInsights, key = { "${it.groupId}_${it.memberId}" }) { insight ->
                            BehaviorInsightCard(
                                insight = insight,
                                onClick = { onNavigateToBehaviorTracking(insight.memberId) }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }

        state.selectedMemberSummary?.let { summary ->
        MemberSummaryDialog(
            summary = summary,
            onDismiss = { vm.dismissMemberSummary() }
        )
    }

        if (state.isLoadingMemberSummary) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f)), Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        payoutToConfirm?.let { (payout, targetStatus) ->
            AlertDialog(
                onDismissRequest = { payoutToConfirm = null },
                title = { Text("Confirm Payout Action") },
                text = {
                    val actionName = when (targetStatus) {
                        PayoutStatus.GROUP_APPROVED -> "approve"
                        PayoutStatus.REJECTED -> "reject"
                        PayoutStatus.COMPLETED -> "complete"
                        else -> "process"
                    }
                    Text("Are you sure you want to $actionName this payout request of ${formatZAR(payout.amount)}?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when (targetStatus) {
                                PayoutStatus.GROUP_APPROVED -> vm.approvePayout(payout.id ?: "")
                                PayoutStatus.REJECTED -> vm.rejectPayout(payout.id ?: "")
                                PayoutStatus.COMPLETED -> vm.completePayout(payout.id ?: "")
                                else -> {}
                            }
                            payoutToConfirm = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (targetStatus == PayoutStatus.REJECTED) ErrorRed else Navy
                        )
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { payoutToConfirm = null }) {
                        Text(stringResource(R.string.ui_cancel))
                    }
                }
            )
        }
    }
}
