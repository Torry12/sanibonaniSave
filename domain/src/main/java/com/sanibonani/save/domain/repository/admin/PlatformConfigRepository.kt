package com.sanibonani.save.domain.repository.admin

import com.sanibonani.save.domain.model.PlatformConfig
import kotlinx.coroutines.flow.StateFlow

interface PlatformConfigRepository {
    val config: StateFlow<PlatformConfig>
    fun current(): PlatformConfig
    fun update(
        monthlyMemberFee: com.sanibonani.save.domain.model.Money,
        registrationFee: com.sanibonani.save.domain.model.Money,
        payoutFee: com.sanibonani.save.domain.model.Money? = null,
        shortfallRecoveryFee: com.sanibonani.save.domain.model.Money? = null,
        lateFeePercent: com.sanibonani.save.domain.model.Money? = null,
        autoSuspensionDays: Int? = null
    )
}
