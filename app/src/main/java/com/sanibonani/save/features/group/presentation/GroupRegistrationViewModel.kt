package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.service.PlatformFeeService
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.auth.GetCurrentSessionEmailUseCase
import com.sanibonani.save.domain.usecase.auth.IsLoggedInUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupByIdUseCase
import com.sanibonani.save.domain.usecase.group.UploadGroupConstitutionUseCase
import com.sanibonani.save.domain.usecase.group.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.service.UserProfileCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

private const val STEP_INCOMPLETE_MESSAGE = "Please fix the highlighted fields to continue."

data class GroupRegistrationState(
    val name: String = "",
    val type: GroupType = GroupType.STOKVEL,
    val province: String = "",
    val city: String = "",
    val street: String = "",
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
    val burialClaimAmount: String = "10000",
    val minMembersForClaims: String = "10",
    val minReservePercentage: String = "20",
    val lateFeeGraceDays: String = "5",
    val probationMonths: String = "3",
    val paymentDueDay: String = "28",
    val termsAccepted: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val needsPayment: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val createdGroupId: String? = null,
    val addressSuggestions: List<com.sanibonani.save.domain.service.LocationAddress> = emptyList(),
    val addressQuery: String = "",
    val isSearchingAddress: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val allowPartialPayment: Boolean = false,
    val goalAmount: String = "0",
    val periodMonths: String = "6",
    val roscaCycle: Int = 6,
    val geohash: String? = null,
    val currentStep: Int = 1,
    val totalSteps: Int = 6,
    val isLoggedIn: Boolean = false,
    val constitutionUrl: String? = null,
    val constitutionStatus: DocumentStatus = DocumentStatus.PENDING,
    val pendingConstitutionName: String? = null,
    val useStandardConstitution: Boolean = false,
    val platformRegistrationFee: Money = Money.ZERO
) {
    /** Label for the primary wizard button; a free group never asks for a payment. */
    val summaryActionLabel: String
        get() = when {
            currentStep < totalSteps -> "Next"
            platformRegistrationFee.isPositive -> "Pay ${platformRegistrationFee.format()}"
            else -> "Create Group"
        }
}

/**
 * ViewModel responsible for the multi-step group registration process.
 * Handles state transitions, address autocomplete, and group creation/activation.
 */
@HiltViewModel
class GroupRegistrationViewModel @Inject constructor(
    private val createGroup: CreateGroupUseCase,
    private val activateGroup: ActivateGroupUseCase,
    private val feeService: PlatformFeeService,
    private val locationService: LocationService,
    private val isLoggedIn: IsLoggedInUseCase,
    private val getCurrentSessionEmail: GetCurrentSessionEmailUseCase,
    private val getAttributableActorId: GetAttributableActorIdUseCase,
    private val userProfileCache: UserProfileCacheService? = null,
    private val memberContextCacheService: MemberGroupContextCacheService? = null,
    private val getGroupById: GetGroupByIdUseCase? = null,
    private val uploadGroupConstitution: UploadGroupConstitutionUseCase? = null,
) : BaseViewModel<GroupRegistrationState>(GroupRegistrationState()) {

    val state: StateFlow<GroupRegistrationState> = uiState

    private val submitter = GroupRegistrationSubmitter(
        createGroup = createGroup,
        activateGroup = activateGroup,
        getAttributableActorId = getAttributableActorId,
        userProfileCache = userProfileCache,
        memberContextCacheService = memberContextCacheService,
        uploadGroupConstitution = uploadGroupConstitution,
        getGroupById = getGroupById
    )

    private val registrationHost = object : GroupRegistrationHost {
        override val state: StateFlow<GroupRegistrationState>
            get() = this@GroupRegistrationViewModel.state

        override fun updateState(reducer: (GroupRegistrationState) -> GroupRegistrationState) =
            this@GroupRegistrationViewModel.updateState(reducer)

        override fun launchJob(key: String, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Job =
            this@GroupRegistrationViewModel.launchJob(key, block)
    }

    init {
        // AI Note: Warmup the state with current login status, standard platform fees, and cached user profile.
        val loggedIn = isLoggedIn()
        val fee = feeService.getRegistrationFee()
        val cachedName = userProfileCache?.getFullName() ?: ""
        // The admin email field is read-only for a signed-in account, so an empty cache would
        // leave a required, uneditable field blank; fall back to the session's own email.
        val cachedEmail = userProfileCache?.getEmail()?.takeIf { it.isNotBlank() }
            ?: getCurrentSessionEmail().orEmpty()
        val cachedPhone = userProfileCache?.getPhone() ?: ""

        updateState {
            it.copy(
                isLoggedIn = loggedIn,
                platformRegistrationFee = fee,
                adminFullName = if (cachedName.isNotBlank()) cachedName else it.adminFullName,
                adminEmail = if (cachedEmail.isNotBlank()) cachedEmail else it.adminEmail,
                adminPhone = if (cachedPhone.isNotBlank()) cachedPhone else it.adminPhone
            )
        }
    }

    /**
     * Resets the registration form to its initial state.
     */
    fun resetNavigation() {
        submitter.clearPendingConstitution()
        updateState { initialState.copy(
            isLoggedIn = isLoggedIn(),
            platformRegistrationFee = it.platformRegistrationFee
        ) }
    }

    fun nextStep() {
        updateState { state ->
            if (state.currentStep >= state.totalSteps) return@updateState state
            val errors = RegisterGroupValidator.validateStep(state.currentStep, state)
            if (errors.isEmpty()) {
                state.copy(currentStep = state.currentStep + 1, fieldErrors = emptyMap(), error = null)
            } else {
                state.copy(fieldErrors = errors, error = STEP_INCOMPLETE_MESSAGE)
            }
        }
    }

    fun prevStep() {
        updateState {
            it.copy(
                currentStep = (it.currentStep - 1).coerceAtLeast(1),
                fieldErrors = emptyMap(),
                error = null
            )
        }
    }

    /**
     * Applies a field edit and clears only the errors for the fields the user touched, so the
     * remaining highlights still point at everything else that needs attention.
     */
    private fun updateField(vararg keys: String, reducer: (GroupRegistrationState) -> GroupRegistrationState) {
        updateState { state ->
            reducer(state).copy(fieldErrors = state.fieldErrors - keys.toSet(), error = null)
        }
    }

    /**
     * Centralized event handler for UI interactions.
     * AI Optimization: Consolidating form changes into a single event stream reduces state race conditions.
     */
    fun onEvent(event: GroupFormEvent) {
        when (event) {
            is GroupFormEvent.NameChanged -> updateField("name") { it.copy(name = event.name) }
            is GroupFormEvent.TypeSelected -> updateField(
                "maxMembers", "maxBeneficiaries", "beneficiaryIncreasePct", "goalAmount", "periodMonths"
            ) { it.copy(type = event.type) }
            is GroupFormEvent.ProvinceSelected -> updateField("province") {
                it.copy(province = event.province, latitude = null, longitude = null, geohash = null)
            }
            is GroupFormEvent.CityChanged -> {
                updateField("city") { it.copy(city = event.city, latitude = null, longitude = null, geohash = null) }
                searchAddress(event.city)
            }
            is GroupFormEvent.TownshipChanged -> {
                updateField("township") { it.copy(township = event.township, latitude = null, longitude = null, geohash = null) }
                searchAddress(event.township)
            }
            is GroupFormEvent.StreetChanged -> {
                updateField("street") { it.copy(street = event.street, latitude = null, longitude = null, geohash = null) }
                searchAddress(event.street)
            }
            is GroupFormEvent.DescriptionChanged -> updateField("description") { it.copy(description = event.description) }
            is GroupFormEvent.JoiningFeeChanged -> updateField("joiningFee") { it.copy(joiningFee = event.value) }
            is GroupFormEvent.MonthlyContributionChanged -> updateField("monthlyContribution") { it.copy(monthlyContribution = event.value) }
            is GroupFormEvent.LateFeeChanged -> updateField("lateFee") { it.copy(lateFee = event.value) }
            is GroupFormEvent.MaxMembersChanged -> updateField("maxMembers") { it.copy(maxMembers = event.value) }
            is GroupFormEvent.AdminEmailChanged -> updateField("adminEmail") { it.copy(adminEmail = event.email) }
            is GroupFormEvent.AdminFullNameChanged -> updateField("adminFullName") { it.copy(adminFullName = event.name) }
            is GroupFormEvent.AdminPhoneChanged -> updateField("adminPhone") { it.copy(adminPhone = event.phone) }
            is GroupFormEvent.AdminIdNumberChanged -> updateField("adminIdNumber") { it.copy(adminIdNumber = event.id) }
            is GroupFormEvent.AdminPasswordChanged -> updateField("adminPassword") { it.copy(adminPassword = event.password) }
            is GroupFormEvent.BankNameSelected -> updateField("bankName") { it.copy(bankName = event.bank) }
            is GroupFormEvent.AccountNumberChanged -> updateField("accountNumber") { it.copy(accountNumber = event.value) }
            is GroupFormEvent.BranchCodeChanged -> updateField("branchCode") { it.copy(branchCode = event.value) }
            is GroupFormEvent.RoscaRotationMethodSelected -> updateField("rotationMethod") { it.copy(rotationMethod = event.method) }
            is GroupFormEvent.TermsAcceptedToggled -> updateField("termsAccepted") { it.copy(termsAccepted = event.accepted) }
            is GroupFormEvent.AddressQueryChanged -> {
                updateField("addressQuery") { it.copy(addressQuery = event.query) }
                searchAddress(event.query)
            }
            is GroupFormEvent.AddressSelected -> onAddressSelected(event.address)
            is GroupFormEvent.CoordinatesPicked -> updateField("latitude", "longitude") { it.copy(latitude = event.lat, longitude = event.lon) }
            is GroupFormEvent.GpsLocationPicked -> updateField("city", "latitude", "longitude") {
                it.copy(
                    latitude = event.lat,
                    longitude = event.lon,
                    city = event.placeName?.takeIf { n -> n.isNotBlank() } ?: it.city
                )
            }
            is GroupFormEvent.RoscaCycleChanged -> updateField("roscaCycle", "maxMembers") { it.copy(roscaCycle = event.months) }
            is GroupFormEvent.LateFeeGraceDaysChanged -> updateField("lateFeeGraceDays") { it.copy(lateFeeGraceDays = event.value) }
            is GroupFormEvent.ProbationMonthsChanged -> updateField("probationMonths") { it.copy(probationMonths = event.value) }
            is GroupFormEvent.PaymentDueDayChanged -> updateField("paymentDueDay") { it.copy(paymentDueDay = event.value) }
            is GroupFormEvent.GoalAmountChanged -> updateField("goalAmount") { it.copy(goalAmount = event.value) }
            is GroupFormEvent.PeriodMonthsChanged -> updateField("periodMonths") { it.copy(periodMonths = event.value) }
            is GroupFormEvent.MaxBeneficiariesChanged -> updateField("maxBeneficiaries") { it.copy(maxBeneficiaries = event.value) }
            is GroupFormEvent.BeneficiaryIncreasePctChanged -> updateField("beneficiaryIncreasePct") { it.copy(beneficiaryIncreasePct = event.value) }
            is GroupFormEvent.AllowPartialPaymentToggled -> updateField("allowPartialPayment") { it.copy(allowPartialPayment = event.allow) }
            is GroupFormEvent.UseStandardConstitutionToggled -> updateField("useStandardConstitution") { it.copy(useStandardConstitution = event.use) }
            is GroupFormEvent.LogoEmojiSelected -> updateField("logoEmoji") { it.copy(logoEmoji = event.emoji) }
            GroupFormEvent.NextStep -> nextStep()
            GroupFormEvent.PrevStep -> prevStep()
            GroupFormEvent.Submit -> submitGroup()
            GroupFormEvent.DismissPayment -> updateState { it.copy(needsPayment = false) }
        }
    }

    /**
     * Debounced address searching using Geoapify via LocationService.
     */
    private fun searchAddress(query: String) {
        if (query.length < 3) {
            updateState { it.copy(addressSuggestions = emptyList(), isSearchingAddress = false) }
            return
        }
        // AI Note: Uses launchJob with a specific key to automatically cancel previous search requests (Debouncing).
        launchJob("address_search") {
            delay(500.milliseconds)
            updateState { it.copy(isSearchingAddress = true) }
            val res = safeRunCatching { locationService.searchAddress(query).getOrThrow() }
            res.onSuccess { list ->
                updateState { it.copy(addressSuggestions = list, isSearchingAddress = false) }
            }.onFailure {
                updateState { it.copy(addressSuggestions = emptyList(), isSearchingAddress = false) }
            }
        }
    }

    /**
     * Applies a selected address suggestion to the state.
     */
    fun onAddressSelected(address: com.sanibonani.save.domain.service.LocationAddress) {
        updateField("street", "township", "city", "province", "addressQuery") { it.copy(
            street = address.formatted,
            township = address.township ?: it.township,
            city = address.city ?: it.city,
            province = address.province ?: it.province,
            latitude = address.latitude,
            longitude = address.longitude,
            addressQuery = address.formatted,
            addressSuggestions = emptyList(),
            isSearchingAddress = false
        ) }
    }

    fun uploadConstitution(fileName: String, fileBytes: ByteArray) {
        submitter.selectConstitution(fileName, fileBytes, registrationHost)
    }

    /**
     * Validates and submits the group draft.
     * Creates group and automatically registers creator as the initial member.
     */
    fun submitGroup() {
        val state = uiState.value
        // A group that already exists only owes payment or activation; its persisted fields must
        // not block the resume just because the live form has since been edited.
        if (state.createdGroupId.isNullOrBlank()) {
            val errors = RegisterGroupValidator.validateAll(state)
            if (errors.isNotEmpty()) {
                updateState {
                    it.copy(
                        fieldErrors = errors,
                        error = STEP_INCOMPLETE_MESSAGE,
                        currentStep = RegisterGroupValidator.firstInvalidStep(it) ?: it.currentStep
                    )
                }
                return
            }
        }
        updateState { it.copy(fieldErrors = emptyMap()) }
        submitter.submit(registrationHost)
    }

    /**
     * Finalizes the registration by activating the group after payment confirmation
     * and warming up member context cache for onboarding.
     * @param txId The external transaction reference (e.g., from YoCo).
     */
    fun finalizeRegistrationAfterPayment(txId: String?) = submitter.finalizeAfterPayment(txId, registrationHost)
}
