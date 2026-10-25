package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.repository.admin.PlatformConfigRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.features.admin.presentation.PlatformFeeConfigViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * What the platform charges its groups is decided on this screen, so the amounts published to
 * `platform_settings` have to be the amounts the admin typed. A field that was cleared or mistyped
 * is not an instruction to bill a default, and it is not a waiver either.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformFeeConfigSaveTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val platformRepo = mockk<PlatformRepository>(relaxed = true)
    private val configRepo = mockk<PlatformConfigRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlatformFeeConfigViewModel

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
        viewModel = PlatformFeeConfigViewModel(platformRepo, configRepo)
    }

    @After
    fun tearDown() {
        // Drain while Main is still installed, otherwise a suspended collector resumes after
        // resetMain() and reports that the platform dispatcher is absent.
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun fillAllFees() {
        viewModel.updateMemberCharge("12.50")
        viewModel.updateRegistrationFee("700.00")
        viewModel.updatePayoutFee("5.00")
        viewModel.updateLateFeePercent("10.00")
        viewModel.updateShortfallRecoveryFee("20.00")
        viewModel.updateLoanInterestRate("20.00")
        viewModel.updateLoanProcessingFee("15.00")
        viewModel.updateGroupSuspensionLiftingFee("250.00")
        viewModel.updateMemberSuspensionLiftingFee("50.00")
        viewModel.updateAutoSuspensionDays("90")
    }

    @Test
    fun `a cleared fee field publishes nothing`() = runTest {
        fillAllFees()
        viewModel.updatePayoutFee("")
        coEvery { platformRepo.updateGlobalFees(any()) } returns Result.success(Unit)

        viewModel.saveGlobalFees()
        advanceUntilIdle()

        coVerify(exactly = 0) { platformRepo.updateGlobalFees(any()) }
        assertEquals(false, viewModel.state.value.isSaving)
        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
    }

    @Test
    fun `a mistyped fee field publishes nothing`() = runTest {
        fillAllFees()
        viewModel.updateMemberCharge("1O.00")
        coEvery { platformRepo.updateGlobalFees(any()) } returns Result.success(Unit)

        viewModel.saveGlobalFees()
        advanceUntilIdle()

        coVerify(exactly = 0) { platformRepo.updateGlobalFees(any()) }
    }

    @Test
    fun `an unreadable suspension period publishes nothing`() = runTest {
        fillAllFees()
        viewModel.updateAutoSuspensionDays("three months")
        coEvery { platformRepo.updateGlobalFees(any()) } returns Result.success(Unit)

        viewModel.saveGlobalFees()
        advanceUntilIdle()

        coVerify(exactly = 0) { platformRepo.updateGlobalFees(any()) }
    }

    @Test
    fun `the amounts the admin typed are the amounts that get published`() = runTest {
        fillAllFees()
        // Zero is a decision here, not an empty field: the payout fee is being waived.
        viewModel.updatePayoutFee("0.00")
        val config = slot<PlatformConfig>()
        coEvery { platformRepo.updateGlobalFees(capture(config)) } returns Result.success(Unit)

        viewModel.saveGlobalFees()
        advanceUntilIdle()

        assertEquals(Money("12.50"), config.captured.monthlyMemberFee)
        assertEquals(Money("0.00"), config.captured.payoutFee)
        assertEquals(Money("20.00"), config.captured.shortfallRecoveryFee)
        assertEquals(90, config.captured.autoSuspensionDays)
        assertEquals(true, viewModel.state.value.saveSuccess)
    }

    @Test
    fun `a published save is the config billing uses next`() = runTest {
        fillAllFees()
        coEvery { platformRepo.updateGlobalFees(any()) } returns Result.success(Unit)

        viewModel.saveGlobalFees()
        advanceUntilIdle()

        verify(exactly = 1) {
            configRepo.hydrate(match {
                it.monthlyMemberFee == Money("12.50") &&
                    it.shortfallRecoveryFee == Money("20.00") &&
                    it.autoSuspensionDays == 90
            })
        }
    }

    @Test
    fun `a failed publish does not change the config billing uses`() = runTest {
        fillAllFees()
        coEvery { platformRepo.updateGlobalFees(any()) } returns
            Result.failure(IllegalStateException("HTTP 503 Service Unavailable"))

        viewModel.saveGlobalFees()
        advanceUntilIdle()

        verify(exactly = 0) { configRepo.hydrate(any()) }
    }
}
