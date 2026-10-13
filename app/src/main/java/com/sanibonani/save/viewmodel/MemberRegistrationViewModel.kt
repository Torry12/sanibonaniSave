package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.service.LocationAddress
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.usecase.RegisterMemberUseCase
import com.sanibonani.save.viewmodel.state.RegisterMemberState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberRegistrationViewModel @Inject constructor(
    private val registerMemberUseCase: RegisterMemberUseCase,
    private val locationService: LocationService,
    private val memberRepo: MemberRepository,
    private val groupRepo: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterMemberState())
    val uiState: StateFlow<RegisterMemberState> = _uiState.asStateFlow()

    private var addressSearchJob: Job? = null

    fun initializeRegistration(groupId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(targetGroupId = groupId, isSubmitting = true) }
            groupRepo.getGroupById(groupId).onSuccess { group ->
                _uiState.update { it.copy(
                    joiningFee = group.joiningFee,
                    isSubmitting = false
                ) }
            }.onFailure {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onFieldChange(key: String, value: String) {
        _uiState.update { state ->
            when (key) {
                "fullName" -> state.copy(fullName = value)
                "idNumber" -> state.copy(idNumber = value)
                "phone" -> state.copy(phone = value)
                "email" -> state.copy(email = value)
                "street" -> state.copy(street = value)
                "suburb" -> state.copy(suburb = value)
                "city" -> state.copy(city = value)
                "province" -> state.copy(province = value)
                else -> state
            }
        }
    }

    fun searchAddress(query: String) {
        if (query.length < 3) {
            _uiState.update { it.copy(addressSuggestions = emptyList()) }
            return
        }
        addressSearchJob?.cancel()
        addressSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingAddress = true) }
            locationService.searchAddress(query).onSuccess { addresses ->
                _uiState.update { it.copy(addressSuggestions = addresses, isSearchingAddress = false) }
            }.onFailure {
                _uiState.update { it.copy(isSearchingAddress = false) }
            }
        }
    }

    fun onAddressSelected(address: LocationAddress) {
        _uiState.update { it.copy(
            street = address.street.orEmpty(),
            suburb = address.suburb.orEmpty(),
            city = address.city.orEmpty(),
            province = address.state.orEmpty(),
            addressSuggestions = emptyList()
        ) }
    }

    fun setNotificationPref(pref: NotificationPref) {
        _uiState.update { it.copy(notificationPref = pref) }
    }

    fun checkExistingMembership(userId: String, groupId: String) {
        viewModelScope.launch {
            memberRepo.getMemberByUserId(userId, groupId).onSuccess {
                _uiState.update { it.copy(success = true) } // Already a member
            }
        }
    }

    fun submit(userId: String, transactionId: String? = null) {
        val state = _uiState.value
        val groupId = state.targetGroupId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            
            val member = Member(
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
                status = MemberStatus.PENDING
            )

            registerMemberUseCase(member, transactionId).onSuccess {
                _uiState.update { it.copy(isSubmitting = false, success = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false, error = e.toUserMessage()) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
