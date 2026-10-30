package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformAuditReport
import com.sanibonani.save.domain.repository.admin.AuditRepository
import com.sanibonani.save.domain.usecase.admin.GeneratePlatformAuditReportUseCase
import com.sanibonani.save.domain.usecase.admin.ReadPlatformAuditReportUseCase
import com.sanibonani.save.features.admin.presentation.PlatformAuditViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * `generate_platform_audit_report_v1()` inserts a row into `platform_audit_reports` and upserts one
 * into `audit_reports` every time it runs. Opening the Platform Audit tab therefore must not be the
 * thing that regenerates the snapshot: reading and regenerating are separate actions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformAuditReadPathTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val auditRepo = mockk<AuditRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlatformAuditViewModel

    private val report = PlatformAuditReport(
        generatedAt = "2026-10-26 11:04:52+02",
        liquidity = Money("48200.00"),
        revenue = Money("1910.50")
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
        viewModel = PlatformAuditViewModel(
            ReadPlatformAuditReportUseCase(auditRepo),
            GeneratePlatformAuditReportUseCase(auditRepo)
        )
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loading the tab reads the stored snapshot instead of regenerating it`() = runTest {
        coEvery { auditRepo.getPlatformAuditReport() } returns Result.success(report)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(report, (viewModel.state.value.platformAuditReport as AsyncResult.Success).data)
        coVerify(exactly = 0) { auditRepo.triggerPlatformAudit() }
    }

    @Test
    fun `a platform that has never been audited leaves the tab ready to run one`() = runTest {
        coEvery { auditRepo.getPlatformAuditReport() } returns Result.success(null)

        viewModel.load()
        advanceUntilIdle()

        // Uninitialized, not Error: the tab then offers "Run Platform Audit" instead of a failure
        // screen for a platform that has simply never been audited.
        assertTrue(viewModel.state.value.platformAuditReport is AsyncResult.Uninitialized)
        coVerify(exactly = 0) { auditRepo.triggerPlatformAudit() }
    }

    @Test
    fun `a snapshot the repository could not read is reported as a failure`() = runTest {
        coEvery { auditRepo.getPlatformAuditReport() } returns
            Result.failure(IllegalStateException("HTTP 503 Service Unavailable"))

        viewModel.load()
        advanceUntilIdle()

        val result = viewModel.state.value.platformAuditReport
        assertTrue(result is AsyncResult.Error)
        assertTrue((result as AsyncResult.Error).message.contains("503"))
    }

    @Test
    fun `only an explicit refresh regenerates the snapshot`() = runTest {
        coEvery { auditRepo.triggerPlatformAudit() } returns Result.success(report)

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { auditRepo.triggerPlatformAudit() }
        coVerify(exactly = 0) { auditRepo.getPlatformAuditReport() }
        assertTrue(viewModel.state.value.platformAuditReport is AsyncResult.Success)
    }

    @Test
    fun `a failed refresh reports the error instead of showing a stale snapshot`() = runTest {
        coEvery { auditRepo.getPlatformAuditReport() } returns Result.success(report)
        coEvery { auditRepo.triggerPlatformAudit() } returns
            Result.failure(IllegalStateException("HTTP 503"))

        viewModel.load()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.platformAuditReport is AsyncResult.Error)
    }
}
