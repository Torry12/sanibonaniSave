package com.sanibonani.save.features.group.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.repository.treasury.*
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.usecase.equity.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.treasury.ActivateGroupUseCase
import com.sanibonani.save.domain.usecase.groups.GenerateStandardConstitutionUseCase
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.GroupFormEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@HiltViewModel
class GroupRegistrationViewModel @Inject constructor(
    private val createGroupUseCase: CreateGroupUseCase,
    private val activateGroupUseCase: ActivateGroupUseCase,
    private val documentRepo: GroupDocumentRepository,
    private val platformRepo: PlatformRepository,
    private val locationService: LocationService,
    private val generateStandardConstitutionUseCase: GenerateStandardConstitutionUseCase,
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<GroupRegistrationState>(GroupRegistrationState()) {

    val state: StateFlow<GroupRegistrationState> = uiState

    init {
        updateState { it.copy(isLoggedIn = supabaseRepo.isLoggedIn) }
    }

    fun resetNavigation() { updateState { initialState.copy(isLoggedIn = supabaseRepo.isLoggedIn) } }

    fun nextStep() { updateState { it.copy(currentStep = (it.currentStep + 1).coerceAtMost(it.totalSteps)) } }
    fun prevStep() { updateState { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(1)) } }

    fun onEvent(event: GroupFormEvent) {
        when (event) {
            is GroupFormEvent.NameChanged -> updateState { it.copy(name = event.name) }
            is GroupFormEvent.TypeSelected -> updateState { it.copy(type = event.type) }
            is GroupFormEvent.ProvinceSelected -> updateState { it.copy(province = event.province) }
            is GroupFormEvent.CityChanged -> updateState { it.copy(city = event.city) }
            is GroupFormEvent.TownshipChanged -> updateState { it.copy(township = event.township) }
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
            is GroupFormEvent.AddressQueryChanged -> searchAddress(event.query)
            GroupFormEvent.NextStep -> nextStep()
            GroupFormEvent.PrevStep -> prevStep()
            GroupFormEvent.Submit -> submitGroup()
            else -> {}
        }
    }

    private fun searchAddress(query: String) {
        viewModelScope.launch {
            locationService.searchAddress(query).onSuccess { list ->
                updateState { it.copy(addressSuggestions = list) }
            }
        }
    }

    fun onAddressSelected(address: com.sanibonani.save.domain.service.LocationAddress) {
        updateState { it.copy(street = address.formatted, city = address.city ?: "", province = address.province ?: "", addressSuggestions = emptyList()) }
    }

    fun uploadConstitution(bytes: ByteArray, fileName: String) {
        updateState { it.copy(pendingConstitutionName = fileName, useStandardConstitution = false) }
    }

    fun submitGroup() {
        val s = uiState.value
        val group = RegisterGroupValidator.toGroupDraft(s).getOrNull() ?: return
        viewModelScope.launch {
            updateState { it.copy(isSubmitting = true) }
            createGroupUseCase(group, s.adminEmail, s.adminFullName, s.adminPhone, s.adminIdNumber, s.adminPassword).onSuccess { id ->
                updateState { it.copy(createdGroupId = id, isSubmitting = false, needsPayment = true) }
            }
        }
    }

    fun finalizeRegistrationAfterPayment(txId: String?) {
        val groupId = uiState.value.createdGroupId ?: return
        viewModelScope.launch {
            updateState { it.copy(isSubmitting = true) }
            activateGroupUseCase(groupId, txId).onSuccess {
                updateState { it.copy(isSubmitting = false, success = true, needsPayment = false) }
            }
        }
    }
}
