package com.sanibonani.save.viewmodel.treasury

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.usecase.UpdateGroupSettingsUseCase
import com.sanibonani.save.domain.usecase.groups.GenerateStandardConstitutionUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.UIEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminSettingsUiState(
    val group: Group? = null,
    val settings: GroupSettings = GroupSettings(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AdminSettingsViewModel @Inject constructor(
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val groupRepo: GroupRepository,
    private val updateGroupSettingsUseCase: UpdateGroupSettingsUseCase,
    private val generateStandardConstitutionUseCase: GenerateStandardConstitutionUseCase
) : BaseViewModel<AdminSettingsUiState>(AdminSettingsUiState()) {

    val state: StateFlow<AdminSettingsUiState> = uiState

    init {
        viewModelScope.launch {
            adminContextCacheService.currentGroup.collect { group ->
                updateState { it.copy(group = group, settings = group?.let { toGroupSettings(it) } ?: GroupSettings()) }
            }
        }
    }

    fun updateSetting(key: String, value: Any) {
        updateState { s ->
            val updated = when (key) {
                "joining_fee" -> s.settings.copy(joiningFee = value as String)
                "monthly_contribution" -> s.settings.copy(monthlyContribution = value as String)
                "late_fee" -> s.settings.copy(lateFee = value as String)
                "allow_partial_payment" -> s.settings.copy(allowPartialPayment = value as Boolean)
                "bank_name" -> s.settings.copy(bankName = value as String)
                "account_number" -> s.settings.copy(accountNumber = value as String)
                "branch_code" -> s.settings.copy(branchCode = value as String)
                "financial_provider_id" -> s.settings.copy(financialProviderId = value as? String)
                else -> s.settings
            }
            s.copy(settings = updated)
        }
    }

    fun saveSettings() {
        val groupId = uiState.value.group?.id ?: return
        viewModelScope.launch {
            updateState { it.copy(isSaving = true) }
            updateGroupSettingsUseCase(groupId, uiState.value.settings).onSuccess {
                updateState { it.copy(isSaving = false, saveSuccess = true) }
                sendEvent(UIEvent.ShowMessage("Settings saved"))
                adminContextCacheService.warmUpGroup(groupId)
            }
        }
    }

    private fun toGroupSettings(group: Group) = GroupSettings(
        joiningFee = String.format("%.2f", group.joiningFee.toDouble()),
        monthlyContribution = String.format("%.2f", group.monthlyContribution.toDouble()),
        lateFee = String.format("%.2f", group.lateFee.toDouble()),
        allowPartialPayment = group.allowPartialPayment,
        bankName = group.bankName ?: "",
        accountNumber = group.accountNumber ?: "",
        branchCode = group.branchCode ?: "",
        financialProviderId = group.financialProviderId
    )
}
