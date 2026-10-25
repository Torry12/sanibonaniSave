package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.service.LocationAddress
import com.sanibonani.save.domain.usecase.member.RegisterMemberUseCase
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.validation.ValidationUtils
import com.sanibonani.save.service.UserProfileCacheService
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.member.presentation.state.RegisterMemberState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

import com.sanibonani.save.service.MemberGroupContextCacheService

/**
 * ViewModel for the member registration process.
 * Handles user input, address lookup, and member enrollment in a specific group.
 */
@HiltViewModel
class MemberRegistrationViewModel @Inject constructor(
    private val registerMemberUseCase: RegisterMemberUseCase,
    private val groupRepo: GroupRepository,
    private val supabaseRepo: SupabaseRepository,
    private val locationService: LocationService,
    private val userProfileCache: UserProfileCacheService,
    private val memberContextCacheService: MemberGroupContextCacheService? = null
) : BaseViewModel<RegisterMemberState>(RegisterMemberState()) {

    val registerState: StateFlow<RegisterMemberState> = uiState

    init {
        // AI Note: Pre-populate known user data from local cache to reduce friction.
        updateState { it.copy(
            fullName = userProfileCache.getFullName(),
            email = userProfileCache.getEmail(),
            phone = userProfileCache.getPhone()
        ) }
    }

    /**
     * Initializes the registration flow for a specific group.
     * Fetches group-specific details like joining fees.
     */
    fun initializeRegistration(groupId: String) {
        launchJob("registration_init") {
            updateState { it.copy(targetGroupId = groupId, isLoading = true, error = null) }
            groupRepo.getGroupById(groupId)
                .onSuccess { group ->
                    updateState { it.copy(joiningFee = group.joiningFee, isLoading = false) }
                }
                .onFailure { e ->
                    updateState { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun searchAddress(query: String) {
        if (query.length < 3) {
            updateState { it.copy(addressSuggestions = emptyList()) }
            return
        }
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
     * Updates specific form fields in the UI state.
     */
    fun onFieldChange(field: String, value: String) {
        // Address lookup is launched outside the state update: `updateState` re-runs its lambda
        // while concurrent updates contend, which would fire the search more than once.
        if (field == "street") searchAddress(value)
        updateState { s ->
            when (field) {
                "fullName" -> s.copy(fullName = value)
                "idNumber" -> s.copy(idNumber = value)
                "phone" -> s.copy(phone = value)
                "email" -> s.copy(email = value)
                "street" -> s.copy(street = value)
                "suburb" -> s.copy(suburb = value)
                "city" -> s.copy(city = value)
                "province" -> s.copy(province = value)
                else -> s
            }
        }
    }

    /**
     * Maps a selected address suggestion to the registration form.
     */
    fun onAddressSelected(address: LocationAddress) {
        updateState { it.copy(
            street = address.formatted,
            suburb = address.township ?: it.suburb,
            city = address.city ?: it.city,
            province = address.province ?: it.province,
            addressSuggestions = emptyList(),
            isSearchingAddress = false
        ) }
    }

    /**
     * Executes the member registration request.
     * AI Optimization: Uses launchJob with "registration_submit" key to prevent accidental duplicate submissions.
     */
    fun submit(groupId: String, transactionId: String?) {
        val s = uiState.value
        val userId = supabaseRepo.currentUserId

        if (s.fullName.isBlank() || s.fullName.trim().length < 3) {
            updateState { it.copy(error = "Full Name must be at least 3 characters.") }
            return
        }
        if (s.idNumber.isNotBlank() && !ValidationUtils.isValidSAIdNumber(s.idNumber)) {
            updateState { it.copy(error = "SA ID Number must be exactly 13 digits with a valid date of birth and checksum.") }
            return
        }
        if (s.email.isNotBlank() && !ValidationUtils.isValidEmail(s.email.trim())) {
            updateState { it.copy(error = "Please enter a valid email address.") }
            return
        }

        launchJob("registration_submit") {
            updateState { it.copy(isSubmitting = true, error = null) }
            val member = Member(
                groupId = groupId,
                userId = userId,
                fullName = s.fullName.trim(),
                idNumber = s.idNumber.trim(),
                phone = s.phone.trim(),
                email = s.email.trim(),
                street = s.street.trim(),
                suburb = s.suburb.trim(),
                city = s.city.trim(),
                province = s.province.trim()
            )
            registerMemberUseCase(member, transactionId)
                .onSuccess {
                    // Save user profile for downstream forms
                    if (s.fullName.isNotBlank() && s.email.isNotBlank()) {
                        userProfileCache.save(s.fullName, s.email, s.phone)
                    }

                    // Warm up member context cache for user
                    if (userId != null) {
                        memberContextCacheService?.warmUpForUser(userId)
                    }

                    updateState { it.copy(isSubmitting = false, success = true) }
                }
                .onFailure { e ->
                    updateState { it.copy(isSubmitting = false, error = e.toUserMessage()) }
                }
        }
    }
}
