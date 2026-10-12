package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*
import java.io.File

data class AdminFinancialUiState(
    val ledger: List<LedgerEntry> = emptyList(),
    val unreconciledBankTx: List<BankTransaction> = emptyList(),
    val payouts: List<PayoutRequest> = emptyList(),
    val groupLoans: List<Loan> = emptyList(),
    val burialClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val latestReconciliation: ReconciliationRecord? = null,
    val isReconciling: Boolean = false,
    val isConsolidating: Boolean = false,
    val isExporting: Boolean = false,
    val exportFile: File? = null,
    val reconciliationHistory: List<ReconciliationRecord> = emptyList(),
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val selectedMemberId: String? = null,
    val payoutAmount: String = "",
    val isRequestingPayout: Boolean = false,
    val payoutRequestSuccess: Boolean = false,
    val isRefreshingBank: Boolean = false,
    val bankRefreshSuccess: Boolean = false,
    val processingIds: Set<String> = emptySet(),
    val showMfaChallenge: Boolean = false,
    val mfaChallengeError: String? = null,
    val isVerifyingMfa: Boolean = false,

    // Selections for details/verification
    val selectedClaim: BeneficiaryPayoutClaim? = null,
    val selectedPayout: PayoutRequest? = null,

    // Bank Integration
    val bankConnection: BankConnection? = null,
    val availableProviders: List<FinancialProvider> = emptyList(),
    val isLinkingBank: Boolean = false,
    val bankLinkingUrl: String? = null
)
