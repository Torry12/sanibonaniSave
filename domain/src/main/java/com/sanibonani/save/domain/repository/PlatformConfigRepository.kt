package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.PlatformConfig
import kotlinx.coroutines.flow.StateFlow

interface PlatformConfigRepository {
    val config: StateFlow<PlatformConfig>
    fun current(): PlatformConfig
    fun update(
        monthlyMemberFee: Double,
        registrationFee: Double,
        payoutFee: Double? = null,
        whatsappFee: Double? = null,
        lateFeePercent: Double? = null,
        autoSuspensionDays: Int? = null
    )
}
