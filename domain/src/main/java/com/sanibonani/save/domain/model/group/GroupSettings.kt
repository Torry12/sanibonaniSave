package com.sanibonani.save.domain.model.group

data class GroupSettings(
    val joiningFee: String = "150",
    val monthlyContribution: String = "250",
    val lateFee: String = "50",
    val lateFeeGraceDays: String = "5",
    val probationMonths: String = "3",
    val paymentDueDay: String = "28",
    val maxMembers: String = "50",
    val allowPartialPayment: Boolean = false,
    val joiningFeeWaiver: Boolean = false,
    val autoSuspendAfter: String = "2",
    val bankName: String = "FNB",
    val accountNumber: String = "",
    val branchCode: String = "",
    val accountType: String = "Savings",
    val maxBeneficiaries: String = "0",
    val beneficiary_increase_pct: String = "0", // Corrected name or keep as is?
    val goalAmount: String = "10000",
    val periodMonths: String = "12",
    val loanInterestRate: String = "0",
    val loanMaxAmount: String = "0",
    val loanMaxMonths: String = "0",
    val rotationMethod: RoscaRotationMethod = RoscaRotationMethod.FIXED,
    val isSaving: Boolean = false,
    val savedSuccess: Boolean = false
)
