package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

@Serializable
@Parcelize
enum class RiskLevel(val displayName: String) : Parcelable {
    @SerialName("low") LOW("Low Risk"),
    @SerialName("moderate") MODERATE("Moderate Risk"),
    @SerialName("high") HIGH("High Risk"),
    @SerialName("critical") CRITICAL("Critical Risk")
}
