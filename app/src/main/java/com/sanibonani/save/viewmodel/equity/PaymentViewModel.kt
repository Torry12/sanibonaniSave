package com.sanibonani.save.viewmodel.equity

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.domain.utils.DateProvider
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.validation.ValidationResult
import com.sanibonani.save.domain.validation.ValidationUtils
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.usecase.equity.ProcessContributionUseCase
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val realtimeShortfall: Money = Money.ZERO,
    val realtimeOverpayment: Money = Money.ZERO,
    val nextDueDate: String = "",
    val currentInputAmount: Money = Money.ZERO,
    val selectedMethod: PaymentMethod = PaymentMethod.BANK,
    val checkoutUrl: String? = null,
    val platformConfig: PlatformConfig? = null,
    val destination: PaymentDestination = PaymentDestination.GROUP
)

internal data class RealtimePaymentPreview(
    val shortfall: Money,
    val overpayment: Money,
    val nextDueDate: String
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val groupRepo: GroupRepository,
    private val memberRepo: MemberRepository,
    private val contributionRepo: ContributionRepository,
    private val loanRepo: LoanRepository,
    private val platformConfigRepo: PlatformConfigRepository,
    private val processContributionUseCase: ProcessContributionUseCase
) : BaseViewModel<PaymentUiState>(PaymentUiState()) {

    val state: StateFlow<PaymentUiState> = uiState
    private var paymentContextJob: Job? = null
    private var mutationJob: Job? = null
    private val isActive = MutableStateFlow(false)

    fun setActive(active: Boolean) {
        isActive.value = active
        if (!active) {
            paymentContextJob?.cancel()
            paymentContextJob = null
            mutationJob?.cancel()
            mutationJob = null
        }
    }

    fun loadPaymentContext(groupId: String, type: String) {
        AppAnalytics.track(AnalyticsTaxonomy.Events.PAYMENT_CONTEXT_LOAD_STARTED, mapOf(AnalyticsTaxonomy.Params.GROUP_ID to groupId))
        val userId = supabaseRepo.currentUserId
        if (userId.isNullOrBlank()) {
            updateState { it.copy(isProcessing = false, error = "You are not signed in.") }
            return
        }

        paymentContextJob?.cancel()
        paymentContextJob = viewModelScope.launch {
            updateState { it.copy(isProcessing = true, error = null) }
            val destination = when (type.lowercase()) {
                "registration", "platform_fee", "joining_fee" -> PaymentDestination.PLATFORM
                else -> PaymentDestination.GROUP
            }

            val member = memberRepo.getMemberByUserId(userId, groupId).getOrNull()
            val group = groupRepo.getGroupById(groupId).getOrNull()
            val config = platformConfigRepo.current()

            if (member == null || group == null) {
                updateState { it.copy(isProcessing = false, error = "Failed to load context") }
                return@launch
            }

            updateState { it.copy(platformConfig = config, destination = destination) }

            combine(
                contributionRepo.observeMemberContributions(member.id!!, groupId),
                loanRepo.getMemberLoans(member.id)
            ) { contributionsRes, loansRes ->
                val contributions = contributionsRes.getOrDefault(emptyList())
                val loans = loansRes.getOrDefault(emptyList())
                val calculation = PaymentCalculator.calculateStatus(group, member, contributions, loans)
                val preview = calculateRealtimePreview(group, member, contributions, calculation, _uiState.value.currentInputAmount)
                PaymentContextPackage(contributions, loans, calculation, preview)
            }.collectLatest { pkg ->
                updateState { it.withLoadedPaymentContext(member, group, pkg.contributions, pkg.calculation, pkg.preview, null) }
            }
        }
    }

    private data class PaymentContextPackage(
        val contributions: List<Contribution>,
        val loans: List<Loan>,
        val calculation: PaymentCalculation,
        val preview: RealtimePaymentPreview
    )

    fun onAmountChanged(newAmount: Money) {
        updateState { it.copy(currentInputAmount = newAmount) }
        val currentState = uiState.value
        val preview = calculateRealtimePreview(currentState.group!!, currentState.member!!, currentState.contributions, currentState.calculation!!, newAmount)
        updateState { it.withRealtimePreview(preview) }
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
        if (type == "contribution" && group != null && calc != null && !group.allowPartialPayment) {
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
        mutationJob = viewModelScope.launch {
            updateState { it.copy(isProcessing = true, isSuccess = false, error = null, checkoutUrl = null, transactionId = null) }
            val selectedMethod = uiState.value.selectedMethod

            if (selectedMethod == PaymentMethod.BANK || selectedMethod == PaymentMethod.CASH) {
                processContributionUseCase(
                    type = paymentType,
                    amount = amount,
                    groupId = groupId,
                    member = uiState.value.member,
                    group = uiState.value.group,
                    calculation = uiState.value.calculation,
                    method = selectedMethod
                ).onSuccess { txId ->
                    updateState { it.copy(isProcessing = false, isSuccess = true, transactionId = txId) }
                }.onFailure { e ->
                    updateState { it.copy(isProcessing = false, error = e.toUserMessage()) }
                    handleError(e)
                }
            } else {
                // External flow logic...
            }
        }
    }

    private fun calculateRealtimePreview(group: Group, member: Member, contributions: List<Contribution>, calculation: PaymentCalculation, inputAmount: Money): RealtimePaymentPreview {
        val totalPaidBefore = contributions.filter { it.status == ContributionStatus.PAID }.fold(Money.ZERO) { acc, c -> acc + c.amount }
        val (newShortfall, newOverpayment, nextDate) = PaymentCalculator.calculateRealtime(
            inputAmount, calculation.shortfall, calculation.overpayment, if (calculation.isOverdue) group.lateFee else Money.ZERO,
            group.monthlyContribution, group, java.time.LocalDate.now(), totalPaidBefore
        )
        return RealtimePaymentPreview(newShortfall, newOverpayment, nextDate)
    }

    private fun PaymentUiState.withLoadedPaymentContext(member: Member, group: Group, contributions: List<Contribution>, calculation: PaymentCalculation, preview: RealtimePaymentPreview, error: String?): PaymentUiState {
        return copy(isProcessing = false, member = member, group = group, contributions = contributions, calculation = calculation, currentInputAmount = if (currentInputAmount.isZero) calculation.totalDueNow else currentInputAmount, realtimeShortfall = preview.shortfall, realtimeOverpayment = preview.overpayment, nextDueDate = preview.nextDueDate, error = error)
    }

    private fun PaymentUiState.withRealtimePreview(preview: RealtimePaymentPreview): PaymentUiState {
        return copy(realtimeShortfall = preview.shortfall, realtimeOverpayment = preview.overpayment, nextDueDate = preview.nextDueDate)
    }
}
