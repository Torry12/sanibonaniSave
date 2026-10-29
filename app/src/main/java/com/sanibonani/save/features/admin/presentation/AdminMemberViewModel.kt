package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.domain.usecase.equity.ObserveGroupContributionsUseCase
import com.sanibonani.save.domain.usecase.equity.ObserveMemberContributionsUseCase
import com.sanibonani.save.domain.usecase.auth.BuildDocumentAuthHeadersUseCase
import com.sanibonani.save.domain.usecase.member.ObserveBeneficiariesUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMemberDocumentsUseCase
import com.sanibonani.save.domain.usecase.member.UpdateBeneficiaryUseCase
import com.sanibonani.save.domain.usecase.member.UpdateMemberBankingDetailsUseCase
import com.sanibonani.save.domain.usecase.member.UpdateMemberStatusUseCase
import com.sanibonani.save.domain.usecase.member.VerifyMemberDocumentUseCase
import com.sanibonani.save.domain.usecase.member.VerifyRelationalDocumentUseCase
import com.sanibonani.save.domain.usecase.system.ExportMemberStatementUseCase
import com.sanibonani.save.domain.usecase.system.SendNotificationUseCase
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.features.admin.presentation.state.AdminMemberUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.common.validation.BeneficiaryForm
import com.sanibonani.save.service.AdminGroupContextCacheService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltViewModel
class AdminMemberViewModel @Inject constructor(
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val updateMemberBankingDetails: UpdateMemberBankingDetailsUseCase,
    private val observeGroupContributions: ObserveGroupContributionsUseCase,
    private val observeMemberContributions: ObserveMemberContributionsUseCase,
    private val observeBeneficiaries: ObserveBeneficiariesUseCase,
    private val updateBeneficiaryUseCase: UpdateBeneficiaryUseCase,
    private val observeMemberDocuments: ObserveMemberDocumentsUseCase,
    private val exportMemberStatementUseCase: ExportMemberStatementUseCase,
    private val buildDocumentAuthHeaders: BuildDocumentAuthHeadersUseCase,
    private val updateMemberStatusUseCase: UpdateMemberStatusUseCase,
    private val verifyMemberDocument: VerifyMemberDocumentUseCase,
    private val verifyRelationalDocumentUseCase: VerifyRelationalDocumentUseCase,
    private val validateLoanEligibility: ValidateLoanEligibilityUseCase,
    private val sendNotification: SendNotificationUseCase,
    private val paymentCalculator: PaymentCalculator
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

                if (context.isFullyHydrated && (context.group != null) && context.members.isNotEmpty()) {
                    refreshAllMemberCalculations(context.group, context.members)
                }
            }
        }
    }

    private fun refreshAllMemberCalculations(group: Group, members: List<Member>) {
        val groupId = group.id ?: return
        launchJob("calculations_refresh") {
            observeGroupContributions(groupId).collectLatest { result: Result<List<Contribution>> ->
                result.onSuccess { allContribs ->
                    val allCalculations = withContext(kotlinx.coroutines.Dispatchers.Default) {
                        val contribsByMember = allContribs.groupBy { it.memberId }
                        members.mapNotNull { member ->
                            val id = member.id ?: return@mapNotNull null
                            id to paymentCalculator.calculateStatus(group, member, contribsByMember[id] ?: emptyList(), emptyList())
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
        updateState { s ->
            s.copy(
                selectedMember = member,
                selectedMemberCalculation = member?.id?.let { uiState.value.memberCalculations[it] },
                messageText = "",
                messageSentSuccess = false
            )
        }
        if (member != null) loadMemberDetails(member)
    }

    private fun loadMemberDetails(member: Member) {
        val memberId = member.id ?: return
        val group = uiState.value.group ?: return
        launchJob("member_details_loading") {
            combine(observeBeneficiaries(memberId), observeMemberDocuments(memberId)) { bRes, dRes ->
                updateState { it.copy(selectedMemberBeneficiaries = bRes.getOrDefault(emptyList()), selectedMemberDocuments = dRes.getOrDefault(emptyList()), isLoading = false) }
            }.collect()
        }
        checkLoanEligibility(member, group)
    }

    private fun checkLoanEligibility(member: Member, group: Group) {
        launchJob("loan_eligibility_check") {
            when (val result = validateLoanEligibility(member, group)) {
                is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible -> updateState { it.copy(isEligibleForLoan = true, loanIneligibilityReason = null) }
                is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible -> updateState { it.copy(isEligibleForLoan = false, loanIneligibilityReason = result.reason) }
            }
        }
    }

    fun updateMemberStatus(memberId: String, status: MemberStatus) {
        launchJob("member_status_update") {
            updateMemberStatusUseCase(memberId, status).onSuccess {
                showMessage("Status updated")
                adminContextCacheService.warmUpGroup(uiState.value.group?.id ?: "", force = true)
            }.onFailure { handleError(it) }
        }
    }

    fun verifyDocument(memberId: String, docIndex: Int, approve: Boolean) {
        launchJob("verify_document") {
            verifyMemberDocument(memberId, docIndex, approve)
                .onSuccess { showMessage("Document verified") }
                .onFailure { handleError(it) }
        }
    }

    fun verifyRelationalDocument(docId: String, approve: Boolean) {
        val doc = uiState.value.selectedMemberDocuments.find { it.id == docId } ?: return
        launchJob("verify_relational_document") {
            verifyRelationalDocumentUseCase(doc, approve)
                .onSuccess { showMessage("Document verified") }
                .onFailure { handleError(it) }
        }
    }

    fun sendMessage(message: String, memberId: String) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        launchJob("send_message") {
            updateState { it.copy(isSendingMessage = true, messageSentSuccess = false) }
            try {
                sendNotification(groupId, message, memberId, uiState.value.selectedNotifChannel)
                    .onSuccess {
                        updateState { it.copy(messageSentSuccess = true, messageText = "") }
                    }
                    .onFailure { handleError(it) }
            } finally {
                updateState { it.copy(isSendingMessage = false) }
            }
        }
    }

    fun updateMessageText(text: String) { updateState { it.copy(messageText = text) } }

    fun updateNotifChannel(channel: NotifChannel) { updateState { it.copy(selectedNotifChannel = channel) } }

    fun exportMemberStatement(member: Member, pdf: Boolean) {
        val group = uiState.value.group ?: return
        val memberId = member.id ?: return
        val groupId = group.id ?: return

        launchJob("export_member_statement") {
            updateState { it.copy(isExporting = true) }
            try {
                val list = observeMemberContributions(memberId, groupId).first().getOrThrow()
                val file = exportMemberStatementUseCase(group, member, list, pdf).getOrThrow()
                updateState { it.copy(exportFile = file) }
                sendEvent(UIEvent.OpenFile(file, if (pdf) "application/pdf" else "text/csv", "Statement"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e)
            } finally {
                updateState { it.copy(isExporting = false) }
            }
        }
    }

    fun getDownloadParams(url: String): Map<String, String> = buildDocumentAuthHeaders(url)

    fun downloadMemberDocument(url: String, label: String): Pair<String, Map<String, String>> = url to getDownloadParams(url)

    fun updateBankingDetails(memberId: String, bank: String, acc: String, branch: String) {
        launchJob("update_banking_details") {
            updateMemberBankingDetails(memberId, bank, acc, branch)
                .onSuccess { showMessage("Details updated") }
                .onFailure { handleError(it) }
        }
    }

    fun updateBeneficiary(
        beneficiary: Beneficiary,
        name: String,
        idNumber: String,
        relationship: String,
        dateOfBirth: String,
        isOver65: Boolean
    ) {
        val record = when (val validation = BeneficiaryForm.validate(
            template = beneficiary,
            name = name,
            idNumber = idNumber,
            relationship = relationship,
            dateOfBirth = dateOfBirth,
            isOver65 = isOver65
        )) {
            is BeneficiaryForm.Validation.Invalid -> {
                sendEvent(UIEvent.ShowError(validation.message))
                return
            }
            is BeneficiaryForm.Validation.Valid -> validation.record
        }

        launchJob("update_beneficiary") {
            updateBeneficiaryUseCase(record)
                .onSuccess { showMessage("Beneficiary updated") }
                .onFailure { handleError(it) }
        }
    }

    fun clearError() { updateState { it.copy(error = null) } }
}
