package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*

data class PlatformFeeConfigUiState(
    val memberCharge: String = "10.0",
    val registrationFee: String = "700.0",
    val payoutFee: String = "5.0",
    val lateFeePercent: String = "10.0",
    val shortfallRecoveryFee: String = "20.0",
    val loanInterestRate: String = "20.0",
    val loanProcessingFee: String = "15.0",
    val groupSuspensionLiftingFee: String = "250.0",
    val memberSuspensionLiftingFee: String = "50.0",
    val autoSuspensionDays: String = "90",
    val platformBankName: String = "",
    val platformAccountNumber: String = "",
    val platformBranchCode: String = "",
    val platformAccountHolder: String = "",
    val granularFees: List<PlatformFeeConfig> = emptyList(),
    val granularFeeInputs: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val isSavingGranularFee: Boolean = false,
    val saveSuccess: Boolean = false
)
