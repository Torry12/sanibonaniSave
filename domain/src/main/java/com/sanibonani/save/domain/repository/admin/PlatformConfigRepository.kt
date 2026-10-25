package com.sanibonani.save.domain.repository.admin

import com.sanibonani.save.domain.model.PlatformConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory view of the platform's published fee configuration.
 *
 * [hydrate] seeds the snapshot from a config the caller has just read or written.
 * Until that happens, [current] serves [PlatformConfig] defaults.
 */
interface PlatformConfigRepository {
    val config: StateFlow<PlatformConfig>

    fun current(): PlatformConfig

    /** Publishes a config that was just read from or written to the server. */
    fun hydrate(config: PlatformConfig)
}
