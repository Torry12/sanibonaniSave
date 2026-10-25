package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import io.github.jan.supabase.SupabaseClient

/**
 * Base class for Supabase-driven data sources.
 * Ensures the SupabaseClient is always retrieved from the connection protocol,
 * enabling dynamic switching between local and online environments.
 */
abstract class BaseSupabaseDataSource(
    private val protocol: DataConnectionProtocol
) {
    protected val client: SupabaseClient
        get() = protocol.getRemoteClient(SupabaseClient::class.java)
            ?: throw IllegalStateException("SupabaseClient not available through protocol")
}
