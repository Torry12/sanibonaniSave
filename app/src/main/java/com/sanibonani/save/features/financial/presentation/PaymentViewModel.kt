package com.sanibonani.save.features.financial.presentation

import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.utils.RealtimePaymentPreview
import com.sanibonani.save.domain.validation.ValidationResult
import com.sanibonani.save.domain.validation.ValidationUtils
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.usecase.equity.ProcessContributionUseCase
import com.sanibonani.save.domain.usecase.payment.LoadPaymentContextUseCase
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import java.util.UUID

data class PaymentUiState(
    val paymentContext: AsyncResult<PaymentContextPackage> = AsyncResult.Uninitialized,
    val isProcessing: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val transactionId: String? = null,
    val member: Member? = null,
    val group: Group? = null,
    val contributions: List<Contribution> = emptyList(),
    val calculation: PaymentCalculation? = null,
    val realtimeShortfall: Money = Money.ZERO,
    val realtimeOverpayment: Money = Money.ZERO,
    val nextDueDate: String = "",
    val currentInputAmount: Money = Money.ZERO,
    val selectedMethod: PaymentMethod = PaymentMethod.BANK,
    val checkoutUrl: String? = null,
    val platformConfig: PlatformConfig? = null,
    val destination: PaymentDestination = PaymentDestination.GROUP,
)

