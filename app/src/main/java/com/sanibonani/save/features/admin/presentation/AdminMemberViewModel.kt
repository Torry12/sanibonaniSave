package com.sanibonani.save.features.admin.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.*
import com.sanibonani.save.domain.repository.credit.*
import com.sanibonani.save.domain.repository.disbursement.*
import com.sanibonani.save.domain.repository.dynamics.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.repository.treasury.*
import com.sanibonani.save.domain.usecase.dynamics.UpdateMemberStatusUseCase
import com.sanibonani.save.domain.usecase.disbursement.VerifyMemberDocumentUseCase
import com.sanibonani.save.domain.usecase.disbursement.VerifyRelationalDocumentUseCase
import com.sanibonani.save.domain.usecase.dynamics.SendNotificationUseCase
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.UIEvent
import com.sanibonani.save.features.admin.presentation.state.AdminMemberUiState
import com.sanibonani.save.domain.utils.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
    private val exportRepo: ExportRepository,
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<AdminMemberUiState>(AdminMemberUiState()) {

    val state: StateFlow<AdminMemberUiState> = uiState

    init {
        observeContext()
    }

    private fun observeContext() {
        launchJob("context_observation") {
            observeGroupContext(
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

                if (context.isFullyHydrated && context.group != null && context.members.isNotEmpty()) {
                    refreshAllMemberCalculations(context.group, context.members)
                }
            }.join()
        }
    }

    private fun refreshAllMemberCalculations(group: Group, members: List<Member>) {
        val groupId = group.id ?: return
        launchJob("calculations_refresh") {
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
        launchJob("member_details_loading") {
            combine(beneficiaryRepo.observeBeneficiaries(memberId), memberDocumentRepo.observeMemberDocuments(memberId)) { bRes, dRes ->
                updateState { it.copy(selectedMemberBeneficiaries = bRes.getOrDefault(emptyList()), selectedMemberDocuments = dRes.getOrDefault(emptyList()), isLoading = false) }
            }.collect()
        }
        checkLoanEligibility(member, group)
    }

    private fun checkLoanEligibility(member: Member, group: Group) {
        launchJob("loan_eligibility_check") {
            val result = validateLoanEligibilityUseCase(member, group)
            when (result) {
                is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible -> updateState { it.copy(isEligibleForLoan = true, loanIneligibilityReason = null) }
                is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible -> updateState { it.copy(isEligibleForLoan = false, loanIneligibilityReason = result.reason) }
            }
        }
    }

    fun updateMemberStatus(memberId: String, status: MemberStatus) {
        launchJob("member_status_update") {
            updateMemberStatusUseCase(memberId, status).onSuccess {
                updateState { it.copy(successMessage = "Status updated") }
                adminContextCacheService.warmUpGroup(uiState.value.group?.id ?: "", force = true)
            }.onFailure { handleError(it) }
        }
    }

    fun verifyDocument(memberId: String, docIndex: Int, approve: Boolean) {
        viewModelScope.launch { verifyMemberDocumentUseCase(memberId, docIndex, approve).onSuccess { updateState { it.copy(successMessage = "Document verified") } } }
    }

    fun verifyRelationalDocument(docId: String, approve: Boolean) {
        val doc = uiState.value.selectedMemberDocuments.find { it.id == docId } ?: return
        viewModelScope.launch { verifyRelationalDocumentUseCase(doc, approve).onSuccess { updateState { it.copy(successMessage = "Document verified") } } }
    }

    fun sendMessage(message: String, memberId: String) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        viewModelScope.launch { sendNotificationUseCase(groupId, message, memberId, uiState.value.selectedNotifChannel).onSuccess { updateState { it.copy(messageSentSuccess = true, messageText = "") } } }
    }

    fun updateMessageText(text: String) { updateState { it.copy(messageText = text) } }

    fun updateNotifChannel(channel: NotifChannel) { updateState { it.copy(selectedNotifChannel = channel) } }

    fun exportMemberStatement(member: Member, pdf: Boolean) {
        val group = uiState.value.group ?: return
        viewModelScope.launch {
            updateState { it.copy(isExporting = true) }
            contributionRepo.observeMemberContributions(member.id!!, group.id!!).first().onSuccess { list ->
                val res = if (pdf) exportRepo.exportContributionsToPdf(group, member, list) else exportRepo.exportContributionsToCsv(group, member, list)
                res.onSuccess { file -> updateState { it.copy(isExporting = false, exportFile = file) }; sendEvent(UIEvent.OpenFile(file, if (pdf) "application/pdf" else "text/csv", "Statement")) }
            }
        }
    }

    fun getDownloadParams(url: String): Map<String, String> = supabaseRepo.accessToken?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()

    fun downloadMemberDocument(url: String, label: String): Pair<String, Map<String, String>> = url to getDownloadParams(url)

    fun updateBankingDetails(memberId: String, bank: String, acc: String, branch: String) {
        viewModelScope.launch { memberRepo.updateMemberBankingDetails(memberId, bank, acc, branch).onSuccess { updateState { it.copy(successMessage = "Details updated") } } }
    }

    fun clearError() { updateState { it.copy(error = null) } }
}
