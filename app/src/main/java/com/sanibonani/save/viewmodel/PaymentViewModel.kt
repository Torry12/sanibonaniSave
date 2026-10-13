package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.data.utils.PaymentCalculation
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.utils.isPositiveMoneyAmount
import com.sanibonani.save.domain.utils.toMoneyBigDecimal
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.validation.ValidationResult
import com.sanibonani.save.domain.validation.ValidationUtils
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.usecase.ProcessPaymentUseCase
import com.sanibonani.save.ui.components.formatZAR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Locale

/**
 * UI state for the Payment screen.
 * Holds all payment context, calculation, and error state for the current member and group.
 * Updated reactively via StateFlow.
 */
data class PaymentUiState(
    val isProcessing: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val transactionId: String? = null,
    val member: Member? = null,
    val group: Group? = null,
    val contributions: List<Contribution> = emptyList(),
    val calculation: PaymentCalculation? = null,
    val realtimeShortfall: Double = 0.0,
    val realtimeOverpayment: Double = 0.0,
    val nextDueDate: String = "",
    val currentInputAmount: Double = 0.0,
    val selectedMethod: PaymentMethod = PaymentMethod.BANK,
    val checkoutUrl: String? = null
)

internal data class RealtimePaymentPreview(
    val shortfall: Double,
    val overpayment: Double,
    val nextDueDate: String
)

internal fun resolvePaymentType(type: String): PaymentType? {
    return when (type) {
        "registration", "admin_fee", "platform_fee" -> PaymentType.PLATFORM_FEE
        "joining_fee" -> PaymentType.JOINING_FEE
        "contribution" -> PaymentType.CONTRIBUTION
        else -> null
    }
}

internal fun calculateRealtimePaymentPreview(
    group: Group,
    member: Member,
    contributions: List<Contribution>,
    calculation: PaymentCalculation,
    inputAmount: Double,
    currentDate: java.time.LocalDate = java.time.LocalDate.now()
): RealtimePaymentPreview {
    val monthlyAmount = PaymentCalculator.calculateMonthlyContribution(group, member)
    val joinedDate = runCatching {
        java.time.LocalDate.parse(member.joinedAt?.substringBefore("T") ?: currentDate.toString())
    }.getOrElse { currentDate }

    val totalPaidBefore = contributions
        .filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }
        .sumOf { it.amount }

    val lateFee = if (calculation.isOverdue) group.lateFee else 0.0

    val (newShortfall, newOverpayment, nextDate) = PaymentCalculator.calculateRealtime(
        inputAmount = inputAmount,
        currentShortfall = calculation.shortfall,
        currentOverpayment = calculation.overpayment,
        lateFee = lateFee,
        monthlyContribution = monthlyAmount,
        group = group,
        joinedDate = joinedDate,
        totalPaidBefore = totalPaidBefore
    )

    return RealtimePaymentPreview(
        shortfall = newShortfall,
        overpayment = newOverpayment,
        nextDueDate = nextDate
    )
}

/**
 * ViewModel for handling payment logic, including contributions, joining fees, and platform fees.
 * Uses StateFlow for UI state, Hilt for DI, and enforces error handling and validation patterns.
 * All business logic is kept out of Composables.
 */
