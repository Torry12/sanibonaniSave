package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.BuildConfig
import com.sanibonani.save.data.FileUploadLimits
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.data.remote.Feature
import com.sanibonani.save.data.remote.GeoapifyService
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.data.utils.LocationUtils
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.data.validation.ValidationResult
import com.sanibonani.save.data.validation.ValidationUtils
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
    val pendingConstitutionBytes: ByteArray? = null,
    val pendingConstitutionName: String? = null
)

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groupRepo: GroupRepository,
    private val createGroupUseCase: CreateGroupUseCase,
    private val getPublicGroupsUseCase: GetPublicGroupsUseCase,
    private val geoapifyService: GeoapifyService
) : ViewModel() {

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
        viewModelScope.launch {
            _listState.update { it.copy(isLoading = true, error = null) }
            getPublicGroupsUseCase().collect { res ->
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
        
        viewModelScope.launch {
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
                            AppLogger.w("GroupVM", "Failed to persist geocoded coordinates for group ${g.id}: ${e.message}")
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

    fun updateField(field: String, value: Any) {
        val cleanValue = if (value is String) value.trimEnd() else value
        _registerState.update {
            when (field) {
                "name" -> it.copy(name = cleanValue.toString())
                "description" -> it.copy(description = cleanValue.toString())
                "city" -> it.copy(city = cleanValue.toString(), latitude = null, longitude = null, geohash = null)
                "township" -> it.copy(township = cleanValue.toString(), latitude = null, longitude = null, geohash = null)
                "joiningFee" -> it.copy(joiningFee = cleanValue.toString())
                "monthlyContribution" -> it.copy(monthlyContribution = cleanValue.toString())
                "lateFee" -> it.copy(lateFee = cleanValue.toString())
                "maxMembers" -> it.copy(maxMembers = cleanValue.toString())
                "adminEmail" -> it.copy(adminEmail = cleanValue.toString())
                "adminFullName" -> it.copy(adminFullName = cleanValue.toString())
                "adminPhone" -> it.copy(adminPhone = cleanValue.toString())
                "adminIdNumber" -> it.copy(adminIdNumber = cleanValue.toString())
                "adminPassword" -> it.copy(adminPassword = cleanValue.toString())
                "accountNumber" -> it.copy(accountNumber = cleanValue.toString())
                "branchCode" -> it.copy(branchCode = cleanValue.toString())
                "logoEmoji" -> it.copy(logoEmoji = cleanValue.toString())
                "termsAccepted" -> it.copy(termsAccepted = cleanValue.toString().toBoolean())
                "type" -> {
                    when (value) {
                        is GroupType -> it.copy(type = value)
                        is String -> {
                           val matched = GroupType.entries.find { t -> t.name == value }
                           if (matched != null) it.copy(type = matched) else it
                        }
                        else -> it
                    }
                }
                "province" -> it.copy(province = cleanValue.toString(), latitude = null, longitude = null, geohash = null)
                "bankName" -> it.copy(bankName = cleanValue.toString())
                "maxBeneficiaries" -> it.copy(maxBeneficiaries = cleanValue.toString())
                "beneficiaryIncreasePct" -> it.copy(beneficiaryIncreasePct = cleanValue.toString())
                "lateFeeGraceDays" -> it.copy(lateFeeGraceDays = cleanValue.toString())
                "probationMonths" -> it.copy(probationMonths = cleanValue.toString())
                "paymentDueDay" -> it.copy(paymentDueDay = cleanValue.toString())
                "goalAmount" -> it.copy(goalAmount = cleanValue.toString())
                "periodMonths" -> it.copy(periodMonths = cleanValue.toString())
                "allowPartialPayment" -> {
                    when (value) {
                        is Boolean -> it.copy(allowPartialPayment = value)
                        is String -> it.copy(allowPartialPayment = value.toBoolean())
                        else -> it
                    }
                }
                "needsPayment" -> it.copy(needsPayment = cleanValue.toString().toBoolean())
                "constitutionUrl" -> it.copy(constitutionUrl = cleanValue as? String)
                "constitutionStatus" -> it.copy(constitutionStatus = value as DocumentStatus)
                else -> it
            }
        }
        
        if (field == "city" || field == "township") {
            searchAddress(cleanValue.toString())
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

            val group = Group(
                name = finalState.name.trim(),
                type = finalState.type,
                province = finalState.province.trim(),
                city = finalState.city.trim(),
                township = finalState.township.trim(),
                description = finalState.description.trim(),
                logoEmoji = finalState.logoEmoji,
                joiningFee = finalState.joiningFee.toDoubleOrNull() ?: 0.0,
                monthlyContribution = finalState.monthlyContribution.toDoubleOrNull() ?: 0.0,
                lateFee = finalState.lateFee.toDoubleOrNull() ?: 0.0,
                maxMembers = finalState.maxMembers.toIntOrNull() ?: 50,
                bankName = finalState.bankName.trim(),
                accountNumber = finalState.accountNumber.trim(),
                branchCode = finalState.branchCode.trim(),
                maxBeneficiaries = finalState.maxBeneficiaries.toIntOrNull()?.takeIf { it > 0 },
                beneficiaryIncreasePct = finalState.beneficiaryIncreasePct.toDoubleOrNull()?.takeIf { it > 0.0 },
                latitude = finalState.latitude,
                longitude = finalState.longitude,
                geohash = finalState.geohash,
                lateFeeGraceDays = finalState.lateFeeGraceDays.toIntOrNull() ?: 5,
                probationMonths = finalState.probationMonths.toIntOrNull() ?: 3,
                paymentDueDay = finalState.paymentDueDay.toIntOrNull() ?: 28,
                allowPartialPayment = finalState.allowPartialPayment,
                goalAmount = finalState.goalAmount.toDoubleOrNull() ?: 10000.0,
                periodMonths = finalState.periodMonths.toIntOrNull() ?: 12,
                constitutionUrl = finalState.constitutionUrl,
                constitutionStatus = finalState.constitutionStatus
            )

            createGroupUseCase(
                group, 
                finalState.adminEmail.trim(),
                finalState.adminPassword.takeIf { it.isNotBlank() }?.trim(),
                finalState.adminFullName.trim(),
                finalState.adminPhone.trim(),
                finalState.adminIdNumber.trim()
            ).onSuccess { id ->
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
            AppLogger.w("GroupViewModel", "⚠️ Geocoding failed for: $address - ${e.message}")
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
                val group = Group(
                    name = snapshot.name.trim(),
                    type = snapshot.type,
                    province = snapshot.province.trim(),
                    city = snapshot.city.trim(),
                    township = snapshot.township.trim(),
                    description = snapshot.description.trim(),
                    logoEmoji = snapshot.logoEmoji,
                    joiningFee = snapshot.joiningFee.toDoubleOrNull() ?: 0.0,
                    monthlyContribution = snapshot.monthlyContribution.toDoubleOrNull() ?: 0.0,
                    lateFee = snapshot.lateFee.toDoubleOrNull() ?: 0.0,
                    maxMembers = snapshot.maxMembers.toIntOrNull() ?: 50,
                    bankName = snapshot.bankName.trim(),
                    accountNumber = snapshot.accountNumber.trim(),
                    branchCode = snapshot.branchCode.trim(),
                    maxBeneficiaries = snapshot.maxBeneficiaries.toIntOrNull()?.takeIf { it > 0 },
                    beneficiaryIncreasePct = snapshot.beneficiaryIncreasePct.toDoubleOrNull()?.takeIf { it > 0.0 },
                    latitude = snapshot.latitude,
                    longitude = snapshot.longitude,
                    geohash = snapshot.geohash,
                    lateFeeGraceDays = snapshot.lateFeeGraceDays.toIntOrNull() ?: 5,
                    probationMonths = snapshot.probationMonths.toIntOrNull() ?: 3,
                    paymentDueDay = snapshot.paymentDueDay.toIntOrNull() ?: 28,
                    allowPartialPayment = snapshot.allowPartialPayment,
                    goalAmount = snapshot.goalAmount.toDoubleOrNull() ?: 10000.0,
                    periodMonths = snapshot.periodMonths.toIntOrNull() ?: 12,
                    constitutionUrl = snapshot.constitutionUrl,
                    constitutionStatus = snapshot.constitutionStatus
                )

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
            val latest = _registerState.value
            if (latest.pendingConstitutionBytes != null && latest.pendingConstitutionName != null) {
                groupRepo.uploadConstitution(groupId, latest.pendingConstitutionBytes, latest.pendingConstitutionName)
                    .onFailure { e ->
                        // Don't block activation, but inform the user so they can retry later.
                        AppLogger.w("GroupViewModel", "⚠️ Constitution upload failed during activation: ${e.message}")
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
                    AppLogger.e("GroupViewModel", "❌ Group activation failed: ${e.message}")
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
                AppLogger.w("GroupViewModel", "⚠️ Geocoding failed for: $address - ${e.message}")
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
            _registerState.update { it.copy(
                pendingConstitutionBytes = fileBytes,
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
                    _registerState.update { it.copy(
                        constitutionUrl = url,
                        constitutionStatus = DocumentStatus.PENDING,
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
        _registerState.update { it.copy(success = false, createdGroupId = null) }
    }
}
