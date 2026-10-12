package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.repository.payment.FinancialProviderRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.treasury.ReconciliationRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.features.admin.presentation.actions.AdminBankActions
import com.sanibonani.save.features.admin.presentation.actions.AdminClaimActions
import com.sanibonani.save.features.admin.presentation.actions.AdminFinancialActionHost
import com.sanibonani.save.features.admin.presentation.actions.AdminLedgerActions
import com.sanibonani.save.features.admin.presentation.actions.AdminLoanActions
import com.sanibonani.save.features.admin.presentation.actions.AdminPayoutActions
import com.sanibonani.save.features.admin.presentation.state.AdminFinancialUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import com.sanibonani.save.domain.repository.credit.LoanRepository

/**
 * Admin financial dashboard ViewModel.
 *
 * Owns the UiState container, group-context observation and MFA challenge flow.
 * Cohesive action groups are delegated to [AdminLoanActions], [AdminClaimActions],
 * [AdminPayoutActions], [AdminBankActions] and [AdminLedgerActions].
 */
@HiltViewModel
class AdminFinancialViewModel @Inject constructor(
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val ledgerRepo: LedgerRepository,
    private val reconRepo: ReconciliationRepository,
    private val bankRepo: BankRepository,
    private val payoutRepo: PayoutRepository,
    private val loanRepo: LoanRepository,
    private val claimRepo: BeneficiaryClaimRepository,
    private val financialProviderRepo: FinancialProviderRepository,
    private val supabaseRepo: SupabaseRepository,
    private val loanActions: AdminLoanActions,
    private val claimActions: AdminClaimActions,
    private val payoutActions: AdminPayoutActions,
    private val bankActions: AdminBankActions,
    private val ledgerActions: AdminLedgerActions,
) : BaseViewModel<AdminFinancialUiState>(AdminFinancialUiState()), AdminFinancialActionHost {

    val state: StateFlow<AdminFinancialUiState> = uiState

    init {
        observeContext()
        bankActions.observeBankCallbacks(this)
    }

    // region AdminFinancialActionHost

    override val actionState: AdminFinancialUiState
        get() = uiState.value

    override fun updateActionState(reducer: (AdminFinancialUiState) -> AdminFinancialUiState) {
        updateState(reducer)
    }

    override fun launchAction(key: String, block: suspend CoroutineScope.() -> Unit): Job = launchJob(key, block)

    override fun reportError(throwable: Throwable) = handleError(throwable)

    override fun reportActionError(throwable: Throwable) = handleActionFailure(throwable)

    override fun publishEvent(event: UIEvent) = sendEvent(event)

    override fun notifyUser(message: String) = showMessage(message)

    override fun reloadFinancialData(groupId: String) = loadFinancialData(groupId)

    // endregion

    // region Group context & observation

    private var contextJob: Job? = null

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
        launchJob("load_financial_data") {
            updateState { it.copy(isLoading = true) }

            combine(
                combine(
                    ledgerRepo.observeConsolidatedAuditTrail(groupId).orEmptyList(),
                    bankRepo.observeUnreconciledTransactions(groupId).orEmptyList(),
                    payoutRepo.observePayouts(groupId).orEmptyList(),
                    loanRepo.getGroupLoans(groupId).orEmptyList()
                ) { ledger, bankTx, payouts, loans ->
                    LedgerBundle(ledger, bankTx, payouts, loans)
                },
                combine(
                    claimRepo.observeClaimsForGroup(groupId).orEmptyList(),
                    reconRepo.observeReconciliations(groupId).orEmptyList(),
                    bankRepo.observeBankConnection(groupId).orNull(),
                    financialProviderRepo.observeProviders().orEmptyList()
                ) { claims, recons, connection, providers ->
                    ContextBundle(claims, recons, connection, providers)
                }
            ) { ledgerBundle, contextBundle ->
                DataPackage(
                    ledger = ledgerBundle.ledger,
                    bankTx = ledgerBundle.bankTx,
                    payouts = ledgerBundle.payouts,
                    loans = ledgerBundle.loans,
                    claims = contextBundle.claims,
                    recons = contextBundle.recons,
                    connection = contextBundle.connection,
                    providers = contextBundle.providers
                )
            }
                .distinctUntilChanged()
                .collect { pkg ->
                    updateState { it.copy(
                        ledger = pkg.ledger,
                        unreconciledBankTx = pkg.bankTx,
                        payouts = pkg.payouts,
                        groupLoans = pkg.loans,
                        burialClaims = pkg.claims,
                        reconciliationHistory = pkg.recons,
                        latestReconciliation = pkg.recons.firstOrNull(),
                        bankConnection = pkg.connection,
                        availableProviders = pkg.providers.filter { it.type == FinancialProviderType.BANK },
                        isLoading = false
                    ) }
                }
        }
    }

    /**
     * Unwraps a repository `Result` stream into a stream that never fails,
     * so failures surface as empty data rather than tearing down the combine.
     */
    private fun <T> Flow<Result<List<T>>>.orEmptyList(): Flow<List<T>> =
        map { result -> result.getOrDefault(emptyList()) }

    private fun <T> Flow<Result<T>>.orNull(): Flow<T?> =
        map { result -> result.getOrNull() }

    private data class LedgerBundle(
        val ledger: List<LedgerEntry>,
        val bankTx: List<BankTransaction>,
        val payouts: List<PayoutRequest>,
        val loans: List<Loan>
    )

    private data class ContextBundle(
        val claims: List<BeneficiaryPayoutClaim>,
        val recons: List<ReconciliationRecord>,
        val connection: BankConnection?,
        val providers: List<FinancialProvider>
    )

    private data class DataPackage(
        val ledger: List<LedgerEntry>,
        val bankTx: List<BankTransaction>,
        val payouts: List<PayoutRequest>,
        val loans: List<Loan>,
        val claims: List<BeneficiaryPayoutClaim>,
        val recons: List<ReconciliationRecord>,
        val connection: BankConnection?,
        val providers: List<FinancialProvider>
    )

    // endregion

    // region Delegated actions

    fun performReconciliation() = ledgerActions.performReconciliation(this)

    fun resolveDiscrepancy(reconciliationId: String, notes: String) =
        ledgerActions.resolveDiscrepancy(this, reconciliationId, notes)

    fun consolidateLedger() = ledgerActions.consolidateLedger(this)

    fun reconcileManual(bankTxId: String, ledgerId: String) =
        ledgerActions.reconcileManual(this, bankTxId, ledgerId)

    fun downloadGroupStatement() = ledgerActions.downloadGroupStatement(this)

    fun submitPayoutRequest() = payoutActions.submitPayoutRequest(this)

    fun executeRoscaPayout() = payoutActions.executeRoscaPayout(this)

    fun executeStokvelBatchPayout() = payoutActions.executeStokvelBatchPayout(this)

    fun approveAndEscalatePayoutRequest(payoutId: String) =
        payoutActions.approveAndEscalatePayoutRequest(this, payoutId)

    fun verifyPayoutDocument(payoutId: String, approve: Boolean) =
        payoutActions.verifyPayoutDocument(this, payoutId, approve)

    fun approveLoan(loanId: String) = loanActions.approveLoan(this, loanId)

    fun rejectLoan(loanId: String, reason: String = "Declined by group admin") =
        loanActions.rejectLoan(this, loanId, reason)

    fun processClaim(claimId: String, approve: Boolean) = claimActions.processClaim(this, claimId, approve)

    fun verifyClaimDocument(claimId: String, docKey: String, approve: Boolean) =
        claimActions.verifyClaimDocument(this, claimId, docKey, approve)

    fun refreshBankData() = bankActions.refreshBankData(this)

    fun initiateBankLinking(providerId: String) = bankActions.initiateBankLinking(this, providerId)

    fun unlinkBankAccount() = bankActions.unlinkBankAccount(this)

    fun handleBankCallback(connectionId: String, status: String) =
        bankActions.handleBankCallback(this, connectionId, status)

    fun ignoreBankTransaction(bankTxId: String, reason: String) =
        bankActions.ignoreBankTransaction(this, bankTxId, reason)

    // endregion

    // region State & MFA

    fun updatePayoutAmount(amount: String) { updateState { it.copy(payoutAmount = amount) } }

    fun updateSelectedMemberId(id: String?) { updateState { it.copy(selectedMemberId = id) } }

    fun selectClaim(claim: BeneficiaryPayoutClaim?) { updateState { it.copy(selectedClaim = claim) } }

    fun selectPayout(payout: PayoutRequest?) { updateState { it.copy(selectedPayout = payout) } }

    fun verifyMfaChallenge(code: String) {
        launchJob("verify_mfa_challenge") {
            updateState { it.copy(isVerifyingMfa = true) }
            supabaseRepo.getMfaFactors().onSuccess { factors ->
                val factor = factors.firstOrNull() ?: return@onSuccess
                supabaseRepo.verifyMfa(factor.id, code, true).onSuccess {
                    updateState { it.copy(isVerifyingMfa = false, showMfaChallenge = false) }
                }.onFailure { e -> updateState { it.copy(isVerifyingMfa = false, mfaChallengeError = e.toUserMessage()) } }
            }
        }
    }

    fun dismissMfaChallenge() {
        updateState { it.copy(showMfaChallenge = false) }
    }

    private fun handleActionFailure(e: Throwable) {
        val msg = e.message ?: ""
        if (msg.contains("MFA_REQUIRED")) {
            updateState { it.copy(showMfaChallenge = true, mfaChallengeError = null) }
        } else {
            handleError(e)
        }
    }

    fun clearError() { updateState { it.copy(error = null) } }

    // endregion
}


