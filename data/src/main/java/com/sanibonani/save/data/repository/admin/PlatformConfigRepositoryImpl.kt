package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.domain.config.PlatformFees
import com.sanibonani.save.domain.model.PlatformConfig
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

    override fun hydrate(config: PlatformConfig) {
        _config.value = config

        // Legacy bridge: keep old call sites synchronized while migration completes.
        PlatformFees.update(config.monthlyMemberFee, config.registrationFee)
    }
}
