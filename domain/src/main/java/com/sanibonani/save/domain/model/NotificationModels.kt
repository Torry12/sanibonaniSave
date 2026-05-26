package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

@Serializable
enum class NotifChannel {
    @SerialName("whatsapp") WHATSAPP,
    @SerialName("email")    EMAIL,
    @SerialName("both")     BOTH
}

@Serializable
enum class NotifEvent {
    @SerialName("payment_due")           PAYMENT_DUE,
    @SerialName("payment_confirmed")     PAYMENT_CONFIRMED,
    @SerialName("payment_overdue")       PAYMENT_OVERDUE,
    @SerialName("deposit_received")      DEPOSIT_RECEIVED,
    @SerialName("document_verified")     DOCUMENT_VERIFIED,
    @SerialName("probation_ended")       PROBATION_ENDED,
    @SerialName("new_member")            NEW_MEMBER,
    @SerialName("fee_settings_changed")  FEE_SETTINGS_CHANGED,
    @SerialName("platform_fee_due")      PLATFORM_FEE_DUE,
    @SerialName("platform_fee_warning")  PLATFORM_FEE_WARNING,
    @SerialName("group_suspended")       GROUP_SUSPENDED,
    @SerialName("group_restored")        GROUP_RESTORED,
    @SerialName("payout_requested")      PAYOUT_REQUESTED,
    @SerialName("payout_processed")       PAYOUT_PROCESSED,
    @SerialName("payout_failed")          PAYOUT_FAILED,
    @SerialName("payout_cancelled")       PAYOUT_CANCELLED,
    @SerialName("loan_requested")        LOAN_REQUESTED,
    @SerialName("loan_approved")         LOAN_APPROVED,
    @SerialName("loan_rejected")         LOAN_REJECTED,
    @SerialName("loan_defaulted")        LOAN_DEFAULTED,
    @SerialName("actuarial_alert")       ACTUARIAL_ALERT,
    @SerialName("investment_payout")     INVESTMENT_PAYOUT,
    @SerialName("member_message")        MEMBER_MESSAGE,
    @SerialName("custom")                CUSTOM
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Parcelize
data class AppNotification(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id")       val groupId: String = "",
    @SerialName("member_id")      val memberId: String? = null,
    val message: String = "",
    @SerialName("channel")        val channel: NotifChannel = NotifChannel.BOTH,
    @SerialName("trigger_event")  val triggerEvent: NotifEvent = NotifEvent.CUSTOM,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable
