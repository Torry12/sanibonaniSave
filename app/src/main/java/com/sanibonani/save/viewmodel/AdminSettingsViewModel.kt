package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.RoscaRotationMethod
import com.sanibonani.save.domain.usecase.UpdateGroupSettingsUseCase
import com.sanibonani.save.domain.usecase.groups.GenerateStandardConstitutionUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.viewmodel.state.admin.AdminSettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminSettingsViewModel @Inject constructor(
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val updateGroupSettingsUseCase: UpdateGroupSettingsUseCase,
    private val generateStandardConstitutionUseCase: GenerateStandardConstitutionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AdminSettingsState())
    val state: StateFlow<AdminSettingsState> = _state.asStateFlow()

    init {
        observeGroupContext()
    }

    private fun observeGroupContext() {
        adminContextCacheService.currentGroup.onEach { group ->
            group?.let { g ->
                _state.update { it.copy(
                    settings = it.settings.copy(
                        joiningFee = g.joiningFee.toString(),
                        monthlyContribution = g.monthlyContribution.toString(),
                        lateFee = g.lateFee.toString(),
                        lateFeeGraceDays = g.lateFeeGraceDays.toString(),
                        probationMonths = g.probationMonths.toString(),
                        maxMembers = g.maxMembers.toString(),
                        allowPartialPayment = g.allowPartialPayment,
                        maxBeneficiaries = g.maxBeneficiaries?.toString() ?: "0",
                        beneficiaryIncreasePct = g.beneficiaryIncreasePct?.toString() ?: "0",
                        goalAmount = g.goalAmount.toString(),
                        periodMonths = g.periodMonths.toString(),
                        loanInterestRate = g.loanInterestRate?.toString() ?: "0",
                        loanMaxAmount = g.loanMaxAmount?.toString() ?: "0",
                        loanMaxMonths = g.loanMaxMonths?.toString() ?: "0",
                        rotationMethod = g.rotationMethod
                    )
                ) }
            }
        }.launchIn(viewModelScope)
    }

    fun updateSetting(key: String, value: Any) {
        _state.update {
            it.copy(
                settings = when (key) {
                    "monthlyContribution" -> it.settings.copy(monthlyContribution = value as String)
                    "joiningFee" -> it.settings.copy(joiningFee = value as String)
                    "lateFee" -> it.settings.copy(lateFee = value as String)
                    "lateFeeGraceDays" -> it.settings.copy(lateFeeGraceDays = value as String)
                    "probationMonths" -> it.settings.copy(probationMonths = value as String)
                    "maxMembers" -> it.settings.copy(maxMembers = value as String)
                    "allowPartialPayment" -> it.settings.copy(allowPartialPayment = value as Boolean)
                    "maxBeneficiaries" -> it.settings.copy(maxBeneficiaries = value as String)
                    "beneficiaryIncreasePct" -> it.settings.copy(beneficiaryIncreasePct = value as String)
                    "goalAmount" -> it.settings.copy(goalAmount = value as String)
                    "periodMonths" -> it.settings.copy(periodMonths = value as String)
                    "loanInterestRate" -> it.settings.copy(loanInterestRate = value as String)
                    "loanMaxAmount" -> it.settings.copy(loanMaxAmount = value as String)
                    "loanMaxMonths" -> it.settings.copy(loanMaxMonths = value as String)
                    "rotationMethod" -> it.settings.copy(rotationMethod = value as RoscaRotationMethod)
                    else -> it.settings
                },
                saveSuccess = false
            )
        }
    }

    fun saveSettings() {
        val groupId = adminContextCacheService.currentGroup.value?.id ?: return
        val settings = _state.value.settings
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            updateGroupSettingsUseCase(groupId, settings).onSuccess {
                _state.update { it.copy(isSaving = false, saveSuccess = true) }
                adminContextCacheService.refreshContext()
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun generateAndUploadStandardConstitution() {
        val groupId = adminContextCacheService.currentGroup.value?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            generateStandardConstitutionUseCase(groupId).onSuccess {
                _state.update { it.copy(isSaving = false) }
                adminContextCacheService.refreshContext()
            }.onFailure {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
}
