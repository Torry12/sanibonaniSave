package com.sanibonani.save.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A warmup slot held by a run that has already read the group's pillars cannot answer a later
 * request, so the request must survive it. Dropping the request instead left a refresh invisible
 * until the next sweep, while running it concurrently would let two hydrations race for one
 * snapshot.
 */
class GroupContextWarmupCoordinatorTest {

    private val groupId = "group-1"

    /** Unconfined keeps the ordering assertions deterministic: each block runs to its first
     * suspension point on the calling thread. */
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    @Test
    fun `a request that arrives before the owner reads is answered by that run`() {
        val coordinator = GroupContextWarmupCoordinator()
        val canRead = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val runs = mutableListOf<String>()

        coordinator.launch(groupId, scope) {
            canRead.await()
            coordinator.markReadsStarted(groupId)
            runs += "first"
            release.await()
        }
        // The owner is still parked before its reads, so its run covers this request.
        coordinator.launch(groupId, scope) { runs += "second" }

        canRead.complete(Unit)
        release.complete(Unit)
        assertEquals(listOf("first"), runs)
    }

    @Test
    fun `a request that arrives after the owner reads runs when the owner finishes`() {
        val coordinator = GroupContextWarmupCoordinator()
        val release = CompletableDeferred<Unit>()
        val runs = mutableListOf<String>()

        coordinator.launch(groupId, scope) {
            coordinator.markReadsStarted(groupId)
            runs += "first"
            release.await()
        }
        coordinator.launch(groupId, scope) { runs += "second" }

        // Queued, not raced: the snapshot must not be written twice for one group.
        assertEquals(listOf("first"), runs)
        release.complete(Unit)
        assertEquals(listOf("first", "second"), runs)
    }

    @Test
    fun `only one round of hydration is ever in flight for a group`() {
        val coordinator = GroupContextWarmupCoordinator()
        val release = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0

        val body: suspend () -> Unit = {
            active++
            peak = maxOf(peak, active)
            coordinator.markReadsStarted(groupId)
            try {
                release.await()
            } finally {
                active--
            }
        }

        coordinator.launch(groupId, scope, block = body)
        coordinator.launch(groupId, scope, block = body)
        coordinator.launch(groupId, scope, block = body)

        release.complete(Unit)
        assertEquals(1, peak)
    }

    @Test
    fun `a superseded run unwinds before the replacement reads`() {
        val coordinator = GroupContextWarmupCoordinator()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        coordinator.launch(groupId, scope) {
            try {
                coordinator.markReadsStarted(groupId)
                order += "old:reading"
                release.await()
            } finally {
                // The cancellation only lands when the read resumes, so this is the last write
                // the superseded snapshot could still make.
                order += "old:unwound"
            }
        }
        coordinator.launch(groupId, scope, replaceActive = true) { order += "replacement" }

        release.complete(Unit)
        assertEquals(listOf("old:reading", "old:unwound", "replacement"), order)
    }

    @Test
    fun `a superseded warmup that never unwinds does not park the entry`() = runBlocking<Unit> {
        // The wait for a cancelled predecessor was open-ended, so a warm-up wedged in a read that
        // never answered held the group's slot and whatever opened the group behind it: the entry
        // that was meant to replace a stale snapshot became a second stall, indistinguishable on
        // screen from a hydration that never started.
        val coordinator = GroupContextWarmupCoordinator(supersedeJoinMillis = 50)
        val wedged = CompletableDeferred<Unit>()
        val tookSlot = CompletableDeferred<Unit>()

        val holding = scope.launch {
            coordinator.runExclusive(groupId, scope) {
                tookSlot.complete(Unit)
                try {
                    withContext(NonCancellable) { wedged.await() }
                } catch (_: CancellationException) {
                    // An orderly run resumes here; this one is stuck and must not be waited on.
                }
            }
        }
        // Without this handoff the wait measured below could be a wait on a slot the wedged run never
        // took, which would pass on the timing alone.
        withTimeout(2_000) { tookSlot.await() }

        val startedAt = System.currentTimeMillis()
        val outcome = withTimeout(2_000) {
            coordinator.runExclusive(groupId, scope) { "entered" }
        }
        val waited = System.currentTimeMillis() - startedAt

        assertEquals("entered", outcome)
        // The replacement has to have waited on the run ahead of it and then given up: a wait of
        // nothing means the wedge was never exercised, and an unbounded one is the stall this bound
        // exists to remove.
        assertTrue("the entry waited past its supersede bound instead of giving up: ${waited}ms", waited >= 50)
        // Giving up on the run is what frees the group's slot. It cannot stop the caller behind it,
        // whose read ignores cancellation, so that run stays alive and may still write - the case the
        // coordinator logs rather than prevents.
        assertTrue("the wedged run is left to finish its own read", holding.isActive)

        wedged.complete(Unit)
    }

