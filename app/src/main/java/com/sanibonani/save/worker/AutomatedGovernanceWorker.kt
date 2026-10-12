package com.sanibonani.save.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanibonani.save.domain.usecase.group.AutomateGovernanceUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AutomatedGovernanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val automateGovernanceUseCase: AutomateGovernanceUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return automateGovernanceUseCase().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}



