package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.features.admin.presentation.state.PlatformFeeConfigUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformFeeConfigViewModel @Inject constructor(
    private val platformRepo: PlatformRepository
) : BaseViewModel<PlatformFeeConfigUiState>(PlatformFeeConfigUiState()) {

    val state: StateFlow<PlatformFeeConfigUiState> = uiState

    fun loadSettings() {
        launchJob("load_settings") {
            platformRepo.getPlatformSettings().onSuccess { config ->
                updateState { it.copy(
                    memberCharge = config.monthlyMemberFee.toPlainString(),
                    registrationFee = config.registrationFee.toPlainString(),
                    payoutFee = config.payoutFee.toPlainString(),
                    lateFeePercent = config.lateFeePercent.toPlainString(),
                    shortfallRecoveryFee = config.shortfallRecoveryFee.toPlainString(),
                    loanInterestRate = config.loanInterestRate.toPlainString(),
                    loanProcessingFee = config.loanProcessingFee.toPlainString(),
                    groupSuspensionLiftingFee = config.groupSuspensionLiftingFee.toPlainString(),
                    memberSuspensionLiftingFee = config.memberSuspensionLiftingFee.toPlainString(),
                    autoSuspensionDays = config.autoSuspensionDays.toString(),
                    platformBankName = config.bankName,
                    platformAccountNumber = config.accountNumber,
                    platformBranchCode = config.branchCode,
                    platformAccountHolder = config.accountHolder
                ) }
            }
            platformRepo.getGranularFeeConfigs().onSuccess { loaded ->
                val configs = if (loaded.isNotEmpty()) loaded else createDefaultGranularFeeConfigs()
                val inputs = buildGranularFeeInputs(configs)
                updateState { it.copy(granularFees = configs, granularFeeInputs = inputs) }
            }.onFailure {
                val configs = createDefaultGranularFeeConfigs()
                val inputs = buildGranularFeeInputs(configs)
                updateState { it.copy(granularFees = configs, granularFeeInputs = inputs) }
            }
        }
    }

    fun updateMemberCharge(value: String) { updateState { it.copy(memberCharge = value) } }
    fun updateRegistrationFee(value: String) { updateState { it.copy(registrationFee = value) } }
    fun updatePayoutFee(value: String) { updateState { it.copy(payoutFee = value) } }
    fun updateLateFeePercent(value: String) { updateState { it.copy(lateFeePercent = value) } }
    fun updateShortfallRecoveryFee(value: String) { updateState { it.copy(shortfallRecoveryFee = value) } }
    fun updateLoanInterestRate(value: String) { updateState { it.copy(loanInterestRate = value) } }
    fun updateLoanProcessingFee(value: String) { updateState { it.copy(loanProcessingFee = value) } }
    fun updateGroupSuspensionLiftingFee(value: String) { updateState { it.copy(groupSuspensionLiftingFee = value) } }
    fun updateMemberSuspensionLiftingFee(value: String) { updateState { it.copy(memberSuspensionLiftingFee = value) } }
    fun updateAutoSuspensionDays(value: String) { updateState { it.copy(autoSuspensionDays = value) } }

    fun updatePlatformBankName(value: String) { updateState { it.copy(platformBankName = value) } }
    fun updatePlatformAccountNumber(value: String) { updateState { it.copy(platformAccountNumber = value) } }
    fun updatePlatformBranchCode(value: String) { updateState { it.copy(platformBranchCode = value) } }
    fun updatePlatformAccountHolder(value: String) { updateState { it.copy(platformAccountHolder = value) } }

    fun saveGlobalFees() {
        launchJob("save_fees") {
            updateState { it.copy(isSaving = true) }
            val config = PlatformConfig(
                monthlyMemberFee = Money(uiState.value.memberCharge.replace(',', '.').ifBlank { "0.00" }),
                registrationFee = Money(uiState.value.registrationFee.replace(',', '.').ifBlank { "0.00" }),
                payoutFee = Money(uiState.value.payoutFee.replace(',', '.').ifBlank { "0.00" }),
                lateFeePercent = Money(uiState.value.lateFeePercent.replace(',', '.').ifBlank { "0.00" }),
                shortfallRecoveryFee = Money(uiState.value.shortfallRecoveryFee.replace(',', '.').ifBlank { "0.00" }),
                loanInterestRate = Money(uiState.value.loanInterestRate.replace(',', '.').ifBlank { "20.00" }),
                loanProcessingFee = Money(uiState.value.loanProcessingFee.replace(',', '.').ifBlank { "15.00" }),
                groupSuspensionLiftingFee = Money(uiState.value.groupSuspensionLiftingFee.replace(',', '.').ifBlank { "250.00" }),
                memberSuspensionLiftingFee = Money(uiState.value.memberSuspensionLiftingFee.replace(',', '.').ifBlank { "50.00" }),
                autoSuspensionDays = uiState.value.autoSuspensionDays.toIntOrNull() ?: 90,
                bankName = uiState.value.platformBankName,
                accountNumber = uiState.value.platformAccountNumber,
                branchCode = uiState.value.platformBranchCode,
                accountHolder = uiState.value.platformAccountHolder
            )
            platformRepo.updateGlobalFees(config).onSuccess {
                updateState { it.copy(isSaving = false, saveSuccess = true) }
                showMessage("Global fees and platform bank details updated")
            }.onFailure {
                updateState { it.copy(isSaving = false) }
                handleError(it)
            }
        }
    }

    fun mapGlobalToGranularFees() {
        val globalMonthly = safeRunCatching { Money(uiState.value.memberCharge.replace(',', '.').trim()) }.getOrDefault(Money("10.0"))
        val globalReg = safeRunCatching { Money(uiState.value.registrationFee.replace(',', '.').trim()) }.getOrDefault(Money("700.0"))
        val globalRecovery = safeRunCatching { Money(uiState.value.shortfallRecoveryFee.replace(',', '.').trim()) }.getOrDefault(Money("20.0"))

        val updated = uiState.value.granularFees.map { config ->
            config.copy(
                monthlyFee = globalMonthly,
                registrationFee = globalReg,
                shortfallRecoveryFee = globalRecovery
            )
        }
        val inputs = buildGranularFeeInputs(updated)
        updateState { it.copy(granularFees = updated, granularFeeInputs = inputs) }
        showMessage("Global fields mapped to all group tier fields successfully.")
    }

    fun updateGranularFeeInput(type: GroupType, cycle: Int, field: String, value: String) {
        val key = "${type}_${cycle}_$field"
        val newInputs = uiState.value.granularFeeInputs + (key to value)

        val currentList = uiState.value.granularFees.ifEmpty { createDefaultGranularFeeConfigs() }
        val current = currentList.find { (it.groupType == type) && (it.roscaCycle == cycle) }
            ?: PlatformFeeConfig(groupType = type, roscaCycle = cycle)

        val cleanVal = value.replace(',', '.').trim()
        val parsedMoney = if (cleanVal.isBlank() || cleanVal == ".") {
            null
        } else {
            safeRunCatching { Money(cleanVal) }.getOrNull()
        }

        val updatedConfig = when (field) {
            "registration" -> if (parsedMoney != null) current.copy(registrationFee = parsedMoney) else current
            "monthly" -> if (parsedMoney != null) current.copy(monthlyFee = parsedMoney) else current
            "shortfall" -> if (parsedMoney != null) current.copy(shortfallRecoveryFee = parsedMoney) else current
            else -> current
        }

        val updatedList = currentList.filterNot { it.groupType == type && it.roscaCycle == cycle } + updatedConfig
        updateState { it.copy(granularFeeInputs = newInputs, granularFees = updatedList) }
    }

    fun saveGranularFees() {
        launchJob("save_granular_fees") {
            updateState { it.copy(isSavingGranularFee = true) }
            uiState.value.granularFees.forEach {
                platformRepo.updateGranularFeeConfig(it).onFailure { e -> AppLogger.e("PlatformFeeConfigVM", "Failed to save fee for ${it.groupType}", e) }
            }
            updateState { it.copy(isSavingGranularFee = false) }
            showMessage("Granular fees saved")
        }
    }

    private fun buildGranularFeeInputs(configs: List<PlatformFeeConfig>): MutableMap<String, String> {
        val inputs = mutableMapOf<String, String>()
        configs.forEach { c ->
            inputs["${c.groupType}_${c.roscaCycle}_registration"] = c.registrationFee.toPlainString()
            inputs["${c.groupType}_${c.roscaCycle}_monthly"] = c.monthlyFee.toPlainString()
            inputs["${c.groupType}_${c.roscaCycle}_shortfall"] = c.shortfallRecoveryFee.toPlainString()
        }
        return inputs
    }

    private fun createDefaultGranularFeeConfigs(): List<PlatformFeeConfig> {
        return listOf(
            PlatformFeeConfig(groupType = GroupType.STOKVEL, roscaCycle = 0, registrationFee = Money(100.0), monthlyFee = Money(10.0), shortfallRecoveryFee = Money(5.0)),
            PlatformFeeConfig(groupType = GroupType.BURIAL_SOCIETY, roscaCycle = 0, registrationFee = Money(50.0), monthlyFee = Money(10.0), shortfallRecoveryFee = Money(5.0)),
            PlatformFeeConfig(groupType = GroupType.INVESTMENT_CLUB, roscaCycle = 0, registrationFee = Money(150.0), monthlyFee = Money(20.0), shortfallRecoveryFee = Money(10.0)),
            PlatformFeeConfig(groupType = GroupType.ROSCA, roscaCycle = 3, registrationFee = Money(50.0), monthlyFee = Money(10.0), shortfallRecoveryFee = Money(5.0)),
            PlatformFeeConfig(groupType = GroupType.ROSCA, roscaCycle = 6, registrationFee = Money(50.0), monthlyFee = Money(10.0), shortfallRecoveryFee = Money(5.0)),
            PlatformFeeConfig(groupType = GroupType.ROSCA, roscaCycle = 12, registrationFee = Money(50.0), monthlyFee = Money(10.0), shortfallRecoveryFee = Money(5.0)),
            PlatformFeeConfig(groupType = GroupType.EMERGENCY_FUND, roscaCycle = 0, registrationFee = Money(50.0), monthlyFee = Money(10.0), shortfallRecoveryFee = Money(5.0))
        )
    }
}
