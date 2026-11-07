package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.MainDispatcherRule
import com.sanibonani.save.domain.usecase.treasury.AuditGroupBalanceUseCase
import com.sanibonani.save.domain.usecase.treasury.ConsolidateGroupLedgerUseCase
import com.sanibonani.save.domain.usecase.treasury.GetPlatformLedgerUseCase
import com.sanibonani.save.domain.usecase.treasury.ObserveGroupReconciliationsUseCase
import com.sanibonani.save.features.admin.presentation.PlatformLedgerViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The platform ledger tab answers "has this group ever drifted?" from `groupReconciliations`, and
 * the observation only wrote on success. A stream that failed was indistinguishable from a group
 * with no reconciliation history - the reading an auditor takes away from a silent empty panel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformLedgerReconciliationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val observeGroupReconciliations = mockk<ObserveGroupReconciliationsUseCase>()
    private val testDispatcher = mainDispatcherRule.dispatcher
    private lateinit var viewModel: PlatformLedgerViewModel

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk<FirebaseCrashlytics>(relaxed = true)

        viewModel = mainDispatcherRule.track(PlatformLedgerViewModel(
            getPlatformLedger = mockk<GetPlatformLedgerUseCase>(relaxed = true),
            observeGroupReconciliations = observeGroupReconciliations,
            auditGroupBalance = mockk<AuditGroupBalanceUseCase>(relaxed = true),
            consolidateLedger = mockk<ConsolidateGroupLedgerUseCase>(relaxed = true)
        ))
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        unmockkAll()
    }

    @Test
    fun `a reconciliation read that answers is recorded even when it is empty`() = runTest {
        every { observeGroupReconciliations("g-1") } returns flowOf(Result.success(emptyList()))

        viewModel.fetchReconciliations("g-1")
        advanceUntilIdle()

        assertTrue(
            "an answered empty history must be distinguishable from no answer",
            viewModel.state.value.groupReconciliations.containsKey("g-1")
        )
    }

    @Test
    fun `a failed reconciliation read is reported instead of reading as no history`() = runTest {
        every { observeGroupReconciliations("g-1") } returns
            flowOf(Result.failure(IOException("reconciliation read failed")))

        viewModel.fetchReconciliations("g-1")
        advanceUntilIdle()

        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
    }
}
