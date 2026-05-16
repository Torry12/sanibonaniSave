package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.model.PlatformFees
import com.sanibonani.save.domain.repository.PlatformConfigRepository
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
        monthlyMemberFee: Double,
        registrationFee: Double,
        payoutFee: Double?,
        whatsappFee: Double?,
        lateFeePercent: Double?,
        autoSuspensionDays: Int?,
    ) {
        val current = _config.value
        val updated = current.copy(
            monthlyMemberFee = monthlyMemberFee.coerceAtLeast(0.0),
            registrationFee = registrationFee.coerceAtLeast(0.0),
            payoutFee = payoutFee?.coerceAtLeast(0.0) ?: current.payoutFee,
            whatsappFee = whatsappFee?.coerceAtLeast(0.0) ?: current.whatsappFee,
            lateFeePercent = lateFeePercent?.coerceAtLeast(0.0) ?: current.lateFeePercent,
            autoSuspensionDays = autoSuspensionDays?.coerceAtLeast(1) ?: current.autoSuspensionDays
        )

        _config.value = updated

        // Legacy bridge: keep old call sites synchronized while migration completes.
        PlatformFees.update(updated.monthlyMemberFee, updated.registrationFee)
    }
}