@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val groupRepo: GroupRepository,
    private val memberRepo: MemberRepository,
    private val processPaymentUseCase: ProcessPaymentUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentUiState())
    val state: StateFlow<PaymentUiState> = _state.asStateFlow()
    private var paymentContextJob: Job? = null

    private val isActive = MutableStateFlow(false)

    fun setActive(active: Boolean) {
        isActive.value = active
        if (!active) {
            paymentContextJob?.cancel()
            paymentContextJob = null
        }
    }

    fun loadPaymentContext(groupId: String) {
        AppAnalytics.track(
            AnalyticsTaxonomy.Events.PAYMENT_CONTEXT_LOAD_STARTED,
            mapOf(AnalyticsTaxonomy.Params.GROUP_ID to groupId)
        )
        val userId = supabaseRepo.currentUserId
        if (userId.isNullOrBlank()) {
            _state.update { it.copy(isProcessing = false, error = "You are not signed in.") }
            AppAnalytics.track(
                AnalyticsTaxonomy.Events.PAYMENT_CONTEXT_LOAD_FAILURE,
                mapOf(
                    AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                    AnalyticsTaxonomy.Params.ERROR_TYPE to "not_signed_in"
                )
            )
            return
        }

        paymentContextJob?.cancel()
        paymentContextJob = viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }

            val memberResult = memberRepo.getMemberByUserId(userId, groupId)
            val groupResult = groupRepo.getGroupById(groupId)

            val member = memberResult.getOrNull()
            val group = groupResult.getOrNull()

            val memberId = member?.id
            if (member == null || group == null || memberId.isNullOrBlank()) {
                _state.update { it.copy(isProcessing = false, error = "Failed to load payment context") }
                AppAnalytics.track(
                    AnalyticsTaxonomy.Events.PAYMENT_CONTEXT_LOAD_FAILURE,
                    mapOf(
                        AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                        AnalyticsTaxonomy.Params.ERROR_TYPE to "missing_context"
                    )
                )
                return@launch
            }

            memberRepo.getMemberContributions(memberId, groupId).collectLatest { contributionsResult ->
                val contributions = contributionsResult.getOrDefault(emptyList())
                val calculation = PaymentCalculator.calculateStatus(group, member, contributions)
                val realtimePreview = calculateRealtimePaymentPreview(
                    group = group,
                    member = member,
                    contributions = contributions,
                    calculation = calculation,
                    inputAmount = _state.value.currentInputAmount
                )

                _state.update { state ->
                    state.withLoadedPaymentContext(
                        member = member,
                        group = group,
                        contributions = contributions,
                        calculation = calculation,
                        preview = realtimePreview,
                        error = contributionsResult.exceptionOrNull()?.toUserMessage()
                    )
                }
                AppAnalytics.track(
                    AnalyticsTaxonomy.Events.PAYMENT_CONTEXT_LOAD_SUCCESS,
                    mapOf(
                        AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                        AnalyticsTaxonomy.Params.MEMBER_ID to memberId
                    )
                )
            }
        }
    }

    fun onAmountChanged(newAmount: Double) {
        _state.update { it.copy(currentInputAmount = newAmount) }

        recalculateRealtimePreview(newAmount)
    }

    fun onMethodChanged(method: PaymentMethod) {
        _state.update { it.copy(selectedMethod = method) }
    }

    private fun recalculateRealtimePreview(inputAmount: Double) {
        val currentState = _state.value
        val calculation = currentState.calculation ?: return
        val group = currentState.group ?: return
        val member = currentState.member ?: return

        val realtimePreview = calculateRealtimePaymentPreview(
            group = group,
            member = member,
            contributions = currentState.contributions,
            calculation = calculation,
            inputAmount = inputAmount
        )

        _state.update { it.withRealtimePreview(realtimePreview) }
    }

    fun processPayment(
        type: String,
        amount: Double,
        groupId: String,
        cardNumber: String,
        expiry: String,
        cvv: String
    ) {
        // Enforce provider-specific validation
        if (_state.value.selectedMethod == PaymentMethod.YOCO) {
            val validation = ValidationUtils.validatePaymentFields(cardNumber, expiry, cvv)
            if (validation !is ValidationResult.Valid) {
                _state.update { it.copy(isSuccess = false, error = validation.getErrorMessage()) }
                return
            }
        }

        // Validate input
        if (!amount.isPositiveMoneyAmount()) {
            _state.update { it.copy(isSuccess = false, error = "Amount must be positive") }
            return
        }

        // Enforce Partial Payment Rule
        val group = _state.value.group
        val calc = _state.value.calculation
        if (type == "contribution" && group != null && calc != null && !group.allowPartialPayment) {
            val minRequired = calc.totalDueNow
            if (amount.toMoneyBigDecimal() < minRequired.toMoneyBigDecimal()) {
                _state.update { it.copy(isSuccess = false, error = "This group does not allow partial payments. Min due: ${formatZAR(minRequired)}") }
                return
            }
        }

        val paymentType = resolvePaymentType(type)
        if (paymentType == null) {
            _state.update {
                it.copy(
                    isProcessing = false,
                    isSuccess = false,
                    error = "Unsupported payment type. Please retry from the previous screen."
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isProcessing = true,
                    isSuccess = false,
                    error = null,
                    checkoutUrl = null,
                    transactionId = null
                )
            }
            AppAnalytics.track(
                AnalyticsTaxonomy.Events.PAYMENT_PROCESS_STARTED,
                mapOf(
                    AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                    AnalyticsTaxonomy.Params.PAYMENT_TYPE to paymentType.name.lowercase(),
                    AnalyticsTaxonomy.Params.PAYMENT_METHOD to _state.value.selectedMethod.name
                )
            )

            val selectedMethod = _state.value.selectedMethod
            if (selectedMethod == PaymentMethod.YOCO || selectedMethod == PaymentMethod.BANK || selectedMethod == PaymentMethod.CASH) {
                if (selectedMethod == PaymentMethod.YOCO) delay(1500) // Simulate gateway latency

                processPaymentUseCase(
                    type = paymentType,
                    amount = amount,
                    groupId = groupId,
                    member = _state.value.member,
                    group = _state.value.group,
                    calculation = _state.value.calculation,
                    method = selectedMethod
                ).onSuccess { txId ->
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            isSuccess = true,
                            error = null,
                            transactionId = txId
                        )
                    }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PAYMENT_PROCESS_SUCCESS,
                        mapOf(
                            AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                            AnalyticsTaxonomy.Params.PAYMENT_TYPE to paymentType.name.lowercase()
                        )
                    )
                }.onFailure { e ->
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            isSuccess = false,
                            error = e.toUserMessage()
                        )
                    }
                }
            } else {
                // Stitch or PayFast: Initiate gateway and get checkout URL
                processPaymentUseCase.initiate(
                    method = _state.value.selectedMethod,
                    type = paymentType,
                    amount = amount,
                    groupId = groupId,
                    memberId = _state.value.member?.id,
                    description = type.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                ).onSuccess { result ->
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            isSuccess = false,
                            error = null,
                            checkoutUrl = result.checkoutUrl,
                            transactionId = result.transactionId
                        )
                    }
                }.onFailure { e ->
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            isSuccess = false,
                            error = e.toUserMessage()
                        )
                    }
                }
            }
        }
    }

    /**
     * Called when the user returns from a gateway (Stitch/PayFast).
     * Usually triggered by a deep link or back navigation.
     */
    fun onReturnFromGateway(transactionId: String, amount: Double, type: String, groupId: String) {
        val paymentType = resolvePaymentType(type) ?: return
        
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, isSuccess = false, error = null) }
            
            // In a real app, we'd verify with PaymentGatewayRepository first
            processPaymentUseCase.confirm(
                txId = transactionId,
                method = _state.value.selectedMethod,
                type = paymentType,
                amount = amount,
                groupId = groupId,
                member = _state.value.member,
                group = _state.value.group,
                calculation = _state.value.calculation
            ).onSuccess {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        isSuccess = true,
                        error = null,
                        transactionId = transactionId
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isProcessing = false,
                        isSuccess = false,
                        error = e.toUserMessage()
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        paymentContextJob?.cancel()
        // No hard state reset here to prevent race conditions during fast navigation transitions.
    }
}

/**
 * Extension to update PaymentUiState with loaded payment context and preview.
 */
private fun PaymentUiState.withLoadedPaymentContext(
    member: Member,
    group: Group,
    contributions: List<Contribution>,
    calculation: PaymentCalculation,
    preview: RealtimePaymentPreview,
    error: String?
): PaymentUiState {
    return copy(
        isProcessing = false,
        member = member,
        group = group,
        contributions = contributions,
        calculation = calculation,
        realtimeShortfall = preview.shortfall,
        realtimeOverpayment = preview.overpayment,
        nextDueDate = preview.nextDueDate,
        error = error
    )
}

/**
 * Extension to update PaymentUiState with new realtime payment preview.
 */
private fun PaymentUiState.withRealtimePreview(preview: RealtimePaymentPreview): PaymentUiState {
    return copy(
        realtimeShortfall = preview.shortfall,
        realtimeOverpayment = preview.overpayment,
        nextDueDate = preview.nextDueDate
    )
}
