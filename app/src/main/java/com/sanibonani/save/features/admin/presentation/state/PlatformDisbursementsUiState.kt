package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*

data class PlatformDisbursementsUiState(
    val payouts: List<PayoutRequest> = emptyList(),
    val loanRequestsByGroup: Map<String, List<Loan>> = emptyMap(),
    val escalatedClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    val isProcessingPayout: Boolean = false,
    val isProcessingLoanRequest: Boolean = false,
    val isProcessingClaim: Boolean = false,
    val processingIds: Set<String> = emptySet(),
    val selectedMemberSummary: MemberSummary? = null,
    val isLoadingMemberSummary: Boolean = false
)
