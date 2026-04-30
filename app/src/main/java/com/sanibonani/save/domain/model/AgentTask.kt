package com.sanibonani.save.domain.model

import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import android.os.Parcelable

@Serializable
@Parcelize
data class AgentTask(
    @SerialName("id") val id: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("payload") val payload: String = "",
    @SerialName("created_at") val createdAt: String? = null
) : Parcelable
