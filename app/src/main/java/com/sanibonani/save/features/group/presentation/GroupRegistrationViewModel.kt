package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.repository.group.GroupDocumentRepository
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.service.PlatformFeeService
import com.sanibonani.save.domain.usecase.group.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.service.UserProfileCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

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
)

/**
 * ViewModel responsible for the multi-step group registration process.
 * Handles state transitions, address autocomplete, and group creation/activation.
 */
@HiltViewModel
class GroupRegistrationViewModel @Inject constructor(
    private val createGroupUseCase: CreateGroupUseCase,
    private val activateGroupUseCase: ActivateGroupUseCase,
    private val feeService: PlatformFeeService,
    private val locationService: LocationService,
    private val supabaseRepo: SupabaseRepository,
    private val userProfileCache: UserProfileCacheService? = null,
    private val memberContextCacheService: MemberGroupContextCacheService? = null,
    private val documentRepository: GroupDocumentRepository? = null,
) : BaseViewModel<GroupRegistrationState>(GroupRegistrationState()) {

    val state: StateFlow<GroupRegistrationState> = uiState

    // Held outside the state flow: a 3MB PDF does not belong in a StateFlow that Compose
    // recomposes on every keystroke. Cleared once it has been stored.
    private var pendingConstitutionBytes: ByteArray? = null

    init {
        // AI Note: Warmup the state with current login status, standard platform fees, and cached user profile.
        val isLoggedIn = supabaseRepo.isLoggedIn
        val fee = feeService.getRegistrationFee()
        val cachedName = userProfileCache?.getFullName() ?: ""
        val cachedEmail = userProfileCache?.getEmail() ?: ""
        val cachedPhone = userProfileCache?.getPhone() ?: ""

        updateState {
            it.copy(
                isLoggedIn = isLoggedIn,
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
        pendingConstitutionBytes = null
        updateState { initialState.copy(
            isLoggedIn = supabaseRepo.isLoggedIn,
            platformRegistrationFee = it.platformRegistrationFee
        ) }
    }

    fun nextStep() { updateState { it.copy(currentStep = (it.currentStep + 1).coerceAtMost(it.totalSteps)) } }
    fun prevStep() { updateState { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(1)) } }

    /**
     * Centralized event handler for UI interactions.
     * AI Optimization: Consolidating form changes into a single event stream reduces state race conditions.
     */
    fun onEvent(event: GroupFormEvent) {
        when (event) {
            is GroupFormEvent.NameChanged -> updateState { it.copy(name = event.name) }
            is GroupFormEvent.TypeSelected -> updateState { it.copy(type = event.type) }
            is GroupFormEvent.ProvinceSelected -> updateState { it.copy(province = event.province, latitude = null, longitude = null, geohash = null) }
            is GroupFormEvent.CityChanged -> {
                updateState { it.copy(city = event.city, latitude = null, longitude = null, geohash = null) }
                searchAddress(event.city)
            }
            is GroupFormEvent.TownshipChanged -> {
                updateState { it.copy(township = event.township, latitude = null, longitude = null, geohash = null) }
                searchAddress(event.township)
            }
            is GroupFormEvent.StreetChanged -> {
                updateState { it.copy(street = event.street, latitude = null, longitude = null, geohash = null) }
                searchAddress(event.street)
            }
            is GroupFormEvent.DescriptionChanged -> updateState { it.copy(description = event.description) }
            is GroupFormEvent.JoiningFeeChanged -> updateState { it.copy(joiningFee = event.value) }
            is GroupFormEvent.MonthlyContributionChanged -> updateState { it.copy(monthlyContribution = event.value) }
            is GroupFormEvent.LateFeeChanged -> updateState { it.copy(lateFee = event.value) }
            is GroupFormEvent.MaxMembersChanged -> updateState { it.copy(maxMembers = event.value) }
            is GroupFormEvent.AdminEmailChanged -> updateState { it.copy(adminEmail = event.email) }
            is GroupFormEvent.AdminFullNameChanged -> updateState { it.copy(adminFullName = event.name) }
            is GroupFormEvent.AdminPhoneChanged -> updateState { it.copy(adminPhone = event.phone) }
            is GroupFormEvent.AdminIdNumberChanged -> updateState { it.copy(adminIdNumber = event.id) }
            is GroupFormEvent.AdminPasswordChanged -> updateState { it.copy(adminPassword = event.password) }
            is GroupFormEvent.BankNameSelected -> updateState { it.copy(bankName = event.bank) }
            is GroupFormEvent.AccountNumberChanged -> updateState { it.copy(accountNumber = event.value) }
            is GroupFormEvent.BranchCodeChanged -> updateState { it.copy(branchCode = event.value) }
            is GroupFormEvent.RoscaRotationMethodSelected -> updateState { it.copy(rotationMethod = event.method) }
            is GroupFormEvent.TermsAcceptedToggled -> updateState { it.copy(termsAccepted = event.accepted) }
            is GroupFormEvent.AddressQueryChanged -> {
                updateState { it.copy(addressQuery = event.query) }
                searchAddress(event.query)
            }
            is GroupFormEvent.AddressSelected -> onAddressSelected(event.address)
            is GroupFormEvent.CoordinatesPicked -> updateState { it.copy(latitude = event.lat, longitude = event.lon) }
            is GroupFormEvent.GpsLocationPicked -> updateState {
                it.copy(
                    latitude = event.lat,
                    longitude = event.lon,
                    city = event.placeName?.takeIf { n -> n.isNotBlank() } ?: it.city
                )
            }
            is GroupFormEvent.RoscaCycleChanged -> updateState { it.copy(roscaCycle = event.months) }
            is GroupFormEvent.LateFeeGraceDaysChanged -> updateState { it.copy(lateFeeGraceDays = event.value) }
            is GroupFormEvent.ProbationMonthsChanged -> updateState { it.copy(probationMonths = event.value) }
            is GroupFormEvent.PaymentDueDayChanged -> updateState { it.copy(paymentDueDay = event.value) }
            is GroupFormEvent.GoalAmountChanged -> updateState { it.copy(goalAmount = event.value) }
            is GroupFormEvent.PeriodMonthsChanged -> updateState { it.copy(periodMonths = event.value) }
            is GroupFormEvent.MaxBeneficiariesChanged -> updateState { it.copy(maxBeneficiaries = event.value) }
            is GroupFormEvent.BeneficiaryIncreasePctChanged -> updateState { it.copy(beneficiaryIncreasePct = event.value) }
            is GroupFormEvent.AllowPartialPaymentToggled -> updateState { it.copy(allowPartialPayment = event.allow) }
            is GroupFormEvent.UseStandardConstitutionToggled -> updateState { it.copy(useStandardConstitution = event.use) }
            is GroupFormEvent.LogoEmojiSelected -> updateState { it.copy(logoEmoji = event.emoji) }
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
        updateState { it.copy(
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
        pendingConstitutionBytes = fileBytes
        updateState { it.copy(pendingConstitutionName = fileName, useStandardConstitution = false) }
    }

    /**
     * Stores the constitution the admin picked for the group that was just created. The upload is
     * intentionally non-fatal and only logged: losing a completed registration because a PDF could
     * not be stored would discard the group, and the document can be attached again afterwards.
     */
    private suspend fun storePendingConstitution(groupId: String) {
        val bytes = pendingConstitutionBytes ?: return
        val fileName = uiState.value.pendingConstitutionName ?: return
        val repository = documentRepository ?: return
        repository.uploadConstitution(groupId, bytes, fileName)
            .onSuccess { url ->
                pendingConstitutionBytes = null
                updateState { it.copy(constitutionUrl = url) }
            }
            .onFailure { e ->
                AppLogger.w("GroupRegViewModel", "Constitution upload failed for group $groupId: ${e.message}")
            }
    }

    /**
     * Validates and submits the group draft.
     * Creates group and automatically registers creator as the initial member.
     */
    fun submitGroup() {
        val s = uiState.value
        val alreadyCreated = s.createdGroupId
        if (!alreadyCreated.isNullOrBlank()) {
            // The group exists in the database already (the payment sheet was dismissed after
            // creation). Running creation again would register a duplicate group and a second
            // admin account, so resume the step that is still owed for this group instead.
            if (s.platformRegistrationFee == Money.ZERO) {
                launchJob("submit_group") {
                    storePendingConstitution(alreadyCreated)
                    autoActivateZeroFee(alreadyCreated)
                }
            } else {
                updateState { it.copy(needsPayment = true, error = null) }
            }
            return
        }

        val groupRes = RegisterGroupValidator.toGroupDraft(s)
        if (groupRes.isFailure) {
            updateState { it.copy(error = groupRes.exceptionOrNull()?.message ?: "Validation failed. Please check all fields.") }
            return
        }
        val group = groupRes.getOrThrow()

        launchJob("submit_group") {
            updateState { it.copy(isSubmitting = true, error = null) }
            val result = if (s.street.isNotBlank() || s.township.isNotBlank()) {
                createGroupUseCase(
                    group = group,
                    adminEmail = s.adminEmail,
                    adminPassword = s.adminPassword,
                    adminFullName = s.adminFullName,
                    adminPhone = s.adminPhone,
                    adminIdNumber = s.adminIdNumber,
                    adminStreet = s.street.takeIf { it.isNotBlank() },
                    adminSuburb = s.township.takeIf { it.isNotBlank() }
                )
            } else {
                createGroupUseCase(
                    group = group,
                    adminEmail = s.adminEmail,
                    adminPassword = s.adminPassword,
                    adminFullName = s.adminFullName,
                    adminPhone = s.adminPhone,
                    adminIdNumber = s.adminIdNumber
                )
            }

            result.onSuccess { id ->
                // Cache user profile for downstream forms
                if (s.adminFullName.isNotBlank() && s.adminEmail.isNotBlank()) {
                    userProfileCache?.save(s.adminFullName, s.adminEmail, s.adminPhone)
                }

                storePendingConstitution(id)

                if (s.platformRegistrationFee == Money.ZERO) {
                    autoActivateZeroFee(id)
                } else {
                    updateState { it.copy(createdGroupId = id, isSubmitting = false, needsPayment = true) }
                }
            }.onFailure { e ->
                updateState { it.copy(isSubmitting = false, error = e.toUserMessage()) }
            }
        }
    }

    /**
     * Activates a group that owes no platform registration fee. Kept separate from creation so a
     * retry after a failed activation reuses the group that already exists.
     */
    private suspend fun autoActivateZeroFee(groupId: String) {
        updateState { it.copy(isSubmitting = true, error = null) }
        activateGroupUseCase(groupId, txId = "FREE_ONBOARDING:$groupId")
            .onSuccess {
                warmUpMemberContext()
                updateState { it.copy(createdGroupId = groupId, isSubmitting = false, success = true, needsPayment = false) }
            }
            .onFailure { e ->
                AppLogger.w("GroupRegViewModel", "Auto-activation failed for zero-fee group: ${e.message}")
                // Nothing is owed here, so the payment screen would be a dead end;
                // keep the group and surface the failure so the admin can retry.
                updateState { it.copy(createdGroupId = groupId, isSubmitting = false, needsPayment = false, error = e.toUserMessage()) }
            }
    }

    private suspend fun warmUpMemberContext() {
        val userId = supabaseRepo.currentUserId
        if (userId != null) {
            memberContextCacheService?.warmUpForUser(userId)
        }
    }

    /**
     * Finalizes the registration by activating the group after payment confirmation
     * and warming up member context cache for onboarding.
     * @param txId The external transaction reference (e.g., from YoCo).
     */
    fun finalizeRegistrationAfterPayment(txId: String?) {
        val groupId = uiState.value.createdGroupId
        if (groupId.isNullOrBlank()) {
            // The fee may already be captured at this point, so silently returning would take
            // the money and leave the group inactive with nothing on screen to explain it.
            AppLogger.e(tag = "GroupRegViewModel", message = "Payment finalization has no group to activate (txId=$txId).")
            updateState { it.copy(error = "We could not match this payment to a group. Your group is not active yet - please retry the registration or contact support.") }
            return
        }
        launchJob("finalize_payment") {
            updateState { it.copy(isSubmitting = true, error = null) }
            storePendingConstitution(groupId)
            activateGroupUseCase(groupId, txId)
                .onSuccess {
                    warmUpMemberContext()
                    updateState { it.copy(isSubmitting = false, success = true, needsPayment = false) }
                }
                .onFailure { e ->
                    updateState { it.copy(isSubmitting = false, error = e.toUserMessage()) }
                }
        }
    }
}
