package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.admin.GetGranularFeeConfigsUseCase
import com.sanibonani.save.domain.usecase.admin.GetPlatformSettingsUseCase
import com.sanibonani.save.domain.usecase.admin.UpdateGlobalFeesUseCase
import com.sanibonani.save.domain.usecase.admin.UpdateGranularFeeConfigUseCase
import com.sanibonani.save.domain.validation.InputValidator
import com.sanibonani.save.domain.validation.ValidationResult
import com.sanibonani.save.features.admin.presentation.state.PlatformFeeConfigUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformFeeConfigViewModel @Inject constructor(
    private val getPlatformSettings: GetPlatformSettingsUseCase,
    private val getGranularFeeConfigs: GetGranularFeeConfigsUseCase,
    private val updateGlobalFees: UpdateGlobalFeesUseCase,
    private val updateGranularFeeConfig: UpdateGranularFeeConfigUseCase
) : BaseViewModel<PlatformFeeConfigUiState>(PlatformFeeConfigUiState()) {

    val state: StateFlow<PlatformFeeConfigUiState> = uiState

    fun loadSettings() {
        launchJob("load_settings") {
            getPlatformSettings().onSuccess { config ->
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
            getGranularFeeConfigs().onSuccess { loaded ->
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
            val current = uiState.value
            val invalid = firstInvalidFeeField(current)
            val suspensionDays = current.autoSuspensionDays.trim().toIntOrNull()?.takeIf { it > 0 }

            if (invalid != null || suspensionDays == null) {
                updateState { it.copy(isSaving = false) }
                handleError(
                    IllegalStateException("Invalid platform fee input"),
                    invalid?.let { "$it must be an amount in rand, with at most two decimals." }
                        ?: "Auto suspension days must be a whole number greater than 0."
                )
                return@launchJob
            }

            // Every field passed the amount rule above, so these parses cannot fail.
            val config = PlatformConfig(
                monthlyMemberFee = Money(normaliseAmount(current.memberCharge)),
                registrationFee = Money(normaliseAmount(current.registrationFee)),
                payoutFee = Money(normaliseAmount(current.payoutFee)),
                lateFeePercent = Money(normaliseAmount(current.lateFeePercent)),
                shortfallRecoveryFee = Money(normaliseAmount(current.shortfallRecoveryFee)),
                loanInterestRate = Money(normaliseAmount(current.loanInterestRate)),
                loanProcessingFee = Money(normaliseAmount(current.loanProcessingFee)),
                groupSuspensionLiftingFee = Money(normaliseAmount(current.groupSuspensionLiftingFee)),
                memberSuspensionLiftingFee = Money(normaliseAmount(current.memberSuspensionLiftingFee)),
                autoSuspensionDays = suspensionDays,
                bankName = uiState.value.platformBankName,
                accountNumber = uiState.value.platformAccountNumber,
                branchCode = uiState.value.platformBranchCode,
                accountHolder = uiState.value.platformAccountHolder
            )
            updateGlobalFees(config).onSuccess {
                updateState { it.copy(isSaving = false, saveSuccess = true) }
                showMessage("Global fees and platform bank details updated")
            }.onFailure {
                updateState { it.copy(isSaving = false) }
                handleError(it)
            }
        }
    }

    /**
     * The field the admin left unreadable, if any. A cleared or mistyped price must stop the save:
     * publishing a fallback would bill groups an amount nobody chose.
     */
    private fun firstInvalidFeeField(current: PlatformFeeConfigUiState): String? =
        listOf(
            "Monthly member fee" to current.memberCharge,
            "Registration fee" to current.registrationFee,
            "Payout fee" to current.payoutFee,
            "Late fee percentage" to current.lateFeePercent,
            "Shortfall recovery fee" to current.shortfallRecoveryFee,
            "Loan interest rate" to current.loanInterestRate,
            "Loan processing fee" to current.loanProcessingFee,
            "Group suspension lifting fee" to current.groupSuspensionLiftingFee,
            "Member suspension lifting fee" to current.memberSuspensionLiftingFee
        ).firstOrNull { (label, raw) ->
            InputValidator.validateNonNegativeAmount(normaliseAmount(raw), label) is ValidationResult.Error
        }?.first

    private fun normaliseAmount(raw: String): String = raw.trim().replace(',', '.')

    fun mapGlobalToGranularFees() {
        val current = uiState.value
        val invalid = listOf(
            "Monthly member fee" to current.memberCharge,
            "Registration fee" to current.registrationFee,
            "Shortfall recovery fee" to current.shortfallRecoveryFee
        ).firstOrNull { (label, raw) ->
            InputValidator.validateNonNegativeAmount(normaliseAmount(raw), label) is ValidationResult.Error
        }
        if (invalid != null) {
            handleError(
                IllegalStateException("Invalid platform fee input"),
                "${invalid.first} must be an amount in rand, with at most two decimals."
            )
            return
        }

        val globalMonthly = Money(normaliseAmount(current.memberCharge))
        val globalReg = Money(normaliseAmount(current.registrationFee))
        val globalRecovery = Money(normaliseAmount(current.shortfallRecoveryFee))

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
                updateGranularFeeConfig(it).onFailure { e -> AppLogger.e("PlatformFeeConfigVM", "Failed to save fee for ${it.groupType}", e) }
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
            PlatformFeeConfig(groupType = GroupType.STOKVEL, roscaCycle = 0, registrationFee = Money("100.0"), monthlyFee = Money("10.0"), shortfallRecoveryFee = Money("5.0")),
            PlatformFeeConfig(groupType = GroupType.BURIAL_SOCIETY, roscaCycle = 0, registrationFee = Money("50.0"), monthlyFee = Money("10.0"), shortfallRecoveryFee = Money("5.0")),
            PlatformFeeConfig(groupType = GroupType.INVESTMENT_CLUB, roscaCycle = 0, registrationFee = Money("150.0"), monthlyFee = Money("20.0"), shortfallRecoveryFee = Money("10.0")),
            PlatformFeeConfig(groupType = GroupType.ROSCA, roscaCycle = 3, registrationFee = Money("50.0"), monthlyFee = Money("10.0"), shortfallRecoveryFee = Money("5.0")),
            PlatformFeeConfig(groupType = GroupType.ROSCA, roscaCycle = 6, registrationFee = Money("50.0"), monthlyFee = Money("10.0"), shortfallRecoveryFee = Money("5.0")),
            PlatformFeeConfig(groupType = GroupType.ROSCA, roscaCycle = 12, registrationFee = Money("50.0"), monthlyFee = Money("10.0"), shortfallRecoveryFee = Money("5.0")),
            PlatformFeeConfig(groupType = GroupType.EMERGENCY_FUND, roscaCycle = 0, registrationFee = Money("50.0"), monthlyFee = Money("10.0"), shortfallRecoveryFee = Money("5.0"))
        )
    }
}
