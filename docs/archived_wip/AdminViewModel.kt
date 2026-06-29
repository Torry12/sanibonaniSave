package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.data.utils.*
import com.sanibonani.save.data.logging.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.config.FileUploadLimits
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.data.utils.toUserMessage
import javax.inject.Inject

data class AdminUiState(
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val metrics: ActuarialMetrics = ActuarialMetrics(),
    val businessInsight: GetGroupBusinessInsightsUseCase.GroupBusinessInsight = GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty,
    val feeStatus: AdminFeeState = AdminFeeState.DUE,
    val settings: GroupSettings = GroupSettings(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val selectedTab: Int = 0,
    val daysOverdue: Int = 0,
    val restoreRequested: Boolean = false,
    
    // Viability Planning
    val viabilityPlan: ViabilityPlan? = null,
    val isCalculatingViability: Boolean = false,
    val notifications: List<AppNotification> = emptyList(),
    val memberMessages: List<AppNotification> = emptyList(),
    
    // Messaging
    val messageText: String = "",
    val isSendingMessage: Boolean = false,
    val messageSentSuccess: Boolean = false,
    val isSendingWhatsAppTest: Boolean = false,
    val whatsAppTestResult: String? = null,

    // Export & Upload
    val isExporting: Boolean = false,
    val exportFile: java.io.File? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Double? = null,

    // Member Detail
    val selectedMember: Member? = null,
    val selectedMemberBeneficiaries: List<Beneficiary> = emptyList(),
    val selectedMemberDocuments: List<MemberDocument> = emptyList(),
    val selectedMemberCalculation: PaymentCalculation? = null,
    val isEligibleForLoan: Boolean = false,
    val loanIneligibilityReason: String? = null,
    val memberCalculations: Map<String, PaymentCalculation> = emptyMap(),
    
    // Beneficiary Edit
    val editingBeneficiary: Beneficiary? = null,
    val isSavingBeneficiary: Boolean = false,
    
    // Multi-group Admin
    val managedGroups: List<Group> = emptyList(),
    val currentGroupId: String? = null,
    
    // Payouts
    val payouts: List<PayoutRequest> = emptyList(),
    val isRequestingPayout: Boolean = false,
    val payoutRequestSuccess: Boolean = false,
    val payoutAmount: String = "",
    val payoutBankName: String = "",
    val payoutAccountNo: String = "",
    val payoutBranchCode: String = "",
    val successMessage: String? = null,
    val loadingMessage: String? = null,
    
    // Loans
    val groupLoans: List<Loan> = emptyList(),
    val isProcessingLoan: Boolean = false,

    // Burial Society Claims
    val burialClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    val isProcessingClaim: Boolean = false,
    val ledger: List<LedgerEntry> = emptyList(),
    val selectedLedgerEntry: LedgerEntry? = null,
    val healthScore: GroupHealthScore? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val groupRepo: GroupRepository,
    private val memberRepo: MemberRepository,
    private val beneficiaryRepo: BeneficiaryRepository,
    private val memberDocumentRepo: MemberDocumentRepository,
    private val actuarialRepo: ActuarialRepository,
    private val notifRepo: NotificationRepository,
    private val paymentRepo: PaymentRepository,
    private val payoutRepo: PayoutRepository,
    private val exportRepo: ExportRepository,
    private val loanRepo: LoanRepository,
    private val claimRepo: BeneficiaryClaimRepository,
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val verifyMemberDocumentUseCase: VerifyMemberDocumentUseCase,
    private val updateGroupSettingsUseCase: UpdateGroupSettingsUseCase,
    private val applyViabilityPlanUseCase: ApplyViabilityPlanUseCase,
    private val verifyRelationalDocumentUseCase: VerifyRelationalDocumentUseCase,
    private val getManagedGroupsUseCase: GetManagedGroupsUseCase,
    private val calculateViabilityUseCase: CalculateViabilityUseCase,
    private val updateMemberStatusUseCase: UpdateMemberStatusUseCase,
    private val sendNotificationUseCase: SendNotificationUseCase,
    private val requestPayoutUseCase: RequestPayoutUseCase,
    private val validateLoanEligibilityUseCase: ValidateLoanEligibilityUseCase,
    private val generateLoanContractUseCase: GenerateLoanContractUseCase,
    private val getGroupBusinessInsightsUseCase: GetGroupBusinessInsightsUseCase,
    private val calculateGroupHealthScoreUseCase: CalculateGroupHealthScoreUseCase,
    private val healthScoreRepo: HealthScoreRepository,
    private val ledgerRepo: LedgerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private val isActive = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(supabaseRepo.sessionFlow, isActive) { session, active ->
                session != null && active
            }.collect { shouldObserve ->
                if (shouldObserve) {
                    observeAdminData()
                } else {
                    cancelGroupJobs()
                    managedGroupsJob?.cancel()
                    managedGroupsJob = null
                }
            }
        }
    }

    fun setActive(active: Boolean) {
        isActive.value = active
    }

    private var currentObservedGroupId: String? = null
    private var managedGroupsJob: Job? = null
    private var groupObservationVersion: Long = 0L
    private var groupObservationJob: Job? = null
    private var calculationsJob: Job? = null
    private var memberBeneficiariesJob: Job? = null
    private var memberDocumentsJob: Job? = null
    private var memberCalculationsJob: Job? = null
    private var groupLoansJob: Job? = null
    private var burialClaimsJob: Job? = null
    private var ledgerJob: Job? = null
    private var healthScoreJob: Job? = null
    private var refreshMetricsJob: Job? = null
    private var businessInsightsJob: Job? = null

    /** Returns true when the current emission belongs to a superseded observation request. */
    private fun isStaleAdminObservation(groupId: String, requestVersion: Long): Boolean {
        return requestVersion != groupObservationVersion || _state.value.currentGroupId != groupId
    }

    private fun observeAdminData() {
        val userId = supabaseRepo.currentUserId ?: return
        adminContextCacheService.ensureUserSession(userId)
        managedGroupsJob?.cancel()
        managedGroupsJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // Seed initial state immediately (helps UI + unit tests), then keep it updated via realtime observation.
            // We intentionally call the use case without forcing adminOnly here and filter locally,
            // so the logic remains compatible with older stubs/mocks while still enforcing admin-only.
            getManagedGroupsUseCase(userId)
                .onSuccess { groups ->
                    val adminGroups = groups.filter { it.adminUserId == userId }
                    _state.update { it.copy(managedGroups = adminGroups) }
                    adminContextCacheService.warmManagedGroupsInBackground(userId, adminGroups)
                    if (adminGroups.isNotEmpty() && currentObservedGroupId == null) {
                        adminGroups.first().id?.let { selectGroup(it) }
                    }
                }
                .onFailure { e ->
                    // Don't fail hard here; realtime observation below may still succeed.
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }

            // Observe groups with real-time updates
            getManagedGroupsUseCase.observeManagedGroups(userId, adminOnly = true).collect { result ->
                result.onSuccess { groups ->
                    _state.update { it.copy(managedGroups = groups) }
                    adminContextCacheService.warmManagedGroupsInBackground(userId, groups)
                    if (groups.isNotEmpty() && currentObservedGroupId == null) {
                        groups.first().id?.let { selectGroup(it) }
                    } else {
                        _state.update { it.copy(isLoading = false) }
                    }
                }.onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.toUserMessage()
                        )
                    }
                }
            }
        }
    }

    fun selectGroup(groupId: String) {
        if (currentObservedGroupId == groupId && groupObservationJob?.isActive == true && _state.value.error == null) return
        
        currentObservedGroupId = groupId
        applyCachedGroupContext(groupId)
        startObservingGroup(groupId)
    }


    private fun cancelGroupJobs() {
        groupObservationJob?.cancel()
        groupLoansJob?.cancel()
        calculationsJob?.cancel()
        burialClaimsJob?.cancel()
        ledgerJob?.cancel()
        healthScoreJob?.cancel()
        refreshMetricsJob?.cancel()
        businessInsightsJob?.cancel()
        cancelMemberJobs()
    }

    private fun cancelMemberJobs() {
        memberBeneficiariesJob?.cancel()
        memberDocumentsJob?.cancel()
        memberCalculationsJob?.cancel()
    }

    private fun resetStateForNewGroup(groupId: String, isLoading: Boolean) {
        _state.update { it.copy(
            currentGroupId = groupId,
            isLoading = isLoading,
            error = null,
            group = null,
            members = emptyList(),
            payouts = emptyList(),
            metrics = ActuarialMetrics(),
            healthScore = null,
            settings = GroupSettings(),
            viabilityPlan = null,
            payoutRequestSuccess = false,
            payoutAmount = "",
            payoutBankName = "",
            payoutAccountNo = "",
            payoutBranchCode = "",
            messageSentSuccess = false,
            successMessage = null,
            selectedMember = null,
            selectedMemberBeneficiaries = emptyList(),
            selectedMemberDocuments = emptyList(),
            selectedMemberCalculation = null,
            memberCalculations = emptyMap(),
            notifications = emptyList(),
            memberMessages = emptyList(),
            groupLoans = emptyList(),
            burialClaims = emptyList()
        ) }
    }

    private fun startObservingGroup(groupId: String) {
        val requestVersion = ++groupObservationVersion
        val hasCachedContext = adminContextCacheService.getContext(groupId)?.let { cached ->
            cached.group != null ||
                cached.members.isNotEmpty() ||
                cached.notifications.isNotEmpty() ||
                cached.memberMessages.isNotEmpty() ||
                cached.payouts.isNotEmpty() ||
                cached.metrics != null
        } == true
        
        cancelGroupJobs()
        if (!hasCachedContext) {
            resetStateForNewGroup(groupId, isLoading = true)
        }
        
        // Main flow combining Group and Members for consistent UI updates
        // All realtime sub-collectors are children of this job to ensure cancellation on group switch
        groupObservationJob = viewModelScope.launch {
            // 1. Observe Group Payouts
            launch {
                payoutRepo.observePayouts(groupId).collect { res ->
                    if (isStaleAdminObservation(groupId, requestVersion)) return@collect
                    res.onSuccess { list ->
                        _state.update { it.copy(payouts = list) }
                        adminContextCacheService.updateContext(groupId) { cached ->
                            cached.copy(payouts = list)
                        }
                    }
                }
            }

            // 2. Observe Fee Status (Directly from realtime channel)
            launch {
                groupRepo.observeGroupFeeStatus(groupId).collect { status ->
                    if (isStaleAdminObservation(groupId, requestVersion)) return@collect
                    _state.update { it.copy(feeStatus = status) }
                    adminContextCacheService.updateContext(groupId) { cached ->
                        cached.copy(feeStatus = status)
                    }
                }
            }

            // 3. Observe Group Notifications (Realtime)
            launch {
                notifRepo.observeNotifications(groupId).collect { res ->
                    if (isStaleAdminObservation(groupId, requestVersion)) return@collect
                    res.onSuccess { list ->
                        val (messages, system) = list.filter { 
                            it.memberId == null || it.triggerEvent == NotifEvent.MEMBER_MESSAGE 
                        }.partition { it.triggerEvent == NotifEvent.MEMBER_MESSAGE }
                        
                        _state.update { it.copy(
                            notifications = system.sortedByDescending { n -> n.id ?: "" },
                            memberMessages = messages.sortedByDescending { m -> m.id ?: "" }
                        ) }
                        adminContextCacheService.updateContext(groupId) { cached ->
                            cached.copy(
                                notifications = system.sortedByDescending { n -> n.id ?: "" },
                                memberMessages = messages.sortedByDescending { m -> m.id ?: "" }
                            )
                        }
                    }
                }
            }

            // 4. Group and Members Observation
            val groupFlow = groupRepo.observeGroup(groupId)
            val membersFlow = memberRepo.getGroupMembers(groupId)
            
            combine(groupFlow, membersFlow) { groupRes, membersRes ->
                val group = groupRes.getOrNull()
                val members = membersRes.getOrNull() ?: emptyList()
                
                // If we have an error and NO data, surface the error.
                // If we have an error but HAVE data, we keep showing what we have.
                // Note: groupRes.getOrNull() can be null if the cache is empty (initial emit)
                val error = if (groupRes.isFailure && group == null) {
                    groupRes.exceptionOrNull()?.toUserMessage()
                } else if (membersRes.isFailure && members.isEmpty()) {
                    membersRes.exceptionOrNull()?.toUserMessage()
                } else {
                    null
                }
                
                Triple(group, members, error)
            }.collect { (group, members, error) ->
                if (isStaleAdminObservation(groupId, requestVersion)) {
                    return@collect
                }
                _state.update {
                    val settings = if (group != null && it.group?.id != group.id) {
                        // Group changed, full initialization
                        toGroupSettings(group)
                    } else if (group != null && !it.isSaving && it.settings == GroupSettings()) {
                        // Initial load for current group
                        toGroupSettings(group)
                    } else {
                        // Keep current settings to avoid overwriting user input
                        it.settings
                    }

                    it.copy(
                        group = group,
                        members = members,
                        isLoading = (group == null && error == null),
                        error = error,
                        feeStatus = group?.feeStatus ?: it.feeStatus,
                        settings = settings
                    )
                }

                adminContextCacheService.updateContext(groupId) { cached ->
                    cached.copy(
                        group = group ?: cached.group,
                        members = members,
                        feeStatus = group?.feeStatus ?: cached.feeStatus
                    )
                }

                if (group != null) {
                    refreshMetrics(groupId)
                    refreshAllMemberCalculations(group, members)
                    _state.value.selectedMember?.let { member ->
                        refreshSelectedMemberCalculation(group, member)
                    }
                    
                    // Specialized Business Insights
                    businessInsightsJob?.cancel()
                    businessInsightsJob = viewModelScope.launch {
                        val insights = getGroupBusinessInsightsUseCase(group, members)
                        _state.update { it.copy(businessInsight = insights) }
                    }
                }
            }
        }

        groupLoansJob = viewModelScope.launch {
            loanRepo.getGroupLoans(groupId).collect { result ->
                if (isStaleAdminObservation(groupId, requestVersion)) return@collect
                val loans = result.getOrDefault(emptyList())
                _state.update { it.copy(groupLoans = loans) }
            }
        }

        burialClaimsJob = viewModelScope.launch {
            claimRepo.observeClaimsForGroup(groupId).collect { result ->
                if (isStaleAdminObservation(groupId, requestVersion)) return@collect
                val claims = result.getOrDefault(emptyList())
                _state.update { it.copy(burialClaims = claims) }
            }
        }

        ledgerJob = viewModelScope.launch {
            ledgerRepo.observeGroupLedger(groupId).collect { result ->
                if (isStaleAdminObservation(groupId, requestVersion)) return@collect
                val entries = result.getOrDefault(emptyList())
                _state.update { it.copy(ledger = entries) }
            }
        }

        healthScoreJob = viewModelScope.launch {
            healthScoreRepo.observeGroupHealthScore(groupId).collect { result ->
                if (isStaleAdminObservation(groupId, requestVersion)) return@collect
                result.onSuccess { score ->
                    _state.update { it.copy(healthScore = score) }
                }
            }
        }
    }


    private fun refreshAllMemberCalculations(group: Group, members: List<Member>) {
        val groupId = group.id ?: return
        calculationsJob?.cancel()
        calculationsJob = viewModelScope.launch {
            // Optimization: Fetch all contributions for the entire group once
            val result = memberRepo.getGroupContributions(groupId).firstOrNull() ?: Result.success(emptyList())
            result.onSuccess { allContribs ->
                val contribsByMember = allContribs.groupBy { it.memberId }
                val allCalculations = buildMap {
                    members.forEach { member ->
                        val memberId = member.id ?: return@forEach
                        val memberContribs = contribsByMember[memberId] ?: emptyList()
                        put(memberId, PaymentCalculator.calculateStatus(group, member, memberContribs))
                    }
                }
                _state.update { it.copy(memberCalculations = allCalculations) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }


    private fun refreshMetrics(groupId: String) {
        refreshMetricsJob?.cancel()
        refreshMetricsJob = viewModelScope.launch {
            // 1. Compute basic actuarial metrics
            actuarialRepo.computeMetrics(groupId).onSuccess { m ->
                _state.update { it.copy(metrics = m) }
                adminContextCacheService.updateContext(groupId) { cached ->
                    cached.copy(metrics = m)
                }
            }
            .onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
            }

            // 2. Compute comprehensive health score
            calculateGroupHealthScoreUseCase(groupId).onFailure { e ->
                AppLogger.e("AdminVM", "Failed to calculate health score", e)
            }
        }
    }

    private fun applyCachedGroupContext(groupId: String): Boolean {
        val cached = adminContextCacheService.getContext(groupId) ?: return false
        val hasUsefulData = cached.group != null ||
            cached.members.isNotEmpty() ||
            cached.notifications.isNotEmpty() ||
            cached.memberMessages.isNotEmpty() ||
            cached.payouts.isNotEmpty() ||
            cached.metrics != null

        if (hasUsefulData) {
            _state.update { it.copy(
                currentGroupId = groupId,
                group = cached.group,
                members = cached.members,
                notifications = cached.notifications,
                memberMessages = cached.memberMessages,
                payouts = cached.payouts,
                metrics = cached.metrics ?: it.metrics,
                feeStatus = cached.feeStatus ?: it.feeStatus,
                isLoading = false,
                error = null
            ) }
            
            // Re-sync settings if we have the group
            cached.group?.let { g ->
                _state.update { it.copy(settings = toGroupSettings(g)) }
                refreshAllMemberCalculations(g, cached.members)
            }
        }
        return hasUsefulData
    }

    private fun toGroupSettings(group: Group): GroupSettings {
        return GroupSettings(
            joiningFee = group.joiningFee.toString(),
            monthlyContribution = group.monthlyContribution.toString(),
            lateFee = group.lateFee.toString(),
            lateFeeGraceDays = group.lateFeeGraceDays.toString(),
            probationMonths = group.probationMonths.toString(),
            paymentDueDay = group.paymentDueDay.toString(),
            maxMembers = group.maxMembers.toString(),
            allowPartialPayment = group.allowPartialPayment,
            autoSuspendAfter = group.autoSuspendAfter.toString(),
            bankName = group.bankName ?: "",
            accountNumber = group.accountNumber ?: "",
            branchCode = group.branchCode ?: "",
            accountType = group.accountType,
            maxBeneficiaries = (group.maxBeneficiaries ?: 0).toString(),
            beneficiaryIncreasePct = (group.beneficiaryIncreasePct ?: 0.0).toString(),
            goalAmount = group.goalAmount.toString(),
            periodMonths = group.periodMonths.toString(),
            loanInterestRate = (group.loanInterestRate ?: 0.0).toString(),
            loanMaxAmount = (group.loanMaxAmount ?: 0.0).toString(),
            loanMaxMonths = (group.loanMaxMonths ?: 0).toString()
        )
    }

    private data class GroupSettingChange(
        val label: String,
        val from: String,
        val to: String
    )

    private fun detectGroupSettingChanges(group: Group, settings: GroupSettings): List<GroupSettingChange> {
        val changes = mutableListOf<GroupSettingChange>()

        fun addDouble(label: String, oldValue: Double, rawNewValue: String, formatAsCurrency: Boolean = true) {
            val newValue = rawNewValue.toDoubleOrNull() ?: oldValue
            if (kotlin.math.abs(newValue - oldValue) > 0.0001) {
                val fromStr = if (formatAsCurrency) "R${"%.2f".format(oldValue)}" else "${"%.2f".format(oldValue)}%"
                val toStr = if (formatAsCurrency) "R${"%.2f".format(newValue)}" else "${"%.2f".format(newValue)}%"
                changes += GroupSettingChange(label, fromStr, toStr)
            }
        }

        fun addInt(label: String, oldValue: Int, rawNewValue: String) {
            val newValue = rawNewValue.toIntOrNull() ?: oldValue
            if (newValue != oldValue) {
                changes += GroupSettingChange(label, oldValue.toString(), newValue.toString())
            }
        }

        fun addText(label: String, oldValue: String?, newValue: String) {
            val normalizedOld = oldValue.orEmpty().trim()
            val normalizedNew = newValue.trim()
            if (normalizedOld != normalizedNew) {
                changes += GroupSettingChange(label, normalizedOld.ifBlank { "(blank)" }, normalizedNew.ifBlank { "(blank)" })
            }
        }

        fun addBoolean(label: String, oldValue: Boolean, newValue: Boolean) {
            if (oldValue != newValue) {
                changes += GroupSettingChange(label, oldValue.toString(), newValue.toString())
            }
        }

        addDouble("Joining Fee", group.joiningFee, settings.joiningFee)
        addDouble("Monthly Contribution", group.monthlyContribution, settings.monthlyContribution)
        addDouble("Late Fee", group.lateFee, settings.lateFee)
        addInt("Late Fee Grace Days", group.lateFeeGraceDays, settings.lateFeeGraceDays)
        addInt("Probation Months", group.probationMonths, settings.probationMonths)
        addInt("Payment Due Day", group.paymentDueDay, settings.paymentDueDay)
        addInt("Max Members", group.maxMembers, settings.maxMembers)
        addBoolean("Allow Partial Payment", group.allowPartialPayment, settings.allowPartialPayment)
        addInt("Auto Suspend After", group.autoSuspendAfter, settings.autoSuspendAfter)
        addText("Bank Name", group.bankName, settings.bankName)
        addText("Account Number", group.accountNumber, settings.accountNumber)
        addText("Branch Code", group.branchCode, settings.branchCode)
        addText("Account Type", group.accountType, settings.accountType)
        addInt("Max Beneficiaries", group.maxBeneficiaries ?: 0, settings.maxBeneficiaries)
        addDouble("Beneficiary Increase %", group.beneficiaryIncreasePct ?: 0.0, settings.beneficiaryIncreasePct, formatAsCurrency = false)
        addDouble("Goal Amount", group.goalAmount, settings.goalAmount)
        addInt("Period Months", group.periodMonths, settings.periodMonths)
        addDouble("Loan Interest Rate", group.loanInterestRate ?: 0.0, settings.loanInterestRate, formatAsCurrency = false)
        addDouble("Loan Max Amount", group.loanMaxAmount ?: 0.0, settings.loanMaxAmount)
        addInt("Loan Max Months", group.loanMaxMonths ?: 0, settings.loanMaxMonths)

        return changes
    }

    private suspend fun broadcastSettingsChange(
        groupId: String,
        groupName: String,
        changes: List<GroupSettingChange>
    ): Result<Unit> {
        val changedLines = changes.take(5).joinToString(separator = "; ") { change ->
            "${change.label}: ${change.from} -> ${change.to}"
        }
        val extra = if (changes.size > 5) " (+${changes.size - 5} more)" else ""
        val message = "Group settings updated for $groupName. Changes: $changedLines$extra"

        return sendNotificationUseCase(
            groupId = groupId,
            memberId = null,
            message = message,
            triggerEvent = NotifEvent.FEE_SETTINGS_CHANGED,
            channel = NotifChannel.BOTH
        )
    }

    fun requestRestore() {
        val group = state.value.group
        if (group == null) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val message = "RESTORE REQUEST: Group '${group.name}' (ID: ${group.id}) has requested suspension lifting."
            sendNotificationUseCase.notifyPlatformAdmin(message)
                .onSuccess {
                    _state.update { it.copy(isLoading = false, restoreRequested = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun loadAdminData() {
        observeAdminData()
    }

    fun calculateViability() {
        val groupId = state.value.group?.id ?: return
        val s = state.value.settings
        
        val goal = s.goalAmount.toDoubleOrNull() ?: 0.0
        val months = s.periodMonths.toIntOrNull() ?: 0
        
        if (goal <= 0 || months <= 0) {
            _state.update { it.copy(error = "Please enter a valid goal amount and time period.") }
            return
        }
        
        viewModelScope.launch {
            _state.update { it.copy(isCalculatingViability = true, error = null, viabilityPlan = null) }
            calculateViabilityUseCase(groupId, goal, months).onSuccess { plan ->
                _state.update { it.copy(viabilityPlan = plan, isCalculatingViability = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage(), isCalculatingViability = false) }
            }
        }
    }

    fun applySuggestedContribution() {
        val plan = state.value.viabilityPlan
        if (plan == null) {
            _state.update { it.copy(error = "No viability plan calculated. Please calculate strategy first.") }
            return
        }
        val groupId = state.value.group?.id
        if (groupId == null) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }
        val currentSettings = state.value.settings

        val group = state.value.group
        val previousMonthly = currentSettings.monthlyContribution.toDoubleOrNull()

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }

            val applyResult = applyViabilityPlanUseCase(groupId, plan)
            if (applyResult.isFailure) {
                _state.update { it.copy(isSaving = false, error = applyResult.exceptionOrNull()?.toUserMessage()) }
                return@launch
            }

            val settingsAfterApply = currentSettings.copy(
                monthlyContribution = plan.suggestedMonthlyContribution.toString(),
                goalAmount = plan.goalAmount.toString(),
                periodMonths = plan.periodMonths.toString()
            )

            var warningMessage: String? = null
            if (group != null) {
                val changes = detectGroupSettingChanges(group, settingsAfterApply)
                if (changes.isNotEmpty()) {
                    broadcastSettingsChange(groupId, group.name, changes)
                        .onFailure { e -> warningMessage = "Strategy applied, but broadcast failed: ${e.toUserMessage()}" }
                }
            }

            _state.update {
                it.copy(
                    isSaving = false,
                    saveSuccess = true,
                    successMessage = "Strategy applied! Monthly contribution updated to R${"%.2f".format(plan.suggestedMonthlyContribution)}",
                    settings = settingsAfterApply,
                    error = warningMessage
                )
            }

            if (previousMonthly != null && kotlin.math.abs(previousMonthly - plan.suggestedMonthlyContribution) > 0.0001) {
                refreshMetrics(groupId)
            }
        }
    }

    fun setTab(index: Int) {
        val current = _state.value
        if (current.selectedTab == index) return
        _state.update {
            it.copy(
                selectedTab = index,
                messageSentSuccess = false
            )
        }
    }
    
    fun clearLoadingMessage() {
        _state.update { it.copy(loadingMessage = null) }
    }
    
    private fun tabDisplayName(index: Int): String {
        return when (index) {
            0 -> "Overview"
            1 -> "Members"
            2 -> "Alerts"
            3 -> "Messaging"
            4 -> "Viability"
            5 -> "Account"
            6 -> "Settings"
            7 -> "Payouts"
            else -> "Admin"
        }
    }

    fun updateMessageText(text: String) {
        _state.update { it.copy(messageText = text) }
    }

    fun broadcastMessage() = dispatchMessage(memberId = null)

    fun sendMessageToMember(memberId: String?) {
        if (memberId == null) {
            broadcastMessage()
            return
        }
        dispatchMessage(memberId = memberId)
    }

    /**
     * Shared implementation for both broadcast and direct-member messages.
     * Null [memberId] sends to all group members (broadcast).
     */
    private fun dispatchMessage(memberId: String?) {
        val groupId = state.value.group?.id
        if (groupId.isNullOrBlank()) {
            _state.update { it.copy(error = "Please select a group before sending a message.") }
            return
        }

        val message = state.value.messageText.trim()
        if (message.isBlank()) {
            _state.update { it.copy(error = "Please enter a message before sending.") }
            return
        }

        val successText = if (memberId == null) "Message broadcasted successfully" else "Message sent successfully"

        viewModelScope.launch {
            _state.update { it.copy(isSendingMessage = true) }

            sendNotificationUseCase(
                groupId = groupId,
                memberId = memberId,
                message = message,
                triggerEvent = NotifEvent.CUSTOM,
                channel = NotifChannel.BOTH
            ).onSuccess {
                _state.update { it.copy(
                    isSendingMessage = false,
                    messageText = "",
                    messageSentSuccess = true,
                    successMessage = successText
                ) }
            }.onFailure { e ->
                _state.update { it.copy(isSendingMessage = false, error = e.toUserMessage()) }
            }
        }
    }

    fun sendWhatsAppTestToSelectedMember() {
        val selectedMember = _state.value.selectedMember
        val memberId = selectedMember?.id
        val groupId = _state.value.group?.id

        if (memberId.isNullOrBlank() || groupId.isNullOrBlank()) {
            _state.update { it.copy(error = "Select a member before running WhatsApp test.") }
            return
        }

        viewModelScope.launch {
            val cannedMessage = "DEBUG WHATSAPP TEST for ${selectedMember.fullName} at ${System.currentTimeMillis()}"
            _state.update {
                it.copy(
                    isSendingWhatsAppTest = true,
                    whatsAppTestResult = null,
                    error = null
                )
            }

            sendNotificationUseCase(
                groupId = groupId,
                memberId = memberId,
                message = cannedMessage,
                triggerEvent = NotifEvent.CUSTOM,
                channel = NotifChannel.WHATSAPP
            ).onSuccess {
                _state.update {
                    it.copy(
                        isSendingWhatsAppTest = false,
                        whatsAppTestResult = "WhatsApp test sent successfully."
                    )
                }
            }.onFailure { e ->
                val userMessage = e.toUserMessage()
                _state.update {
                    it.copy(
                        isSendingWhatsAppTest = false,
                        whatsAppTestResult = userMessage,
                        error = userMessage
                    )
                }
            }
        }
    }

    fun updateSetting(key: String, value: Any) {
        _state.update {
            val s = it.settings
            it.copy(settings = when(key) {
                "joiningFee" -> s.copy(joiningFee = value.toString())
                "monthlyContribution" -> s.copy(monthlyContribution = value.toString())
                "lateFee" -> s.copy(lateFee = value.toString())
                "lateFeeGraceDays" -> s.copy(lateFeeGraceDays = value.toString())
                "probationMonths" -> s.copy(probationMonths = value.toString())
                "paymentDueDay" -> s.copy(paymentDueDay = value.toString())
                "maxMembers" -> s.copy(maxMembers = value.toString())
                "maxBeneficiaries" -> s.copy(maxBeneficiaries = value.toString())
                "beneficiaryIncreasePct" -> s.copy(beneficiaryIncreasePct = value.toString())
                "allowPartialPayment" -> s.copy(allowPartialPayment = value as? Boolean ?: s.allowPartialPayment)
                "autoSuspendAfter" -> s.copy(autoSuspendAfter = value.toString())
                "bankName" -> s.copy(bankName = value.toString())
                "accountNumber" -> s.copy(accountNumber = value.toString())
                "branchCode" -> s.copy(branchCode = value.toString())
                "accountType" -> s.copy(accountType = value.toString())
                "goalAmount" -> s.copy(goalAmount = value.toString())
                "periodMonths" -> s.copy(periodMonths = value.toString())
                "loanInterestRate" -> s.copy(loanInterestRate = value.toString())
                "loanMaxAmount" -> s.copy(loanMaxAmount = value.toString())
                "loanMaxMonths" -> s.copy(loanMaxMonths = value.toString())
                "rotationMethod" -> s.copy(rotationMethod = value as? RoscaRotationMethod ?: s.rotationMethod)
                else -> s
            })
        }
    }

    fun saveSettings() {
        val groupId = state.value.group?.id
        if (groupId.isNullOrBlank()) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }
        val settings = state.value.settings
        val group = state.value.group
        val changes = if (group != null) detectGroupSettingChanges(group, settings) else emptyList()
        
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }

            val saveResult = updateGroupSettingsUseCase(groupId, settings)
            if (saveResult.isFailure) {
                _state.update { it.copy(isSaving = false, error = saveResult.exceptionOrNull()?.toUserMessage()) }
                return@launch
            }

            var warningMessage: String? = null
            if (group != null && changes.isNotEmpty()) {
                broadcastSettingsChange(groupId, group.name, changes)
                    .onFailure { e -> warningMessage = "Settings saved, but broadcast failed: ${e.toUserMessage()}" }
            }

            _state.update {
                it.copy(
                    isSaving = false,
                    saveSuccess = true,
                    successMessage = "Settings saved successfully",
                    error = warningMessage
                )
            }

            if (changes.isNotEmpty()) {
                refreshMetrics(groupId)
            }
        }
    }

    fun clearExportFile() {
        _state.update { it.copy(exportFile = null) }
    }

    fun updateMemberStatus(memberId: String, status: MemberStatus) {
        if (_state.value.currentGroupId.isNullOrBlank()) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = updateMemberStatusUseCase(memberId, status)
            _state.update { it.copy(isLoading = false) }
            result.onSuccess {
                _state.update { it.copy(successMessage = "Member status updated to ${status.displayName}") }
            }.onFailure { err ->
                _state.update { it.copy(error = err.toUserMessage()) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _state.update { it.copy(successMessage = null) }
    }

    fun exportGroupStatement() = performExport(pdf = false)

    fun exportGroupLedger(pdf: Boolean) {
        val group = state.value.group
        if (group == null) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }
        val entries = state.value.ledger
        if (entries.isEmpty()) {
            _state.update { it.copy(error = "No ledger entries found to export.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, error = null) }
            val result = if (pdf) {
                exportRepo.exportLedgerToPdf(group, entries)
            } else {
                exportRepo.exportLedgerToCsv(group, entries)
            }
            
            result.onSuccess { file ->
                _state.update { it.copy(isExporting = false, exportFile = file) }
            }.onFailure { e ->
                _state.update { it.copy(isExporting = false, error = e.toUserMessage()) }
            }
        }
    }

    fun exportMemberStatement(member: Member, pdf: Boolean = true) {
        val group = state.value.group
        if (group == null) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }
        val memberId = member.id
        if (memberId.isNullOrBlank()) {
            _state.update { it.copy(error = "Member data is invalid. Please refresh and try again.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, error = null) }
            memberRepo.getMemberContributions(memberId, group.id ?: "").first()
                .onSuccess { contributions ->
                    if (contributions.isEmpty()) {
                        _state.update { it.copy(isExporting = false, error = "No transactions found for this member.") }
                        return@onSuccess
                    }
                    val result = if (pdf) {
                        exportRepo.exportContributionsToPdf(group, member, contributions)
                    } else {
                        exportRepo.exportContributionsToCsv(group, member, contributions)
                    }
                    result
                        .onSuccess { file ->
                            _state.update { it.copy(isExporting = false, exportFile = file) }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(isExporting = false, error = e.toUserMessage()) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(isExporting = false, error = e.toUserMessage()) }
                }
        }
    }

    fun approveLoan(loanId: String) {
        val group = state.value.group
        if (group == null) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isProcessingLoan = true) }
            
            // 1. Approve the loan in DB
            val result = loanRepo.approveLoan(loanId)
            if (result.isFailure) {
                _state.update { it.copy(isProcessingLoan = false, error = result.exceptionOrNull()?.toUserMessage()) }
                return@launch
            }

            // 2. Fetch updated loan with details for contract
            val loanResult = loanRepo.getLoanById(loanId)
            val loan = loanResult.getOrNull()
            val member = state.value.members.find { it.id == loan?.memberId }

            if (loan != null && member != null) {
                // 3. Generate & Upload Contract PDF
                generateLoanContractUseCase(loan, member, group)
                    .onSuccess { file ->
                        loanRepo.uploadLoanContract(loanId, file.readBytes(), "Loan_Agreement_${member.fullName.replace(" ", "_")}.pdf")
                            .onFailure { e -> AppLogger.e("AdminVM", "Failed to upload loan contract", e) }
                    }
                    .onFailure { e -> AppLogger.e("AdminVM", "Failed to generate loan contract", e) }
            }

            _state.update { it.copy(
                isProcessingLoan = false,
                successMessage = "Loan approved and contract generated successfully",
                error = null
            ) }
        }
    }

    fun disburseLoan(loanId: String, paymentMethod: PaymentMethod = PaymentMethod.BANK) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessingLoan = true) }
            val result = loanRepo.disburseLoan(loanId, paymentMethod)
            _state.update { it.copy(
                isProcessingLoan = false,
                successMessage = if (result.isSuccess) "Loan disbursed and group balance updated" else null,
                error = result.exceptionOrNull()?.toUserMessage()
            ) }
            if (result.isSuccess) {
                _state.value.currentGroupId?.let { gid ->
                    refreshMetrics(gid)
                }
            }
        }
    }

    fun rejectLoan(loanId: String, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessingLoan = true) }
            val result = loanRepo.rejectLoan(loanId, reason)
            _state.update { it.copy(
                isProcessingLoan = false,
                successMessage = if (result.isSuccess) "Loan rejected" else null,
                error = result.exceptionOrNull()?.toUserMessage()
            ) }
        }
    }

    fun clearLoanProcessing() {
        _state.update { it.copy(isProcessingLoan = false) }
    }

    fun downloadPdfStatement() = performExport(pdf = true)

    fun downloadGroupConstitution() {
        val group = state.value.group
        if (group == null) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }

        // Note: For official uploaded constitutions, the UI should use FileDownloader directly.
        // This function handles the generation fallback.
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, error = null) }
            exportRepo.exportGroupConstitution(group)
                .onSuccess { file ->
                    _state.update { it.copy(isExporting = false, exportFile = file) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isExporting = false, error = e.toUserMessage()) }
                }
        }
    }

    fun generateAndUploadStandardConstitution() {
        val group = state.value.group
        if (group == null) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }
        val groupId = group.id
        if (groupId.isNullOrBlank()) {
            _state.update { it.copy(error = "Group data is invalid. Please refresh and try again.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, uploadProgress = 0.0) }

            exportRepo.exportGroupConstitution(group)
                .onSuccess { file ->
                    groupRepo.uploadConstitution(groupId, file.readBytes(), "Constitution_${group.name.replace(" ", "_")}.pdf")
                        .onSuccess {
                            _state.update { it.copy(
                                isUploading = false,
                                uploadProgress = 1.0,
                                successMessage = "Standard constitution generated and uploaded successfully"
                            ) }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(isUploading = false, error = e.toUserMessage()) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(isUploading = false, error = e.toUserMessage()) }
                }
        }
    }

    /**
     * Fetches group payments then exports them as CSV or PDF.
     * Shared implementation removes the duplicated fetch-then-export pattern.
     */
    private fun performExport(pdf: Boolean) {
        val group = state.value.group
        if (group == null) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }
        val groupId = group.id
        if (groupId.isNullOrBlank()) {
            _state.update { it.copy(error = "Group data is invalid. Please refresh and try again.") }
            return
        }
        val members = state.value.members

        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, error = null) }
            paymentRepo.getPayments(groupId).first()
                .onSuccess { payments ->
                    val exportResult = if (pdf) {
                        exportRepo.exportPaymentsToPdf(group, payments, members)
                    } else {
                        exportRepo.exportPaymentsToCsv(group, payments, members)
                    }
                    exportResult
                        .onSuccess { file ->
                            _state.update { it.copy(isExporting = false, exportFile = file) }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(isExporting = false, error = e.toUserMessage()) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(isExporting = false, error = e.toUserMessage()) }
                }
        }
    }

    fun uploadLoanContract(loanId: String, byteArray: ByteArray, fileName: String) {
        if (byteArray.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
            _state.update { it.copy(error = "File size exceeds 3MB limit") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, uploadProgress = 0.0) }
            loanRepo.uploadLoanContract(loanId, byteArray, fileName)
                .onSuccess {
                    _state.update { it.copy(isUploading = false, uploadProgress = 1.0, successMessage = "Loan contract uploaded successfully") }
                }
                .onFailure { e ->
                    _state.update { it.copy(isUploading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun getDownloadParams(url: String): Map<String, String> {
        return if (requiresSupabaseAuthHeaders(url)) {
            buildSupabaseAuthHeaders(BuildConfig.SUPABASE_ANON_KEY, supabaseRepo.accessToken)
        } else {
            emptyMap()
        }
    }

    fun downloadMemberDocument(url: String, label: String): Pair<String, Map<String, String>> {
        return Pair(url, getDownloadParams(url))
    }

    fun selectMember(member: Member?) {
        _state.update { it.copy(
            selectedMember = member,
            selectedMemberBeneficiaries = emptyList(),
            selectedMemberDocuments = emptyList(),
            selectedMemberCalculation = null,
            isEligibleForLoan = false,
            loanIneligibilityReason = null,
            messageText = "",
            messageSentSuccess = false,
            isSendingWhatsAppTest = false,
            whatsAppTestResult = null
        ) }

        cancelMemberJobs()

        if (member != null) {
            val memberId = member.id
            if (memberId.isNullOrBlank()) {
                _state.update { it.copy(error = "Member data is invalid. Please refresh and try again.") }
                return
            }

            // Observe beneficiaries for this member
            memberBeneficiariesJob = viewModelScope.launch {
                beneficiaryRepo.observeBeneficiaries(memberId).collect { result ->
                    result.onSuccess { list ->
                        _state.update { it.copy(selectedMemberBeneficiaries = list) }
                    }
                }
            }

            // Observe documents for this member
            memberDocumentsJob = viewModelScope.launch {
                memberDocumentRepo.observeMemberDocuments(memberId).collect { result ->
                    result.onSuccess { list ->
                        _state.update { it.copy(selectedMemberDocuments = list) }
                    }
                }
            }

            // Calculate status for this member
            _state.value.group?.let { group ->
                refreshSelectedMemberCalculation(group, member)
            }
        }
    }

    private fun refreshSelectedMemberCalculation(group: Group, member: Member) {
        val memberId = member.id ?: return
        val groupId = group.id ?: return
        memberCalculationsJob?.cancel()
        memberCalculationsJob = viewModelScope.launch {
            // Check Loan Eligibility in parallel with calculation
            launch {
                validateLoanEligibilityUseCase(member, group).let { result ->
                    _state.update {
                        it.copy(
                            isEligibleForLoan = result is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible,
                            loanIneligibilityReason = (result as? ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible)?.reason
                        )
                    }
                }
            }

            memberRepo.getMemberContributions(memberId, groupId).collect { res ->
                val contribs = res.getOrNull() ?: emptyList()
                val calc = PaymentCalculator.calculateStatus(group, member, contribs)
                _state.update { it.copy(selectedMemberCalculation = calc) }
            }
        }
    }

    fun verifyDocument(memberId: String, docIndex: Int, approve: Boolean) {
        viewModelScope.launch {
            val status = if (approve) DocumentStatus.VERIFIED else DocumentStatus.REJECTED
            
            // Optimistic update for immediate UI feedback
            _state.update { s ->
                val updatedMember = s.selectedMember?.let { m ->
                    if (m.id == memberId) {
                        val base = when (docIndex) {
                            1 -> m.copy(document1Status = status)
                            2 -> m.copy(document2Status = status)
                            3 -> m.copy(document3Status = status)
                            4 -> m.copy(document4Status = status)
                            5 -> m.copy(document5Status = status)
                            else -> m
                        }
                        if (!approve) base.copy(status = MemberStatus.SUSPENDED) else base
                    } else m
                }
                
                val updatedMembers = s.members.map { 
                    if (it.id == memberId && updatedMember != null) updatedMember else it 
                }
                
                s.copy(
                    selectedMember = updatedMember,
                    members = updatedMembers
                )
            }

            verifyMemberDocumentUseCase(memberId, docIndex, approve)
                .onSuccess {
                    _state.update { it.copy(
                        successMessage = "Document ${if (approve) "verified" else "rejected"} successfully"
                    ) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                    // Revert optimistic update by refreshing member data
                    memberRepo.getMemberById(memberId).onSuccess { refreshedMember ->
                        if (refreshedMember != null) {
                            _state.update { s ->
                                s.copy(
                                    selectedMember = if (s.selectedMember?.id == memberId) refreshedMember else s.selectedMember,
                                    members = s.members.map { if (it.id == memberId) refreshedMember else it }
                                )
                            }
                        }
                    }
                }
        }
    }

    fun verifyRelationalDocument(docId: String, approve: Boolean) {
        viewModelScope.launch {
            val document = _state.value.selectedMemberDocuments.find { it.id == docId } ?: return@launch
            val memberId = document.memberId
            val status = if (approve) DocumentStatus.VERIFIED else DocumentStatus.REJECTED
            val updatedDoc = document.copy(status = status)
            
            // Optimistic update
            _state.update { s ->
                val updatedDocs = s.selectedMemberDocuments.map {
                    if (it.id == docId) updatedDoc else it
                }
                
                val updatedMember = s.selectedMember?.let { m ->
                    if (m.id == memberId && !approve) m.copy(status = MemberStatus.SUSPENDED) else m
                }

                val updatedMembers = s.members.map {
                    if (it.id == memberId && updatedMember != null) updatedMember else it
                }

                s.copy(
                    selectedMember = updatedMember,
                    members = updatedMembers,
                    selectedMemberDocuments = updatedDocs
                )
            }

            verifyRelationalDocumentUseCase(document, approve)
                .onSuccess {
                    _state.update { it.copy(successMessage = "Document ${if (approve) "verified" else "rejected"} successfully") }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                    // Revert by re-fetching documents and member data
                    memberDocumentRepo.syncMemberDocuments(memberId).onSuccess {
                        memberRepo.getMemberById(memberId).onSuccess { refreshedMember ->
                            if (refreshedMember != null) {
                                _state.update { s ->
                                    s.copy(
                                        selectedMember = if (s.selectedMember?.id == memberId) refreshedMember else s.selectedMember,
                                        members = s.members.map { if (it.id == memberId) refreshedMember else it }
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }


    fun resetLocalData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            supabaseRepo.resetLocalCache()
            _state.update { it.copy(isLoading = false) }
            // Use a specific message or just allow the app to refresh naturally
        }
    }

    fun startEditBeneficiary(beneficiary: Beneficiary?) {
        _state.update { it.copy(editingBeneficiary = beneficiary) }
    }

    fun updateEditingBeneficiary(updates: (Beneficiary) -> Beneficiary) {
        _state.update { s ->
            s.editingBeneficiary?.let { b ->
                s.copy(editingBeneficiary = updates(b))
            } ?: s
        }
    }

    // Payout logic
    fun updatePayoutAmount(v: String) { _state.update { it.copy(payoutAmount = v) } }
    fun updatePayoutBank(v: String) { _state.update { it.copy(payoutBankName = v) } }
    fun updatePayoutAccount(v: String) { _state.update { it.copy(payoutAccountNo = v) } }
    fun updatePayoutBranch(v: String) { _state.update { it.copy(payoutBranchCode = v) } }

    fun cancelPayoutRequest(payoutId: String) {
        if (state.value.group?.id == null) return
        viewModelScope.launch {
            _state.update { it.copy(isRequestingPayout = true) }
            payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.CANCELLED)
                .onSuccess {
                    _state.update { it.copy(isRequestingPayout = false, successMessage = "Request cancelled") }
                    refreshPayouts()
                }
                .onFailure { e ->
                    _state.update { it.copy(isRequestingPayout = false, error = e.toUserMessage()) }
                }
        }
    }

    fun approveAndEscalatePayoutRequest(payoutId: String) {
        val groupId = state.value.group?.id
        if (groupId.isNullOrBlank()) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }
        val groupName = state.value.group?.name ?: "Group"
        val payoutAmount = state.value.payouts.firstOrNull { it.id == payoutId }?.amount
        viewModelScope.launch {
            _state.update { it.copy(isRequestingPayout = true, error = null) }
            payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED)
                .onSuccess {
                    AppLogger.i(
                        tag = "AdminPayoutAudit",
                        message = "PAYOUT_ESCALATED groupId=$groupId payoutId=$payoutId amount=${payoutAmount ?: 0.0}"
                    )
                    val notifyResult = sendNotificationUseCase.notifyPlatformAdmin(
                        message = buildString {
                            append("PAYOUT ESCALATION: ")
                            append(groupName)
                            append(" (")
                            append(groupId)
                            append(") escalated payout ")
                            append(payoutId)
                            payoutAmount?.let { append(" for R").append("%.2f".format(it)) }
                            append(" for final platform approval.")
                        }
                    )
                    _state.update {
                        it.copy(
                            isRequestingPayout = false,
                            successMessage = if (notifyResult.isSuccess) {
                                "Request validated and escalated to platform admin"
                            } else {
                                "Request escalated. Platform admin notification will retry."
                            }
                        )
                    }
                    refreshPayouts()
                }
                .onFailure { e ->
                    _state.update { it.copy(isRequestingPayout = false, error = e.toUserMessage()) }
                }
        }
    }

    /** SA bank account numbers are accepted in the 7–13 digit range. */
    private fun isValidAccount(acc: String) = acc.length in 7..13 && acc.all { it.isDigit() }
    private fun isValidBranch(branch: String) = branch.length == 6 && branch.all { it.isDigit() }

    fun refreshPayouts() {
        val group = state.value.group
        if (group == null) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // Small delay to ensure DB/Network consistency if called immediately after success
            delay(500L)
            payoutRepo.observePayouts(group.id ?: "").first().onSuccess { list ->
                _state.update { it.copy(payouts = list, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
            }
        }
    }

    fun submitPayoutRequest() {
        val s = _state.value
        val group = s.group
        if (group == null) {
            _state.update { it.copy(error = "No group selected. Please select a group first.") }
            return
        }
        val groupId = group.id
        if (groupId.isNullOrBlank()) {
            _state.update { it.copy(error = "Group data is invalid. Please refresh and try again.") }
            return
        }
        val amount = s.payoutAmount.toDoubleOrNull() ?: 0.0
        val balance = group.balance
        
        val validationError = when {
            amount <= 0 -> "Amount must be greater than zero"
            amount > balance -> "Insufficient balance"
            s.payoutBankName.isBlank() -> "Bank Name is required"
            !isValidAccount(s.payoutAccountNo) -> "Invalid Account Number (7-13 digits)"
            !isValidBranch(s.payoutBranchCode) -> "Invalid Branch Code (6 digits)"
            else -> null
        }

        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isRequestingPayout = true) }
            requestPayoutUseCase(groupId, amount, s.payoutBankName, s.payoutAccountNo, s.payoutBranchCode)
                .onSuccess {
                    AppLogger.i(
                        tag = "AdminPayoutAudit",
                        message = "PAYOUT_REQUEST_SUBMITTED groupId=$groupId groupType=${group.type.name} amount=$amount"
                    )
                    val notifyResult = if (group.type == GroupType.ROSCA) {
                        sendNotificationUseCase(
                            groupId = groupId,
                            memberId = null,
                            message = "New ROSCA payout request of R${"%.2f".format(amount)} requires group admin validation.",
                            triggerEvent = NotifEvent.PAYOUT_REQUESTED,
                            channel = NotifChannel.BOTH
                        )
                    } else {
                        Result.success(Unit)
                    }

                    _state.update { it.copy(
                        isRequestingPayout = false, 
                        payoutRequestSuccess = true,
                        payoutAmount = "",
                        successMessage = if (notifyResult.isSuccess) {
                            "Request submitted for admin validation"
                        } else {
                            "Request submitted. Group admin notification will retry."
                        }
                    ) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isRequestingPayout = false, error = e.toUserMessage()) }
                }
        }
    }

    fun saveBeneficiary() {
        val b = _state.value.editingBeneficiary
        if (b == null) {
            _state.update { it.copy(error = "No beneficiary selected for editing.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSavingBeneficiary = true) }
            val result = if (b.id == null) {
                beneficiaryRepo.addBeneficiary(b)
            } else {
                beneficiaryRepo.updateBeneficiary(b)
            }
            
            result.onSuccess {
                _state.update { it.copy(isSavingBeneficiary = false, editingBeneficiary = null) }
            }.onFailure { e ->
                _state.update { it.copy(isSavingBeneficiary = false, error = e.toUserMessage()) }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BURIAL SOCIETY CLAIMS
    // ══════════════════════════════════════════════════════════════════════════

    fun verifyClaim(claimId: String, approve: Boolean, adminNotes: String? = null) {
        val adminId = supabaseRepo.currentUserId
        if (adminId.isNullOrBlank()) {
            _state.update { it.copy(error = "You are not signed in. Please sign in and try again.") }
            return
        }
        val status = if (approve) BeneficiaryClaimStatus.UNDER_REVIEW else BeneficiaryClaimStatus.REJECTED
        
        viewModelScope.launch {
            _state.update { it.copy(isProcessingClaim = true) }
            claimRepo.updateClaimStatus(
                claimId = claimId,
                status = status,
                reviewedBy = adminId,
                adminNotes = adminNotes,
                rejectionReason = if (!approve) adminNotes else null
            ).onSuccess {
                _state.update { it.copy(isProcessingClaim = false, successMessage = if (approve) "Claim verified and under review." else "Claim rejected.") }
            }.onFailure { error ->
                _state.update { it.copy(isProcessingClaim = false, error = error.toUserMessage()) }
            }
        }
    }

    fun escalateClaim(claimId: String) {
        val adminId = supabaseRepo.currentUserId
        if (adminId.isNullOrBlank()) {
            _state.update { it.copy(error = "You are not signed in. Please sign in and try again.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isProcessingClaim = true) }
            claimRepo.updateClaimStatus(
                claimId = claimId,
                status = BeneficiaryClaimStatus.ESCALATED,
                reviewedBy = adminId,
                adminNotes = "Escalated to platform admin for final payout approval."
            ).onSuccess {
                _state.update { it.copy(isProcessingClaim = false, successMessage = "Claim escalated to platform admin.") }
            }.onFailure { error ->
                _state.update { it.copy(isProcessingClaim = false, error = error.toUserMessage()) }
            }
        }
    }

    fun clearClaimProcessing() {
        _state.update { it.copy(isProcessingClaim = false) }
    }

    fun selectLedgerEntry(entry: LedgerEntry?) {
        _state.update { it.copy(selectedLedgerEntry = entry) }
    }

    override fun onCleared() {
        super.onCleared()
        cancelGroupJobs()
        managedGroupsJob?.cancel()
        // No hard state reset here to prevent race conditions during fast navigation transitions.
        // Caches are managed by singleton services.
    }
}
