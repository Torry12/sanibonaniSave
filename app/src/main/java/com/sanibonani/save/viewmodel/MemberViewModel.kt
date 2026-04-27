package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.data.FileUploadLimits
import com.sanibonani.save.data.remote.Feature
import com.sanibonani.save.data.remote.GeoapifyService
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.data.utils.applyUploadedMemberDocument
import com.sanibonani.save.data.utils.filterNotificationsForMember
import com.sanibonani.save.data.utils.memberDocumentLabel
import com.sanibonani.save.data.utils.mergeUploadedMemberDocument
import com.sanibonani.save.data.utils.partitionMemberNotifications
import com.sanibonani.save.data.utils.buildSupabaseAuthHeaders
import com.sanibonani.save.data.utils.requiresSupabaseAuthHeaders
import com.sanibonani.save.data.utils.sanitizeMemberDocumentUploadError
import com.sanibonani.save.data.utils.shouldRefreshProfileImageVersion
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.data.validation.ValidationResult
import com.sanibonani.save.data.validation.ValidationUtils
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.RegisterMemberUseCase
import com.sanibonani.save.domain.usecase.SendNotificationUseCase
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.viewmodel.state.MemberEvent
import com.sanibonani.save.viewmodel.state.MemberUiState
import com.sanibonani.save.viewmodel.state.RegisterMemberState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * ViewModel for the Member Portal.
 *
 * Handles member-specific operations including:
 * - Viewing and managing memberships
 * - Beneficiary management
 * - Document uploads
 * - Communication with admins
 * - Registration flow
 *
 * Follows MVVM architecture with clean separation of concerns.
 * All business logic is delegated to use cases and repositories.
 */
