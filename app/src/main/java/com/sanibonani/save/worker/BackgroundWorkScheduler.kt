package com.sanibonani.save.worker

import androidx.work.WorkManager
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.usecase.group.GetManagedGroupsUseCase
import com.sanibonani.save.domain.utils.safeRunCatching
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that decides which background automation this session is entitled to run.
 *
 * Six workers sit in this package and, before this class existed, none of them was ever
 * enqueued - the governance sweep, forced loan settlement, ROSCA rotation, fee enforcement,
 * ledger consistency check and probation promotion only ran when a screen invoked the use case
 * by hand. An app that was opened and left alone performed no automation at all.
 *
 * Two of the six - [AutomatedGovernanceWorker] and [LoanSettlementWorker] - walk every group
 * through [com.sanibonani.save.domain.repository.admin.PlatformRepository.getAllGroups], which
 * RLS confines to a platform admin, so they are scheduled only for that role. An ordinary group
 * admin gets the per-group work for the groups they administer plus [ProbationCompletionWorker],
 * which reads the probation roster the caller's own members sit on.
 *
 * Everything is enqueued with [androidx.work.ExistingPeriodicWorkPolicy.KEEP]: a refresh runs on
 * every session, and UPDATE would restart each period from now, so a daily job on an app opened
 * daily would drift later and later and eventually never fire.
 *
 * Known limitation, accepted rather than engineered around: signing out does not cancel what a
 * different identity scheduled. WorkManager cannot read a queued job's input back from
 * [androidx.work.WorkInfo], so cancelling the previous user's per-group work would need its own
 * persisted registry. The stale jobs cost one refused network call per group per day - the
 * server-side `SEC_002` guards and RLS decide entitlement, not the schedule - and the next
 * sign-in adds its own names.
 *
 * The mirror of that: a group created mid-session is not scheduled until the next [refresh],
 * because no caller re-enters this class when the roster changes. Nothing is lost in the gap - a
 * group with no ledger, no members and no fee period has nothing for the four per-group workers
 * to act on, and the platform fee itself is enforced server-side by `automate_monthly_billing_v1`.
 */
@Singleton
class BackgroundWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val supabaseRepository: SupabaseRepository,
    private val getManagedGroups: GetManagedGroupsUseCase
) {

    suspend fun refresh() {
        val userId = supabaseRepository.currentUserId ?: return
        if (!supabaseRepository.isLoggedIn) return

        // Fail closed on privilege, the same way the session hydrator does: an unreadable role
        // gets the member's schedule, never an admin's platform-wide sweep.
        val isPlatformAdmin = safeRunCatching("BackgroundWorkScheduler") { supabaseRepository.getUserRole() }
            .getOrDefault(UserRole.MEMBER) == UserRole.PLATFORM_ADMIN

        ProbationCompletionWorker.schedule(workManager)
        if (isPlatformAdmin) {
            AutomatedGovernanceWorker.schedule(workManager)
            LoanSettlementWorker.schedule(workManager)
        }

        // The app-wide automations above are already queued, so a group read that fails leaves a
        // partial - and still valid - schedule rather than nothing.
        val groups = getManagedGroups(
            userId = userId,
            adminOnly = true,
            isPlatformAdmin = isPlatformAdmin
        ).getOrNull() ?: return

        groups.forEach { group ->
            // A row without an id cannot be addressed by a worker: the unique name and the input
            // key are both built from it.
            val groupId = group.id ?: return@forEach
            LedgerConsistencyWorker.schedule(workManager, groupId)
            FeeEnforcementWorker.schedule(workManager, groupId)
            if (group.type == GroupType.ROSCA) {
                AutomatedRoscaWorker.schedule(workManager, groupId)
            }
        }
    }
}
