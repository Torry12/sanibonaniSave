package com.sanibonani.save.features.admin.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.*
import com.sanibonani.save.domain.repository.credit.*
import com.sanibonani.save.domain.repository.disbursement.*
import com.sanibonani.save.domain.repository.dynamics.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.repository.treasury.*
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.usecase.disbursement.RequestPayoutUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.domain.usecase.disbursement.ProcessDisbursementUseCase
import com.sanibonani.save.domain.usecase.disbursement.ProcessBurialClaimUseCase
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.UIEvent
import com.sanibonani.save.features.admin.presentation.state.AdminFinancialUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import javax.inject.Inject

@HiltViewModel
class AdminFinancialViewModel @Inject constructor(
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val ledgerRepo: LedgerRepository,
    private val reconRepo: ReconciliationRepository,
    private val bankRepo: BankRepository,
    private val payoutRepo: PayoutRepository,
    private val loanRepo: LoanRepository,
    private val claimRepo: BeneficiaryClaimRepository,
    private val exportRepo: ExportRepository,
    private val requestPayoutUseCase: RequestPayoutUseCase,
    private val financialProviderRepo: FinancialProviderRepository,
    private val processLoanDisbursementUseCase: ProcessLoanDisbursementUseCase,
    private val processBurialClaimUseCase: ProcessBurialClaimUseCase,
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<AdminFinancialUiState>(AdminFinancialUiState()) {

    val state: StateFlow<AdminFinancialUiState> = uiState

    private var dataJob: Job? = null
    private var contextJob: Job? = null

    init {
        observeContext()
    }

    private fun observeContext() {
        contextJob?.cancel()
        contextJob = observeGroupContext(
            groupIdFlow = adminContextCacheService.selectedGroupId,
            contextSource = adminContextCacheService.contexts
        ) { context ->
            if (context == null) return@observeGroupContext

            val isNewGroup = context.group?.id != uiState.value.group?.id

            updateState { it.copy(
                group = context.group,
                members = context.members,
                selectedMemberId = if (isNewGroup) null else it.selectedMemberId,
                payoutAmount = if (isNewGroup) "" else it.payoutAmount
            ) }

            loadFinancialData(context.group?.id ?: return@observeGroupContext)
        }
    }

    private fun loadFinancialData(groupId: String) {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            updateState { it.copy(isLoading = true) }

            val ledgerFlow = ledgerRepo.observeConsolidatedAuditTrail(groupId)
            val bankFlow = bankRepo.observeUnreconciledTransactions(groupId)
            val payoutsFlow = payoutRepo.observePayouts(groupId)
            val loansFlow = loanRepo.getGroupLoans(groupId)
            val claimsFlow = claimRepo.observeClaimsForGroup(groupId)
            val reconciliationsFlow = reconRepo.observeReconciliations(groupId)

            combine(
                ledgerFlow,
                bankFlow,
                payoutsFlow,
                loansFlow,
                claimsFlow,
                reconciliationsFlow
            ) { args: Array<*> ->
                val ledger = (args[0] as Result<List<LedgerEntry>>).getOrDefault(emptyList())
                val bankTx = (args[1] as Result<List<BankTransaction>>).getOrDefault(emptyList())
                val payouts = (args[2] as Result<List<PayoutRequest>>).getOrDefault(emptyList())
                val loans = (args[3] as Result<List<Loan>>).getOrDefault(emptyList())
                val claims = (args[4] as Result<List<BeneficiaryPayoutClaim>>).getOrDefault(emptyList())
                val recons = (args[5] as Result<List<ReconciliationRecord>>).getOrDefault(emptyList())

                DataPackage(ledger, bankTx, payouts, loans, claims, recons)
            }.distinctUntilChanged().collect { pkg ->
                updateState { it.copy(
                    ledger = pkg.ledger.asSequence().toList(),
                    unreconciledBankTx = pkg.bankTx.asSequence().toList(),
                    payouts = pkg.payouts.asSequence().toList(),
                    groupLoans = pkg.loans.asSequence().toList(),
                    burialClaims = pkg.claims.asSequence().toList(),
                    reconciliationHistory = pkg.recons,
                    latestReconciliation = pkg.recons.firstOrNull(),
                    isLoading = false
                ) }
            }
        }
    }

    private data class DataPackage(
        val ledger: List<LedgerEntry>,
        val bankTx: List<BankTransaction>,
        val payouts: List<PayoutRequest>,
        val loans: List<Loan>,
        val claims: List<BeneficiaryPayoutClaim>,
        val recons: List<ReconciliationRecord>
    )

    fun performReconciliation() {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        runSafe(
            onLoading = { loading -> updateState { it.copy(isReconciling = loading) } },
            onSuccess = { record ->
                updateState { it.copy(successMessage = "Reconciliation complete: ${record.status.name}") }
            }
        ) {
            reconRepo.performReconciliation(groupId)
        }
    }

    fun resolveDiscrepancy(reconciliationId: String, notes: String) {
        runSafe(
            onLoading = { loading -> updateState { it.copy(isReconciling = loading) } },
            onSuccess = {
                updateState { it.copy(successMessage = "Discrepancy resolved and documented.") }
            }
        ) {
            reconRepo.resolveDiscrepancy(reconciliationId, notes)
        }
    }

    fun consolidateLedger() {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        viewModelScope.launch {
            updateState { it.copy(isConsolidating = true, error = null) }
            ledgerRepo.consolidateGroupLedger(groupId)
                .onSuccess { report ->
                    updateState { it.copy(
                        isConsolidating = false,
                        successMessage = "Deep consolidation complete. ${report.entriesReconstructed} entries reconstructed."
                    ) }
                    adminContextCacheService.warmUpGroup(groupId)
                    loadFinancialData(groupId)
                }
                .onFailure { e ->
                    updateState { it.copy(isConsolidating = false) }
                    handleError(e)
                }
        }
    }

    fun downloadGroupStatement() {
        val group = uiState.value.group ?: return
        val ledger = uiState.value.ledger
        if (ledger.isEmpty()) {
            updateState { it.copy(error = "No group transactions found to export.") }
            return
        }

        viewModelScope.launch {
            updateState { it.copy(isExporting = true, error = null) }
            ledgerRepo.getGroupLedger(group.id!!).onSuccess { entries ->
                exportRepo.exportLedgerToPdf(group, entries).onSuccess { file ->
                    updateState { it.copy(isExporting = false, exportFile = file) }
                    sendEvent(UIEvent.OpenFile(file, "application/pdf", "Group Statement"))
                }.onFailure { e ->
                    updateState { it.copy(isExporting = false) }
                    handleError(e)
                }
            }.onFailure { e ->
                updateState { it.copy(isExporting = false) }
                handleError(e)
            }
        }
    }

    fun updatePayoutAmount(amount: String) { updateState { it.copy(payoutAmount = amount) } }

    fun updateSelectedMemberId(id: String?) { updateState { it.copy(selectedMemberId = id) } }

    fun submitPayoutRequest() {
        val s = uiState.value
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        val amountValue = s.payoutAmount.toDoubleOrNull()
        val moneyAmount = if (amountValue != null) Money(amountValue) else null

        viewModelScope.launch {
            updateState { it.copy(isRequestingPayout = true, error = null) }
            requestPayoutUseCase(
                groupId = groupId,
                memberId = s.selectedMemberId,
                requestedAmount = moneyAmount
            ).onSuccess {
                updateState { it.copy(
                    isRequestingPayout = false,
                    payoutRequestSuccess = true,
                    payoutAmount = "",
                    selectedMemberId = null,
                    successMessage = "Payout request submitted successfully"
                ) }
            }.onFailure { e ->
                updateState { it.copy(isRequestingPayout = false) }
                handleActionFailure(e)
            }
        }
    }

    private fun handleActionFailure(e: Throwable) {
        val msg = e.message ?: ""
        if (msg.contains("MFA_REQUIRED")) {
            updateState { it.copy(showMfaChallenge = true, mfaChallengeError = null) }
        } else {
            handleError(e)
        }
    }

    fun executeRoscaPayout() {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        viewModelScope.launch {
            updateState { it.copy(isRequestingPayout = true, error = null) }
            requestPayoutUseCase(groupId = groupId).onSuccess { result ->
                updateState { it.copy(isRequestingPayout = false, successMessage = result) }
            }.onFailure { e ->
                updateState { it.copy(isRequestingPayout = false) }
                handleActionFailure(e)
            }
        }
    }

    fun approveLoan(loanId: String) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        val adminId = supabaseRepo.currentUserId ?: return
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            processLoanDisbursementUseCase(loanId, adminId, PaymentMethod.BANK).onSuccess {
                updateState { it.copy(isLoading = false, successMessage = "Loan approved and disbursed") }
                adminContextCacheService.warmUpGroup(groupId)
            }.onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleActionFailure(e)
            }
        }
    }

    fun processClaim(claimId: String, approve: Boolean) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        val adminId = supabaseRepo.currentUserId ?: return
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            val status = if (approve) BeneficiaryClaimStatus.PAID else BeneficiaryClaimStatus.REJECTED
            processBurialClaimUseCase(
                claimId = claimId,
                status = status,
                reviewedBy = adminId
            ).onSuccess {
                updateState { it.copy(isLoading = false, successMessage = "Claim ${if (approve) "processed as PAID" else "rejected"}") }
                if (approve) adminContextCacheService.warmUpGroup(groupId)
            }.onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleActionFailure(e)
            }
        }
    }

    fun verifyMfaChallenge(code: String) {
        viewModelScope.launch {
            updateState { it.copy(isVerifyingMfa = true) }
            supabaseRepo.getMfaFactors().onSuccess { factors ->
                val factor = factors.firstOrNull() ?: return@onSuccess
                supabaseRepo.verifyMfa(factor.id, code, true).onSuccess {
                    updateState { it.copy(isVerifyingMfa = false, showMfaChallenge = false) }
                    // retry last action or refresh?
                }.onFailure { e -> updateState { it.copy(isVerifyingMfa = false, mfaChallengeError = e.toUserMessage()) } }
            }
        }
    }

    fun dismissMfaChallenge() {
        updateState { it.copy(showMfaChallenge = false) }
    }

    fun approveAndEscalatePayoutRequest(payoutId: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED).onSuccess {
                updateState { it.copy(isLoading = false, successMessage = "Request escalated for platform approval") }
            }.onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleError(e)
            }
        }
    }

    fun reconcileManual(bankTxId: String, ledgerId: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            bankRepo.reconcileManual(bankTxId, ledgerId)
                .onSuccess {
                    updateState { it.copy(isLoading = false, successMessage = "Transaction reconciled successfully") }
                }
                .onFailure { e ->
                    updateState { it.copy(isLoading = false) }
                    handleError(e)
                }
        }
    }

    fun ignoreBankTransaction(bankTxId: String, reason: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            bankRepo.ignoreTransaction(bankTxId, reason)
                .onSuccess {
                    updateState { it.copy(isLoading = false, successMessage = "Bank transaction ignored") }
                }
                .onFailure { e ->
                    updateState { it.copy(isLoading = false) }
                    handleError(e)
                }
        }
    }

    fun refreshBankData() {
        val group = uiState.value.group ?: return
        val providerId = group.financialProviderId
        if (providerId.isNullOrBlank()) {
            updateState { it.copy(error = "No bank account linked to this group.") }
            return
        }

        viewModelScope.launch {
            updateState { it.copy(isRefreshingBank = true, error = null) }
            coroutineScope {
                val balanceJob = async {
                    financialProviderRepo.executeOperation(
                        FinancialOperationRequest(
                            operation = FinancialOperationType.GET_BALANCE,
                            providerId = providerId,
                            groupId = group.id
                        )
                    )
                }
                val txJob = async {
                    financialProviderRepo.executeOperation(
                        FinancialOperationRequest(
                            operation = FinancialOperationType.GET_TRANSACTIONS,
                            providerId = providerId,
                            groupId = group.id
                        )
                    )
                }

                val balanceRes = balanceJob.await()
                val txRes = txJob.await()

                if (balanceRes.isFailure) {
                    AppLogger.w("AdminFinancialVM", "Failed to refresh bank balance: ${balanceRes.exceptionOrNull()?.message}")
                }
                if (txRes.isFailure) {
                    AppLogger.w("AdminFinancialVM", "Failed to refresh bank transactions: ${txRes.exceptionOrNull()?.message}")
                }
            }

            updateState { it.copy(isRefreshingBank = false, bankRefreshSuccess = true) }
            showMessage("Bank data synchronized successfully")
            adminContextCacheService.warmUpGroup(group.id!!)
        }
    }

    fun clearError() { updateState { it.copy(error = null) } }
}
