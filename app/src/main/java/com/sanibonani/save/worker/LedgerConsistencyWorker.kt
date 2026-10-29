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
import com.sanibonani.save.domain.service.ReconciliationService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Autonomous financial guardian.
 * Periodically audits the group ledger against the group balance.
 * If a critical discrepancy is found, it logs an alert and triggers a repair.
 */
@HiltWorker
class LedgerConsistencyWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reconciliationService: ReconciliationService,
    private val platformRepo: PlatformRepository,
    private val auditRepo: AuditRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val groupId = inputData.getString(KEY_GROUP_ID) ?: return Result.failure()

        return try {
            val auditResult = reconciliationService.auditGroupLedger(groupId)

            auditResult.fold(
                onSuccess = { record ->
                    if (record.status == ReconciliationStatus.DISCREPANCY) {
                        com.sanibonani.save.domain.logging.AppLogger.e(
                            "LedgerGuardian",
                            "🚨 Critical Discrepancy Found in Group $groupId: ${record.discrepancy.format()}"
                        )

                        // Trigger an automatic repair if the discrepancy is significant
                        if (record.discrepancy.abs() > Money("1.0")) {
                            val repairOutcome = platformRepo.repairGroupData(groupId)
                            if (repairOutcome.isSuccess) {
                                auditRepo.logAuditEvent(AuditLog(
                                    actorId = "SYSTEM_GUARDIAN",
                                    targetGroupId = groupId,
                                    action = "AUTO_REPAIR_LEDGER",
                                    details = Json.encodeToJsonElement(mapOf(
                                        "discrepancy" to record.discrepancy.format(),
                                        "reason" to "Ledger sum mismatch"
                                    ))
                                ))
                            } else {
                                // A repair that never happened must not appear in the audit trail.
                                com.sanibonani.save.domain.logging.AppLogger.e(
                                    "LedgerGuardian",
                                    "Auto-repair failed for group $groupId; no audit entry written.",
                                    repairOutcome.exceptionOrNull()
                                )
                            }
                        }
                    } else {
                        com.sanibonani.save.domain.logging.AppLogger.i("LedgerGuardian", "✅ Group $groupId is balanced.")
                    }
                    Result.success()
                },
                // An audit that could not run is not a clean balance: report it so the work is
                // retried instead of silently marking the guardian pass as successful.
                onFailure = { e ->
                    com.sanibonani.save.domain.logging.AppLogger.e(
                        "LedgerGuardian",
                        "Ledger audit could not run for group $groupId",
                        e
                    )
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            com.sanibonani.save.domain.logging.AppLogger.e(
                "LedgerGuardian",
                "Ledger audit failed for group $groupId",
                e
            )
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_GROUP_ID = "group_id"
        const val WORK_NAME = "ledger_consistency_audit"
        private const val MAX_ATTEMPTS = 3

        /**
         * One schedule per group, for the same reason as [FeeEnforcementWorker]: a shared unique
         * name means the last group enqueued is the only group audited.
         */
        fun schedule(workManager: WorkManager, groupId: String) {
            val data = Data.Builder().putString(KEY_GROUP_ID, groupId).build()
            val request = PeriodicWorkRequestBuilder<LedgerConsistencyWorker>(
                repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setInputData(data)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()

            workManager.enqueueUniquePeriodicWork(
                "${WORK_NAME}_$groupId",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
