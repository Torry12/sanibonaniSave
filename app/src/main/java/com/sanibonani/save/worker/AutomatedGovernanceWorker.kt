package com.sanibonani.save.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanibonani.save.domain.usecase.group.AutomateGovernanceUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Daily platform-wide governance sweep: inactivity suspension, overdue handling, and the
 * other rules that no member-facing screen triggers.
 */
@HiltWorker
class AutomatedGovernanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val automateGovernance: AutomateGovernanceUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return automateGovernance().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val WORK_NAME = "governance_automation"

        /**
         * App-wide, not per-group: the use case reads every group through
         * [com.sanibonani.save.domain.repository.admin.PlatformRepository.getAllGroups], which
         * RLS confines to a platform admin, so [BackgroundWorkScheduler] only enqueues this for
         * that role.
         */
        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<AutomatedGovernanceWorker>(
                repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}



