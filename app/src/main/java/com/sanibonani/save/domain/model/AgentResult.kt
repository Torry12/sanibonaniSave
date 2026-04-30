package com.sanibonani.save.domain.model

import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import android.os.Parcelable

@Serializable
@Parcelize
data class AgentResult(
    @SerialName("task_id") val taskId: String = "",
    @SerialName("status") val status: String = "",
    @SerialName("output") val output: String = "",
    @SerialName("created_at") val createdAt: String? = null
) : Parcelable
