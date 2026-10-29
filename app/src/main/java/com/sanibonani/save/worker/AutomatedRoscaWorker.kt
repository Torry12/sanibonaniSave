package com.sanibonani.save.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.usecase.disbursement.ExecuteRoscaPayoutUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Automates payouts for ROSCA groups.
 * Runs periodically to check if a ROSCA payout is due for the current month
 * and executes it automatically.
 */
@HiltWorker
class AutomatedRoscaWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val groupRepo: GroupRepository,
    private val executeRoscaPayout: ExecuteRoscaPayoutUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val groupId = inputData.getString(KEY_GROUP_ID) ?: return Result.failure()

        return try {
            val group = groupRepo.getGroupById(groupId).getOrNull() ?: return Result.success()

            if (group.type != GroupType.ROSCA) {
                return Result.success()
            }

            AppLogger.i("AutomatedRoscaWorker", "Starting automated payout check for ROSCA group $groupId")

            val payoutResult = executeRoscaPayout(groupId)

            payoutResult.onSuccess { msg ->
                AppLogger.i("AutomatedRoscaWorker", "Payout processed: $msg")
            }.onFailure { e ->
                when {
                    // Business-rule conditions that legitimately occur on a periodic
                    // schedule (worker ran before the payout was due). Reporting these
                    // as failure would trigger WorkManager retries that cannot succeed.
                    e.isExpectedRoscaCondition() -> AppLogger.i(
                        "AutomatedRoscaWorker",
                        "No payout due for ROSCA group $groupId: ${e.message}"
                    )
                    // Genuine failure: surface it instead of masquerading as success,
                    // otherwise a broken payout is silently skipped every cycle.
                    else -> {
                        AppLogger.e("AutomatedRoscaWorker", "Payout failed for ROSCA group $groupId", e)
                        return Result.failure()
                    }
                }
            }

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("AutomatedRoscaWorker", "Worker exception for ROSCA group $groupId", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    /**
     * Conditions raised by [ExecuteRoscaPayoutUseCase] when the group simply has no
     * payout due yet. These are expected outcomes of a daily recurring job, not faults.
     */
    private fun Throwable.isExpectedRoscaCondition(): Boolean =
        message?.let { msg -> EXPECTED_ROSCA_CONDITIONS.any { msg.contains(it, ignoreCase = true) } } == true

    companion object {
        const val KEY_GROUP_ID = "group_id"
        const val WORK_NAME = "rosca_payout_automation"
        private const val MAX_ATTEMPTS = 3
        private val EXPECTED_ROSCA_CONDITIONS = listOf(
            "No members",
            "No current members",
            "Not a ROSCA",
            "Group not found"
        )

        /**
         * One schedule per group: the unique name carries the id, so two ROSCA groups on one
         * device rotate independently. Scheduled by [BackgroundWorkScheduler] for a group whose
         * type is [com.sanibonani.save.domain.model.GroupType.ROSCA].
         */
        fun schedule(workManager: WorkManager, groupId: String) {
            val data = Data.Builder().putString(KEY_GROUP_ID, groupId).build()
            val request = PeriodicWorkRequestBuilder<AutomatedRoscaWorker>(
                repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                "${WORK_NAME}_$groupId",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
