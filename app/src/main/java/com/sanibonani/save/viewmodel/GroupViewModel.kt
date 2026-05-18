package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.domain.config.FileUploadLimits
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.RoscaRotationMethod
import com.sanibonani.save.data.remote.Feature
import com.sanibonani.save.data.remote.GeoapifyService
import com.sanibonani.save.domain.repository.ExportRepository
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.data.utils.LocationUtils
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.data.utils.logAndGetMessage
import com.sanibonani.save.domain.validation.ValidationResult
import com.sanibonani.save.domain.validation.ValidationUtils
import com.sanibonani.save.domain.usecase.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.GetPublicGroupsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupListState(
    val rawGroups: List<Group> = emptyList(),
    val filteredGroups: List<Group> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val provinceFilter: String? = null,
    val typeFilter: GroupType? = null
)

data class GroupDetailState(
    val group: Group? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class RegisterGroupState(
    val name: String = "",
    val type: GroupType = GroupType.STOKVEL,
    val province: String = "",
    val city: String = "",
    val township: String = "",
    val description: String = "",
    val rotationMethod: RoscaRotationMethod = RoscaRotationMethod.FIXED,
    val joiningFee: String = "200",
    val monthlyContribution: String = "500",
    val lateFee: String = "50",
    val maxMembers: String = "50",
    val adminEmail: String = "",
    val adminFullName: String = "",
    val adminPhone: String = "",
    val adminIdNumber: String = "",
    val adminPassword: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val branchCode: String = "",
    val logoEmoji: String = "🤝",
    val maxBeneficiaries: String = "0",
    val beneficiaryIncreasePct: String = "0",
    val lateFeeGraceDays: String = "5",
    val probationMonths: String = "3",
    val paymentDueDay: String = "28",
    val termsAccepted: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val needsPayment: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
    val createdGroupId: String? = null,
    val addressSuggestions: List<Feature> = emptyList(),
    val isSearchingAddress: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val allowPartialPayment: Boolean = false,
    val goalAmount: String = "0",
    val periodMonths: String = "12",
    val geohash: String? = null,
    val currentStep: Int = 1,
    val totalSteps: Int = 6,
    val isLoggedIn: Boolean = false,
    val constitutionUrl: String? = null,
    val constitutionStatus: DocumentStatus = DocumentStatus.PENDING,
    val pendingConstitutionName: String? = null,
    val useStandardConstitution: Boolean = false
)

private data class PendingConstitutionUpload(
    val bytes: ByteArray,
    val fileName: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingConstitutionUpload) return false
        return fileName == other.fileName && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + fileName.hashCode()
}

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groupRepo: GroupRepository,
    private val exportRepo: ExportRepository,
    private val createGroupUseCase: CreateGroupUseCase,
    private val getPublicGroupsUseCase: GetPublicGroupsUseCase,
    private val geoapifyService: GeoapifyService
) : ViewModel() {

    private var pendingConstitutionUpload: PendingConstitutionUpload? = null

    private val _listState = MutableStateFlow(GroupListState())
    val listState: StateFlow<GroupListState> = _listState
        .map { state ->
            val filtered = state.rawGroups.filter { g ->
                val matchesQuery = state.searchQuery.isBlank() || 
                    g.name.contains(state.searchQuery, ignoreCase = true) ||
                    g.city?.contains(state.searchQuery, ignoreCase = true) == true ||
                    g.township?.contains(state.searchQuery, ignoreCase = true) == true
                
                val matchesProvince = state.provinceFilter == null || g.province == state.provinceFilter
                val matchesType = state.typeFilter == null || g.type == state.typeFilter
                
                matchesQuery && matchesProvince && matchesType
            }
            state.copy(filteredGroups = filtered)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _listState.value)

    private val _detail = MutableStateFlow(GroupDetailState())
    val detail: StateFlow<GroupDetailState> = _detail.asStateFlow()

    private val _registerState = MutableStateFlow(RegisterGroupState())
    val registerState: StateFlow<RegisterGroupState> = _registerState.asStateFlow()

    private var loadGroupsJob: Job? = null
    private var geocodeBatchJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadGroups()
        
        // Auto-fill admin email if user is logged in
        viewModelScope.launch {
            val email = groupRepo.getCurrentUserEmail()
            if (email != null) {
                _registerState.update { it.copy(adminEmail = email, isLoggedIn = true) }
            }
        }
    }

    fun loadGroups() {
        loadGroupsJob?.cancel()
        loadGroupsJob = viewModelScope.launch {
            _listState.update { it.copy(isLoading = true, error = null) }
            getPublicGroupsUseCase().collectLatest { res ->
                res.onSuccess { list ->
                    _listState.update { it.copy(rawGroups = list, isLoading = false, error = null) }
                    // Background geocode groups without coordinates
                    geocodeGroupsWithoutCoordinates(list)
                }
                .onFailure { e -> 
                    // Only show error if we have no local data
                    if (_listState.value.rawGroups.isEmpty()) {
                        _listState.update { it.copy(isLoading = false, error = e.toUserMessage()) } 
                    } else {
                        _listState.update { it.copy(isLoading = false) }
                    }
                }
            }
        }
    }

    /**
     * Batch geocode groups that don't have coordinates.
     * This runs in the background without blocking the UI.
     */
    private fun geocodeGroupsWithoutCoordinates(groups: List<Group>) {
        val groupsNeedingGeocoding = groups.filter { it.latitude == null || it.longitude == null }
        if (groupsNeedingGeocoding.isEmpty()) return

        geocodeBatchJob?.cancel()
        geocodeBatchJob = viewModelScope.launch {
            groupsNeedingGeocoding.forEach { group ->
                attemptGeocodeGroup(group)
                delay(200) // Rate limit API calls
            }
        }
    }

    private var detailJob: Job? = null

    fun loadGroup(id: String) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _detail.update { it.copy(isLoading = true, error = null) }
            
            groupRepo.observeGroup(id).collect { res ->
                res.onSuccess { g ->
                    _detail.update { it.copy(group = g, isLoading = false) }
                    if (g != null && (g.latitude == null || g.longitude == null)) {
                        attemptGeocodeGroup(g)
                    }
                }
                .onFailure { e -> 
                    _detail.update { it.copy(error = e.toUserMessage(), isLoading = false) } 
                }
            }
        }
    }

    private fun attemptGeocodeGroup(g: Group) {
        val address = listOfNotNull(g.township, g.city, g.province)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        
        if (address.isBlank()) return

        viewModelScope.launch {
            try {
                val response = geoapifyService.autocomplete(address, BuildConfig.GEOAPIFY_API_KEY)
                response.features.firstOrNull()?.let { feature ->
                    val props = feature.properties
                    val lat = props.lat ?: feature.geometry.coordinates.getOrNull(1)
                    val lon = props.lon ?: feature.geometry.coordinates.getOrNull(0)
                    
                    if (lat != null && lon != null) {
                        val geohash = LocationUtils.encodeGeohash(lat, lon)
                        val updatedGroup = g.copy(latitude = lat, longitude = lon, geohash = geohash)

                        // Update detail state immediately
                        _detail.update { state ->
                            if (state.group?.id == g.id) {
                                state.copy(group = updatedGroup)
                            } else state
                        }
                        
                        // Update list state for immediate map update
                        _listState.update { state ->
                            state.copy(rawGroups = state.rawGroups.map { 
                                if (it.id == g.id) updatedGroup else it 
                            })
                        }

                        // Persist to database
                        groupRepo.updateGroup(updatedGroup).onFailure { e ->
                            val userMsg = e.logAndGetMessage("GroupViewModel")
                            AppLogger.w("GroupVM", "Failed to persist geocoded coordinates for group ${g.id}: $userMsg")
                        }
                    }
                }
            } catch (_: Exception) {
                AppLogger.w("GroupVM", "Silent geocoding failed for group ${g.id}")
            }
        }
    }

    fun updateFilter(
        query: String? = null,
        province: String? = null,
        type: GroupType? = null,
        clearType: Boolean = false
    ) {
        _listState.update {
            it.copy(
                searchQuery = query ?: it.searchQuery,
                provinceFilter = if (province == "All Provinces") null else province ?: it.provinceFilter,
                typeFilter = if (clearType) null else type ?: it.typeFilter
            )
        }
    }

    // ── Type-safe event handler (preferred API) ───────────────────────────

    /**
     * Handles a [GroupFormEvent], providing compile-time safety for all form
     * field updates.  Composables should call this instead of passing raw strings.
     */
    fun onEvent(event: GroupFormEvent) {
        _registerState.update { s ->
            when (event) {
                is GroupFormEvent.NameChanged                   -> s.copy(name = event.name.trimEnd())
                is GroupFormEvent.AdminEmailChanged             -> s.copy(adminEmail = event.email.trimEnd())
                is GroupFormEvent.AdminPhoneChanged             -> s.copy(adminPhone = event.phone)
                is GroupFormEvent.TypeSelected                  -> s.copy(type = event.type)
                is GroupFormEvent.RoscaRotationMethodSelected   -> s.copy(rotationMethod = event.method)
                is GroupFormEvent.LogoEmojiSelected             -> s.copy(logoEmoji = event.emoji)
                is GroupFormEvent.CityChanged                   -> s.copy(city = event.city.trimEnd(), latitude = null, longitude = null, geohash = null)
                is GroupFormEvent.TownshipChanged               -> s.copy(township = event.township.trimEnd(), latitude = null, longitude = null, geohash = null)
                is GroupFormEvent.ProvinceSelected              -> s.copy(province = event.province, latitude = null, longitude = null, geohash = null)
                is GroupFormEvent.DescriptionChanged            -> s.copy(description = event.description.trimEnd())
                is GroupFormEvent.JoiningFeeChanged             -> s.copy(joiningFee = event.value)
                is GroupFormEvent.MonthlyContributionChanged    -> s.copy(monthlyContribution = event.value)
                is GroupFormEvent.LateFeeChanged                -> s.copy(lateFee = event.value)
                is GroupFormEvent.LateFeeGraceDaysChanged       -> s.copy(lateFeeGraceDays = event.value)
                is GroupFormEvent.ProbationMonthsChanged        -> s.copy(probationMonths = event.value)
                is GroupFormEvent.PaymentDueDayChanged          -> s.copy(paymentDueDay = event.value)
                is GroupFormEvent.MaxMembersChanged             -> s.copy(maxMembers = event.value)
                is GroupFormEvent.GoalAmountChanged             -> s.copy(goalAmount = event.value)
                is GroupFormEvent.PeriodMonthsChanged           -> s.copy(periodMonths = event.value)
                is GroupFormEvent.MaxBeneficiariesChanged       -> s.copy(maxBeneficiaries = event.value)
                is GroupFormEvent.BeneficiaryIncreasePctChanged -> s.copy(beneficiaryIncreasePct = event.value)
                is GroupFormEvent.AllowPartialPaymentToggled    -> s.copy(allowPartialPayment = event.allow)
                is GroupFormEvent.TermsAcceptedToggled          -> s.copy(termsAccepted = event.accepted)
                is GroupFormEvent.BankNameSelected              -> s.copy(bankName = event.bank)
                is GroupFormEvent.AccountNumberChanged          -> s.copy(accountNumber = event.value)
                is GroupFormEvent.BranchCodeChanged             -> s.copy(branchCode = event.value)
                is GroupFormEvent.UseStandardConstitutionToggled-> s.copy(useStandardConstitution = event.use)
                is GroupFormEvent.ConstitutionStatusChanged     -> s.copy(constitutionUrl = event.url, constitutionStatus = event.status)
                is GroupFormEvent.AdminFullNameChanged          -> s.copy(adminFullName = event.name.trimEnd())
                is GroupFormEvent.AdminIdNumberChanged          -> s.copy(adminIdNumber = event.id.trimEnd())
                is GroupFormEvent.AdminPasswordChanged          -> s.copy(adminPassword = event.password)
                is GroupFormEvent.DismissPayment                -> s.copy(needsPayment = false)
            }
        }

        // Side-effect: trigger address autocomplete when location fields change
        when (event) {
            is GroupFormEvent.CityChanged     -> searchAddress(event.city.trimEnd())
            is GroupFormEvent.TownshipChanged -> searchAddress(event.township.trimEnd())
            else -> Unit
        }
    }


    private fun searchAddress(query: String) {
        searchJob?.cancel()
        if (query.length < 3) {
            _registerState.update { it.copy(addressSuggestions = emptyList()) }
            return
        }
        
        searchJob = viewModelScope.launch {
            delay(500) 
            try {
                _registerState.update { it.copy(isSearchingAddress = true) }
                val response = geoapifyService.autocomplete(query, BuildConfig.GEOAPIFY_API_KEY)
                _registerState.update { it.copy(addressSuggestions = response.features, isSearchingAddress = false) }
            } catch (_: Exception) {
                _registerState.update { it.copy(isSearchingAddress = false) }
            }
        }
    }

    fun onAddressSelected(feature: Feature) {
        val props = feature.properties
        val lat = props.lat ?: feature.geometry.coordinates.getOrNull(1)
        val lon = props.lon ?: feature.geometry.coordinates.getOrNull(0)
        
        _registerState.update { it.copy(
            city = props.city ?: props.township ?: props.village ?: it.city,
            township = props.suburb ?: props.neighbourhood ?: props.quarter ?: props.township ?: it.township,
            province = props.state ?: it.province,
            latitude = lat,
            longitude = lon,
            geohash = if (lat != null && lon != null) LocationUtils.encodeGeohash(lat, lon) else null,
            addressSuggestions = emptyList()
        ) }
    }

    // ── Private helper: build a Group domain model from current form state ─

    /**
     * Converts the mutable UI form state into an immutable [Group] domain object.
     * Single source of truth — call this instead of duplicating the mapping.
     */
    private fun buildGroupFromState(s: RegisterGroupState): Result<Group> =
        RegisterGroupValidator.toGroupDraft(s)

    fun submitGroup() {
        val s = _registerState.value
        if (!s.termsAccepted) {
            _registerState.update { it.copy(error = "You must accept the terms and conditions.") }
            return
        }

        val validation = ValidationUtils.validateGroupStep6(
            s.adminFullName, s.adminEmail, s.adminPassword, s.isLoggedIn, s.adminIdNumber
        )
        if (validation !is ValidationResult.Valid) {
            _registerState.update { it.copy(error = (validation as ValidationResult.Error).message) }
            return
        }

        viewModelScope.launch {
            _registerState.update { it.copy(isSubmitting = true, error = null) }

            // Ensure geolocation is captured before submission
            var finalState = _registerState.value
            if (finalState.latitude == null || finalState.longitude == null) {
                val coords = geocodeAddressSync(finalState.city, finalState.province)
                if (coords != null) {
                    finalState = finalState.copy(
                        latitude = coords.first,
                        longitude = coords.second,
                        geohash = LocationUtils.encodeGeohash(coords.first, coords.second)
                    )
                    _registerState.update { it.copy(
                        latitude = coords.first,
                        longitude = coords.second,
                        geohash = finalState.geohash
                    ) }
                }
            }

            val group = buildGroupFromState(finalState).getOrElse { e ->
                val message = e.message?.takeIf { it.isNotBlank() } ?: e.toUserMessage()
                _registerState.update { it.copy(isSubmitting = false, error = message) }
                return@launch
            }

            createGroupUseCase(
                group, 
                finalState.adminEmail.trim(),
                finalState.adminPassword.takeIf { it.isNotBlank() }?.trim(),
                finalState.adminFullName.trim(),
                finalState.adminPhone.trim(),
                finalState.adminIdNumber.trim()
            ).onSuccess { id ->
                // Generate standard constitution if requested and none uploaded
                if (finalState.useStandardConstitution && finalState.constitutionUrl == null) {
                    viewModelScope.launch {
                        exportRepo.exportGroupConstitution(group.copy(id = id))
                            .onSuccess { file ->
                                groupRepo.uploadConstitution(id, file.readBytes(), "Constitution_${group.name.replace(" ", "_")}.pdf")
                            }
                    }
                }

                _registerState.update { it.copy(
                    isSubmitting = false,
                    createdGroupId = id,
                    needsPayment = true,
                    error = null
                ) }
            }.onFailure { e ->
                _registerState.update { it.copy(isSubmitting = false, error = e.toUserMessage()) }
            }
        }
    }

    private suspend fun geocodeAddressSync(city: String, province: String): Pair<Double, Double>? {
        val address = listOfNotNull(city.takeIf { it.isNotBlank() }, province.takeIf { it.isNotBlank() })
            .joinToString(", ")

        if (address.isBlank()) return null

        return try {
            val response = geoapifyService.autocomplete("$address, South Africa", BuildConfig.GEOAPIFY_API_KEY)
            val feature = response.features.firstOrNull() ?: return null

            val props = feature.properties
            val lat = props.lat ?: feature.geometry.coordinates.getOrNull(1)
            val lon = props.lon ?: feature.geometry.coordinates.getOrNull(0)

            if (lat != null && lon != null) {
                AppLogger.d("GroupViewModel", "📍 Geocoded address (sync): $address -> ($lat, $lon)")
                Pair(lat, lon)
            } else null
        } catch (e: Exception) {
            val userMsg = e.logAndGetMessage("GroupViewModel")
            AppLogger.w("GroupViewModel", "⚠️ Geocoding failed for: $address - $userMsg")
            null
        }
    }

    fun finalizeRegistrationAfterPayment(txId: String? = null) {
        val snapshot = _registerState.value

        viewModelScope.launch {
            _registerState.update { it.copy(isSubmitting = true, error = null, success = false) }

            val transactionId = txId ?: "reg_flow_${System.currentTimeMillis()}"

            // Backward-compatible flow:
            // Some unit tests (and older UI flows) call `finalizeRegistrationAfterPayment()` directly
            // without first calling `submitGroup()`.
            // If we don't yet have a groupId, create the group first, then activate it.
            val groupId = snapshot.createdGroupId ?: run {
                val group = buildGroupFromState(snapshot).getOrElse { e ->
                    _registerState.update {
                        it.copy(isSubmitting = false, error = e.message?.takeIf(String::isNotBlank) ?: e.toUserMessage())
                    }
                    return@launch
                }

                createGroupUseCase(
                    group,
                    snapshot.adminEmail.trim(),
                    snapshot.adminPassword.takeIf { it.isNotBlank() }?.trim(),
                    snapshot.adminFullName.trim(),
                    snapshot.adminPhone.trim(),
                    snapshot.adminIdNumber.trim()
                ).getOrElse { e ->
                    _registerState.update { it.copy(isSubmitting = false, error = e.toUserMessage()) }
                    return@launch
                }.also { createdId ->
                    _registerState.update { it.copy(createdGroupId = createdId) }
                }
            }

            AppLogger.d("GroupViewModel", "📍 Starting group activation after payment: $groupId")

            // Upload constitution if pending
            val pendingUpload = pendingConstitutionUpload
            if (pendingUpload != null) {
                groupRepo.uploadConstitution(groupId, pendingUpload.bytes, pendingUpload.fileName)
                    .onSuccess {
                        pendingConstitutionUpload = null
                        _registerState.update { it.copy(pendingConstitutionName = null) }
                    }
                        .onFailure { e ->
                                // Don't block activation, but inform the user so they can retry later.
                                val userMsg = e.logAndGetMessage("GroupViewModel")
                                AppLogger.w("GroupViewModel", "⚠️ Constitution upload failed during activation: $userMsg")
                                _registerState.update { it.copy(error = e.toUserMessage()) }
                            }
            }

            // Automatically activate since payment was just confirmed
            groupRepo.activateGroup(groupId, transactionId)
                .onSuccess {
                    AppLogger.d("GroupViewModel", "✅ Group activated successfully")
                    _registerState.update {
                        it.copy(
                            isSubmitting = false,
                            success = true,
                            needsPayment = false,
                            createdGroupId = groupId
                        )
                    }
                    loadGroups()
                }
                .onFailure { e ->
                    val userMsg = e.logAndGetMessage("GroupViewModel")
                    AppLogger.e("GroupViewModel", "❌ Group activation failed: $userMsg")
                    _registerState.update { it.copy(isSubmitting = false, success = false, error = e.toUserMessage()) }
                }
        }
    }

    fun nextStep() {
        val s = _registerState.value
        val validation = when (s.currentStep) {
            1 -> ValidationUtils.validateGroupStep1(s.name, s.adminEmail, s.adminPhone)
            2 -> ValidationUtils.validateGroupStep2(s.province, s.city)
            3 -> ValidationUtils.validateGroupStep3(s.joiningFee, s.monthlyContribution, s.maxMembers)
            4 -> ValidationUtils.validateGroupStep4(s.bankName, s.accountNumber, s.branchCode)
            5 -> if (s.constitutionUrl == null) ValidationResult.Error("Please upload your group constitution") else ValidationResult.Valid
            else -> ValidationResult.Valid
        }

        if (validation is ValidationResult.Error) {
            _registerState.update { it.copy(error = validation.message) }
            return
        }

        // Attempt geocoding if user didn't select from autocomplete (step 2)
        if (s.currentStep == 2 && s.latitude == null && s.longitude == null) {
            attemptGeocodeAddress(s.city, s.province)
        }

        if (s.currentStep < s.totalSteps) {
            _registerState.update { it.copy(currentStep = it.currentStep + 1, error = null) }
        }
    }

    private fun attemptGeocodeAddress(city: String, province: String) {
        val address = listOfNotNull(city.takeIf { it.isNotBlank() }, province.takeIf { it.isNotBlank() })
            .joinToString(", ")

        if (address.isBlank()) return

        viewModelScope.launch {
            try {
                val response = geoapifyService.autocomplete("$address, South Africa", BuildConfig.GEOAPIFY_API_KEY)
                val feature = response.features.firstOrNull() ?: return@launch

                val props = feature.properties
                val lat = props.lat ?: feature.geometry.coordinates.getOrNull(1)
                val lon = props.lon ?: feature.geometry.coordinates.getOrNull(0)

                if (lat != null && lon != null) {
                    _registerState.update { it.copy(
                        latitude = lat,
                        longitude = lon,
                        geohash = LocationUtils.encodeGeohash(lat, lon)
                    ) }
                    AppLogger.d("GroupViewModel", "📍 Geocoded address: $address -> ($lat, $lon)")
                }
            } catch (e: Exception) {
                val userMsg = e.logAndGetMessage("GroupViewModel")
                AppLogger.w("GroupViewModel", "⚠️ Geocoding failed for: $address - $userMsg")
                // Don't block registration if geocoding fails
            }
        }
    }

    fun uploadConstitution(fileBytes: ByteArray, fileName: String) {
        if (fileBytes.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
            _registerState.update { it.copy(error = "File size exceeds 3MB limit") }
            return
        }

        val groupId = _registerState.value.createdGroupId
        
        if (groupId == null) {
            // New registration flow - store for later upload
            pendingConstitutionUpload = PendingConstitutionUpload(fileBytes, fileName)
            _registerState.update { it.copy(
                pendingConstitutionName = fileName,
                constitutionUrl = "pending_local_upload", // Temporary flag for UI
                constitutionStatus = DocumentStatus.PENDING
            ) }
            return
        }

        viewModelScope.launch {
            _registerState.update { it.copy(isLoading = true, error = null) }
            groupRepo.uploadConstitution(groupId, fileBytes, fileName)
                .onSuccess { url ->
                    pendingConstitutionUpload = null
                    _registerState.update { it.copy(
                        constitutionUrl = url,
                        constitutionStatus = DocumentStatus.PENDING,
                        pendingConstitutionName = null,
                        isLoading = false
                    ) }
                }
                .onFailure { e ->
                    _registerState.update { it.copy(
                        isLoading = false,
                        error = e.toUserMessage()
                    ) }
                }
        }
    }

    fun prevStep() {
        if (_registerState.value.currentStep > 1) {
            _registerState.update { it.copy(currentStep = it.currentStep - 1) }
        }
    }

    fun resetNavigation() {
        pendingConstitutionUpload = null
        _registerState.update { it.copy(success = false, createdGroupId = null, pendingConstitutionName = null) }
    }
}
