package com.sanibonani.save.viewmodel.equity

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.usecase.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.treasury.ActivateGroupUseCase
import com.sanibonani.save.domain.usecase.groups.GenerateStandardConstitutionUseCase
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.GroupFormEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupRegistrationState(
    val name: String = "",
    val type: GroupType = GroupType.STOKVEL,
    val currentStep: Int = 1,
    val totalSteps: Int = 6,
    val isSubmitting: Boolean = false,
    val success: Boolean = false,
    val needsPayment: Boolean = false,
    val createdGroupId: String? = null,
    val platformRegistrationFee: Money = Money.ZERO,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GroupRegistrationViewModel @Inject constructor(
    private val createGroupUseCase: CreateGroupUseCase,
    private val activateGroupUseCase: ActivateGroupUseCase,
    private val documentRepo: GroupDocumentRepository,
    private val platformRepo: PlatformRepository,
    private val generateStandardConstitutionUseCase: GenerateStandardConstitutionUseCase,
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<GroupRegistrationState>(GroupRegistrationState()) {

    val state: StateFlow<GroupRegistrationState> = uiState

    fun nextStep() { updateState { it.copy(currentStep = (it.currentStep + 1).coerceAtMost(it.totalSteps)) } }
    fun prevStep() { updateState { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(1)) } }

    fun submitGroup() {
        viewModelScope.launch {
            updateState { it.copy(isSubmitting = true) }
            // Simulation logic
            updateState { it.copy(isSubmitting = false, needsPayment = true, createdGroupId = "sim-id") }
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
