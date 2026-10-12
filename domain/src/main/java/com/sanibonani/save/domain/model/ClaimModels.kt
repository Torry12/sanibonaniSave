@file:OptIn(ExperimentalSerializationApi::class)

package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

// ─────────────────────────────────────────────────────────────────────────────
//  BURIAL SOCIETY CLAIM
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
enum class BeneficiaryClaimStatus(val displayName: String) {
    @SerialName("submitted")  SUBMITTED("Submitted"),
    @SerialName("under_review") UNDER_REVIEW("Under Review"),
    @SerialName("escalated")  ESCALATED("Escalated to Platform"),
    @SerialName("approved")   APPROVED("Approved"),
    @SerialName("rejected")   REJECTED("Rejected"),
    @SerialName("paid")       PAID("Paid Out"),
    @SerialName("cancelled")  CANCELLED("Cancelled")
}

@Serializable
@Parcelize
data class BeneficiaryPayoutClaim(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id")          val groupId: String = "",
    @SerialName("member_id")         val memberId: String = "",
    @SerialName("beneficiary_id")    val beneficiaryId: String = "",
    @SerialName("beneficiary_name")  val beneficiaryName: String = "",
    @SerialName("face_photo_url")    val facePhotoUrl: String? = null,
    @SerialName("face_photo_status") val facePhotoStatus: DocumentStatus = DocumentStatus.PENDING,
    @SerialName("beneficiary_id_url") val beneficiaryIdUrl: String? = null,
    @SerialName("beneficiary_id_status") val beneficiaryIdStatus: DocumentStatus = DocumentStatus.PENDING,
    @SerialName("death_certificate_url") val deathCertificateUrl: String? = null,
    @SerialName("death_certificate_status") val deathCertificateStatus: DocumentStatus = DocumentStatus.PENDING,
    @SerialName("member_id_url")      val memberIdUrl: String? = null,
    @SerialName("member_id_status")   val memberIdStatus: DocumentStatus = DocumentStatus.PENDING,
    @SerialName("cause_of_death")    val causeOfDeath: String = "",
    @SerialName("date_of_death")     val dateOfDeath: String = "",
    @SerialName("claim_amount")      val claimAmount: Money = Money.ZERO,
    @SerialName("bank_name")         val bankName: String = "",
    @SerialName("account_no")        val accountNo: String = "",
    @SerialName("branch_code")       val branchCode: String = "",
    @SerialName("account_holder")    val accountHolder: String = "",
    val notes: String? = null,
    val status: BeneficiaryClaimStatus = BeneficiaryClaimStatus.SUBMITTED,
    @SerialName("reviewed_by")       val reviewedBy: String? = null,
    @SerialName("reviewed_at")       val reviewedAt: String? = null,
    @SerialName("admin_notes")       val adminNotes: String? = null,
    @SerialName("rejection_reason")  val rejectionReason: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")        val createdAt: String? = null
) : Parcelable
