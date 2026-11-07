package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*

data class AdminFinancialUiState(
    val ledger: List<LedgerEntry> = emptyList(),
    val unreconciledBankTx: List<BankTransaction> = emptyList(),
    val payouts: List<PayoutRequest> = emptyList(),
    val groupLoans: List<Loan> = emptyList(),
    val burialClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    val isReconciling: Boolean = false,
    val isConsolidating: Boolean = false,
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val selectedMemberId: String? = null,
    val payoutAmount: String = "",
    val isRequestingPayout: Boolean = false,
    val isRefreshingBank: Boolean = false,
    val processingIds: Set<String> = emptySet(),
    val showMfaChallenge: Boolean = false,
    val mfaChallengeError: String? = null,
    val isVerifyingMfa: Boolean = false,

    // Selections for details/verification
    val selectedClaim: BeneficiaryPayoutClaim? = null,
    val selectedPayout: PayoutRequest? = null
)
