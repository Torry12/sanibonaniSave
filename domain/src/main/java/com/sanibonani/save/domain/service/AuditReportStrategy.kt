package com.sanibonani.save.domain.service

/**
 * Result wrapper that includes both the decoded domain model and the raw JSON.
 *
 * Declared in `:domain` as a shared return type for the audit data sources in
 * `:data`. The three `*AuditStrategy` classes that used to live here were
 * concrete Supabase/PostgREST implementations (`SupabaseClient` +
 * `postgrest.rpc(...)`) — data-access code in the domain layer — and had no
 * callers, so they were removed. `:data` performs the RPCs via
 * `SupabaseAuditDataSourceImpl`; `:domain` owns only the contract types.
 */
data class AuditResult<T>(
    val data: T,
    val rawJson: String
)
