package com.sanibonani.save.viewmodel.equity

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.disbursement.BeneficiaryClaimRepository
import com.sanibonani.save.domain.service.MemberActuarialService
import com.sanibonani.save.domain.usecase.disbursement.ProcessDisbursementUseCase
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.MemberUiState
import com.sanibonani.save.viewmodel.state.UIEvent
import com.sanibonani.save.domain.utils.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberOverviewViewModel @Inject constructor(
    private val memberContextCacheService: MemberGroupContextCacheService,
    private val memberRepo: MemberRepository,
    private val contributionRepo: ContributionRepository,
    private val groupRepo: GroupRepository,
    private val ledgerRepo: LedgerRepository,
    private val payoutRepo: PayoutRepository,
    private val networkService: com.sanibonani.save.domain.service.NetworkConnectivityService,
    private val exportRepo: ExportRepository,
    private val actuarialService: MemberActuarialService,
    private val verificationService: com.sanibonani.save.domain.service.DataVerificationService,
    private val supabaseRepo: SupabaseRepository,
    private val devSettings: com.sanibonani.save.domain.utils.DevSettings,
    private val loanRepo: LoanRepository,
    private val auditRepo: AuditRepository,
    private val notifRepo: NotificationRepository,
    private val claimRepo: BeneficiaryClaimRepository,
    private val getGroupBusinessInsightsUseCase: GetGroupBusinessInsightsUseCase
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    private var contextObservationJob: Job? = null
    private var liveDataObservationJob: Job? = null
    private var impersonationJob: Job? = null
    private var selectGroupJob: Job? = null

    init {
        observeContext()
        loadMemberships()
        observeNetwork()
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkService.isOnline.collect { online -> updateState { it.copy(isSystemOnline = online) } }
        }
        viewModelScope.launch {
            networkService.isAirplaneModeActive.collect { active -> updateState { it.copy(isAirplaneMode = active) } }
        }
    }

    private fun observeContext() {
        contextObservationJob?.cancel()
        contextObservationJob = observeGroupContext(
            groupIdFlow = uiState.map { it.selectedGroupId },
            contextSource = memberContextCacheService.contexts
        ) { context ->
            if (context == null) return@observeGroupContext

            val memberId = context.member?.id
            val groupId = context.group?.id

            updateState { it.copy(
                member = context.member,
                group = context.group,
                contributions = context.contributions,
                beneficiaries = context.beneficiaries,
                loans = context.loans,
                loanRepayments = context.loanRepayments,
                paymentCalculation = context.calculation,
                notifications = context.notifications,
                messages = context.messages,
                isLoading = !context.isFullyHydrated,
                cacheLastSyncByGroup = it.cacheLastSyncByGroup + (groupId.orEmpty() to context.lastUpdatedMillis),
            ) }

            if (context.isFullyHydrated && memberId != null && groupId != null) {
                startLiveObservation(memberId, groupId)
                loadInsights(context.group!!)
            }
        }
    }

    private fun loadInsights(group: Group) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            memberRepo.getGroupMembers(group.id!!).collectLatest { res ->
                res.onSuccess { members ->
                    val insights = getGroupBusinessInsightsUseCase(group, members)
                    updateState { it.copy(businessInsight = insights, allMembers = members) }
                }
            }
        }
    }

    private fun startLiveObservation(memberId: String, groupId: String) {
        liveDataObservationJob?.cancel()
        liveDataObservationJob = viewModelScope.launch {
            launch {
                groupRepo.observeGroup(groupId).collect { res ->
                    res.onSuccess { g -> updateState { it.copy(group = g) } }
                }
            }
            launch {
                memberRepo.observeMemberById(memberId).collect { res ->
                    res.onSuccess { m -> if (m != null) updateState { it.copy(member = m) } }
                }
            }
            launch {
                contributionRepo.observeMemberContributions(memberId, groupId).collect { res ->
                    res.onSuccess { list -> updateState { it.copy(contributions = list) } }
                }
            }
            launch {
                loanRepo.getMemberLoans(memberId).collect { res ->
                    res.onSuccess { list -> updateState { it.copy(loans = list) } }
                }
            }
            launch {
                ledgerRepo.observeMemberLedger(memberId).collect { res ->
                    res.onSuccess { list -> updateState { it.copy(memberLedger = list) } }
                }
            }
        }
    }

    private fun loadMemberships() {
        val userId = supabaseRepo.currentUserId ?: return
        memberRepo.observeMemberships(userId).observeInto(
            onSuccess = { memberships ->
                updateState { it.copy(memberships = memberships) }
                if (uiState.value.selectedGroupId == null && memberships.isNotEmpty()) {
                    selectGroup(memberships.first().groupId)
                }
            }
        )
    }

    fun selectGroup(groupId: String) {
        updateState { it.copy(selectedGroupId = groupId, isLoading = true) }
        memberContextCacheService.warmUpForUser(supabaseRepo.currentUserId!!)
    }

    fun beginImpersonation(memberId: String, groupId: String) {
        viewModelScope.launch {
            updateState { it.copy(isImpersonating = true, isLoading = true) }
            memberContextCacheService.impersonateMember(memberId, groupId).onSuccess { context ->
                memberContextCacheService.updateContext(groupId) { context }
            }
        }
    }

    fun clearImpersonation(onComplete: () -> Unit = {}) {
        updateState { it.copy(isImpersonating = false, member = null) }
        onComplete()
    }

    fun switchGroup(groupId: String) { selectGroup(groupId) }

    fun refreshData() {
        val userId = supabaseRepo.currentUserId ?: return
        viewModelScope.launch { memberContextCacheService.warmUpForUser(userId) }
    }

    fun selectTab(index: Int) { updateState { it.copy(selectedTab = index) } }

    fun downloadPdfStatement() {
        val member = uiState.value.member ?: return
        val group = uiState.value.group ?: return
        val ledger = uiState.value.memberLedger
        viewModelScope.launch {
            updateState { it.copy(isExporting = true) }
            exportRepo.exportLedgerToPdf(group, ledger, member).onSuccess { file ->
                updateState { it.copy(isExporting = false) }
                sendEvent(UIEvent.OpenFile(file, "application/pdf", "Statement"))
            }
        }
    }

    fun downloadGroupStatement() {
        val group = uiState.value.group ?: return
        viewModelScope.launch {
            updateState { it.copy(isExporting = true) }
            ledgerRepo.getGroupLedger(group.id!!).onSuccess { entries ->
                exportRepo.exportLedgerToPdf(group, entries).onSuccess { file ->
                    updateState { it.copy(isExporting = false) }
                    sendEvent(UIEvent.OpenFile(file, "application/pdf", "Group Ledger"))
                }
            }
        }
    }

    fun downloadGroupConstitution() {
        val group = uiState.value.group ?: return
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            exportRepo.exportGroupConstitution(group).onSuccess { file ->
                updateState { it.copy(isLoading = false) }
                sendEvent(UIEvent.OpenFile(file, "application/pdf", "Constitution"))
            }
        }
    }

    fun getDownloadParams(url: String): Map<String, String> {
        return supabaseRepo.accessToken?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
    }
}
