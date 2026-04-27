package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.PaymentCalculation
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.data.validation.ValidationResult
import com.sanibonani.save.data.validation.ValidationUtils
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
    val currentInputAmount: Double = 0.0
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

    fun loadPaymentContext(groupId: String) {
        val userId = supabaseRepo.currentUserId
        if (userId.isNullOrBlank()) {
            _state.update { it.copy(isProcessing = false, error = "You are not signed in.") }
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
            }
        }
    }

    fun onAmountChanged(newAmount: Double) {
        _state.update { it.copy(currentInputAmount = newAmount) }

        recalculateRealtimePreview(newAmount)
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
        // Validate input
        if (amount <= 0) {
            _state.update { it.copy(error = "Amount must be positive") }
            return
        }

        val validation = ValidationUtils.validatePaymentFields(cardNumber, expiry, cvv)
        if (validation !is ValidationResult.Valid) {
            _state.update { it.copy(error = validation.getErrorMessage()) }
            return
        }

        // Enforce Partial Payment Rule
        val group = _state.value.group
        val calc = _state.value.calculation
        if (type == "contribution" && group != null && calc != null && !group.allowPartialPayment) {
            val minRequired = calc.totalDueNow
            if (amount < minRequired - 0.01) { // 1 cent tolerance for float precision
                _state.update { it.copy(error = "This group does not allow partial payments. Min due: ${formatZAR(minRequired)}") }
                return
            }
        }

        val paymentType = resolvePaymentType(type)
        if (paymentType == null) {
            _state.update {
                it.copy(
                    isProcessing = false,
                    error = "Unsupported payment type. Please retry from the previous screen."
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                _state.update { it.copy(isProcessing = true, error = null) }
                delay(2000) // Simulate YoCo

                processPaymentUseCase(
                    type = paymentType,
                    amount = amount,
                    groupId = groupId,
                    member = _state.value.member,
                    group = _state.value.group,
                    calculation = _state.value.calculation
                ).onSuccess { txId ->
                    _state.update { it.copy(isProcessing = false, isSuccess = true, transactionId = txId) }
                }.onFailure { e ->
                    _state.update { it.copy(isProcessing = false, error = e.toUserMessage()) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isProcessing = false, error = e.toUserMessage()) }
            }
        }
    }
}

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

private fun PaymentUiState.withRealtimePreview(preview: RealtimePaymentPreview): PaymentUiState {
    return copy(
        realtimeShortfall = preview.shortfall,
        realtimeOverpayment = preview.overpayment,
        nextDueDate = preview.nextDueDate
    )
}

