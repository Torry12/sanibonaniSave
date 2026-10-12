package com.sanibonani.save.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlatformConfig(
    @SerialName("monthly_member_fee") val monthlyMemberFee: Money = Money(10.0),
    @SerialName("registration_fee") val registrationFee: Money = Money(700.0),
    @SerialName("payout_fee") val payoutFee: Money = Money(5.0),
    @SerialName("whatsapp_fee") val whatsappFee: Money = Money(0.50),
    @SerialName("late_fee_percent") val lateFeePercent: Money = Money(10.0),
    @SerialName("shortfall_recovery_fee") val shortfallRecoveryFee: Money = Money(20.0),
    @SerialName("loan_interest_rate") val loanInterestRate: Money = Money(20.0),
    @SerialName("loan_processing_fee") val loanProcessingFee: Money = Money(15.0),
    @SerialName("group_suspension_lifting_fee") val groupSuspensionLiftingFee: Money = Money(250.0),
    @SerialName("member_suspension_lifting_fee") val memberSuspensionLiftingFee: Money = Money(50.0),
    @SerialName("auto_suspension_days") val autoSuspensionDays: Int = 90,
    @SerialName("bank_name") val bankName: String = "",
    @SerialName("account_number") val accountNumber: String = "",
    @SerialName("branch_code") val branchCode: String = "",
    @SerialName("account_holder") val accountHolder: String = ""
)
