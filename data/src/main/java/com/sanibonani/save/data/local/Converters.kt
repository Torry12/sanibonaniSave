package com.sanibonani.save.data.local

import androidx.room.TypeConverter
import com.sanibonani.save.domain.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class Converters {
    @TypeConverter fun fromGroupType(v: GroupType): String = v.name
    @TypeConverter fun toGroupType(v: String): GroupType {
        val normalized = v.trim().uppercase()
        return GroupType.entries.find {
            it.name == normalized ||
            it.displayName.replace(" ", "_").uppercase() == normalized
        } ?: GroupType.OTHER
    }

    @TypeConverter fun fromAdminFeeState(v: AdminFeeState): String = v.name
    @TypeConverter fun toAdminFeeState(v: String): AdminFeeState = try {
        AdminFeeState.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: AdminFeeState.DUE
    } catch (e: Exception) { AdminFeeState.DUE }

    @TypeConverter fun fromMemberStatus(v: MemberStatus): String = v.name
    @TypeConverter fun toMemberStatus(v: String): MemberStatus {
        val normalized = v.trim().uppercase()
        return MemberStatus.entries.find { it.name == normalized } ?: MemberStatus.SUSPENDED
    }

    @TypeConverter fun fromDocStatus(v: DocumentStatus): String = v.name
    @TypeConverter fun toDocStatus(v: String): DocumentStatus = try {
        DocumentStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: DocumentStatus.PENDING
    } catch (e: Exception) { DocumentStatus.PENDING }

    @TypeConverter fun fromNotifPref(v: NotificationPref): String = v.name
    @TypeConverter fun toNotifPref(v: String): NotificationPref = try {
        NotificationPref.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: NotificationPref.BOTH
    } catch (e: Exception) { NotificationPref.BOTH }

    @TypeConverter fun fromContribStatus(v: ContributionStatus): String = v.name
    @TypeConverter fun toContribStatus(v: String): ContributionStatus = try {
        ContributionStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: ContributionStatus.DUE
    } catch (e: Exception) { ContributionStatus.DUE }

    @TypeConverter fun fromPaymentType(v: PaymentType): String = v.name
    @TypeConverter fun toPaymentType(v: String): PaymentType = try {
        PaymentType.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: PaymentType.CONTRIBUTION
    } catch (e: Exception) { PaymentType.CONTRIBUTION }

    @TypeConverter fun fromPaymentMethod(v: PaymentMethod): String = v.name
    @TypeConverter fun toPaymentMethod(v: String): PaymentMethod = try {
        PaymentMethod.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: PaymentMethod.BANK
    } catch (e: Exception) { PaymentMethod.BANK }

    @TypeConverter fun fromPaymentStatus(v: PaymentStatus): String = v.name
    @TypeConverter fun toPaymentStatus(v: String): PaymentStatus = try {
        PaymentStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: PaymentStatus.PENDING
    } catch (e: Exception) { PaymentStatus.PENDING }

    @TypeConverter fun fromNotifChannel(v: NotifChannel): String = v.name
    @TypeConverter fun toNotifChannel(v: String): NotifChannel = try {
        NotifChannel.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: NotifChannel.BOTH
    } catch (e: Exception) { NotifChannel.BOTH }

    @TypeConverter fun fromNotifEvent(v: NotifEvent): String = v.name
    @TypeConverter fun toNotifEvent(v: String): NotifEvent = try {
        NotifEvent.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: NotifEvent.CUSTOM
    } catch (e: Exception) { NotifEvent.CUSTOM }

    @TypeConverter fun fromPayoutStatus(v: PayoutStatus): String = v.name
    @TypeConverter fun toPayoutStatus(v: String): PayoutStatus = try {
        PayoutStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: PayoutStatus.PENDING
    } catch (e: Exception) { PayoutStatus.PENDING }

    @TypeConverter fun fromPayoutType(v: PayoutType): String = v.name
    @TypeConverter fun toPayoutType(v: String): PayoutType = try {
        PayoutType.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: PayoutType.STANDARD
    } catch (e: Exception) { PayoutType.STANDARD }

    @TypeConverter fun fromLoanStatus(v: LoanStatus): String = v.name
    @TypeConverter fun toLoanStatus(v: String): LoanStatus = try {
        LoanStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: LoanStatus.PENDING
    } catch (e: Exception) { LoanStatus.PENDING }

    @TypeConverter fun fromBeneficiaryClaimStatus(v: BeneficiaryClaimStatus): String = v.name
    @TypeConverter fun toBeneficiaryClaimStatus(v: String): BeneficiaryClaimStatus = try {
        BeneficiaryClaimStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: BeneficiaryClaimStatus.SUBMITTED
    } catch (e: Exception) { BeneficiaryClaimStatus.SUBMITTED }

    @TypeConverter fun fromRoscaRotationMethod(v: RoscaRotationMethod): String = v.name
    @TypeConverter fun toRoscaRotationMethod(v: String): RoscaRotationMethod = try {
        RoscaRotationMethod.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: RoscaRotationMethod.FIXED
    } catch (e: Exception) { RoscaRotationMethod.FIXED }

    @TypeConverter fun fromAmortizationType(v: AmortizationType): String = v.name
    @TypeConverter fun toAmortizationType(v: String): AmortizationType = try {
        AmortizationType.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: AmortizationType.FLAT
    } catch (e: Exception) { AmortizationType.FLAT }

    @TypeConverter fun fromSettlementStatus(v: SettlementStatus): String = v.name
    @TypeConverter fun toSettlementStatus(v: String): SettlementStatus = try {
        SettlementStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: SettlementStatus.PENDING
    } catch (e: Exception) { SettlementStatus.PENDING }

    @TypeConverter fun fromReconciliationStatus(v: ReconciliationStatus): String = v.name
    @TypeConverter fun toReconciliationStatus(v: String): ReconciliationStatus = try {
        ReconciliationStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: ReconciliationStatus.PENDING
    } catch (e: Exception) { ReconciliationStatus.PENDING }

    @TypeConverter fun fromBankTxStatus(v: BankTransactionStatus): String = v.name
    @TypeConverter fun toBankTxStatus(v: String): BankTransactionStatus = try {
        BankTransactionStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: BankTransactionStatus.UNRECONCILED
    } catch (e: Exception) { BankTransactionStatus.UNRECONCILED }

    @TypeConverter fun fromFinProviderType(v: FinancialProviderType): String = v.name
    @TypeConverter fun toFinProviderType(v: String): FinancialProviderType = try {
        FinancialProviderType.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: FinancialProviderType.BANK
    } catch (e: Exception) { FinancialProviderType.BANK }

    @TypeConverter fun fromPollStatus(v: PollStatus): String = v.name
    @TypeConverter fun toPollStatus(v: String): PollStatus = try {
        PollStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: PollStatus.OPEN
    } catch (e: Exception) { PollStatus.OPEN }

    @TypeConverter fun fromPollType(v: PollType): String = v.name
    @TypeConverter fun toPollType(v: String): PollType = try {
        PollType.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: PollType.GENERAL
    } catch (e: Exception) { PollType.GENERAL }

    @TypeConverter fun fromEffectStatus(v: EffectStatus): String = v.name
    @TypeConverter fun toEffectStatus(v: String): EffectStatus = try {
        EffectStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: EffectStatus.NONE
    } catch (e: Exception) { EffectStatus.NONE }

    @TypeConverter fun fromBehaviorStatus(v: BehaviorStatus): String = v.name
    @TypeConverter fun toBehaviorStatus(v: String): BehaviorStatus = try {
        BehaviorStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: BehaviorStatus.FAIR
    } catch (e: Exception) { BehaviorStatus.FAIR }

    @TypeConverter fun fromFraudRiskLevel(v: FraudRiskLevel): String = v.name
    @TypeConverter fun toFraudRiskLevel(v: String): FraudRiskLevel = try {
        FraudRiskLevel.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: FraudRiskLevel.LOW
    } catch (e: Exception) { FraudRiskLevel.LOW }

    @TypeConverter fun fromFinOpTypeList(v: List<FinancialOperationType>): String = Json.encodeToString(v)
    @TypeConverter fun toFinOpTypeList(v: String): List<FinancialOperationType> = try { Json.decodeFromString(v) } catch (e: Exception) { emptyList() }

    @TypeConverter fun fromMoney(v: Money?): String? = v?.toPlainString()
    @TypeConverter fun toMoney(v: String?): Money? = v?.let { Money(it) }

    @TypeConverter fun fromMap(v: Map<String, String>): String = Json.encodeToString(v)
    @TypeConverter fun toMap(v: String): Map<String, String> = try { Json.decodeFromString(v) } catch (e: Exception) { emptyMap() }

    @TypeConverter fun fromJsonObject(v: JsonObject?): String? = v?.let { Json.encodeToString(it) }
    @TypeConverter fun toJsonObject(v: String?): JsonObject? = v?.let { try { Json.decodeFromString(it) } catch (_: Exception) { null } }
}