data class PaymentContextPackage(
    val member: Member,
    val group: Group,
    val contributions: List<Contribution>,
    val loans: List<Loan>,
    val calculation: PaymentCalculation,
    val preview: RealtimePaymentPreview,
    val config: PlatformConfig?,
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val loadContext: LoadPaymentContextUseCase,
    private val processContribution: ProcessContributionUseCase,
    private val paymentCalculator: PaymentCalculator
) : BaseViewModel<PaymentUiState>(PaymentUiState()) {

    val state: StateFlow<PaymentUiState> = uiState
    private var paymentContextJob: Job? = null
    private var mutationJob: Job? = null
    private val isActive = MutableStateFlow(value = false)
    private var pendingPaymentAttempt: PendingPaymentAttempt? = null

    private data class PaymentFingerprint(
        val groupId: String,
        val memberId: String?,
        val type: PaymentType,
        val amount: Money,
        val method: PaymentMethod
    )

    private data class PendingPaymentAttempt(
        val fingerprint: PaymentFingerprint,
        val transactionId: String
    )

    fun setActive(active: Boolean) {
        isActive.value = active
        if (!active) {
            paymentContextJob?.cancel()
            paymentContextJob = null
            mutationJob?.cancel()
            mutationJob = null
            clearTransactionState()
        } else {
            clearTransactionState()
        }
    }

    fun clearTransactionState() {
        pendingPaymentAttempt = null
        updateState {
            it.copy(
                isProcessing = false,
                isSuccess = false,
                error = null,
                checkoutUrl = null,
                transactionId = null
            )
        }
    }

    fun loadPaymentContext(groupId: String, type: String) {
        AppAnalytics.track(AnalyticsTaxonomy.Events.PAYMENT_CONTEXT_LOAD_STARTED, mapOf(AnalyticsTaxonomy.Params.GROUP_ID to groupId))
        val userId = loadContext.currentUserId()
        if (userId.isNullOrBlank()) {
            updateState { it.copy(isProcessing = false, error = "You are not signed in.") }
            return
        }

        paymentContextJob?.cancel()
        paymentContextJob = launchJob("load_payment_context") {
            updateState { it.copy(paymentContext = AsyncResult.Loading) }

            loadContext(LoadPaymentContextUseCase.Params(userId, groupId, type)).collectLatest { outcome ->
                when (outcome) {
                    LoadPaymentContextUseCase.Outcome.Unavailable ->
                        updateState { it.copy(paymentContext = AsyncResult.Error("Failed to load context", "LoadError")) }

                    is LoadPaymentContextUseCase.Outcome.Ready -> {
                        val context = outcome.context
                        updateState { state ->
                            val preview = calculateRealtimePreview(
                                context.member, context.group, context.contributions, context.calculation, state.currentInputAmount
                            )
                            state.copy(
                                paymentContext = AsyncResult.Success(
                                    PaymentContextPackage(
                                        member = context.member,
                                        group = context.group,
                                        contributions = context.contributions,
                                        loans = context.loans,
                                        calculation = context.calculation,
                                        preview = preview,
                                        config = context.config
                                    )
                                ),
                                member = context.member,
                                group = context.group,
                                contributions = context.contributions,
                                calculation = context.calculation,
                                currentInputAmount = if (state.currentInputAmount.isZero) context.calculation.totalDueNow else state.currentInputAmount,
                                realtimeShortfall = preview.shortfall,
                                realtimeOverpayment = preview.overpayment,
                                nextDueDate = preview.nextDueDate,
                                platformConfig = context.config,
                                destination = context.destination,
                            )
                        }
                    }
                }
            }
        }
    }

    fun onAmountChanged(newAmount: Money) {
        updateState { it.copy(currentInputAmount = newAmount) }
        val currentState = uiState.value
        val group = currentState.group ?: return
        val calculation = currentState.calculation ?: return
        val member = currentState.member ?: return

        val preview = calculateRealtimePreview(member, group, currentState.contributions, calculation, newAmount)
        updateState { it.copy(realtimeShortfall = preview.shortfall, realtimeOverpayment = preview.overpayment, nextDueDate = preview.nextDueDate) }
    }

    fun onMethodChanged(method: PaymentMethod) {
        updateState { it.copy(selectedMethod = method) }
    }

    fun processPayment(type: String, amount: Money, groupId: String, cardNumber: String, expiry: String, cvv: String) {
        val currentState = uiState.value
        if (currentState.isProcessing) return

        if (currentState.selectedMethod == PaymentMethod.YOCO) {
            val validation = ValidationUtils.validatePaymentFields(cardNumber, expiry, cvv)
            if (validation !is ValidationResult.Valid) {
                updateState { it.copy(isSuccess = false, error = validation.getErrorMessage()) }
                return
            }
        }

        if (!amount.isPositive) {
            updateState { it.copy(isSuccess = false, error = "Amount must be positive") }
            return
        }

        val group = currentState.group
        val calc = currentState.calculation
        if (type == "contribution" && (group != null) && (calc != null) && !group.allowPartialPayment) {
            if (amount < calc.totalDueNow) {
                updateState { it.copy(isSuccess = false, error = "Partial payments not allowed. Min due: ${calc.totalDueNow.format()}") }
                return
            }
        }

        val paymentType = when (type.lowercase()) {
            "registration", "platform_fee" -> PaymentType.PLATFORM_FEE
            "joining_fee" -> PaymentType.JOINING_FEE
            else -> PaymentType.CONTRIBUTION
        }

        val member = currentState.member
        val selectedMethod = currentState.selectedMethod
        val fingerprint = PaymentFingerprint(
            groupId = groupId,
            memberId = member?.id,
            type = paymentType,
            amount = amount,
            method = selectedMethod
        )
        val previousAttempt = pendingPaymentAttempt
        val transactionId = if (!currentState.isSuccess && previousAttempt?.fingerprint == fingerprint) {
            previousAttempt.transactionId
        } else {
            "tx_${UUID.randomUUID()}"
        }
        pendingPaymentAttempt = PendingPaymentAttempt(fingerprint, transactionId)
        updateState {
            it.copy(
                isProcessing = true,
                isSuccess = false,
                error = null,
                checkoutUrl = null,
                transactionId = transactionId
            )
        }
        mutationJob = launchJob("process_payment") {
            val outcome = processContribution(
                type = paymentType,
                amount = amount,
                groupId = groupId,
                member = member,
                group = group,
                calculation = calc,
                method = selectedMethod,
                transactionId = transactionId
            )

            handleContributionOutcome(outcome)
        }
    }

    private fun handleContributionOutcome(outcome: ContributionOutcome) {
        when (outcome) {
            is ContributionOutcome.Success -> {
                updateState { it.copy(isProcessing = false, isSuccess = true, transactionId = outcome.transactionId) }
            }
            is ContributionOutcome.RequiresMfa -> {
                updateState { it.copy(isProcessing = false, error = "MFA Required for high-value transaction.") }
                // Future: triggerMfaFlow()
            }
            is ContributionOutcome.ComplianceBlocked -> {
                updateState { it.copy(isProcessing = false, error = outcome.reason) }
            }
            is ContributionOutcome.Failure -> {
                updateState { it.copy(isProcessing = false, error = outcome.message) }
                outcome.throwable?.let { handleError(it) }
            }
        }
    }

    private fun calculateRealtimePreview(
        member: Member,
        group: Group,
        contributions: List<Contribution>,
        calculation: PaymentCalculation,
        inputAmount: Money
    ): RealtimePaymentPreview {
        val paidContributions = contributions.filter {
            it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL
        }
        val totalPaidBefore = paymentCalculator.calculatePaidSavings(paidContributions, member)
        val joinedDate = paymentCalculator.resolveJoinedDate(member, java.time.LocalDate.now(), contributions)

        return paymentCalculator.calculateRealtime(
            inputAmount = inputAmount,
            currentShortfall = calculation.shortfall,
            currentOverpayment = calculation.overpayment,
            lateFee = if (calculation.isOverdue) group.lateFee else Money.ZERO,
            monthlyContribution = group.monthlyContribution,
            group = group,
            joinedDate = joinedDate,
            totalPaidBefore = totalPaidBefore,
        )
    }
}
