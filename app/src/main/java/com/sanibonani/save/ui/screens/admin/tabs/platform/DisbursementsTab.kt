package com.sanibonani.save.ui.screens.admin.tabs.platform

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.SanibonaniTextField
import com.sanibonani.save.ui.theme.Forest
import com.sanibonani.save.ui.theme.ForestMid
import com.sanibonani.save.ui.theme.MidGray
import com.sanibonani.save.viewmodel.PlatformAdminViewModel
import com.sanibonani.save.ui.screens.admin.components.*

@Composable
fun DisbursementsTab(
    payouts: List<PayoutRequest>,
    groups: List<Group>,
    claims: List<BeneficiaryPayoutClaim>,
    loanRequestsByGroup: Map<String, List<Loan>>,
    loanMemberNames: Map<String, String>,
    memberBehaviorInsights: List<MemberBehaviorInsight>,
    selectedRiskFilter: String,
    isLoadingLoanRequests: Boolean,
    isProcessingLoanRequest: Boolean,
    vm: PlatformAdminViewModel,
    onOpenMemberPortal: (groupId: String, payoutId: String?) -> Unit,
    onVerifyMember: (memberId: String, groupId: String) -> Unit,
    onRiskFilterChanged: (String) -> Unit
) {
    var rejectLoanTarget by remember { mutableStateOf<Loan?>(null) }
    var rejectLoanReason by remember { mutableStateOf("") }
    
    var isPayoutsSectionExpanded by remember { mutableStateOf(true) }
    var isLoansSectionExpanded by remember { mutableStateOf(true) }
    var isClaimsSectionExpanded by remember { mutableStateOf(true) }
    var isAnalysisSectionExpanded by remember { mutableStateOf(false) }

    val expandedPayoutProvinces = remember { mutableStateMapOf<String, Boolean>() }
    val expandedLoanProvinces = remember { mutableStateMapOf<String, Boolean>() }
    val expandedClaimProvinces = remember { mutableStateMapOf<String, Boolean>() }

    val provinceMap = remember(groups) { groups.associateBy({ it.id }, { it.province ?: "Other" }) }

    val payoutsByProvince = remember(payouts, provinceMap) {
        payouts.groupBy { provinceMap[it.groupId] ?: "Other" }.toList().sortedBy { it.first }
    }

    val loansByProvince = remember(loanRequestsByGroup, provinceMap) {
        loanRequestsByGroup.entries.groupBy { (groupId, _) ->
            provinceMap[groupId] ?: "Other"
        }.toList().sortedBy { it.first }
    }

    val claimsByProvince = remember(claims, provinceMap) {
        claims.groupBy { claim ->
            provinceMap[claim.groupId] ?: "Other"
        }.toList().sortedBy { it.first }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Escalated Disbursements Section ---
        item {
            SectionHeaderCard(
                icon = "💰",
                title = "Escalated Disbursements",
                subtitle = "Payout requests awaiting final approval.",
                count = payouts.size,
                isExpanded = isPayoutsSectionExpanded,
                onClick = { isPayoutsSectionExpanded = !isPayoutsSectionExpanded }
            )
        }

        if (isPayoutsSectionExpanded) {
            if (payouts.isEmpty()) {
                item { EmptyStateSmall("No disbursement requests found.") }
            } else {
                payoutsByProvince.forEach { (province, provincePayouts) ->
                    item(key = "payout_province_$province") {
                        ProvinceHeaderCard(
                            province = province,
                            count = provincePayouts.size,
                            isExpanded = expandedPayoutProvinces[province] == true,
                            onClick = { expandedPayoutProvinces[province] = !(expandedPayoutProvinces[province] ?: false) }
                        )
                    }

                    if (expandedPayoutProvinces[province] == true) {
                        items(provincePayouts) { payout ->
                            val group = groups.find { it.id == payout.groupId }
                            val groupName = group?.name ?: "Unknown Group"
                            PayoutCard(
                                payout = payout,
                                groupName = groupName,
                                onOpenPortal = {
                                    vm.logAudit(action = "OPEN_MEMBER_PORTAL_FROM_DISBURSEMENT", targetGroupId = payout.groupId, details = mapOf("payoutId" to (payout.id ?: "unknown"), "groupName" to groupName))
                                    onOpenMemberPortal(payout.groupId, payout.id)
                                },
                                onApprove = { payout.id?.let { vm.approvePayout(it, payout.groupId) } },
                                onReject = { payout.id?.let { vm.rejectPayout(it, payout.groupId) } },
                                onComplete = { payout.id?.let { vm.completePayout(it, payout.groupId) } }
                            )
                        }
                    }
                }
            }
        }

        // --- 2. Member Loan Requests Section ---
        item {
            SectionHeaderCard(
                icon = "🏥",
                title = "Member Loan Requests",
                subtitle = "Loan applications requiring platform review.",
                count = loanRequestsByGroup.values.flatten().size,
                isExpanded = isLoansSectionExpanded,
                onClick = { isLoansSectionExpanded = !isLoansSectionExpanded }
            )
        }

        if (isLoansSectionExpanded) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.refreshLoanRequests() }, enabled = !isLoadingLoanRequests && !isProcessingLoanRequest) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isLoadingLoanRequests) "Refreshing..." else "Refresh List", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (isLoadingLoanRequests) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Forest, modifier = Modifier.size(24.dp)) } }
            } else if (loanRequestsByGroup.isEmpty()) {
                item { EmptyStateSmall("No loan requests requiring review.") }
            } else {
                loansByProvince.forEach { (province, provinceEntries) ->
                    item(key = "loan_province_$province") {
                        ProvinceHeaderCard(
                            province = province,
                            count = provinceEntries.sumOf { it.value.size },
                            isExpanded = expandedLoanProvinces[province] == true,
                            onClick = { expandedLoanProvinces[province] = !(expandedLoanProvinces[province] ?: false) }
                        )
                    }

                    if (expandedLoanProvinces[province] == true) {
                        provinceEntries.sortedBy { (groupId, _) -> groups.find { it.id == groupId }?.name ?: "" }.forEach { (groupId, loans) ->
                            item(key = "loan_group_header_$groupId") {
                                val groupName = groups.find { it.id == groupId }?.name ?: "Unknown Group"
                                Text(text = groupName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ForestMid, modifier = Modifier.padding(top = 8.dp, start = 24.dp))
                            }

                            items(loans) { loan ->
                                val memberName = loanMemberNames[loan.memberId] ?: "Member ${loan.memberId.take(8)}"
                                LoanRequestCard(
                                    loan = loan,
                                    memberName = memberName,
                                    isProcessing = isProcessingLoanRequest,
                                    onVerifyProfile = {
                                        vm.logAudit(action = "PLATFORM_VERIFY_LOAN_MEMBER", targetMemberId = loan.memberId, targetGroupId = loan.groupId, details = mapOf("loanId" to (loan.id ?: "unknown")))
                                        onVerifyMember(loan.memberId, loan.groupId)
                                    },
                                    onApprove = { vm.approveLoanRequest(loan) },
                                    onReject = { rejectLoanTarget = loan; rejectLoanReason = "" }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Burial Society Claims Section ---
        item {
            SectionHeaderCard(
                icon = "🕊️",
                title = "Burial Payout Claims",
                subtitle = "Escalated death benefit claims.",
                count = claims.size,
                isExpanded = isClaimsSectionExpanded,
                onClick = { isClaimsSectionExpanded = !isClaimsSectionExpanded }
            )
        }

        if (isClaimsSectionExpanded) {
            if (claims.isEmpty()) {
                item { EmptyStateSmall("No escalated burial claims.") }
            } else {
                claimsByProvince.forEach { (province, provinceClaims) ->
                    item(key = "claim_province_$province") {
                        ProvinceHeaderCard(
                            province = province,
                            count = provinceClaims.size,
                            isExpanded = expandedClaimProvinces[province] == true,
                            onClick = { expandedClaimProvinces[province] = !(expandedClaimProvinces[province] ?: false) }
                        )
                    }

                    if (expandedClaimProvinces[province] == true) {
                        items(provinceClaims) { claim ->
                            val group = groups.find { it.id == claim.groupId }
                            val groupName = group?.name ?: "Unknown Group"
                            BurialClaimCard(
                                claim = claim,
                                groupName = groupName,
                                onApprove = { claim.id?.let { vm.approveBurialClaim(it, "Approved by Platform") } },
                                onPay = { claim.id?.let { vm.payBurialClaim(it, "Paid Out") } },
                                onReject = { claim.id?.let { vm.rejectBurialClaim(it, "Rejected by Platform") } }
                            )
                        }
                    }
                }
            }
        }

        // --- 4. Member Behavior Analysis Section ---
        item {
            SectionHeaderCard(
                icon = "📊",
                title = "Member Behavior Analysis",
                subtitle = "Risk-focused summary of loan behavior.",
                count = null,
                isExpanded = isAnalysisSectionExpanded,
                onClick = { isAnalysisSectionExpanded = !isAnalysisSectionExpanded }
            )
        }

        if (isAnalysisSectionExpanded) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 12.dp)) {
                    listOf("All", "High", "Elevated", "Watch", "Stable").forEach { riskBand ->
                        FilterChip(
                            selected = selectedRiskFilter == riskBand,
                            onClick = { onRiskFilterChanged(riskBand) },
                            label = { Text(riskBand) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Forest.copy(0.1f), selectedLabelColor = Forest)
                        )
                    }
                }
            }

            if (memberBehaviorInsights.isEmpty()) {
                item { EmptyStateSmall("No behavior insights available.") }
            } else {
                items(memberBehaviorInsights.take(15)) { insight ->
                    BehaviorInsightCard(insight)
                }
            }
        }
        
        item { Spacer(Modifier.height(40.dp)) }
    }

    rejectLoanTarget?.let { loan ->
        AlertDialog(
            onDismissRequest = { rejectLoanTarget = null },
            title = { Text("Reject Loan Request", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Provide a reason for rejecting this loan request.")
                    SanibonaniTextField(value = rejectLoanReason, onValueChange = { rejectLoanReason = it }, label = "Rejection Reason", placeholder = "e.g. Contribution history insufficient")
                }
            },
            confirmButton = {
                Button(onClick = { vm.rejectLoanRequest(loan, rejectLoanReason); rejectLoanTarget = null }, enabled = rejectLoanReason.isNotBlank() && !isProcessingLoanRequest, colors = ButtonDefaults.buttonColors(containerColor = com.sanibonani.save.ui.theme.ErrorRed)) { Text("Reject") }
            },
            dismissButton = {
                TextButton(onClick = { rejectLoanTarget = null }) { Text("Cancel", color = MidGray) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}
