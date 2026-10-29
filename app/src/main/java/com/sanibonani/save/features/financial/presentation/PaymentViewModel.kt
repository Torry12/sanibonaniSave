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
                                context.group, context.contributions, context.calculation, state.currentInputAmount
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

        val preview = calculateRealtimePreview(group, currentState.contributions, calculation, newAmount)
        updateState { it.copy(realtimeShortfall = preview.shortfall, realtimeOverpayment = preview.overpayment, nextDueDate = preview.nextDueDate) }
    }

    fun onMethodChanged(method: PaymentMethod) {
        updateState { it.copy(selectedMethod = method) }
    }

    fun processPayment(type: String, amount: Money, groupId: String, cardNumber: String, expiry: String, cvv: String) {
        if (uiState.value.selectedMethod == PaymentMethod.YOCO) {
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

        val group = uiState.value.group
        val calc = uiState.value.calculation
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

        mutationJob?.cancel()
        mutationJob = launchJob("process_payment") {
            updateState { it.copy(isProcessing = true, isSuccess = false, error = null, checkoutUrl = null, transactionId = null) }
            val selectedMethod = uiState.value.selectedMethod

            val outcome = processContribution(
                type = paymentType,
                amount = amount,
                groupId = groupId,
                member = uiState.value.member,
                group = uiState.value.group,
                calculation = uiState.value.calculation,
                method = selectedMethod
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

    private fun calculateRealtimePreview(group: Group, contributions: List<Contribution>, calculation: PaymentCalculation, inputAmount: Money): RealtimePaymentPreview {
        val totalPaidBefore = contributions.asSequence()
            .filter { it.status == ContributionStatus.PAID }
            .fold(Money.ZERO) { acc, c -> acc + c.amount }

        return paymentCalculator.calculateRealtime(
            inputAmount = inputAmount,
            currentShortfall = calculation.shortfall,
            currentOverpayment = calculation.overpayment,
            lateFee = if (calculation.isOverdue) group.lateFee else Money.ZERO,
            monthlyContribution = group.monthlyContribution,
            group = group,
            joinedDate = java.time.LocalDate.now(), // Fallback to now for preview logic
            totalPaidBefore = totalPaidBefore,
        )
    }
}
