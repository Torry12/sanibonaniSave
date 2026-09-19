package com.sanibonani.save.viewmodel.treasury

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.UIEvent
import com.sanibonani.save.domain.utils.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AdminMemberUiState(
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val selectedMember: Member? = null,
    val selectedMemberBeneficiaries: List<Beneficiary> = emptyList(),
    val selectedMemberDocuments: List<MemberDocument> = emptyList(),
    val selectedMemberCalculation: PaymentCalculation? = null,
    val memberCalculations: Map<String, PaymentCalculation> = emptyMap(),
    val isEligibleForLoan: Boolean = false,
    val loanIneligibilityReason: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val isExporting: Boolean = false,
    val exportFile: java.io.File? = null,

    // Messaging
    val messageText: String = "",
    val isSendingMessage: Boolean = false,
    val messageSentSuccess: Boolean = false,
    val selectedNotifChannel: NotifChannel = NotifChannel.BOTH
)

@HiltViewModel
class AdminMemberViewModel @Inject constructor(
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val memberRepo: MemberRepository,
    private val contributionRepo: ContributionRepository,
    private val beneficiaryRepo: BeneficiaryRepository,
    private val memberDocumentRepo: MemberDocumentRepository,
    private val updateMemberStatusUseCase: UpdateMemberStatusUseCase,
    private val verifyMemberDocumentUseCase: VerifyMemberDocumentUseCase,
    private val verifyRelationalDocumentUseCase: VerifyRelationalDocumentUseCase,
    private val validateLoanEligibilityUseCase: ValidateLoanEligibilityUseCase,
    private val sendNotificationUseCase: SendNotificationUseCase,
    private val exportRepo: ExportRepository
) : BaseViewModel<AdminMemberUiState>(AdminMemberUiState()) {

    val state: StateFlow<AdminMemberUiState> = uiState

    private var detailsJob: Job? = null
    private var contextJob: Job? = null
    private var calculationsJob: Job? = null

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
                isLoading = !context.isFullyHydrated,
                selectedMember = if (isNewGroup) null else it.selectedMember
            ) }

            if (context.group != null && context.members.isNotEmpty()) {
                refreshAllMemberCalculations(context.group, context.members)
            }
        }
    }

    private fun refreshAllMemberCalculations(group: Group, members: List<Member>) {
        val groupId = group.id ?: return
        calculationsJob?.cancel()
        calculationsJob = viewModelScope.launch {
            contributionRepo.observeGroupContributions(groupId).collectLatest { result ->
                result.onSuccess { allContribs ->
                    val allCalculations = withContext(kotlinx.coroutines.Dispatchers.Default) {
                        val contribsByMember = allContribs.groupBy { it.memberId }
                        members.mapNotNull { member ->
                            val id = member.id ?: return@mapNotNull null
                            id to PaymentCalculator.calculateStatus(group, member, contribsByMember[id] ?: emptyList())
                        }.toMap()
                    }
                    updateState { it.copy(
                        memberCalculations = allCalculations,
                        selectedMemberCalculation = it.selectedMember?.id?.let { id -> allCalculations[id] } ?: it.selectedMemberCalculation
                    ) }
                }
            }
        }
    }

    fun selectMember(member: Member?) {
        updateState { it.copy(selectedMember = member, selectedMemberCalculation = member?.id?.let { uiState.value.memberCalculations[it] }) }
        if (member != null) loadMemberDetails(member)
    }

    private fun loadMemberDetails(member: Member) {
        val memberId = member.id ?: return
        val group = uiState.value.group ?: return
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            combine(beneficiaryRepo.observeBeneficiaries(memberId), memberDocumentRepo.observeMemberDocuments(memberId)) { bRes, dRes ->
                updateState { it.copy(selectedMemberBeneficiaries = bRes.getOrDefault(emptyList()), selectedMemberDocuments = dRes.getOrDefault(emptyList()), isLoading = false) }
            }.collect()
        }
        checkLoanEligibility(member, group)
    }

    private fun checkLoanEligibility(member: Member, group: Group) {
        viewModelScope.launch {
            val result = validateLoanEligibilityUseCase(member, group)
            when (result) {
                is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible -> updateState { it.copy(isEligibleForLoan = true, loanIneligibilityReason = null) }
                is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible -> updateState { it.copy(isEligibleForLoan = false, loanIneligibilityReason = result.reason) }
            }
        }
    }

    fun updateMemberStatus(memberId: String, status: MemberStatus) {
        viewModelScope.launch { updateMemberStatusUseCase(memberId, status).onSuccess { updateState { it.copy(successMessage = "Status updated") } } }
    }
}
