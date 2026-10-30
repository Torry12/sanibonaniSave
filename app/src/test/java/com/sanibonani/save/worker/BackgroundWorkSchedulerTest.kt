package com.sanibonani.save.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Operation
import androidx.work.WorkManager
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.usecase.group.GetManagedGroupsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Six workers live in `app/.../worker` and, until [BackgroundWorkScheduler] existed, none of
 * them was ever enqueued: the forced loan settlement, governance sweep, ROSCA rotation, fee
 * enforcement, ledger consistency check and probation promotion only ran when a screen
 * invoked the use case by hand.
 *
 * The unique enqueue name is what identifies the worker - each companion owns one `WORK_NAME`
 * and builds its request there - so asserting on names is asserting which class was scheduled.
 *
 * Two of the six sweep every group on the platform through
 * [com.sanibonani.save.domain.repository.admin.PlatformRepository.getAllGroups], which an
 * ordinary group admin cannot read, so those two are scheduled for a platform session only.
 */
class BackgroundWorkSchedulerTest {

    private val workManager = mockk<WorkManager>()
    private val supabaseRepo = mockk<SupabaseRepository>()
    private val getManagedGroups = mockk<GetManagedGroupsUseCase>()
    private val operation = mockk<Operation>(relaxed = true)

    /** Every unique name the scheduler asked WorkManager to enqueue, in call order. */
    private val enqueued = mutableListOf<String>()

    private lateinit var scheduler: BackgroundWorkScheduler

    private val userId = "11111111-1111-1111-1111-111111111111"

    @Before
    fun setUp() {
        every { workManager.enqueueUniquePeriodicWork(capture(enqueued), any(), any()) } returns operation
        scheduler = BackgroundWorkScheduler(workManager, supabaseRepo, getManagedGroups)
    }

    private fun group(id: String?, type: GroupType) = Group(
        id = id,
        name = "Group ${id ?: "unset"}",
        type = type,
        balance = Money("1000.00")
    )

    private fun signedInAs(role: UserRole, groups: List<Group>) {
        every { supabaseRepo.currentUserId } returns userId
        every { supabaseRepo.isLoggedIn } returns true
        coEvery { supabaseRepo.getUserRole() } returns role
        coEvery {
            getManagedGroups(userId, adminOnly = true, isPlatformAdmin = role == UserRole.PLATFORM_ADMIN)
        } returns Result.success(groups)
    }

    @Test
    fun `a signed-out session schedules nothing`() = runTest {
        every { supabaseRepo.currentUserId } returns null
        every { supabaseRepo.isLoggedIn } returns false

        scheduler.refresh()

        assertTrue(enqueued.toString(), enqueued.isEmpty())
    }

    @Test
    fun `a platform session gets all three app-wide automations`() = runTest {
        signedInAs(UserRole.PLATFORM_ADMIN, listOf(group("g1", GroupType.STOKVEL)))

        scheduler.refresh()

        listOf(
            ProbationCompletionWorker.WORK_NAME,
            AutomatedGovernanceWorker.WORK_NAME,
            LoanSettlementWorker.WORK_NAME
        ).forEach { name ->
            assertTrue("$name missing from $enqueued", name in enqueued)
        }
    }

    @Test
    fun `the platform-wide sweeps are not scheduled for an ordinary admin`() = runTest {
        signedInAs(UserRole.GROUP_ADMIN, listOf(group("g1", GroupType.STOKVEL)))

        scheduler.refresh()

        assertTrue(enqueued.toString(), ProbationCompletionWorker.WORK_NAME in enqueued)
        assertTrue(
            "the governance sweep reads every group: $enqueued",
            AutomatedGovernanceWorker.WORK_NAME !in enqueued
        )
        assertTrue(
            "forced settlement reads every group: $enqueued",
            LoanSettlementWorker.WORK_NAME !in enqueued
        )
    }

    @Test
    fun `each managed group gets its own ledger and fee schedule`() = runTest {
        signedInAs(
            UserRole.PLATFORM_ADMIN,
            listOf(group("g1", GroupType.STOKVEL), group("g2", GroupType.BURIAL_SOCIETY))
        )

        scheduler.refresh()

        listOf("g1", "g2").forEach { groupId ->
            assertTrue(
                "$groupId: $enqueued",
                "${LedgerConsistencyWorker.WORK_NAME}_$groupId" in enqueued &&
                    "${FeeEnforcementWorker.WORK_NAME}_$groupId" in enqueued
            )
        }
    }

    @Test
    fun `only a ROSCA group gets the rotation schedule`() = runTest {
        signedInAs(
            UserRole.GROUP_ADMIN,
            listOf(group("g1", GroupType.ROSCA), group("g2", GroupType.INVESTMENT_CLUB))
        )

        scheduler.refresh()

        assertTrue(enqueued.toString(), "${AutomatedRoscaWorker.WORK_NAME}_g1" in enqueued)
        assertTrue(
            "g2 is not a ROSCA: $enqueued",
            "${AutomatedRoscaWorker.WORK_NAME}_g2" !in enqueued
        )
    }

    @Test
    fun `a group without an id is never scheduled against`() = runTest {
        signedInAs(UserRole.GROUP_ADMIN, listOf(group(null, GroupType.ROSCA), group("g3", GroupType.STOKVEL)))

        scheduler.refresh()

        assertTrue(enqueued.toString(), enqueued.none { it.endsWith("_null") })
        assertTrue(enqueued.toString(), "${LedgerConsistencyWorker.WORK_NAME}_g3" in enqueued)
    }

    @Test
    fun `a failed group read still leaves the app-wide schedules in place`() = runTest {
        every { supabaseRepo.currentUserId } returns userId
        every { supabaseRepo.isLoggedIn } returns true
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN
        coEvery { getManagedGroups(any(), any(), any()) } returns
            Result.failure(IllegalStateException("offline"))

        scheduler.refresh()

        assertTrue(enqueued.toString(), AutomatedGovernanceWorker.WORK_NAME in enqueued)
        assertTrue(
            "no group was read, so no per-group work: $enqueued",
            enqueued.none { it.startsWith(LedgerConsistencyWorker.WORK_NAME) }
        )
    }

    @Test
    fun `an existing schedule is kept rather than reset`() = runTest {
        signedInAs(UserRole.GROUP_ADMIN, listOf(group("g1", GroupType.STOKVEL)))

        scheduler.refresh()

        // KEEP, not UPDATE: a refresh runs on every app start, and UPDATE restarts the
        // period each time, so a daily job opened daily would drift later and later.
        verify(atLeast = 1) {
            workManager.enqueueUniquePeriodicWork(
                "${LedgerConsistencyWorker.WORK_NAME}_g1",
                ExistingPeriodicWorkPolicy.KEEP,
                any()
            )
        }
    }
}
