package com.sanibonani.save.features.member.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.GroupRepository
import com.sanibonani.save.domain.repository.system.SupabaseRepository
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.service.LocationAddress
import com.sanibonani.save.domain.usecase.equity.RegisterMemberUseCase
import com.sanibonani.save.service.UserProfileCacheService
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.features.member.presentation.state.RegisterMemberState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberRegistrationViewModel @Inject constructor(
    private val registerMemberUseCase: RegisterMemberUseCase,
    private val groupRepo: GroupRepository,
    private val supabaseRepo: SupabaseRepository,
    private val locationService: LocationService,
    private val userProfileCache: UserProfileCacheService
) : BaseViewModel<RegisterMemberState>(RegisterMemberState()) {

    val registerState: StateFlow<RegisterMemberState> = uiState

    init {
        viewModelScope.launch {
            val userId = supabaseRepo.currentUserId
            if (userId != null) {
                val profile = userProfileCache.getProfile(userId)
                updateState { it.copy(
                    fullName = profile?.fullName ?: "",
                    email = profile?.email ?: ""
                ) }
            }
        }
    }

    fun initializeRegistration(groupId: String) {
        viewModelScope.launch {
            updateState { it.copy(targetGroupId = groupId, isLoading = true) }
            groupRepo.getGroupById(groupId).onSuccess { updateState { it.copy(joiningFee = it.joiningFee, isLoading = false) } }
        }
    }

    fun onFieldChange(field: String, value: String) {
        updateState { s ->
            when (field) {
                "fullName" -> s.copy(fullName = value)
                "idNumber" -> s.copy(idNumber = value)
                "phone" -> s.copy(phone = value)
                "email" -> s.copy(email = value)
                "street" -> s.copy(street = value)
                "city" -> s.copy(city = value)
                "province" -> s.copy(province = value)
                else -> s
            }
        }
    }

    fun onAddressSelected(address: LocationAddress) {
        updateState { it.copy(street = address.formatted, city = address.city ?: "", province = address.province ?: "", addressSuggestions = emptyList()) }
    }

    fun submit(groupId: String, transactionId: String?) {
        val s = uiState.value
        viewModelScope.launch {
            updateState { it.copy(isSubmitting = true) }
            val member = Member(groupId = groupId, fullName = s.fullName, idNumber = s.idNumber, phone = s.phone, email = s.email, street = s.street, city = s.city, province = s.province)
            registerMemberUseCase(member).onSuccess { updateState { it.copy(isSubmitting = false, success = true) } }
        }
    }
}
