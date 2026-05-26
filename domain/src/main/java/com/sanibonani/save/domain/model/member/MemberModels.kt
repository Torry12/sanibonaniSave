package com.sanibonani.save.domain.model.member

import android.os.Parcelable
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.financial.Contribution
import com.sanibonani.save.domain.model.financial.Beneficiary
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

@Serializable
enum class MemberStatus(val displayName: String) {
    @SerialName("active")           ACTIVE("Active"),
    @SerialName("probation")        PROBATION("Probation"),
    @SerialName("suspended")        SUSPENDED("Suspended"),
    @SerialName("pending_payment")  PENDING_PAYMENT("Pending Payment")
}

@Serializable
enum class NotificationPref(val displayName: String) {
    @SerialName("whatsapp") WHATSAPP("WhatsApp"),
    @SerialName("email")    EMAIL("Email"),
    @SerialName("both")     BOTH("Both")
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Parcelize
data class Member(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id")           val groupId: String = "",
    @SerialName("user_id")            val userId: String? = null,
    @SerialName("full_name")          val fullName: String = "",
    @SerialName("id_number")          val idNumber: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val phone: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val email: String? = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val street: String? = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val suburb: String? = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val city: String? = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val province: String? = "",
    @SerialName("notification_pref")  val notificationPref: NotificationPref = NotificationPref.BOTH,
    val status: MemberStatus = MemberStatus.PROBATION,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("joined_at") val joinedAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("probation_end_at") val probationEndAt: String? = null,
    @SerialName("profile_photo_url")  val profilePhotoUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_1_url") val document1Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_1_type") val document1Type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_1_status") val document1Status: DocumentStatus = DocumentStatus.PENDING,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_2_url") val document2Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_2_type") val document2Type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_2_status") val document2Status: DocumentStatus = DocumentStatus.PENDING,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_3_url") val document3Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_3_type") val document3Type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_3_status") val document3Status: DocumentStatus = DocumentStatus.PENDING,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_4_url") val document4Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_4_type") val document4Type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_4_status") val document4Status: DocumentStatus = DocumentStatus.PENDING,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_5_url") val document5Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_5_type") val document5Type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("document_5_status") val document5Status: DocumentStatus = DocumentStatus.PENDING,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("beneficiary_count") val beneficiaryCount: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("beneficiary_over_65_count") val beneficiaryOver65Count: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("monthly_contribution_override") val monthlyContributionOverride: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("total_contributions") val totalContributions: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("total_paid") val totalPaid: Double? = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("fcm_token") val fcmToken: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("member_key") val memberKey: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null,
    @Transient val beneficiaries: List<Beneficiary> = emptyList(),
    @Transient val documents: List<MemberDocument> = emptyList()
) : Parcelable

@Serializable
@Parcelize
data class MemberDocument(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("member_id")      val memberId: String = "",
    @SerialName("group_id")       val groupId: String = "",
    val label: String = "",
    @SerialName("document_url")   val documentUrl: String = "",
    @SerialName("document_type")  val documentType: String? = null,
    val status: DocumentStatus = DocumentStatus.PENDING,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable
