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
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.features.admin.ui.components.*
import com.sanibonani.save.features.admin.presentation.PlatformAdminViewModel

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
    vm: PlatformAdminViewModel,
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
                    "FINANCIAL ESCALATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { vm.loadData() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh", style = MaterialTheme.typography.labelLarge)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- 1. PAYOUT REQUESTS ---
                item {
                    AccordionHeader(
                        title = "Payout Requests",
                        icon = Icons.Default.Payments,
                        count = state.payoutRequests.size,
                        label = "requests",
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
                                    label = "requests"
                                )
                            }
                            if (isExp) {
                                items(payouts) { payout ->
                                    PayoutCard(
                                        payout = payout,
                                        groupName = state.groupNames[payout.groupId] ?: "Unknown Group",
                                        isProcessing = state.isProcessingPayout,
                                        onViewSummary = { vm.fetchMemberSummary(payout.memberId ?: "", payout.groupId) },
                                        onVerifyProfile = { onOpenMemberPortalFromDisbursement(payout.groupId, payout.id) },
                                        onApprove = { vm.approvePayout(payout.id ?: "", payout.groupId) },
                                        onReject = { vm.rejectPayout(payout.id ?: "", payout.groupId) },
                                        onComplete = { vm.completePayout(payout.id ?: "", payout.groupId) }
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
                        title = "Escalated Loan Requests",
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        count = state.loanEscalations.size,
                        label = "loans",
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
                                    label = "loans"
                                )
                            }
                            if (isExp) {
                                items(loans) { loan ->
                                    LoanRequestCard(
                                        loan = loan,
                                        memberName = state.loanMemberNames[loan.memberId] ?: "Member ${loan.memberId.takeLast(4)}",
                                        groupName = state.groupNames[loan.groupId] ?: "Unknown Group",
                                        isProcessing = state.isProcessingLoan,
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
                        title = "Burial Claim Escalations",
                        icon = Icons.Default.Shield,
                        count = state.claimEscalations.size,
                        label = "claims",
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
                                    label = "claims"
                                )
                            }
                            if (isExp) {
                                items(claims) { claim ->
                                    BurialClaimCard(
                                        claim = claim,
                                        groupName = state.groupNames[claim.groupId] ?: "Unknown Group",
                                        isProcessing = state.isProcessingClaim,
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
                        title = "High-Risk Watchlist",
                        icon = Icons.Default.Warning,
                        count = state.behaviorInsights.size,
                        label = "members",
                        isExpanded = expandedWatchlist,
                        onClick = { expandedWatchlist = !expandedWatchlist }
                    )
                }

                if (expandedWatchlist) {
                    if (state.behaviorInsights.isEmpty()) {
                        item { EmptyStateSmall("No behavior insights available.") }
                    } else {
                        items(state.behaviorInsights) { insight ->
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

        if (state.selectedMemberSummary != null) {
            MemberSummaryDialog(
                summary = state.selectedMemberSummary,
                onDismiss = { vm.dismissMemberSummary() }
            )
        }

        if (state.isLoadingMemberSummary) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f)), Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun EmptyStateSmall(message: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
