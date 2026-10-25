package com.sanibonani.save.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.auth.*

import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.system.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Runs daily via WorkManager.
 * Checks the current group's platform fee status and dispatches
 * warnings or suspension notifications when required.
 *
 * Business rules:
 *   Day 0    → DUE  (reminder to admin)
 *   Day 1–6  → WARNING (escalated — notify admin + members)
 *   Day 7+   → SUSPENDED (lock group + notify all members)
 *
 * In production this logic also runs as a Supabase Edge Function (pg_cron)
 * so the app worker is a client-side supplement for offline resilience.
 */
@HiltWorker
class FeeEnforcementWorker @AssistedInject constructor(
    @Assisted appContext : Context,
    @Assisted workerParams: WorkerParameters,
    private val groupRepo        : GroupRepository,
    private val notificationRepo : NotificationRepository,
    private val feeService       : com.sanibonani.save.domain.service.PlatformFeeService,
    private val refreshPlatformConfig: com.sanibonani.save.domain.usecase.admin.RefreshPlatformConfigUseCase,
    private val supabase         : io.github.jan.supabase.SupabaseClient
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val groupId = inputData.getString(KEY_GROUP_ID) ?: return Result.failure()

        return try {
            // The worker can run before any screen has hydrated the fee config, so it must
            // refresh first or it would enforce the in-code defaults.
            refreshPlatformConfig().getOrNull()

            val group = groupRepo.getGroupById(groupId).getOrNull() ?: return Result.success()

            // Logic for automatic status transitions based on "days since due"
            // (Simplified simulation for offline worker)
            val daysOverdue = when (group.feeStatus) {
                AdminFeeState.DUE -> 1
                AdminFeeState.WARNING -> 7
                AdminFeeState.OVERDUE -> 7
                else -> -1
            }

            val newStatus = if (daysOverdue >= 0) {
                feeService.evaluateGroupStanding(group.feeStatus, daysOverdue)
            } else group.feeStatus

            if (newStatus != group.feeStatus) {
                groupRepo.updateFeeStatus(groupId, newStatus).onSuccess {
                    if (newStatus == AdminFeeState.SUSPENDED) {
                        // Ensure platform suspension flag is also set
                        supabase.postgrest["groups"].update(buildJsonObject {
                            put("is_platform_suspended", true)
                        }) { filter { eq("id", groupId) } }
                    }
                }
            }

            // Dispatch notifications based on the (possibly updated) status
            val amountDue = feeService.calculateMonthlyGroupTotal(group)

            when (newStatus) {
                AdminFeeState.DUE -> {
                    notificationRepo.sendFeeEnforcementNotification(
                        groupId     = groupId,
                        event       = NotifEvent.PLATFORM_FEE_DUE,
                        memberCount = group.currentMembers,
                        amountDue   = amountDue
                    )
                }
                AdminFeeState.WARNING -> {
                    notificationRepo.sendFeeEnforcementNotification(
                        groupId     = groupId,
                        event       = NotifEvent.PLATFORM_FEE_WARNING,
                        memberCount = group.currentMembers,
                        amountDue   = amountDue
                    )
                }
                AdminFeeState.SUSPENDED -> {
                    notificationRepo.sendFeeEnforcementNotification(
                        groupId     = groupId,
                        event       = NotifEvent.GROUP_SUSPENDED,
                        memberCount = group.currentMembers,
                        amountDue   = amountDue
                    )
                }
                else -> { /* paid or pending activation — skip */ }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            com.sanibonani.save.domain.logging.AppLogger.e(
                "FeeEnforcementWorker",
                "Failed to enforce fees for group $groupId",
                e
            )
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_GROUP_ID = "group_id"
        const val WORK_NAME    = "fee_enforcement_check"
        private const val MAX_ATTEMPTS = 3

        fun schedule(context: Context, groupId: String): Operation {
            val data = Data.Builder().putString(KEY_GROUP_ID, groupId).build()
            val request = PeriodicWorkRequestBuilder<FeeEnforcementWorker>(
                repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setInputData(data)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
            return WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
