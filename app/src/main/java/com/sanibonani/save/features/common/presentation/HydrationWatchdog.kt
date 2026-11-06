package com.sanibonani.save.features.common.presentation

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One bounded wait for a group hydration that may never answer.
 *
 * Both the admin dashboard and the member portal show a loader while their group context is out, and
 * only a snapshot releases it, so a read the server refused used to leave the loader running until the
 * user left the screen. The bound, the verdict, and the rule that a snapshot arriving later disproves
 * that verdict are the same on both surfaces, so they live here.
 *
 * The part each copy used to get wrong is that a wait is bounded from when it starts. Re-arming an
 * already-running timer restarts it, so a warm-up that kept re-emitting the same unfinished snapshot
 * kept postponing the verdict for as long as its passes kept arriving.
 */
class HydrationWatchdog(
    private val key: String,
    private val timeout: Duration = HYDRATION_TIMEOUT,
    private val launch: (String, suspend () -> Unit) -> Unit,
    private val cancelJob: (String) -> Unit,
    private val shouldReport: () -> Boolean,
    private val onVerdict: (String) -> Unit
) {
    private enum class Phase { Idle, Waiting, Reported }

    private var phase = Phase.Idle

    /** Whether the verdict below is what is currently keeping the loader off the screen. */
    val isReporting: Boolean get() = phase == Phase.Reported

    /** A fresh request for the book: what was said about the last wait does not carry into this one. */
    fun arm() {
        cancelJob(key)
        phase = Phase.Waiting
        launch(key) {
            delay(timeout.inWholeMilliseconds)
            if (shouldReport()) {
                phase = Phase.Reported
                onVerdict(HYDRATION_TIMEOUT_MESSAGE)
            } else {
                phase = Phase.Idle
            }
        }
    }

    /**
     * Bounds a wait that is already open without restarting it.
     *
     * For the emission of a snapshot that is still unfinished: it is the same request the caller
     * armed when it asked for the group, not a new one.
     */
    fun armIfIdle() {
        if (phase == Phase.Idle) arm()
    }

    /**
     * Ends the wait because the book arrived, reporting whether a verdict was on screen so the caller
     * can clear the timeout it has now disproved. An error from anywhere else is the caller's to keep.
     */
    fun settle(): Boolean {
        cancelJob(key)
        val reported = phase == Phase.Reported
        phase = Phase.Idle
        return reported
    }

    /** Ends the wait because nobody is looking at it. */
    fun cancel() {
        cancelJob(key)
        phase = Phase.Idle
    }

    companion object {
        /**
         * Long enough for a slow tier to answer, short enough that the user keeps a way out. The same
         * bound for both surfaces, so a wedged read is not one screen's problem and the other's mercy.
         */
        val HYDRATION_TIMEOUT: Duration = 7.seconds

        /** Rendered as the Retry the user acts on, so both surfaces give the same way out. */
        const val HYDRATION_TIMEOUT_MESSAGE = "Loading timed out. Please try again."
    }
}
