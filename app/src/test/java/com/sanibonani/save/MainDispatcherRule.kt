package com.sanibonani.save

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.rules.ExternalResource

/**
 * Owns the Main test dispatcher for one test class and releases what the class cannot release on
 * its own: a ViewModel scope is cancelled only by `onCleared()`, which no unit test calls, so any
 * ViewModel built in a test keeps its jobs alive past the last assertion. Left running, such a job
 * resumes after `Dispatchers.resetMain()` and its exception is charged to whichever class runs next,
 * which surfaces as `UncaughtExceptionsBeforeTest` on a test that never touched that code path.
 *
 * Every ViewModel the class builds must be passed through [track]. Cancel the scopes, drain the
 * scheduler while Main is still installed, then reset Main - in that order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val kind: Kind = Kind.STANDARD
) : ExternalResource() {

    enum class Kind { STANDARD, UNCONFINED }

    val scheduler = TestCoroutineScheduler()

    /**
     * Assign this to a class's own dispatcher field instead of constructing one, so every existing
     * `runTest(dispatcher)` and `dispatcher.scheduler` reference keeps working on the shared scheduler.
     */
    val dispatcher: TestDispatcher = when (kind) {
        Kind.STANDARD -> StandardTestDispatcher(scheduler)
        Kind.UNCONFINED -> UnconfinedTestDispatcher(scheduler)
    }

    private val viewModels = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(viewModel: T): T {
        viewModels += viewModel
        return viewModel
    }

    override fun before() {
        Dispatchers.setMain(dispatcher)
    }

    override fun after() {
        viewModels.forEach { it.viewModelScope.cancel() }
        scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }
}
