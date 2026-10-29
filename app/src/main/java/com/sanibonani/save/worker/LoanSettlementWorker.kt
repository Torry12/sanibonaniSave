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
import com.sanibonani.save.domain.usecase.credit.ProcessLoanForcedSettlementUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Daily background worker that triggers the forced settlement of loans
 * exceeding the 3-month maximum term.
 */
@HiltWorker
class LoanSettlementWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val forcedSettlement: ProcessLoanForcedSettlementUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return forcedSettlement().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val WORK_NAME = "loan_forced_settlement"

        /**
         * App-wide, not per-group: the use case walks every group through
         * [com.sanibonani.save.domain.repository.admin.PlatformRepository.getAllGroups] before it
         * looks at a single loan, so [BackgroundWorkScheduler] enqueues this for a platform
         * session only.
         */
        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<LoanSettlementWorker>(
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
