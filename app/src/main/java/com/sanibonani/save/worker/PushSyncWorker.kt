package com.sanibonani.save.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.repository.toModel
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.MemberRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class PushSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: SanibonaniDatabase,
    private val supabase: SupabaseClient,
    private val memberRepo: MemberRepository
) : CoroutineWorker(context, params) {

    private val tag = "PushSyncWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(tag, "Starting push sync for local changes...")
            
            syncPendingMembers()
            // Add other sync functions here as needed
            
            AppLogger.d(tag, "Push sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e(tag, "Push sync failed: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun syncPendingMembers() {
        val pendingMembers = db.memberDao().getSyncPendingMembers()
        if (pendingMembers.isEmpty()) return

        AppLogger.d(tag, "Syncing ${pendingMembers.size} pending members...")
        pendingMembers.forEach { entity ->
            runCatching {
                val member = entity.toModel()
                // Reuse registerMember or implement a simpler update
                supabase.postgrest["members"].upsert(member) {
                    onConflict = "id"
                }
                db.memberDao().upsertMember(entity.copy(syncPending = false))
            }.onFailure { e ->
                AppLogger.e(tag, "Failed to sync member ${entity.id}: ${e.message}")
            }
        }
    }
}
