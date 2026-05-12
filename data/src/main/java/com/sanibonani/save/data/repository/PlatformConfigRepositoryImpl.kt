package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.model.PlatformFees
import com.sanibonani.save.domain.repository.PlatformConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformConfigRepositoryImpl @Inject constructor() : PlatformConfigRepository {

    private val _config = MutableStateFlow(
        PlatformConfig(
            monthlyMemberFee = PlatformFees.MONTHLY_PER_MEMBER,
            registrationFee = PlatformFees.REGISTRATION
        )
    )

    override val config: StateFlow<PlatformConfig> = _config.asStateFlow()

    override fun current(): PlatformConfig = _config.value

    override fun update(monthlyMemberFee: Double, registrationFee: Double) {
        val sanitizedMonthly = monthlyMemberFee.coerceAtLeast(0.0)
        val sanitizedRegistration = registrationFee.coerceAtLeast(0.0)

        _config.update {
            it.copy(
                monthlyMemberFee = sanitizedMonthly,
                registrationFee = sanitizedRegistration
            )
        }

        // Legacy bridge: keep old call sites synchronized while migration completes.
        PlatformFees.update(sanitizedMonthly, sanitizedRegistration)
    }
}

