package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Parcelize
data class AuditLog(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("actor_id") val actorId: String = "",
    @SerialName("target_member_id") val targetMemberId: String? = null,
    @SerialName("target_group_id") val targetGroupId: String? = null,
    val action: String = "",
    val details: Map<String, String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable
