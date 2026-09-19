package com.sanibonani.save.features.financial.presentation

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
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.repository.equity.*
import com.sanibonani.save.domain.repository.credit.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.repository.treasury.*
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
    val destination: PaymentDestination = PaymentDestination.GROUP
)

data class PaymentContextPackage(
    val member: Member,
    val group: Group,
    val contributions: List<Contribution>,
    val loans: List<Loan>,
    val calculation: PaymentCalculation,
    val preview: RealtimePaymentPreview,
    val config: PlatformConfig?
)

data class RealtimePaymentPreview(
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
            updateState { it.copy(paymentContext = AsyncResult.Loading) }
            val destination = when (type.lowercase()) {
                "registration", "platform_fee", "joining_fee" -> PaymentDestination.PLATFORM
                else -> PaymentDestination.GROUP
            }

            val member = memberRepo.observeMemberByUserId(userId, groupId).first().getOrNull()
            val group = groupRepo.getGroupById(groupId).getOrNull()
            val config = platformConfigRepo.current()

            if (member == null || group == null) {
                updateState { it.copy(paymentContext = AsyncResult.Error("Failed to load context")) }
                return@launch
            }

            updateState { it.copy(platformConfig = config, destination = destination) }

            val memberId = member.id!!
            combine(
                contributionRepo.observeMemberContributions(memberId, groupId),
                loanRepo.getMemberLoans(memberId)
            ) { contributionsRes, loansRes ->
                val contributions = contributionsRes.getOrDefault(emptyList())
                val loans = loansRes.getOrDefault(emptyList())
                val calculation = PaymentCalculator.calculateStatus(group, member, contributions, loans)
                val preview = calculateRealtimePreview(group, member, contributions, calculation, _uiState.value.currentInputAmount)
                PaymentContextPackage(member, group, contributions, loans, calculation, preview, config)
            }.collectLatest { pkg ->
                updateState {
                    it.copy(
                        paymentContext = AsyncResult.Success(pkg),
                        member = pkg.member,
                        group = pkg.group,
                        contributions = pkg.contributions,
                        calculation = pkg.calculation,
                        currentInputAmount = if (it.currentInputAmount.isZero) pkg.calculation.totalDueNow else it.currentInputAmount,
                        realtimeShortfall = pkg.preview.shortfall,
                        realtimeOverpayment = pkg.preview.overpayment,
                        nextDueDate = pkg.preview.nextDueDate
                    )
                }
            }
        }
    }

    fun onAmountChanged(newAmount: Money) {
        updateState { it.copy(currentInputAmount = newAmount) }
        val currentState = uiState.value
        val group = currentState.group ?: return
        val member = currentState.member ?: return
        val calculation = currentState.calculation ?: return

        val preview = calculateRealtimePreview(group, member, currentState.contributions, calculation, newAmount)
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
                val outcome = processContributionUseCase(
                    type = paymentType,
                    amount = amount,
                    groupId = groupId,
                    member = uiState.value.member,
                    group = uiState.value.group,
                    calculation = uiState.value.calculation,
                    method = selectedMethod
                )

                when (outcome) {
                    is ContributionOutcome.Success -> {
                        updateState { it.copy(isProcessing = false, isSuccess = true, transactionId = outcome.transactionId) }
                    }
                    is ContributionOutcome.RequiresMfa -> {
                        updateState { it.copy(isProcessing = false, error = "MFA Required for high-value transaction.") }
                        // In a real app, we'd trigger the MFA flow here.
                    }
                    is ContributionOutcome.ComplianceBlocked -> {
                        updateState { it.copy(isProcessing = false, error = outcome.reason) }
                    }
                    is ContributionOutcome.Failure -> {
                        updateState { it.copy(isProcessing = false, error = outcome.message) }
                        outcome.throwable?.let { handleError(it) }
                    }
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
}
