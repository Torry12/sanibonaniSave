package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.ProcessPayoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * UI state for the Platform Admin portal.
 * Tracks analytics, group management, payouts, and global settings.
 * Updated via StateFlow for reactive UI.
 */
data class PlatformAdminUiState(
    val analytics: PlatformAnalytics = PlatformAnalytics(),
    val groups: List<Group> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,
    val searchQuery: String = "",
    // Global Settings
    val memberCharge: String = "10.0",
    val registrationFee: String = "700.0",
    // Group Management
    val selectedGroupMetrics: ActuarialMetrics? = null,
    val isSuspending: Boolean = false,
    // Payouts
    val payouts: List<PayoutRequest> = emptyList(),
    val isProcessingPayout: Boolean = false,
    // Impersonation
    val impersonationGroupId: String? = null,
    val impersonationMembers: List<Member> = emptyList(),
    val isLoadingImpersonationMembers: Boolean = false,

    // Burial Society Claims
    val escalatedClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    val isProcessingClaim: Boolean = false,

    // Loan verification + behavior analysis
    val loanRequestsByGroup: Map<String, List<Loan>> = emptyMap(),
    val loanMemberNames: Map<String, String> = emptyMap(),
    val memberBehaviorInsights: List<MemberBehaviorInsight> = emptyList(),
    val filteredMemberBehaviorInsights: List<MemberBehaviorInsight> = emptyList(),
    val selectedRiskFilter: String = "All",
    val isLoadingLoanRequests: Boolean = false,
    val isProcessingLoanRequest: Boolean = false,

    // Messaging & Connectivity
    val broadcastMessage: String = "",
    val isBroadcasting: Boolean = false,
    val broadcastSuccess: Boolean = false,
    val whatsAppTestPhone: String = "",
    val whatsAppTestMessage: String = "SanibonaniSave platform WhatsApp smoke test.",
    val isSendingWhatsAppTest: Boolean = false,
    val whatsAppTestResult: String? = null,
    val auditLogs: List<AuditLog> = emptyList(),
    val isLoadingAuditLogs: Boolean = false,
    val platformLedger: List<LedgerEntry> = emptyList()
)

/**
 * ViewModel for the Platform Admin portal.
 * Handles platform-wide analytics, group management, payout processing, and global fee settings.
 * Uses StateFlow for state, Hilt for DI, and robust error handling.
 */
