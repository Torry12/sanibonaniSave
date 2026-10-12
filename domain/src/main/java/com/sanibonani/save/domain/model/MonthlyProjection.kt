package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

/**
 * Projected financial snapshot for a single future month.
 */
@Serializable
@Parcelize
data class FutureMonthProjection(
    @SerialName("month") val month: Int = 0,
    @SerialName("label") val label: String = "",
    @SerialName("projected_balance") val projectedBalance: Money = Money.ZERO,
    @SerialName("inflow") val inflow: Money = Money.ZERO,
    @SerialName("outflow") val outflow: Money = Money.ZERO,
    @SerialName("net_flow")   val netFlow: Money = Money.ZERO,
    @SerialName("risk_flag")  val riskFlag: Boolean = false
) : Parcelable
