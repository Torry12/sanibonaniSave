package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.GenerateLoanContractUseCase
import com.sanibonani.save.domain.usecase.RequestPayoutUseCase
import com.sanibonani.save.domain.usecase.ValidateLoanEligibilityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminFinancialViewModel @Inject constructor(
    private val payoutRepo: PayoutRepository,
    private val loanRepo: LoanRepository,
    private val ledgerRepo: LedgerRepository,
    private val claimRepo: BeneficiaryClaimRepository,
    private val requestPayoutUseCase: RequestPayoutUseCase,
    private val validateLoanEligibilityUseCase: ValidateLoanEligibilityUseCase,
    private val generateLoanContractUseCase: GenerateLoanContractUseCase,
    private val groupRepo: GroupRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminFinancialUiState())
    val state: StateFlow<AdminFinancialUiState> = _state.asStateFlow()

    private val selectedGroupId = MutableStateFlow<String?>(null)
    private var observationJob: Job? = null

    fun setGroupId(groupId: String?) {
        if (selectedGroupId.value == groupId) return
        selectedGroupId.value = groupId
        if (groupId != null) {
            startObserving(groupId)
        } else {
            observationJob?.cancel()
            _state.update { AdminFinancialUiState() }
        }
    }

    private fun startObserving(groupId: String) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            // Observe Payouts
            launch {
                payoutRepo.observePayouts(groupId).collect { res ->
                    res.onSuccess { list -> _state.update { it.copy(payouts = list) } }
                }
            }

            // Observe Loans
            launch {
                loanRepo.getGroupLoans(groupId).collect { res ->
                    res.onSuccess { list -> _state.update { it.copy(groupLoans = list) } }
                }
            }

            // Observe Ledger
            launch {
                ledgerRepo.observeGroupLedger(groupId).collect { res ->
                    res.onSuccess { list -> _state.update { it.copy(ledger = list) } }
                }
            }

            // Observe Claims
            launch {
                claimRepo.observeClaimsForGroup(groupId).collect { res ->
                    res.onSuccess { list -> _state.update { it.copy(burialClaims = list) } }
                }
            }
        }
    }

    // Payout Actions
    fun submitPayoutRequest(amount: Double, bank: String, account: String, branch: String) {
        val groupId = selectedGroupId.value ?: return
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }
            val payout = PayoutRequest(
                groupId = groupId,
                amount = amount,
                bankName = bank,
                accountNo = account,
                branchCode = branch,
                status = PayoutStatus.PENDING
            )
            requestPayoutUseCase(payout)
                .onSuccess { 
                    _state.update { it.copy(isProcessing = false, payoutSuccess = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isProcessing = false, error = e.toUserMessage()) }
                }
        }
    }

    fun approveLoan(loanId: String, group: Group, member: Member) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            loanRepo.approveLoan(loanId)
                .onSuccess {
                    generateLoanContractUseCase(loanRepo.getLoanById(loanId).getOrNull()!!, member, group)
                        .onSuccess { file ->
                            loanRepo.uploadLoanContract(loanId, file.readBytes(), "Contract.pdf")
                        }
                    _state.update { it.copy(isProcessing = false, successMessage = "Loan approved") }
                }
                .onFailure { e ->
                    _state.update { it.copy(isProcessing = false, error = e.toUserMessage()) }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        observationJob?.cancel()
    }
}

data class AdminFinancialUiState(
    val payouts: List<PayoutRequest> = emptyList(),
    val groupLoans: List<Loan> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val burialClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    val isProcessing: Boolean = false,
    val payoutSuccess: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null
)
