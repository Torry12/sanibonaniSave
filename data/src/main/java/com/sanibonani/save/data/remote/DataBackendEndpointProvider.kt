package com.sanibonani.save.data.remote

import com.sanibonani.save.data.BuildConfig
import com.sanibonani.save.domain.service.BackendEndpointProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the hosted Supabase URL from the `:data` build configuration.
 */
@Singleton
class DataBackendEndpointProvider @Inject constructor() : BackendEndpointProvider {
    override val onlineUrl: String = BuildConfig.SUPABASE_URL_ONLINE.trim().removeSurrounding("\"")
}