@HiltViewModel
class PlatformAdminViewModel @Inject constructor(
    private val platformRepo: PlatformRepository,
    private val payoutRepo: PayoutRepository,
    private val loanRepo: LoanRepository,
    private val processPayoutUseCase: ProcessPayoutUseCase,
    private val claimRepo: BeneficiaryClaimRepository,
    private val memberRepo: MemberRepository,
    private val notifRepo: NotificationRepository,
    private val supabaseRepo: SupabaseRepository,
    private val platformConfigRepository: PlatformConfigRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlatformAdminUiState())
    val state: StateFlow<PlatformAdminUiState> = _state.asStateFlow()
    private var lastLoadedMemberCharge: Double = 10.0
    private var lastLoadedRegistrationFee: Double = 700.0

    init {
        loadData()
        loadSettings()
        observeEscalatedClaims()
    }

    private fun observeEscalatedClaims() {
        claimRepo.observeEscalatedClaims().onEach { result ->
            result.onSuccess { claims ->
                _state.update { it.copy(escalatedClaims = claims) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }.launchIn(viewModelScope)
    }

    fun loadData() {
        viewModelScope.launch {
            AppAnalytics.track(AnalyticsTaxonomy.Events.PLATFORM_DASHBOARD_LOAD_STARTED)
            _state.update { it.copy(isLoading = true, error = null) }
            
            // Run independent fetches in parallel for efficiency
            val analyticsDeferred = async { platformRepo.getPlatformAnalytics() }
            val groupsDeferred = async { platformRepo.getAllGroups() }
            val paymentsDeferred = async { platformRepo.getPlatformPayments() }
            val payoutsDeferred = async { payoutRepo.getPendingPayouts() }
            val auditDeferred = async { platformRepo.getAuditLogs(20) }
            val ledgerDeferred = async { platformRepo.getPlatformLedger() }

            val analyticsResult = analyticsDeferred.await()
            val groupsResult = groupsDeferred.await()
            val paymentsResult = paymentsDeferred.await()
            val payoutsResult = payoutsDeferred.await()
            val auditResult = auditDeferred.await()
            val ledgerResult = ledgerDeferred.await()

            if (analyticsResult.isSuccess && groupsResult.isSuccess) {
                _state.update { it.copy(
                    analytics = analyticsResult.getOrThrow(),
                    groups = groupsResult.getOrThrow(),
                    payments = paymentsResult.getOrDefault(emptyList()),
                    payouts = payoutsResult.getOrDefault(emptyList()),
                    auditLogs = auditResult.getOrDefault(emptyList()),
                    platformLedger = ledgerResult.getOrDefault(emptyList()),
                    isLoading = false
                ) }
                loadLoanRequests(groupsResult.getOrThrow())
                AppAnalytics.track(AnalyticsTaxonomy.Events.PLATFORM_DASHBOARD_LOAD_SUCCESS)
            } else {
                val error = (analyticsResult.exceptionOrNull() ?: groupsResult.exceptionOrNull())
                    ?.toUserMessage()
                    ?: "Unable to load platform data. Please try again."
                _state.update { it.copy(isLoading = false, error = error) }
                AppAnalytics.track(
                    AnalyticsTaxonomy.Events.PLATFORM_DASHBOARD_LOAD_FAILURE,
                    mapOf(AnalyticsTaxonomy.Params.ERROR_TYPE to "load_failed")
                )
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            platformRepo.getPlatformSettings()
                .onSuccess { settings ->
                    val mCharge = settings["monthly_member_fee"] ?: settings["monthly_per_member"] ?: 10.0
                    val rFee = settings["registration_fee"] ?: 700.0

                    lastLoadedMemberCharge = mCharge
                    lastLoadedRegistrationFee = rFee

                    platformConfigRepository.update(mCharge, rFee)

                    _state.update { it.copy(
                        memberCharge = String.format(Locale.US, "%.2f", mCharge),
                        registrationFee = String.format(Locale.US, "%.2f", rFee)
                    ) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    private fun updateGroupSuspensionState(groupId: String, isSuspended: Boolean, feeStatus: AdminFeeState) {
        _state.update { state ->
            state.copy(
                groups = state.groups.map { group ->
                    if (group.id == groupId) {
                        group.copy(
                            isPlatformSuspended = isSuspended,
                            feeStatus = feeStatus
                        )
                    } else {
                        group
                    }
                }
            )
        }
    }

    fun unsuspendGroup(groupId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            AppAnalytics.track(
                AnalyticsTaxonomy.Events.PLATFORM_GROUP_UNSUSPEND_REQUESTED,
                mapOf(AnalyticsTaxonomy.Params.GROUP_ID to groupId)
            )
            platformRepo.unsuspendGroup(groupId)
                .onSuccess {
                    updateGroupSuspensionState(
                        groupId = groupId,
                        isSuspended = false,
                        feeStatus = AdminFeeState.PAID
                    )
                    _state.update { it.copy(isSaving = false, error = null) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_GROUP_UNSUSPEND_SUCCESS,
                        mapOf(AnalyticsTaxonomy.Params.GROUP_ID to groupId)
                    )
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_GROUP_UNSUSPEND_FAILURE,
                        mapOf(
                            AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                            AnalyticsTaxonomy.Params.ERROR_TYPE to "repo"
                        )
                    )
                }
        }
    }

    fun suspendGroup(groupId: String, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSuspending = true) }
            AppAnalytics.track(
                AnalyticsTaxonomy.Events.PLATFORM_GROUP_SUSPEND_REQUESTED,
                mapOf(AnalyticsTaxonomy.Params.GROUP_ID to groupId)
            )
            platformRepo.suspendGroup(groupId, reason)
                .onSuccess {
                    updateGroupSuspensionState(
                        groupId = groupId,
                        isSuspended = true,
                        feeStatus = AdminFeeState.SUSPENDED
                    )
                    _state.update { it.copy(isSuspending = false, error = null) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_GROUP_SUSPEND_SUCCESS,
                        mapOf(AnalyticsTaxonomy.Params.GROUP_ID to groupId)
                    )
                }
                .onFailure { e ->
                    _state.update { it.copy(isSuspending = false, error = e.toUserMessage()) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_GROUP_SUSPEND_FAILURE,
                        mapOf(
                            AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                            AnalyticsTaxonomy.Params.ERROR_TYPE to "repo"
                        )
                    )
                }
        }
    }

    fun fetchGroupMetrics(groupId: String) {
        viewModelScope.launch {
            platformRepo.getGroupMetrics(groupId).onSuccess { metrics ->
                _state.update { it.copy(selectedGroupMetrics = metrics) }
            }
        }
    }

    fun updateMemberCharge(value: String) {
        _state.update { it.copy(memberCharge = value) }
    }

    fun updateRegistrationFee(value: String) {
        _state.update { it.copy(registrationFee = value) }
    }

    fun saveGlobalFees() {
        viewModelScope.launch {
            val charge = state.value.memberCharge.toDoubleOrNull()
            if (charge == null) {
                _state.update { it.copy(error = "Please enter a valid monthly charge.") }
                return@launch
            }
            val regFee = state.value.registrationFee.toDoubleOrNull()
            if (regFee == null) {
                _state.update { it.copy(error = "Please enter a valid registration fee.") }
                return@launch
            }

            _state.update { it.copy(isSaving = true, saveSuccess = false) }
            
            platformRepo.updateGlobalFees(charge, regFee)
                .onSuccess {
                    platformConfigRepository.update(charge, regFee)

                    val changedSettings = mutableListOf<String>()
                    if (kotlin.math.abs(charge - lastLoadedMemberCharge) > 0.0001) {
                        changedSettings += "Monthly member fee: R${"%.2f".format(lastLoadedMemberCharge)} -> R${"%.2f".format(charge)}"
                    }
                    if (kotlin.math.abs(regFee - lastLoadedRegistrationFee) > 0.0001) {
                        changedSettings += "Registration fee: R${"%.2f".format(lastLoadedRegistrationFee)} -> R${"%.2f".format(regFee)}"
                    }

                    var warningMessage: String? = null
                    if (changedSettings.isNotEmpty()) {
                        val message = "Platform fee settings updated. ${changedSettings.joinToString(separator = "; ")}."
                        platformRepo.broadcastPlatformMessage(message)
                            .onFailure { e -> warningMessage = "Fees saved, but broadcast failed: ${e.toUserMessage()}" }
                    }

                    lastLoadedMemberCharge = charge
                    lastLoadedRegistrationFee = regFee

                    _state.update { it.copy(isSaving = false, saveSuccess = true, error = warningMessage) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
                }
        }
    }

    fun setTab(index: Int) {
        _state.update { it.copy(selectedTab = index, saveSuccess = false, error = null) }
        if (index == 2 && _state.value.loanRequestsByGroup.isEmpty()) {
            refreshLoanRequests()
        }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setRiskFilter(riskFilter: String) {
        _state.update { current ->
            current.copy(
                selectedRiskFilter = riskFilter,
                filteredMemberBehaviorInsights = applyRiskFilter(current.memberBehaviorInsights, riskFilter)
            )
        }
    }

    fun refreshLoanRequests() {
        viewModelScope.launch {
            loadLoanRequests(_state.value.groups)
        }
    }

    fun approveLoanRequest(loan: Loan) {
        val loanId = loan.id
        if (loanId.isNullOrBlank()) {
            _state.update { it.copy(error = "This loan request is missing an identifier.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isProcessingLoanRequest = true, error = null) }
            loanRepo.approveLoan(loanId)
                .onSuccess {
                    logAudit(
                        action = "PLATFORM_APPROVE_LOAN_REQUEST",
                        targetMemberId = loan.memberId,
                        targetGroupId = loan.groupId,
                        details = mapOf("loanId" to loanId, "amount" to loan.amount)
                    )
                    _state.update { it.copy(isProcessingLoanRequest = false, saveSuccess = true) }
                    loadLoanRequests(_state.value.groups)
                }
                .onFailure { e ->
                    _state.update { it.copy(isProcessingLoanRequest = false, error = e.toUserMessage()) }
                }
        }
    }

    fun rejectLoanRequest(loan: Loan, reason: String) {
        if (reason.isBlank()) {
            _state.update { it.copy(error = "Please provide a reason before rejecting this loan request.") }
            return
        }
        val loanId = loan.id
        if (loanId.isNullOrBlank()) {
            _state.update { it.copy(error = "This loan request is missing an identifier.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isProcessingLoanRequest = true, error = null) }
            loanRepo.rejectLoan(loanId, reason)
                .onSuccess {
                    logAudit(
                        action = "PLATFORM_REJECT_LOAN_REQUEST",
                        targetMemberId = loan.memberId,
                        targetGroupId = loan.groupId,
                        details = mapOf("loanId" to loanId, "reason" to reason)
                    )
                    _state.update { it.copy(isProcessingLoanRequest = false, saveSuccess = true) }
                    loadLoanRequests(_state.value.groups)
                }
                .onFailure { e ->
                    _state.update { it.copy(isProcessingLoanRequest = false, error = e.toUserMessage()) }
                }
        }
    }

    private suspend fun loadLoanRequests(groups: List<Group>) {
        if (groups.isEmpty()) {
            _state.update {
                it.copy(
                    loanRequestsByGroup = emptyMap(),
                    loanMemberNames = emptyMap(),
                    memberBehaviorInsights = emptyList(),
                    filteredMemberBehaviorInsights = emptyList(),
                    isLoadingLoanRequests = false
                )
            }
            return
        }

        _state.update { it.copy(isLoadingLoanRequests = true) }

        val grouped = groups.mapNotNull { group ->
            val groupId = group.id?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val loans = runCatching {
                loanRepo.getGroupLoans(groupId)
                    .first()
                    .getOrElse { emptyList() }
                    .filter { loan ->
                        loan.status == LoanStatus.PENDING ||
                            loan.status == LoanStatus.APPROVED ||
                            loan.status == LoanStatus.ACTIVE ||
                            loan.status == LoanStatus.PARTIALLY_PAID ||
                            loan.status == LoanStatus.OVERDUE
                    }
                    .sortedByDescending { it.createdAt ?: "" }
            }.getOrElse { emptyList() }

            if (loans.isEmpty()) null else groupId to loans
        }.toMap()

        val memberNameById = grouped.keys
            .flatMap { groupId ->
                runCatching {
                    memberRepo.syncGroupMembers(groupId).getOrElse { emptyList() }
                }.getOrElse { emptyList() }
            }
            .mapNotNull { member ->
                val memberId = member.id ?: return@mapNotNull null
                memberId to member.fullName
            }
            .toMap()

        val allLoans = grouped.values.flatten()
        val localInsights = allLoans
            .groupBy { it.memberId }
            .map { (memberId, loans) ->
                val totalRequests = loans.size
                val pending = loans.count { it.status == LoanStatus.PENDING }
                val overdue = loans.count { it.status == LoanStatus.OVERDUE }
                val completed = loans.count { it.status == LoanStatus.COMPLETED }
                val totalRequested = loans.sumOf { it.amount }
                val outstanding = loans.sumOf { it.balanceRemaining }
                val completion = if (totalRequests == 0) 0.0 else completed.toDouble() / totalRequests.toDouble()
                val riskBand = when {
                    overdue > 0 -> "High"
                    pending >= 2 || outstanding > 20000.0 -> "Elevated"
                    completion >= 0.5 -> "Stable"
                    else -> "Watch"
                }

                MemberBehaviorInsight(
                    memberId = memberId,
                    memberName = memberNameById[memberId] ?: "Member ${memberId.take(8)}",
                    groupId = loans.firstOrNull()?.groupId.orEmpty(),
                    totalLoanRequests = totalRequests,
                    pendingRequests = pending,
                    overdueLoans = overdue,
                    totalRequestedAmount = totalRequested,
                    outstandingAmount = outstanding,
                    completionRatio = completion,
                    riskBand = riskBand
                )
            }
            .sortedWith(
                compareByDescending<MemberBehaviorInsight> { it.overdueLoans }
                    .thenByDescending { it.outstandingAmount }
                    .thenByDescending { it.pendingRequests }
            )

        val serverInsights = platformRepo.getMemberBehaviorInsights().getOrElse { emptyList() }
        val insights = if (serverInsights.isNotEmpty()) {
            serverInsights
        } else {
            localInsights
        }
        val selectedRiskFilter = _state.value.selectedRiskFilter

        _state.update {
            it.copy(
                loanRequestsByGroup = grouped,
                loanMemberNames = memberNameById,
                memberBehaviorInsights = insights,
                filteredMemberBehaviorInsights = applyRiskFilter(insights, selectedRiskFilter),
                isLoadingLoanRequests = false
            )
        }
    }

    private fun applyRiskFilter(
        insights: List<MemberBehaviorInsight>,
        riskFilter: String
    ): List<MemberBehaviorInsight> {
        if (riskFilter == "All") {
            return insights
        }
        return insights.filter { it.riskBand.equals(riskFilter, ignoreCase = true) }
    }

    fun selectImpersonationGroup(groupId: String, forceReload: Boolean = false) {
        if (groupId.isBlank()) {
            _state.update {
                it.copy(
                    impersonationGroupId = null,
                    impersonationMembers = emptyList(),
                    isLoadingImpersonationMembers = false
                )
            }
            return
        }

        if (!forceReload && _state.value.impersonationGroupId == groupId && _state.value.impersonationMembers.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    impersonationGroupId = groupId,
                    isLoadingImpersonationMembers = true,
                    impersonationMembers = emptyList(),
                    error = null
                )
            }

            memberRepo.syncGroupMembers(groupId)
                .onSuccess { members ->
                    _state.update {
                        it.copy(
                            impersonationMembers = members,
                            isLoadingImpersonationMembers = false
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            impersonationMembers = emptyList(),
                            isLoadingImpersonationMembers = false,
                            error = e.toUserMessage()
                        )
                    }
                }
        }
    }

    fun refreshMaintenanceData() {
        _state.update {
            it.copy(
                saveSuccess = false,
                error = null,
                impersonationGroupId = null,
                impersonationMembers = emptyList(),
                isLoadingImpersonationMembers = false,
                isLoadingAuditLogs = true
            )
        }
        viewModelScope.launch {
            val auditResult = platformRepo.getAuditLogs(50)
            _state.update { it.copy(
                auditLogs = auditResult.getOrDefault(emptyList()),
                isLoadingAuditLogs = false
            ) }
        }
        loadData()
        loadSettings()
    }

    fun resetLocalData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, saveSuccess = false, error = null) }
            runCatching { supabaseRepo.resetLocalCache() }
                .onSuccess {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            saveSuccess = true,
                            error = null,
                            impersonationGroupId = null,
                            impersonationMembers = emptyList(),
                            isLoadingImpersonationMembers = false
                        )
                    }
                    loadData()
                    loadSettings()
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, saveSuccess = false, error = e.toUserMessage()) }
                }
        }
    }

    fun dismissSuccess() {
        _state.update { it.copy(saveSuccess = false) }
    }

    fun approvePayout(payoutId: String, groupId: String) {
        processPayout(payoutId, groupId, PayoutStatus.PROCESSING)
    }

    fun completePayout(payoutId: String, groupId: String) {
        processPayout(payoutId, groupId, PayoutStatus.COMPLETED)
    }

    fun rejectPayout(payoutId: String, groupId: String) {
        processPayout(payoutId, groupId, PayoutStatus.FAILED)
    }

    private fun processPayout(payoutId: String, groupId: String, status: PayoutStatus) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessingPayout = true) }
            AppAnalytics.track(
                AnalyticsTaxonomy.Events.PLATFORM_PAYOUT_TRANSITION_REQUESTED,
                mapOf(
                    AnalyticsTaxonomy.Params.PAYOUT_ID to payoutId,
                    AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                    AnalyticsTaxonomy.Params.STATUS to status.name.lowercase()
                )
            )
            processPayoutUseCase(payoutId, groupId, status)
                .onSuccess {
                    _state.update { it.copy(isProcessingPayout = false) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_PAYOUT_TRANSITION_SUCCESS,
                        mapOf(
                            AnalyticsTaxonomy.Params.PAYOUT_ID to payoutId,
                            AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                            AnalyticsTaxonomy.Params.STATUS to status.name.lowercase()
                        )
                    )
                    loadData()
                }
                .onFailure { e ->
                    _state.update { it.copy(isProcessingPayout = false, error = e.toUserMessage()) }
                    AppAnalytics.track(
                        AnalyticsTaxonomy.Events.PLATFORM_PAYOUT_TRANSITION_FAILURE,
                        mapOf(
                            AnalyticsTaxonomy.Params.PAYOUT_ID to payoutId,
                            AnalyticsTaxonomy.Params.GROUP_ID to groupId,
                            AnalyticsTaxonomy.Params.STATUS to status.name.lowercase(),
                            AnalyticsTaxonomy.Params.ERROR_TYPE to "usecase"
                        )
                    )
                }
        }
    }

    fun approveBurialClaim(claimId: String, notes: String) {
        updateClaimStatus(claimId, BeneficiaryClaimStatus.APPROVED, notes)
    }

    fun payBurialClaim(claimId: String, notes: String) {
        updateClaimStatus(claimId, BeneficiaryClaimStatus.PAID, notes)
    }

    fun rejectBurialClaim(claimId: String, reason: String) {
        updateClaimStatus(claimId, BeneficiaryClaimStatus.REJECTED, reason)
    }

    private fun updateClaimStatus(claimId: String, status: BeneficiaryClaimStatus, notes: String) {
        val adminId = supabaseRepo.currentUserId
        if (adminId == null) {
            _state.update { it.copy(isProcessingClaim = false, error = "Your session has expired. Please log in again.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isProcessingClaim = true) }
            claimRepo.updateClaimStatus(
                claimId = claimId,
                status = status,
                reviewedBy = adminId,
                adminNotes = if (status != BeneficiaryClaimStatus.REJECTED) notes else null,
                rejectionReason = if (status == BeneficiaryClaimStatus.REJECTED) notes else null
            ).onSuccess {
                _state.update { it.copy(isProcessingClaim = false, saveSuccess = true) }
            }.onFailure { e ->
                _state.update { it.copy(isProcessingClaim = false, error = e.toUserMessage()) }
            }
        }
    }

    fun logAudit(action: String, targetMemberId: String? = null, targetGroupId: String? = null, details: Map<String, Any>? = null) {
        viewModelScope.launch {
            val actorId = supabaseRepo.currentUserId ?: "SYSTEM"
            val auditLog = AuditLog(
                actorId = actorId,
                targetMemberId = targetMemberId,
                targetGroupId = targetGroupId,
                action = action,
                details = details?.mapValues { it.value.toString() }
            )
            platformRepo.logAuditEvent(auditLog)
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                }
        }
    }

    fun updateBroadcastMessage(text: String) {
        _state.update { it.copy(broadcastMessage = text) }
    }

    fun updateWhatsAppTestPhone(phone: String) {
        _state.update {
            it.copy(
                whatsAppTestPhone = phone,
                whatsAppTestResult = null,
                error = null
            )
        }
    }

    fun updateWhatsAppTestMessage(message: String) {
        _state.update {
            it.copy(
                whatsAppTestMessage = message,
                whatsAppTestResult = null,
                error = null
            )
        }
    }

    fun broadcastMessage() {
        val message = _state.value.broadcastMessage.trim()
        if (message.isBlank()) {
            _state.update { it.copy(error = "Please enter a message to broadcast.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isBroadcasting = true, error = null, broadcastSuccess = false) }
            platformRepo.broadcastPlatformMessage(message)
                .onSuccess {
                    _state.update { it.copy(isBroadcasting = false, broadcastSuccess = true, broadcastMessage = "") }
                    logAudit("PLATFORM_BROADCAST", details = mapOf("message" to message))
                }
                .onFailure { e ->
                    _state.update { it.copy(isBroadcasting = false, error = e.toUserMessage()) }
                }
        }
    }

    fun sendWhatsAppTestToAdmin(groupId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSendingWhatsAppTest = true, whatsAppTestResult = null, error = null) }
            
            // Send a test message specifically to the group admin
            val message = "PLATFORM TEST: WhatsApp connectivity check from Sanibonani Platform Admin at ${System.currentTimeMillis()}"
            notifRepo.sendNotification(AppNotification(
                groupId = groupId,
                memberId = null, // Broadcast to group admin(s) via NotificationRepositoryImpl logic
                message = message,
                triggerEvent = NotifEvent.CUSTOM,
                channel = NotifChannel.WHATSAPP
            )).onSuccess {
                _state.update { it.copy(isSendingWhatsAppTest = false, whatsAppTestResult = "Test message sent to group admin.") }
            }.onFailure { e ->
                _state.update { it.copy(isSendingWhatsAppTest = false, whatsAppTestResult = e.toUserMessage()) }
            }
        }
    }

    fun sendDirectWhatsAppTest() {
        val rawPhone = _state.value.whatsAppTestPhone
        val digitsOnly = rawPhone.filter(Char::isDigit)
        if (!isValidSouthAfricanWhatsAppNumber(digitsOnly)) {
            _state.update {
                it.copy(error = "Enter a valid South African WhatsApp number, for example 0713459563 or 27713459563.")
            }
            return
        }

        val message = _state.value.whatsAppTestMessage.trim().ifBlank {
            "SanibonaniSave platform WhatsApp smoke test at ${System.currentTimeMillis()}"
        }

        viewModelScope.launch {
            _state.update { it.copy(isSendingWhatsAppTest = true, whatsAppTestResult = null, error = null) }
            notifRepo.sendDirectWhatsAppMessage(phone = digitsOnly, message = message)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isSendingWhatsAppTest = false,
                            whatsAppTestResult = "WhatsApp test sent to $digitsOnly.",
                            whatsAppTestPhone = digitsOnly,
                            whatsAppTestMessage = message
                        )
                    }
                }
                .onFailure { e ->
                    val userMessage = when (e) {
                        is WhatsAppSendException -> e.toUserMessage()
                        else -> e.toUserMessage().takeIf {
                            it.isNotBlank() && !it.equals("An error occurred", ignoreCase = true)
                        } ?: "Failed to send WhatsApp test. Please retry and check edge function logs."
                    }
                    _state.update {
                        it.copy(
                            isSendingWhatsAppTest = false,
                            whatsAppTestResult = userMessage,
                            error = null
                        )
                    }
                }
        }
    }

    private fun isValidSouthAfricanWhatsAppNumber(phone: String): Boolean {
        return (phone.length == 10 && phone.startsWith("0")) ||
            (phone.length == 11 && phone.startsWith("27"))
    }

    fun clearBroadcastSuccess() {
        _state.update { it.copy(broadcastSuccess = false) }
    }
}
