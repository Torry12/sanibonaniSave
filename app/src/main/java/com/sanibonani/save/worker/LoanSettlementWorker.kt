package com.sanibonani.save.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanibonani.save.domain.usecase.credit.ProcessLoanForcedSettlementUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Daily background worker that triggers the forced settlement of loans
 * exceeding the 3-month maximum term.
 */
@HiltWorker
class LoanSettlementWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val forcedSettlementUseCase: ProcessLoanForcedSettlementUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return forcedSettlementUseCase().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
