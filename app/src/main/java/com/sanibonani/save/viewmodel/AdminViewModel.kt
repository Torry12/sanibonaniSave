package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.data.utils.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.data.FileUploadLimits
import com.sanibonani.save.service.AdminGroupContextCacheService
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AdminUiState(
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val metrics: ActuarialMetrics = ActuarialMetrics(),
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
    val isProcessingLoan: Boolean = false
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
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val getManagedGroupsUseCase: GetManagedGroupsUseCase,
    private val calculateViabilityUseCase: CalculateViabilityUseCase,
    private val updateMemberStatusUseCase: UpdateMemberStatusUseCase,
    private val sendNotificationUseCase: SendNotificationUseCase,
    private val requestPayoutUseCase: RequestPayoutUseCase,
    private val validateLoanEligibilityUseCase: ValidateLoanEligibilityUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            supabaseRepo.sessionFlow.collect { session ->
                if (session != null) {
                    observeAdminData()
                }
            }
        }
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
                            error = e.toUserMessage() ?: "Failed to load groups"
                        )
                    }
                }
            }
        }
    }

    fun selectGroup(groupId: String) {
        // Only return early if we are already observing this group.
        // We check if currentObservedGroupId matches AND if the observation job is still alive.
        if (currentObservedGroupId == groupId && groupObservationJob?.isActive == true && _state.value.error == null) return
        
        val selectedGroupName = _state.value.managedGroups
            .firstOrNull { it.id == groupId }
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: "selected group"
        _state.update {
            it.copy(loadingMessage = "Loading $selectedGroupName data...")
        }
        
        currentObservedGroupId = groupId
        applyCachedGroupContext(groupId)
        startObservingGroup(groupId)
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
        // Cancel any existing observation jobs
        groupObservationJob?.cancel()
        groupLoansJob?.cancel()
        calculationsJob?.cancel()
        memberBeneficiariesJob?.cancel()
        memberDocumentsJob?.cancel()
        memberCalculationsJob?.cancel()
        
        _state.update { it.copy(
            currentGroupId = groupId, 
            isLoading = !hasCachedContext,
            error = null,
            // Clear group-specific transient state
            group = if (hasCachedContext) it.group else null,
            members = if (hasCachedContext) it.members else emptyList(),
            payouts = if (hasCachedContext) it.payouts else emptyList(),
            metrics = if (hasCachedContext) it.metrics else ActuarialMetrics(),
            settings = if (hasCachedContext) it.settings else GroupSettings(),
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
            notifications = if (hasCachedContext) it.notifications else emptyList(),
            memberMessages = if (hasCachedContext) it.memberMessages else emptyList()
        ) }
        
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
    }


    private fun refreshAllMemberCalculations(group: Group, members: List<Member>) {
        val groupId = group.id ?: return
        calculationsJob?.cancel()
        calculationsJob = viewModelScope.launch {
            // Optimization: Fetch all contributions for the entire group once
            val result = memberRepo.getGroupContributions(groupId).firstOrNull() ?: Result.success(emptyList())
            result.onSuccess { allContribs ->
                val allCalculations = withContext(Dispatchers.Default) {
                    val contribsByMember = allContribs.groupBy { it.memberId }
                    val calculations = mutableMapOf<String, PaymentCalculation>()
                    members.forEach { member ->
                        val memberId = member.id ?: return@forEach
                        val memberContribs = contribsByMember[memberId] ?: emptyList()
                        val calc = PaymentCalculator.calculateStatus(group, member, memberContribs)
                        calculations[memberId] = calc
                    }
                    calculations
                }
                _state.update { it.copy(memberCalculations = allCalculations) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }


    private fun refreshMetrics(groupId: String) {
        viewModelScope.launch {
            actuarialRepo.computeMetrics(groupId).onSuccess { m ->
                _state.update { it.copy(metrics = m) }
                adminContextCacheService.updateContext(groupId) { cached ->
                    cached.copy(metrics = m)
                }
            }
            .onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
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
        if (!hasUsefulData) return false

        _state.update { state ->
            val cachedGroup = cached.group
            state.copy(
                currentGroupId = groupId,
                group = cachedGroup,
                members = cached.members,
                payouts = cached.payouts,
                metrics = cached.metrics ?: state.metrics,
                feeStatus = cached.feeStatus ?: cachedGroup?.feeStatus ?: state.feeStatus,
                settings = cachedGroup?.let { toGroupSettings(it) } ?: state.settings,
                notifications = cached.notifications,
                memberMessages = cached.memberMessages,
                isLoading = false,
                error = null,
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
                memberCalculations = emptyMap()
            )
        }
        return true
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
            periodMonths = group.periodMonths.toString()
        )
    }

    fun requestRestore() {
        val group = state.value.group ?: return
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

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }

            // Update multiple settings at once: monthly contribution, goal, and period
            val updates = mapOf(
                "monthly_contribution" to plan.suggestedMonthlyContribution,
                "goal_amount" to plan.goalAmount,
                "period_months" to plan.periodMonths
            )

            groupRepo.updateGroupSettings(groupId, updates)
                .onSuccess {
                    _state.update { it.copy(
                        isSaving = false, 
                        saveSuccess = true,
                        successMessage = "Strategy applied! Monthly contribution updated to R${String.format(java.util.Locale.US, "%.2f", plan.suggestedMonthlyContribution)}",
                        settings = currentSettings.copy(
                            monthlyContribution = plan.suggestedMonthlyContribution.toString(),
                            goalAmount = plan.goalAmount.toString(),
                            periodMonths = plan.periodMonths.toString()
                        )
                    ) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
                }
        }
    }

    fun setTab(index: Int) {
        val current = _state.value
        if (current.selectedTab == index) return
        _state.update {
            it.copy(
                selectedTab = index,
                messageSentSuccess = false,
                loadingMessage = "Opening ${tabDisplayName(index)} form..."
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
                else -> s
            })
        }
    }

    fun saveSettings() {
        val groupId = state.value.group?.id ?: return
        val s = state.value.settings
        
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val updates = mutableMapOf<String, Any>(
                "joining_fee" to (s.joiningFee.toDoubleOrNull() ?: 0.0),
                "monthly_contribution" to (s.monthlyContribution.toDoubleOrNull() ?: 0.0),
                "late_fee" to (s.lateFee.toDoubleOrNull() ?: 0.0),
                "late_fee_grace_days" to (s.lateFeeGraceDays.toIntOrNull() ?: 0),
                "probation_months" to (s.probationMonths.toIntOrNull() ?: 3),
                "payment_due_day" to (s.paymentDueDay.toIntOrNull() ?: 28),
                "max_members" to (s.maxMembers.toIntOrNull() ?: 10),
                "allow_partial_payment" to s.allowPartialPayment,
                "auto_suspend_after" to (s.autoSuspendAfter.toIntOrNull() ?: 2),
                "bank_name" to (s.bankName),
                "account_number" to (s.accountNumber),
                "branch_code" to (s.branchCode),
                "account_type" to s.accountType,
                "max_beneficiaries" to (s.maxBeneficiaries.toIntOrNull() ?: 0),
                "beneficiary_increase_pct" to (s.beneficiaryIncreasePct.toDoubleOrNull() ?: 0.0),
                "goal_amount" to (s.goalAmount.toDoubleOrNull() ?: 10000.0),
                "period_months" to (s.periodMonths.toIntOrNull() ?: 12)
            )
            
            groupRepo.updateGroupSettings(groupId, updates)
                .onSuccess {
                    _state.update { it.copy(
                        isSaving = false, 
                        saveSuccess = true,
                        successMessage = "Settings saved successfully"
                    ) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
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

    fun approveLoan(loanId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessingLoan = true) }
            val result = loanRepo.approveLoan(loanId)
            _state.update { it.copy(
                isProcessingLoan = false,
                successMessage = if (result.isSuccess) "Loan approved successfully" else null,
                error = result.exceptionOrNull()?.message
            ) }
        }
    }

    fun rejectLoan(loanId: String, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessingLoan = true) }
            val result = loanRepo.rejectLoan(loanId, reason)
            _state.update { it.copy(
                isProcessingLoan = false,
                successMessage = if (result.isSuccess) "Loan rejected" else null,
                error = result.exceptionOrNull()?.message
            ) }
        }
    }

    fun clearLoanProcessing() {
        _state.update { it.copy(isProcessingLoan = false) }
    }

    fun downloadPdfStatement() = performExport(pdf = true)

    /**
     * Fetches group payments then exports them as CSV or PDF.
     * Shared implementation removes the duplicated fetch-then-export pattern.
     */
    private fun performExport(pdf: Boolean) {
        val group = state.value.group ?: return
        val groupId = group.id ?: return
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

    fun uploadMemberDocument(memberId: String, groupId: String, label: String, byteArray: ByteArray, fileName: String) {
        if (byteArray.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
            _state.update { it.copy(error = "File size exceeds 3MB limit") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, uploadProgress = 0.0) }
            memberDocumentRepo.uploadAndAddMemberDocument(
                memberId = memberId,
                groupId = groupId,
                label = label,
                byteArray = byteArray,
                fileName = fileName
            ).onSuccess {
                _state.update { it.copy(isUploading = false, uploadProgress = 1.0, successMessage = "Document uploaded successfully") }
            }.onFailure { e ->
                _state.update { it.copy(isUploading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun downloadMemberDocument(url: String, label: String): Pair<String, Map<String, String>> {
        return Pair(url, buildSupabaseAuthHeaders(BuildConfig.SUPABASE_ANON_KEY, supabaseRepo.accessToken))
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

        // Cancel previous member-specific observations
        memberBeneficiariesJob?.cancel()
        memberDocumentsJob?.cancel()
        memberCalculationsJob?.cancel()

        if (member != null) {
            val memberId = member.id ?: return
            
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

            memberRepo.updateMemberDocumentStatus(memberId, docIndex, status)
                .onSuccess {
                    _state.update { it.copy(
                        successMessage = "Document ${if (approve) "verified" else "rejected"} successfully"
                    ) }
                    
                    // Automatically update member status if document is rejected
                    if (!approve) {
                        updateMemberStatusUseCase(memberId, MemberStatus.SUSPENDED)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                    // Revert optimistic update by refreshing member data
                    memberRepo.getMemberById(memberId)
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

            memberDocumentRepo.updateMemberDocument(updatedDoc)
                .onSuccess {
                    _state.update { it.copy(successMessage = "Document ${if (approve) "verified" else "rejected"} successfully") }
                    // Automatically update member status if document is rejected
                    if (!approve) {
                        updateMemberStatusUseCase(memberId, MemberStatus.SUSPENDED)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage()) }
                    // Revert by re-fetching documents
                    memberDocumentRepo.syncMemberDocuments(memberId)
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
        val groupId = state.value.group?.id ?: return
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
        val groupId = state.value.group?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isRequestingPayout = true, error = null) }
            payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isRequestingPayout = false,
                            successMessage = "Request validated and escalated to platform admin"
                        )
                    }
                    refreshPayouts()
                }
                .onFailure { e ->
                    _state.update { it.copy(isRequestingPayout = false, error = e.toUserMessage()) }
                }
        }
    }

    private fun isValidAccount(acc: String) = acc.length in 7..13 && acc.all { it.isDigit() }
    private fun isValidBranch(branch: String) = branch.length == 6 && branch.all { it.isDigit() }

    fun refreshPayouts() {
        val group = state.value.group ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // Small delay to ensure DB/Network consistency if called immediately after success
            delay(500)
            payoutRepo.observePayouts(group.id ?: "").first().onSuccess { list ->
                _state.update { it.copy(payouts = list, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
            }
        }
    }

    fun submitPayoutRequest() {
        val s = _state.value
        val group = s.group ?: return
        val groupId = group.id ?: return
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
                    _state.update { it.copy(
                        isRequestingPayout = false, 
                        payoutRequestSuccess = true,
                        payoutAmount = "",
                        successMessage = "Request submitted for admin validation"
                    ) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isRequestingPayout = false, error = e.toUserMessage()) }
                }
        }
    }

    fun saveBeneficiary() {
        val b = _state.value.editingBeneficiary ?: return
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
}
