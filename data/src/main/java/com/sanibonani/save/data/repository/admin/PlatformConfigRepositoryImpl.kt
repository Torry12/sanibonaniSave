package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.config.PlatformFees
import com.sanibonani.save.domain.repository.admin.PlatformConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformConfigRepositoryImpl @Inject constructor() : PlatformConfigRepository {

    private val _config = MutableStateFlow(PlatformConfig())

    override val config: StateFlow<PlatformConfig> = _config.asStateFlow()

    override fun current(): PlatformConfig = _config.value

    override fun update(
        monthlyMemberFee: com.sanibonani.save.domain.model.Money,
        registrationFee: com.sanibonani.save.domain.model.Money,
        payoutFee: com.sanibonani.save.domain.model.Money?,
        shortfallRecoveryFee: com.sanibonani.save.domain.model.Money?,
        lateFeePercent: com.sanibonani.save.domain.model.Money?,
        autoSuspensionDays: Int?,
    ) {
        val current = _config.value
        val updated = current.copy(
            monthlyMemberFee = monthlyMemberFee,
            registrationFee = registrationFee,
            payoutFee = payoutFee ?: current.payoutFee,
            shortfallRecoveryFee = shortfallRecoveryFee ?: current.shortfallRecoveryFee,
            lateFeePercent = lateFeePercent ?: current.lateFeePercent,
            autoSuspensionDays = autoSuspensionDays ?: current.autoSuspensionDays
        )

        _config.value = updated

        // Legacy bridge: keep old call sites synchronized while migration completes.
        PlatformFees.update(updated.monthlyMemberFee, updated.registrationFee)
    }
}
