package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RealtimeDataSource
import com.sanibonani.save.domain.model.AdminFeeState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseRealtimeDataSourceImpl @Inject constructor(
    private val client: SupabaseClient
) : RealtimeDataSource {

    override fun observeGroupFeeStatus(groupId: String): Flow<AdminFeeState> = callbackFlow {
        val channel = client.realtime.channel("public:groups:id=eq.$groupId")
        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public")

        val job = launch {
            changes.collect { update ->
                val newStatus = update.record["fee_status"]?.jsonPrimitive?.content
                if (newStatus != null) {
                    val decoded = AdminFeeState.entries.find {
                        it.name.equals(newStatus, true) ||
                        it.toString().lowercase() == newStatus.lowercase()
                    } ?: AdminFeeState.DUE
                    trySend(decoded)
                }
            }
        }

        channel.subscribe()
        awaitClose {
            job.cancel()
            launch { channel.unsubscribe() }
        }
    }
}
