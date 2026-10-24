package com.sanibonani.save.domain.model

import kotlinx.serialization.*

@Serializable
data class AppNotification(
    val id: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("member_id") val memberId: String? = null,
    val message: String = "",
    val channel: NotifChannel = NotifChannel.PUSH,
    @SerialName("trigger_event") val triggerEvent: NotifEvent = NotifEvent.CUSTOM,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
enum class NotifChannel {
    @SerialName("push") PUSH,
    @SerialName("sms") SMS,
    @SerialName("whatsapp") WHATSAPP,
    @SerialName("email") EMAIL,
    @SerialName("both") BOTH
}

@Serializable
enum class NotifEvent {
    @SerialName("payment_confirmed") PAYMENT_CONFIRMED,
    @SerialName("payment_failed") PAYMENT_FAILED,
    @SerialName("deposit_received") DEPOSIT_RECEIVED,
    @SerialName("loan_requested") LOAN_REQUESTED,
    @SerialName("loan_approved") LOAN_APPROVED,
    @SerialName("loan_rejected") LOAN_REJECTED,
    @SerialName("loan_disbursed") LOAN_DISBURSED,
    @SerialName("loan_repaid") LOAN_REPAID,
    @SerialName("loan_defaulted") LOAN_DEFAULTED,
    @SerialName("payout_requested") PAYOUT_REQUESTED,
    @SerialName("payout_processed") PAYOUT_PROCESSED,
    @SerialName("member_joined") MEMBER_JOINED,
    @SerialName("member_left") MEMBER_LEFT,
    @SerialName("group_suspended") GROUP_SUSPENDED,
    @SerialName("group_restored") GROUP_RESTORED,
    @SerialName("platform_fee_due") PLATFORM_FEE_DUE,
    @SerialName("platform_fee_warning") PLATFORM_FEE_WARNING,
    @SerialName("discrepancy_found") DISCREPANCY_FOUND,
    @SerialName("probation_ended") PROBATION_ENDED,
    @SerialName("custom") CUSTOM,
    @SerialName("member_message") MEMBER_MESSAGE,
    @SerialName("platform_broadcast") PLATFORM_BROADCAST
}
