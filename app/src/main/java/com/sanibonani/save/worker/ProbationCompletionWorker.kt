package com.sanibonani.save.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.utils.logAndGetMessage
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Runs daily to check for members whose probation period has ended.
 * Automatically promotes them from PROBATION to ACTIVE status.
 * Sends welcome notifications when members become active.
 *
 * Business rules:
 * - Check all members with status = PROBATION
 * - If probation_end_at < now, promote to ACTIVE
 * - Send notification to member
 * - Update member status in database
 */
@HiltWorker
class ProbationCompletionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val memberRepo: MemberRepository,
    private val notificationRepo: NotificationRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            AppLogger.i("ProbationWorker", "Starting probation completion check")

            val probationMembersResult = memberRepo.getAllProbationMembers()
            if (probationMembersResult.isFailure) {
                val ex = probationMembersResult.exceptionOrNull()
                val userMsg = ex?.logAndGetMessage("ProbationWorker") ?: "Failed to fetch probation members"
                AppLogger.e("ProbationWorker", "Failed to fetch probation members: $userMsg")
                return Result.retry()
            }

            val probationMembers = probationMembersResult.getOrDefault(emptyList())
            AppLogger.i("ProbationWorker", "Found ${probationMembers.size} members in probation")

            var promotedCount = 0
            probationMembers.forEach { member ->
                if (hasProbationEnded(member.probationEndAt)) {
                    val memberId = member.id ?: return@forEach
                    val updateResult = memberRepo.updateMemberStatus(memberId, MemberStatus.ACTIVE)
                    if (updateResult.isSuccess) {
                        promotedCount++
                        // Send welcome notification
                        notificationRepo.sendNotification(
                            AppNotification(
                                memberId = memberId,
                                groupId = member.groupId,
                                channel = NotifChannel.BOTH,
                                message = "Congratulations ${member.fullName}! Your probation period has ended and you are now an active member of the group.",
                                triggerEvent = NotifEvent.PROBATION_ENDED
                            )
                        )
                        AppLogger.i("ProbationWorker", "Promoted member ${member.fullName} from probation to active")
                    }
                }
            }

            AppLogger.i("ProbationWorker", "Probation completion check completed. Promoted $promotedCount members.")
            Result.success()

        } catch (e: Exception) {
            val userMsg = e.logAndGetMessage("ProbationWorker")
            AppLogger.e("ProbationWorker", "Error during probation completion check: $userMsg", e)
            Result.retry()
        }
    }

    /**
     * Check if a specific member's probation has ended
     */
    suspend fun checkMemberProbation(memberId: String) {
        try {
            val memberResult = memberRepo.getMemberById(memberId)
            if (memberResult.isFailure) {
                return
            }

            val member = memberResult.getOrNull() ?: return

            // Check if member is in probation and probation period has ended
            if (member.status == MemberStatus.PROBATION && hasProbationEnded(member.probationEndAt)) {
                // Promote to active
                val updateResult = memberRepo.updateMemberStatus(memberId, MemberStatus.ACTIVE)
                if (updateResult.isSuccess) {
                    // Send welcome notification
                    notificationRepo.sendNotification(
                        AppNotification(
                            memberId = memberId,
                            groupId = member.groupId,
                            channel = NotifChannel.BOTH,
                            message = "Congratulations ${member.fullName}! Your probation period has ended and you are now an active member of the group.",
                            triggerEvent = NotifEvent.PROBATION_ENDED
                        )
                    )
                    AppLogger.i("ProbationWorker", "Promoted member ${member.fullName} from probation to active")
                }
            }
        } catch (e: Exception) {
            val userMsg = e.logAndGetMessage("ProbationWorker")
            AppLogger.e("ProbationWorker", "Error checking member probation for $memberId: $userMsg", e)
        }
    }

    private fun hasProbationEnded(probationEndAt: String?): Boolean {
        if (probationEndAt.isNullOrBlank()) return false

        return try {
            val endDate = LocalDateTime.parse(probationEndAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val now = LocalDateTime.now()
            now.isAfter(endDate)
        } catch (e: Exception) {
            AppLogger.w("ProbationWorker", "Invalid probation end date format: $probationEndAt")
            false
        }
    }

    companion object {
        const val WORK_NAME = "probation_completion_check"

        fun schedule(context: Context): Operation {
            val request = PeriodicWorkRequestBuilder<ProbationCompletionWorker>(
                repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            val workManager = WorkManager.getInstance(context)
            return workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
