package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.GroupAuditResult
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.admin.AuditRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.repository.treasury.ReconciliationRepository
import com.sanibonani.save.domain.usecase.treasury.AuditGroupBalanceUseCase
import com.sanibonani.save.domain.usecase.treasury.ConsolidateGroupLedgerUseCase
import com.sanibonani.save.domain.usecase.treasury.GetPlatformLedgerUseCase
import com.sanibonani.save.domain.usecase.treasury.ObserveGroupReconciliationsUseCase
import com.sanibonani.save.features.admin.presentation.PlatformLedgerViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The platform ledger tab is where a discrepancy is audited and repaired, so its indicators have to
 * describe the operation that actually ran. A row that failed to refresh must not be mistaken for a
 * group that stopped transacting, and a button must not keep spinning after its call is over.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformLedgerAuditTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val auditRepo = mockk<AuditRepository>(relaxed = true)
    private val ledgerRepo = mockk<LedgerRepository>(relaxed = true)
    private val reconRepo = mockk<ReconciliationRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlatformLedgerViewModel

    private val groupId = "group-1"

    private fun entry(id: String) = LedgerEntry(
        id = id,
        groupId = groupId,
        amount = Money("250.00"),
        balanceAfter = Money("250.00"),
        description = "Monthly contribution",
        category = "contribution"
    )

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
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        Dispatchers.setMain(testDispatcher)
        viewModel = PlatformLedgerViewModel(
            getPlatformLedger = GetPlatformLedgerUseCase(ledgerRepo),
            observeGroupReconciliations = ObserveGroupReconciliationsUseCase(reconRepo),
            auditGroupBalance = AuditGroupBalanceUseCase(auditRepo),
            consolidateLedger = ConsolidateGroupLedgerUseCase(ledgerRepo)
        )
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `a failed platform ledger refresh keeps the rows already on screen`() = runTest {
        coEvery { ledgerRepo.getPlatformLedger() } returns Result.success(listOf(entry("l1")))
        viewModel.load()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.platformLedger.size)

        coEvery { ledgerRepo.getPlatformLedger() } returns
            Result.failure(IllegalStateException("HTTP 503 Service Unavailable"))

        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf("l1"), viewModel.state.value.platformLedger.map { it.id })
        val events = viewModel.events.take(1).toList()
        assertTrue(
            "emitted events: $events",
            events.singleOrNull { it is UIEvent.ShowError } is UIEvent.ShowError
        )
    }

    @Test
    fun `a failed audit still clears its indicator`() = runTest {
        coEvery { auditRepo.auditAndFixGroupBalance(groupId, fix = false) } returns
            Result.failure(IllegalStateException("RPC unavailable"))

        viewModel.reconcileGroup(groupId)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isReconciling)
    }

    @Test
    fun `a failed repair still clears its indicator`() = runTest {
        coEvery { auditRepo.auditAndFixGroupBalance(groupId, fix = true) } returns
            Result.failure(IllegalStateException("RPC unavailable"))

        viewModel.repairGroupFinancials(groupId)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRepairing)
    }

    @Test
    fun `a failed consolidation still clears its indicator`() = runTest {
        coEvery { ledgerRepo.consolidateGroupLedger(groupId) } returns
            Result.failure(IllegalStateException("RPC unavailable"))

        viewModel.consolidateGroupLedger(groupId)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isConsolidating)
    }

    @Test
    fun `a completed audit publishes its discrepancy and the refreshed ledger`() = runTest {
        coEvery { ledgerRepo.getPlatformLedger() } returns Result.success(listOf(entry("l1")))
        coEvery { auditRepo.auditAndFixGroupBalance(groupId, fix = false) } returns
            Result.success(GroupAuditResult(groupId = groupId, discrepancy = Money("12.00")))

        viewModel.reconcileGroup(groupId)
        advanceUntilIdle()

        assertEquals(Money("12.00"), viewModel.state.value.lastAuditResult?.discrepancy)
        assertFalse(viewModel.state.value.isReconciling)
        assertEquals(1, viewModel.state.value.platformLedger.size)
    }
}