@HiltViewModel
class MemberViewModel @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val memberRepo: MemberRepository,
    private val groupRepo: GroupRepository,
    private val beneficiaryRepo: BeneficiaryRepository,
    private val notificationRepo: NotificationRepository,
    private val exportRepo: ExportRepository,
    private val loanRepo: LoanRepository,
    private val registerMemberUseCase: RegisterMemberUseCase,
    private val sendNotificationUseCase: SendNotificationUseCase,
    private val geoapifyService: GeoapifyService,
    private val contextCacheService: MemberGroupContextCacheService
) : ViewModel() {

    // ══════════════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    private val _uiState = MutableStateFlow(MemberUiState())
    val uiState: StateFlow<MemberUiState> = _uiState.asStateFlow()

    private val _registerState = MutableStateFlow(RegisterMemberState())
    val registerState: StateFlow<RegisterMemberState> = _registerState.asStateFlow()

    /** Channel for one-time events that UI should consume */
    private val _events = Channel<MemberEvent>(Channel.BUFFERED)
    val events: Flow<MemberEvent> = _events.receiveAsFlow()

    // ══════════════════════════════════════════════════════════════════════════
    // JOB MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    private var membershipObservationJob: Job? = null
    private var groupObservationJob: Job? = null
    private var memberDataObservationJob: Job? = null
    private var notificationObservationJob: Job? = null
    private var beneficiaryObservationJob: Job? = null
    private var loanObservationJob: Job? = null
    private var cacheObservationJob: Job? = null
    private var addressSearchJob: Job? = null
    private var activeObservedGroupId: String? = null
    private var observationVersion: Long = 0L

    // ══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ══════════════════════════════════════════════════════════════════════════

    init {
        observeCacheFreshness()
        loadUserMemberships()
    }

    private fun observeCacheFreshness() {
        cacheObservationJob?.cancel()
        cacheObservationJob = viewModelScope.launch {
            contextCacheService.contexts
                .map { contexts ->
                    contexts.mapNotNull { (groupId, context) ->
                        val ts = context.lastUpdatedMillis
                        if (ts > 0L) groupId to ts else null
                    }.toMap()
                }
                .distinctUntilChanged()
                .collect { freshness ->
                    _uiState.update { it.copy(cacheLastSyncByGroup = freshness) }
                }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MEMBERSHIP MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Loads all memberships for the current user.
     * Automatically switches to the most recent membership if available.
     * Uses distinctUntilChanged to prevent unnecessary recompositions.
     */
    private fun loadUserMemberships() {
        val userId = supabaseRepo.currentUserId ?: return
        contextCacheService.ensureUserSession(userId)

        membershipObservationJob?.cancel()
        membershipObservationJob = viewModelScope.launch {
            // Only set loading if we don't have data yet
            if (_uiState.value.member == null) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            memberRepo.observeMemberships(userId)
                .distinctUntilChanged()
                .collect { result ->
                    result.fold(
                        onSuccess = { memberships ->
                            _uiState.update { it.copy(memberships = memberships) }
                            contextCacheService.warmMembershipsInBackground(userId, memberships)
                            handleMembershipsLoaded(memberships)
                        },
                        onFailure = { error ->
                            handleMembershipsError(error)
                        }
                    )
                }
        }
    }

    /**
     * Handles successful membership load - determines which group to observe.
     */
    private fun handleMembershipsLoaded(memberships: List<Member>) {
        if (memberships.isEmpty()) {
            // No memberships yet, stop loading
            if (_uiState.value.member == null) {
                _uiState.update { it.copy(isLoading = false) }
            }
            return
        }

        val currentId = _uiState.value.currentGroupId
        val targetGroup = selectTargetGroup(memberships, currentId)

        // Avoid tearing down and recreating live observers for the same active group.
        if (hasActiveObservationFor(targetGroup.groupId)) {
            return
        }
        startRealtimeObservation(targetGroup.groupId)
    }

    /**
     * Selects the most appropriate group to observe.
     * Prioritizes current selection, falls back to most recently joined.
     */
    private fun selectTargetGroup(memberships: List<Member>, currentGroupId: String?): Member {
        // If we have a current selection and it exists in memberships, keep it
        if (currentGroupId != null) {
            memberships.find { it.groupId == currentGroupId }?.let { return it }
        }
        // Otherwise, select the most recently joined membership
        return memberships.maxByOrNull { it.joinedAt ?: "" } ?: memberships.first()
    }

    /**
     * Handles errors during membership loading.
     * Only shows error if we have no cached data.
     */
    private fun handleMembershipsError(error: Throwable) {
        val hasLocalData = _uiState.value.member != null
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                error = if (!hasLocalData) error.toUserMessage() else null
            )
        }
    }

    /**
     * Switches the active group context.
     * Performs a deep state reset to prevent data leakage between groups.
     */
    fun switchGroup(groupId: String) {
        // Skip if already on this group with loaded data
        if (_uiState.value.currentGroupId == groupId &&
            _uiState.value.group != null &&
            !_uiState.value.isLoading
        ) return

        if (!applyCachedContext(groupId)) {
            // Deep state reset for group switch when no cached context exists.
            _uiState.update { resetStateForGroupSwitch(it, groupId) }
        }
        startRealtimeObservation(groupId)
    }

    /**
     * Resets UI state when switching groups to prevent data leakage.
     */
    private fun resetStateForGroupSwitch(current: MemberUiState, newGroupId: String): MemberUiState {
        return current.copy(
            currentGroupId = newGroupId,
            member = null,
            group = null,
            contributions = emptyList(),
            beneficiaries = emptyList(),
            calculation = null,
            notifications = emptyList(),
            messages = emptyList(),
            isLoading = true,
            error = null,
            messageSentSuccess = false,
            messageText = ""
        )
    }

    /**
     * Starts real-time observation for a specific group.
     * Sets up flows for member, group, contributions, notifications, and beneficiaries.
     * Only shows loading indicator when switching to a new group without cached data.
     */
    fun startRealtimeObservation(groupId: String) {
        val userId = supabaseRepo.currentUserId ?: return
        val cachedContext = contextCacheService.getContext(groupId)

        if (hasActiveObservationFor(groupId)) {
            return
        }

        // New request supersedes all existing collectors; stale emissions must be ignored.
        val requestVersion = ++observationVersion
        groupObservationJob?.cancel()
        memberDataObservationJob?.cancel()
        notificationObservationJob?.cancel()
        beneficiaryObservationJob?.cancel()

        viewModelScope.launch {
            val role = withTimeoutOrNull(2500) {
                runCatching { supabaseRepo.getUserRole() }.getOrNull()
            } ?: _uiState.value.userRole

            if (requestVersion != observationVersion) return@launch

            groupObservationJob = viewModelScope.launch {
                // Only show loading if we're switching to a different group with no data
                val needsLoading = (_uiState.value.currentGroupId != groupId || _uiState.value.member == null) &&
                    cachedContext?.member == null
                _uiState.update {
                    it.copy(
                        isLoading = needsLoading,
                        userRole = role,
                        currentGroupId = groupId
                    )
                }

                // Hydrate from cache immediately, then fall back to repository fetch.
                if (cachedContext != null) {
                    _uiState.update {
                        it.copy(
                            member = cachedContext.member ?: it.member,
                            group = cachedContext.group ?: it.group,
                            contributions = cachedContext.contributions,
                            beneficiaries = cachedContext.beneficiaries,
                            loans = cachedContext.loans,
                            loanRepayments = cachedContext.loanRepayments,
                            notifications = cachedContext.notifications,
                            messages = cachedContext.messages,
                            calculation = cachedContext.calculation,
                            isLoading = false
                        )
                    }
                } else {
                    // Initial member fetch to get memberId for dependent flows.
                    val initialMember = memberRepo.getMemberByUserId(userId, groupId).getOrNull()
                    if (requestVersion != observationVersion) return@launch
                    _uiState.update { state ->
                        state.copy(
                            member = initialMember ?: state.member,
                            isLoading = false
                        )
                    }
                }

                // Launch concurrent observation flows
                val memberFlow = memberRepo.observeMemberByUserId(userId, groupId)
                    .distinctUntilChanged()
                    .shareIn(
                        scope = this,
                        started = SharingStarted.WhileSubscribed(5_000),
                        replay = 1
                    )

                memberDataObservationJob = observeMemberAndCalculation(memberFlow, groupId, requestVersion)
                notificationObservationJob = observeNotifications(groupId, requestVersion)
                beneficiaryObservationJob = observeBeneficiaries(memberFlow, groupId, requestVersion)
                loanObservationJob = observeLoans(memberFlow, groupId, requestVersion)
                activeObservedGroupId = groupId
            }
        }
    }

    /**
     * Observes member, group, and contributions - calculates payment status.
     * Uses distinctUntilChanged to prevent unnecessary recompositions.
     */
    private fun observeMemberAndCalculation(
        memberFlow: Flow<Result<Member?>>,
        groupId: String,
        requestVersion: Long
    ): Job = viewModelScope.launch {
        val groupFlow = groupRepo.observeGroup(groupId)

        // IMPORTANT:
        // Do NOT tie contribution loading to a one-time `initialMember` fetch.
        // If that initial call fails (network/RLS/schema mismatch), the UI would show a member
        // (from Room/realtime) but keep an empty transaction list forever.
        val contribFlow: Flow<Result<List<Contribution>>> = observeForCurrentMemberId(
            memberFlow = memberFlow,
            emptyValue = emptyList(),
            observer = { memberId -> memberRepo.getMemberContributions(memberId, groupId) }
        )

        combine(memberFlow, groupFlow, contribFlow) { memberRes, groupRes, contribRes ->
            Triple(memberRes, groupRes, contribRes)
        }
            .distinctUntilChanged()
            .collect { (memberRes, groupRes, contribRes) ->
                if (isStaleObservation(groupId, requestVersion)) {
                    return@collect
                }
                val member = memberRes.getOrNull()
                val group = groupRes.getOrNull()
                val contributions = contribRes.getOrElse { emptyList() }
                val memberError = memberRes.exceptionOrNull()
                val groupError = groupRes.exceptionOrNull()

                if (memberError != null || groupError != null) {
                    val uiError = memberError?.toUserMessage() ?: groupError?.toUserMessage()
                    _uiState.update { state ->
                        state.copy(
                            error = uiError,
                            isLoading = false
                        )
                    }
                }

                val contribError = contribRes.exceptionOrNull()
                // Surface contribution-loading issues instead of silently showing "No transactions".
                if (contribError != null) {
                    _uiState.update { state ->
                        state.copy(
                            error = contribError.toUserMessage(),
                            isLoading = false
                        )
                    }
                }

                val calculation = if (member != null && group != null) {
                    PaymentCalculator.calculateStatus(group, member, contributions)
                } else null

                _uiState.update { state ->
                    state.copy(
                        member = member ?: state.member,
                        group = group ?: state.group,
                        contributions = contributions,
                        calculation = calculation,
                        isLoading = false
                    )
                }

                contextCacheService.updateContext(groupId) { cached ->
                    cached.copy(
                        member = member ?: cached.member,
                        group = group ?: cached.group,
                        contributions = contributions,
                        calculation = calculation
                    )
                }
            }
    }

    /**
     * Observes notifications for the group, partitioned into system and messages.
     * Uses distinctUntilChanged to prevent unnecessary recompositions.
     */
    private fun observeNotifications(groupId: String, requestVersion: Long): Job = viewModelScope.launch {
        notificationRepo.observeNotifications(groupId)
            .distinctUntilChanged()
            .collect { result ->
                if (isStaleObservation(groupId, requestVersion)) {
                    return@collect
                }
                result.onSuccess { allNotifs ->
                    val currentMember = _uiState.value.member
                    val myNotifs = filterNotificationsForMember(allNotifs, currentMember?.id)
                    val (messages, systemNotifs) = partitionMemberNotifications(myNotifs)

                    _uiState.update { state ->
                        state.copy(
                            notifications = systemNotifs,
                            messages = messages
                        )
                    }
                    contextCacheService.updateContext(groupId) { cached ->
                        cached.copy(
                            notifications = systemNotifs,
                            messages = messages
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { it.copy(error = error.toUserMessage()) }
                }
            }
    }

    /**
     * Observes beneficiaries for the current member.
     * Uses distinctUntilChanged to prevent unnecessary recompositions.
     */
    private fun observeBeneficiaries(
        memberFlow: Flow<Result<Member?>>,
        groupId: String,
        requestVersion: Long
    ): Job = viewModelScope.launch {
        // Reuse the active member stream to avoid duplicate DB/network observation work.
        observeForCurrentMemberId(
            memberFlow = memberFlow,
            emptyValue = emptyList(),
            observer = beneficiaryRepo::observeBeneficiaries
        )
            .distinctUntilChanged()
            .collect { result ->
                if (isStaleObservation(groupId, requestVersion)) {
                    return@collect
                }
                result.onSuccess { beneficiaries ->
                    _uiState.update { it.copy(beneficiaries = beneficiaries) }
                    contextCacheService.updateContext(groupId) { cached ->
                        cached.copy(beneficiaries = beneficiaries)
                    }
                }.onFailure { error ->
                    _uiState.update { it.copy(error = error.toUserMessage()) }
                }
            }
    }

    /**
     * Observes loans and their repayments for the current member.
     */
    private fun observeLoans(
        memberFlow: Flow<Result<Member?>>,
        groupId: String,
        requestVersion: Long
    ): Job = viewModelScope.launch {
        val loansFlow = observeForCurrentMemberId(
            memberFlow = memberFlow,
            emptyValue = emptyList(),
            observer = loanRepo::getMemberLoans
        )

        loansFlow
            .distinctUntilChanged()
            .collect { result ->
                if (isStaleObservation(groupId, requestVersion)) {
                    return@collect
                }
                result.onSuccess { loans ->
                    _uiState.update { it.copy(loans = loans) }
                    contextCacheService.updateContext(groupId) { it.copy(loans = loans) }
                    
                    // If there's an active loan, observe its repayments
                    val activeLoan = loans.find { it.status == LoanStatus.APPROVED || it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID }
                    if (activeLoan != null) {
                        observeRepayments(activeLoan.id ?: "", groupId, requestVersion)
                    }
                }.onFailure { error ->
                    _uiState.update { it.copy(error = error.toUserMessage()) }
                }
            }
    }

    private var repaymentsJob: Job? = null
    private fun observeRepayments(loanId: String, groupId: String, requestVersion: Long) {
        repaymentsJob?.cancel()
        repaymentsJob = viewModelScope.launch {
            loanRepo.getRepayments(loanId)
                .distinctUntilChanged()
                .collect { result ->
                    if (isStaleObservation(groupId, requestVersion)) {
                        return@collect
                    }
                    result.onSuccess { repayments ->
                        _uiState.update { it.copy(loanRepayments = repayments) }
                        contextCacheService.updateContext(groupId) { it.copy(loanRepayments = repayments) }
                    }
                }
        }
    }

    private fun applyCachedContext(groupId: String): Boolean {
        val cached = contextCacheService.getContext(groupId) ?: return false
        val hasUsefulData = cached.member != null ||
            cached.group != null ||
            cached.contributions.isNotEmpty() ||
            cached.beneficiaries.isNotEmpty() ||
            cached.notifications.isNotEmpty() ||
            cached.messages.isNotEmpty()
        if (!hasUsefulData) return false

        _uiState.update { state ->
            state.copy(
                currentGroupId = groupId,
                member = cached.member,
                group = cached.group,
                contributions = cached.contributions,
                beneficiaries = cached.beneficiaries,
                loans = cached.loans,
                loanRepayments = cached.loanRepayments,
                calculation = cached.calculation,
                notifications = cached.notifications,
                messages = cached.messages,
                isLoading = false,
                error = null,
                messageSentSuccess = false,
                messageText = ""
            )
        }
        return true
    }

    private fun hasActiveObservationFor(groupId: String): Boolean {
        val state = _uiState.value
        return activeObservedGroupId == groupId &&
            memberDataObservationJob?.isActive == true &&
            notificationObservationJob?.isActive == true &&
            beneficiaryObservationJob?.isActive == true &&
            loanObservationJob?.isActive == true &&
            state.member != null &&
            state.group != null &&
            !state.isLoading
    }

    private fun isStaleObservation(groupId: String, requestVersion: Long): Boolean {
        return requestVersion != observationVersion || _uiState.value.currentGroupId != groupId
    }

    private fun <T> observeForCurrentMemberId(
        memberFlow: Flow<Result<Member?>>,
        emptyValue: T,
        observer: (String) -> Flow<Result<T>>
    ): Flow<Result<T>> {
        return memberFlow
            .map { it.getOrNull()?.id }
            .distinctUntilChanged()
            .flatMapLatest { memberId ->
                if (memberId.isNullOrBlank()) {
                    flowOf(Result.success(emptyValue))
                } else {
                    observer(memberId)
                }
            }
    }


    /**
     * Reloads member data. Public entry point for refresh operations.
     */
    fun loadMemberData() {
        loadUserMemberships()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BENEFICIARY MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Adds a new beneficiary for the current member.
     */
    fun addBeneficiary(
        name: String,
        idNumber: String?,
        relationship: String?,
        dob: String?,
        isOver65: Boolean = false
    ) {
        val memberId = _uiState.value.member?.id ?: return
        val groupId = _uiState.value.group?.id ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val beneficiary = Beneficiary(
                groupId = groupId,
                memberId = memberId,
                fullName = name,
                idNumber = idNumber,
                relationship = relationship,
                dateOfBirth = dob,
                isOver65 = isOver65
            )
            
            beneficiaryRepo.addBeneficiary(beneficiary)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    sendEvent(MemberEvent.ShowMessage("Beneficiary added successfully"))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.toUserMessage()) }
                }
        }
    }

    /**
     * Updates an existing beneficiary.
     */
    fun updateBeneficiary(
        id: String,
        name: String,
        idNumber: String?,
        relationship: String?,
        dob: String?,
        isOver65: Boolean = false
    ) {
        val memberId = _uiState.value.member?.id ?: return
        val groupId = _uiState.value.group?.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val beneficiary = Beneficiary(
                id = id,
                groupId = groupId,
                memberId = memberId,
                fullName = name,
                idNumber = idNumber,
                relationship = relationship,
                dateOfBirth = dob,
                isOver65 = isOver65
            )

            beneficiaryRepo.updateBeneficiary(beneficiary)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    sendEvent(MemberEvent.ShowMessage("Beneficiary updated successfully"))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.toUserMessage()) }
                }
        }
    }

    /**
     * Deletes a beneficiary.
     */
    fun deleteBeneficiary(id: String) {
        val memberId = _uiState.value.member?.id ?: return
        val groupId = _uiState.value.group?.id ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            beneficiaryRepo.deleteBeneficiary(groupId, memberId, id)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    sendEvent(MemberEvent.ShowMessage("Beneficiary removed"))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.toUserMessage()) }
                }
        }
    }

    /**
     * Uploads a document for a specific beneficiary.
     */
    fun uploadBeneficiaryDocument(
        beneficiaryId: String,
        byteArray: ByteArray,
        fileName: String
    ) {
        if (byteArray.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
            sendEvent(MemberEvent.ShowMessage("File size exceeds 3MB limit"))
            return
        }

        val memberId = _uiState.value.member?.id ?: return
        val groupId = _uiState.value.group?.id ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }
            
            beneficiaryRepo.uploadBeneficiaryDocument(
                beneficiaryId = beneficiaryId,
                groupId = groupId,
                memberId = memberId,
                byteArray = byteArray,
                fileName = fileName
            ).onSuccess { url ->
                _uiState.update { it.copy(isUploading = false) }
                sendEvent(MemberEvent.ShowMessage("Beneficiary document uploaded successfully"))
            }.onFailure { error ->
                val userMessage = sanitizeMemberDocumentUploadError(error.toUserMessage())
                _uiState.update { it.copy(isUploading = false, error = userMessage) }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DOCUMENT MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Uploads a document for the current member.
     */
    fun uploadDocument(
        documentIndex: Int,
        byteArray: ByteArray,
        fileName: String,
        documentType: String? = null
    ) {
        if (byteArray.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
            sendEvent(MemberEvent.ShowMessage("File size exceeds 3MB limit"))
            return
        }

        val memberId = _uiState.value.member?.id
        val groupId = _uiState.value.member?.groupId

        if (memberId.isNullOrBlank() || groupId.isNullOrBlank()) {
            sendEvent(MemberEvent.ShowMessage("Unable to upload: Member context not loaded"))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null, uploadProgress = 0.0) }
            
            val label = memberDocumentLabel(documentIndex)

            // IMPORTANT: The Member UI currently displays the 5 fixed document slots from the
            // `members` table (document_1_url ... document_5_url). Therefore uploads must update
            // those fields via `memberRepo.uploadMemberDocument(...)`.
            // (The relational `member_documents` table is used for advanced/admin views.)
            memberRepo.uploadMemberDocument(
                memberId = memberId,
                documentIndex = documentIndex,
                byteArray = byteArray,
                fileName = fileName,
                documentType = documentType
            ).onSuccess { uploadedUrl ->
                // Optimistic local update prevents a stale avatar/doc UI while remote sync catches up.
                applyUploadedDocumentToState(documentIndex, uploadedUrl, documentType)

                _uiState.update { it.copy(isUploading = false, uploadProgress = 1.0) }
                sendEvent(MemberEvent.ShowMessage("$label uploaded successfully"))

                // Reconcile from backend in the background without delaying immediate UI feedback.
                reconcileUploadedDocument(memberId, documentIndex, uploadedUrl, documentType)
            }.onFailure { error ->
                val userMessage = sanitizeMemberDocumentUploadError(error.toUserMessage())
                _uiState.update { it.copy(isUploading = false, uploadProgress = null, error = userMessage) }
            }
        }
    }

    private fun reconcileUploadedDocument(
        memberId: String,
        documentIndex: Int,
        uploadedUrl: String,
        documentType: String?
    ) {
        viewModelScope.launch {
            delay(350)
            memberRepo.getMemberById(memberId)
                .onSuccess { refreshed ->
                    _uiState.update { state ->
                        val merged = refreshed.mergeUploadedMemberDocument(
                            documentIndex = documentIndex,
                            uploadedUrl = uploadedUrl,
                            documentType = documentType
                        )
                        state.copy(
                            member = merged,
                            profileImageVersion = if (shouldRefreshProfileImageVersion(documentIndex)) System.currentTimeMillis() else state.profileImageVersion
                        )
                    }
                }
        }
    }

    private fun applyUploadedDocumentToState(
        documentIndex: Int,
        uploadedUrl: String,
        documentType: String?
    ) {
        _uiState.update { state ->
            val currentMember = state.member
            if (currentMember == null) return@update state

            val updatedMember = currentMember.applyUploadedMemberDocument(
                documentIndex = documentIndex,
                uploadedUrl = uploadedUrl,
                documentType = documentType
            )

            state.copy(
                member = updatedMember,
                profileImageVersion = if (shouldRefreshProfileImageVersion(documentIndex)) System.currentTimeMillis() else state.profileImageVersion
            )
        }
    }

    /**
     * Gets authorization headers for document downloads.
     */
    fun getDownloadParams(url: String): Map<String, String> {
        return if (requiresSupabaseAuthHeaders(url)) {
            buildSupabaseAuthHeaders(BuildConfig.SUPABASE_ANON_KEY, supabaseRepo.accessToken)
        } else {
            emptyMap()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REGISTRATION FLOW
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Updates a registration form field.
     */
    fun onFieldChange(field: String, value: String) {
        val cleanValue = value.trimEnd()

        _registerState.update { state ->
            updateRegistrationField(state, field, cleanValue)
        }
        
        // Trigger address search for address-related fields
        if (field in ADDRESS_SEARCH_FIELDS && cleanValue.length >= MIN_ADDRESS_QUERY_LENGTH) {
            searchAddress(cleanValue)
        }
    }

    /**
     * Updates the appropriate field in registration state.
     */
    private fun updateRegistrationField(state: RegisterMemberState, field: String, value: String): RegisterMemberState {
        return when (field) {
            FIELD_FULL_NAME -> state.copy(fullName = value)
            FIELD_ID_NUMBER -> state.copy(idNumber = value)
            FIELD_PHONE -> state.copy(phone = value)
            FIELD_EMAIL -> state.copy(email = value)
            FIELD_STREET -> state.copy(street = value)
            FIELD_SUBURB -> state.copy(suburb = value)
            FIELD_CITY -> state.copy(city = value)
            FIELD_PROVINCE -> state.copy(province = value)
            else -> state
        }
    }

    /**
     * Searches for address suggestions using Geoapify.
     */
    private fun searchAddress(query: String) {
        addressSearchJob?.cancel()

        if (query.length < MIN_ADDRESS_QUERY_LENGTH) {
            _registerState.update { it.copy(addressSuggestions = emptyList()) }
            return
        }

        addressSearchJob = viewModelScope.launch {
            delay(ADDRESS_SEARCH_DEBOUNCE_MS)

            try {
                _registerState.update { it.copy(isSearchingAddress = true) }
                val response = geoapifyService.autocomplete(query, BuildConfig.GEOAPIFY_API_KEY)
                _registerState.update {
                    it.copy(addressSuggestions = response.features, isSearchingAddress = false)
                }
            } catch (e: Exception) {
                _registerState.update { it.copy(isSearchingAddress = false) }
            }
        }
    }

    /**
     * Applies a selected address suggestion to the form.
     */
    fun onAddressSelected(feature: Feature) {
        val props = feature.properties

        _registerState.update { state ->
            state.copy(
                street = props.street
                    ?: props.housenumber?.let { "$it ${props.street}" }
                    ?: state.street,
                suburb = props.suburb
                    ?: props.neighbourhood
                    ?: props.quarter
                    ?: state.suburb,
                city = props.city
                    ?: props.township
                    ?: props.village
                    ?: state.city,
                province = props.state ?: state.province,
                addressSuggestions = emptyList()
            )
        }
    }

    /**
     * Sets the notification preference for registration.
     */
    fun setNotificationPref(pref: NotificationPref) {
        _registerState.update { it.copy(notificationPref = pref) }
    }

    /**
     * Initializes the registration form for a specific group.
     * Pre-fills data from existing memberships if available.
     */
    fun initializeRegistration(groupId: String) {
        val userId = supabaseRepo.currentUserId
        val userEmail = supabaseRepo.currentSession?.user?.email ?: ""
        
        // Reset form state
        _registerState.update {
            RegisterMemberState(
                email = userEmail,
                targetGroupId = groupId
            )
        }

        viewModelScope.launch {
            // Load group joining fee
            groupRepo.getGroupById(groupId).onSuccess { group ->
                _registerState.update { it.copy(joiningFee = group.joiningFee) }
            }
            
            // Check for existing membership
            if (userId != null) {
                checkExistingMembership(userId, groupId)
            }
        }
    }

    /**
     * Checks if user already has membership in the target group.
     */
    private suspend fun checkExistingMembership(userId: String, groupId: String) {
        memberRepo.getMemberByUserId(userId, groupId)
            .onSuccess { existing ->
                // `getMemberByUserId` returns a non-null Member when found.
                // If the membership doesn't exist, the repository will fail the Result.
                handleExistingMembership(existing)
            }
            .onFailure {
                // Pre-fill from any existing membership profile
                // (Also used as a graceful fallback when the lookup fails due to network/RLS.)
                prefillFromExistingProfile(userId)
            }
    }

    /**
     * Handles case where user already has membership in this group.
     */
    private fun handleExistingMembership(member: Member) {
        if (member.status == MemberStatus.PENDING_PAYMENT) {
            // User registered but hasn't paid - skip to payment
            _registerState.update { state ->
                state.copy(
                    fullName = member.fullName,
                    idNumber = member.idNumber ?: "",
                    phone = member.phone,
                    email = member.email ?: "",
                    street = member.street ?: "",
                    suburb = member.suburb ?: "",
                    city = member.city ?: "",
                    province = member.province ?: "",
                    success = true
                )
            }
        } else {
            // Already active member
            _registerState.update { it.copy(error = "You are already a member of this group.") }
        }
    }

    /**
     * Pre-fills registration form from existing membership profile.
     */
    private suspend fun prefillFromExistingProfile(userId: String) {
        memberRepo.getMemberships(userId).onSuccess { memberships ->
            if (memberships.isNotEmpty()) {
                val profile = memberships.first()
                _registerState.update { state ->
                    state.copy(
                        fullName = profile.fullName,
                        idNumber = profile.idNumber ?: "",
                        phone = profile.phone,
                        street = profile.street ?: "",
                        suburb = profile.suburb ?: "",
                        city = profile.city ?: "",
                        province = profile.province ?: "",
                    )
                }
            }
        }
    }

    /**
     * Submits the member registration form.
     */
    fun submit(groupId: String, txId: String? = null) {
        val state = _registerState.value
        val userId = supabaseRepo.currentUserId

        // Validate fields
        val validation = ValidationUtils.validateMemberFields(
            state.fullName, state.idNumber, state.phone, state.email,
            state.street, state.suburb, state.city, state.province
        )

        if (validation !is ValidationResult.Valid) {
            _registerState.update { it.copy(error = (validation as ValidationResult.Error).message) }
            return
        }

        if (userId.isNullOrBlank()) {
            _registerState.update { it.copy(error = "You are not signed in.") }
            return
        }

        viewModelScope.launch {
            _registerState.update { it.copy(isSubmitting = true, error = null) }

            val member = createMemberFromState(state, groupId, userId, txId)

            registerMemberUseCase(member, txId)
                .onSuccess {
                    _registerState.update {
                        it.copy(isSubmitting = false, success = true, transactionId = txId)
                    }
                    // Emit navigation event after successful registration
                    if (txId != null) {
                        sendEvent(MemberEvent.NavigateToLanding)
                    }
                }
                .onFailure { error ->
                    _registerState.update {
                        it.copy(isSubmitting = false, error = error.toUserMessage())
                    }
                }
        }
    }

    /**
     * Creates a Member object from the current registration state.
     */
    private fun createMemberFromState(
        state: RegisterMemberState,
        groupId: String,
        userId: String,
        txId: String?
    ): Member {
        return Member(
            groupId = groupId,
            userId = userId,
            fullName = state.fullName,
            idNumber = state.idNumber,
            phone = state.phone,
            email = state.email,
            street = state.street,
            suburb = state.suburb,
            city = state.city,
            province = state.province,
            notificationPref = state.notificationPref,
            status = if (txId != null) MemberStatus.ACTIVE else MemberStatus.PENDING_PAYMENT
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MESSAGING
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Updates the message text input.
     */
    fun updateMessageText(text: String) {
        _uiState.update { it.copy(messageText = text) }
    }

    /**
     * Sends a message to the group admin.
     */
    fun sendMessageToAdmin() {
        val groupId = _uiState.value.group?.id
        val memberId = _uiState.value.member?.id
        val message = _uiState.value.messageText

        if (groupId.isNullOrBlank() || memberId.isNullOrBlank()) {
            val msg = "Unable to send message: membership context not loaded."
            _uiState.update { it.copy(error = msg) }
            sendEvent(MemberEvent.ShowMessage(msg))
            return
        }

        if (message.isBlank()) {
            val msg = "Please enter a message."
            _uiState.update { it.copy(error = msg) }
            sendEvent(MemberEvent.ShowMessage(msg))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingMessage = true) }
            
            sendNotificationUseCase(
                groupId = groupId,
                memberId = memberId,
                message = "MEMBER INQUIRY: $message",
                triggerEvent = NotifEvent.MEMBER_MESSAGE
            ).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isSendingMessage = false,
                        messageText = "",
                        messageSentSuccess = true
                    )
                }
            }.onFailure { error ->
                val userMessage = error.toUserMessage()
                _uiState.update { it.copy(isSendingMessage = false, error = userMessage) }
                sendEvent(MemberEvent.ShowMessage(userMessage))
            }
        }
    }

    /**
     * Dismisses the message sent success indicator.
     */
    fun dismissMessageSuccess() {
        _uiState.update { it.copy(messageSentSuccess = false) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOAN MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Requests a new loan.
     */
    fun requestLoan(amount: Double, months: Int, purpose: String) {
        val member = _uiState.value.member ?: return
        val group = _uiState.value.group ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Validate eligibility
            if (member.status != MemberStatus.ACTIVE) {
                _uiState.update { it.copy(isLoading = false, error = "Only active members can request loans.") }
                return@launch
            }

            val interestRate = group.loanInterestRate ?: 0.0
            val totalInterest = loanRepo.calculateInterest(amount, interestRate, months)
            
            val loan = Loan(
                memberId = member.id ?: "",
                groupId = group.id ?: "",
                amount = amount,
                interestRate = interestRate,
                totalToRepay = amount + totalInterest,
                totalRepaid = 0.0,
                monthlyRepayment = (amount + totalInterest) / months,
                startDate = "", // Will be set on approval
                endDate = "", // Will be set on approval
                status = LoanStatus.PENDING,
                purpose = purpose
            )

            loanRepo.requestLoan(loan)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    sendEvent(MemberEvent.ShowMessage("Loan request submitted successfully"))
                    
                    // Notify admins about the new loan request
                    sendNotificationUseCase(
                        groupId = group.id ?: "",
                        memberId = member.id ?: "",
                        message = "NEW LOAN REQUEST: ${member.fullName} requested R$amount for $months months.",
                        triggerEvent = NotifEvent.MEMBER_MESSAGE // Using MEMBER_MESSAGE as a generic trigger for now
                    )
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.toUserMessage()) }
                }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXPORT OPERATIONS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Exports the member's contribution statement to CSV.
     */
    fun exportMyStatement() {
        val group = _uiState.value.group ?: return
        val member = _uiState.value.member ?: return
        val contributions = _uiState.value.contributions

        if (contributions.isEmpty()) {
            sendEvent(MemberEvent.ShowMessage("No transactions to export yet."))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }

            exportRepo.exportContributionsToCsv(group, member, contributions)
                .onSuccess { file ->
                    _uiState.update { it.copy(isExporting = false, exportFile = file) }
                    sendEvent(MemberEvent.OpenFile(file, mimeType = "text/csv", chooserTitle = "Share My Statement"))
                }
                .onFailure { error ->
                    val userMessage = error.toUserMessage()
                    _uiState.update { it.copy(isExporting = false, error = userMessage) }
                    sendEvent(MemberEvent.ShowMessage(userMessage))
                }
        }
    }

    /**
     * Initiates PDF statement download.
     * Note: Actual PDF generation is handled by the UI layer via context-aware tools.
     */
    fun downloadPdfStatement() {
        val group = _uiState.value.group
        val member = _uiState.value.member
        val contributions = _uiState.value.contributions

        if (group?.id.isNullOrBlank() || member?.id.isNullOrBlank()) {
            val msg = "Unable to download statement: membership context not loaded."
            _uiState.update { it.copy(error = msg) }
            sendEvent(MemberEvent.ShowMessage(msg))
            return
        }

        if (contributions.isEmpty()) {
            sendEvent(MemberEvent.ShowMessage("No transactions to include in the PDF yet."))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null) }

            exportRepo.exportContributionsToPdf(group, member, contributions)
                .onSuccess { file ->
                    _uiState.update { it.copy(isExporting = false) }
                    sendEvent(MemberEvent.OpenFile(file, mimeType = "application/pdf", chooserTitle = "Open Statement PDF"))
                }
                .onFailure { error ->
                    val userMessage = error.toUserMessage()
                    _uiState.update { it.copy(isExporting = false, error = userMessage) }
                    sendEvent(MemberEvent.ShowMessage(userMessage))
                }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI STATE MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Selects a tab in the member portal.
     */
    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    /**
     * Clears any error message from the UI state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EVENT HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sends a one-time event to the UI.
     */
    private fun sendEvent(event: MemberEvent) {
        viewModelScope.launch {
            _events.send(event)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMPANION OBJECT - CONSTANTS
    // ══════════════════════════════════════════════════════════════════════════

    companion object {
        // Field names for registration form
        private const val FIELD_FULL_NAME = "fullName"
        private const val FIELD_ID_NUMBER = "idNumber"
        private const val FIELD_PHONE = "phone"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_STREET = "street"
        private const val FIELD_SUBURB = "suburb"
        private const val FIELD_CITY = "city"
        private const val FIELD_PROVINCE = "province"

        // Address search configuration
        private val ADDRESS_SEARCH_FIELDS = setOf(FIELD_STREET, FIELD_CITY, FIELD_SUBURB)
        private const val MIN_ADDRESS_QUERY_LENGTH = 3
        private const val ADDRESS_SEARCH_DEBOUNCE_MS = 500L
    }
}


