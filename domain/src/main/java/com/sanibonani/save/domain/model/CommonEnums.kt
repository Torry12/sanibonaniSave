package com.sanibonani.save.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DocumentStatus {
    @SerialName("pending")  PENDING,
    @SerialName("verified") VERIFIED,
    @SerialName("rejected") REJECTED
}

@Serializable
enum class AdminFeeState {
    @SerialName("paid")               PAID,
    @SerialName("due")                DUE,
    @SerialName("overdue")            OVERDUE,
    @SerialName("warning")            WARNING,
    @SerialName("suspended")          SUSPENDED,
    @SerialName("pending_activation") PENDING_ACTIVATION
}
