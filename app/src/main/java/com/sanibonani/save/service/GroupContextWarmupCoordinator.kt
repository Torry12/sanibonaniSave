package com.sanibonani.save.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * One hydration slot per group.
 *
 * A request arriving before the owning run has read the group's pillars is answered by that run,
 * so starting a second one would only make two hydrations race for the same snapshot. A request
 * arriving after those reads cannot be answered by it any more, so it is kept and run when the
 * owner finishes: discarding it instead left a post-write refresh invisible until the next sweep.
 */
internal class GroupContextWarmupCoordinator {

    private class Slot(val groupId: String) {
        // markReadsStarted writes from the run's own thread, outside the map lock, so the
        // coverage decision has to see it.
        @Volatile var owner: Job? = null
        @Volatile var scope: CoroutineScope? = null
        @Volatile var readsStarted: Boolean = false
        @Volatile var pending: (suspend () -> Unit)? = null
        @Volatile var pendingCovered: Boolean = false
    }

    private val slots = ConcurrentHashMap<String, Slot>()

    fun launch(
        groupId: String,
        scope: CoroutineScope,
        replaceActive: Boolean = false,
        block: suspend () -> Unit
    ) {
        var superseded: Job? = null
        var scheduled: Job? = null
        slots.compute(groupId) { _, current ->
            val slot = current ?: Slot(groupId)
            val active = slot.owner
            if (!replaceActive && active?.isActive == true) {
                // A run that has not read yet still will, so it answers this request; once its
                // reads are done the snapshot is fixed and only a fresh run can carry it.
                slot.pending = block
                slot.pendingCovered = !slot.readsStarted
                slot
            } else {
                superseded = active
                slot.scope = scope
                slot.readsStarted = false
                slot.pending = null
                slot.pendingCovered = false
                slot.owner = startRun(slot, active, block).also { scheduled = it }
                slot
            }
        }
        // Cancelling resumes an unwinding run, and its slot release must not re-enter the map
        // operation still holding this key's lock.
        superseded?.cancel()
        scheduled?.start()
    }

    /**
     * Takes the group's slot for the whole of [block], cancelling whatever was warming it.
     * [scope] owns a request that arrives after this run has read, so the follow-up is not left
     * to die with the caller's scope.
     */
    suspend fun <T> runExclusive(
        groupId: String,
        scope: CoroutineScope,
        block: suspend () -> T
    ): T = coroutineScope {
        val refreshJob = checkNotNull(coroutineContext[Job])
        var superseded: Job? = null
        slots.compute(groupId) { _, current ->
            val slot = current ?: Slot(groupId)
            superseded = slot.owner
            slot.scope = scope
            slot.readsStarted = false
            slot.pending = null
            slot.pendingCovered = false
            slot.owner = refreshJob
            slot
        }
        superseded?.cancel()
        // Its last snapshot write has to land before this run reads, or stale data wins.
        superseded?.join()
        try {
            block()
        } finally {
            release(groupId, refreshJob)
        }
    }

    /** Marks that the owning run began reading, so a later request needs a run of its own. */
    suspend fun markReadsStarted(groupId: String) {
        val slot = slots[groupId] ?: return
        if (slot.owner === coroutineContext[Job]) slot.readsStarted = true
    }

    fun cancel(groupId: String) {
        val superseded = slots.remove(groupId)?.owner
        superseded?.cancel()
    }

    fun cancelAll() {
        val superseded = slots.values.mapNotNull { slot ->
            slot.pending = null
            slot.owner.also { slot.owner = null }
        }
        slots.clear()
        superseded.forEach { it.cancel() }
    }

    fun retainOnly(groupIds: Set<String>) {
        slots.keys
            .filterNot(groupIds::contains)
            .forEach(::cancel)
    }

    private fun startRun(slot: Slot, superseded: Job?, block: suspend () -> Unit): Job {
        val scope = checkNotNull(slot.scope) { "The warmup slot lost its scope." }
        return scope.launch(start = CoroutineStart.LAZY) {
            // A cancelled predecessor only stops once it unwinds, and its last snapshot write
            // would otherwise land after the fresher one it was replaced for.
            superseded?.join()
            try {
                block()
            } finally {
                release(slot.groupId, coroutineContext[Job])
            }
        }
    }

    private fun release(groupId: String, finished: Job?) {
        var scheduled: Job? = null
        slots.compute(groupId) { _, slot ->
            when {
                // A superseded run leaves the slot to its replacement: that run answers what was
                // queued, and it must not inherit the superseded run's captured group.
                slot == null -> null
                slot.owner !== finished -> slot
                else -> {
                    val next = slot.pending
                    val answered = slot.pendingCovered && slot.readsStarted
                    slot.owner = null
                    slot.readsStarted = false
                    slot.pending = null
                    slot.pendingCovered = false
                    if (next == null || answered) null
                    else {
                        slot.owner = startRun(slot, null, next).also { scheduled = it }
                        slot
                    }
                }
            }
        }
        scheduled?.start()
    }
}
