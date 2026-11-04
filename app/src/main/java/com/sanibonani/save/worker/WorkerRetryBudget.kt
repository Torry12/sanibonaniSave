package com.sanibonani.save.worker

import androidx.work.ListenableWorker

/** The attempts a periodic cycle gets at work it could not finish before the period ends. */
internal const val MAX_CYCLE_ATTEMPTS = 3

/**
 * True once this run has spent the budget. Retrying is right - a device that was offline when the
 * schedule fired should try again - but an uncapped retry lets one group that will never answer
 * re-run on the backoff schedule forever, so the cycle stops and the next period starts clean.
 */
internal fun outOfRetryBudget(runAttemptCount: Int): Boolean = runAttemptCount >= MAX_CYCLE_ATTEMPTS

/** The verdict a cycle reports for work it could not complete. */
internal fun retryOrGiveUp(runAttemptCount: Int): ListenableWorker.Result =
    if (outOfRetryBudget(runAttemptCount)) ListenableWorker.Result.failure()
    else ListenableWorker.Result.retry()
