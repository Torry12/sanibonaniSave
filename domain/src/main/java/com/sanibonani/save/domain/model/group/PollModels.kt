
@file:OptIn(ExperimentalSerializationApi::class)
package com.sanibonani.save.domain.model.group

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

@Serializable
enum class PollStatus {
    @SerialName("draft") DRAFT,
    @SerialName("open") OPEN,
    @SerialName("closed") CLOSED,
    @SerialName("cancelled") CANCELLED
}

@Serializable
enum class PollType {
    @SerialName("general") GENERAL,
    @SerialName("fee_change") FEE_CHANGE,
    @SerialName("rosca_order") ROSCA_ORDER,
    @SerialName("loan_approval") LOAN_APPROVAL,
    @SerialName("member_removal") MEMBER_REMOVAL
}

@Serializable
enum class EffectStatus {
    @SerialName("none") NONE,
    @SerialName("pending") PENDING,
    @SerialName("applied") APPLIED,
    @SerialName("failed") FAILED
}

@Serializable
@Parcelize
data class GroupPoll(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id") val groupId: String = "",
    @SerialName("created_by_member_id") val createdByMemberId: String? = null,
    val title: String = "",
    val description: String? = null,
    val type: PollType = PollType.GENERAL,
    val status: PollStatus = PollStatus.OPEN,
    @SerialName("effect_status") val effectStatus: EffectStatus = EffectStatus.NONE,
    @SerialName("effect_data") val effectData: String? = null,
    @SerialName("allow_multiple_choice") val allowMultipleChoice: Boolean = false,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable

@Serializable
@Parcelize
data class GroupPollOption(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("poll_id") val pollId: String = "",
    val label: String = "",
    val position: Int = 1,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null
) : Parcelable

@Serializable
@Parcelize
data class GroupPollVote(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("poll_id") val pollId: String = "",
    @SerialName("option_id") val optionId: String = "",
    @SerialName("member_id") val memberId: String = "",
    @SerialName("group_id") val groupId: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable

@Serializable
@Parcelize
data class GroupPollWithOptions(
    val poll: GroupPoll,
    val options: List<GroupPollOption> = emptyList(),
    @SerialName("my_vote_option_id") val myVoteOptionId: String? = null
) : Parcelable