    @Test
    fun `a cancelled run discards its queued request`() {
        val coordinator = GroupContextWarmupCoordinator()
        val release = CompletableDeferred<Unit>()
        val runs = mutableListOf<String>()

        coordinator.launch(groupId, scope) {
            coordinator.markReadsStarted(groupId)
            release.await()
        }
        coordinator.launch(groupId, scope) { runs += "queued" }
        coordinator.cancel(groupId)

        release.complete(Unit)
        assertFalse(runs.contains("queued"))
    }

    @Test
    fun `signing out discards both the run and its queued request`() {
        val coordinator = GroupContextWarmupCoordinator()
        val release = CompletableDeferred<Unit>()
        val runs = mutableListOf<String>()

        coordinator.launch(groupId, scope) {
            coordinator.markReadsStarted(groupId)
            release.await()
        }
        coordinator.launch(groupId, scope) { runs += "queued" }
        coordinator.cancelAll()

        release.complete(Unit)
        assertFalse(runs.contains("queued"))
    }

    @Test
    fun `runExclusive holds the slot and still answers a later request`() = runBlocking {
        val coordinator = GroupContextWarmupCoordinator()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        scope.launch {
            coordinator.runExclusive(groupId, scope) {
                coordinator.markReadsStarted(groupId)
                started.complete(Unit)
                order += "exclusive"
                release.await()
            }
        }
        started.await()

        coordinator.launch(groupId, scope) { order += "sweep" }
        assertEquals(listOf("exclusive"), order)

        release.complete(Unit)
        assertEquals(listOf("exclusive", "sweep"), order)
    }

    @Test
    fun `a sweep that arrives before the exclusive reads are covered by that run`() = runBlocking {
        val coordinator = GroupContextWarmupCoordinator()
        val canRead = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        scope.launch {
            coordinator.runExclusive(groupId, scope) {
                started.complete(Unit)
                canRead.await()
                coordinator.markReadsStarted(groupId)
                order += "exclusive"
                release.await()
            }
        }
        started.await()

        coordinator.launch(groupId, scope) { order += "sweep" }
        canRead.complete(Unit)
        release.complete(Unit)

        assertEquals(listOf("exclusive"), order)
    }

    @Test
    fun `a request credited to a run that never read is still made`() {
        val coordinator = GroupContextWarmupCoordinator()
        val release = CompletableDeferred<Unit>()
        val runs = mutableListOf<String>()

        // A warmup that gives up before reading the group cannot answer anything.
        coordinator.launch(groupId, scope) { release.await() }
        coordinator.launch(groupId, scope) { runs += "second" }

        release.complete(Unit)
        assertEquals(listOf("second"), runs)
    }

    @Test
    fun `a released slot lets the next request run immediately`() {
        val coordinator = GroupContextWarmupCoordinator()
        val order = mutableListOf<String>()

        coordinator.launch(groupId, scope) { order += "first" }
        coordinator.launch(groupId, scope) { order += "second" }

        assertEquals(listOf("first", "second"), order)
    }
}
