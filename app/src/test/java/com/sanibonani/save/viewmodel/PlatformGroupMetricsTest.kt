package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.GroupHealthScore
import com.sanibonani.save.domain.model.RiskZone
import com.sanibonani.save.domain.usecase.actuarial.CalculateGroupHealthScoreUseCase
import com.sanibonani.save.domain.usecase.admin.ComputeSystemWideMetricsUseCase
import com.sanibonani.save.domain.usecase.admin.DeleteGroupFromPlatformUseCase
import com.sanibonani.save.domain.usecase.admin.GetAllPlatformGroupsUseCase
import com.sanibonani.save.domain.usecase.admin.GetGroupPlatformMetricsUseCase
import com.sanibonani.save.domain.usecase.admin.GetPlatformAnalyticsUseCase
import com.sanibonani.save.domain.usecase.admin.GetPlatformDistributionsUseCase
import com.sanibonani.save.features.admin.presentation.PlatformAnalyticsViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tapping a row in the All Groups tab opens ActuarialHealthDialog titled with that group and fed by
 * `selectedGroupMetrics`. The fetch had only an onSuccess leg, so a refused or failed read left the
 * previous group's numbers on screen under the new group's name — for an admin reading solvency
 * figures, another group's numbers are not a blank screen, they are a wrong answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformGroupMetricsTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val getGroupPlatformMetrics = mockk<GetGroupPlatformMetricsUseCase>()
    private val calculateGroupHealthScore = mockk<CalculateGroupHealthScoreUseCase>()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlatformAnalyticsViewModel

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
        viewModel = PlatformAnalyticsViewModel(
            getPlatformAnalytics = mockk<GetPlatformAnalyticsUseCase>(relaxed = true),
            getAllPlatformGroups = mockk<GetAllPlatformGroupsUseCase>(relaxed = true),
            computeSystemWideMetrics = mockk<ComputeSystemWideMetricsUseCase>(relaxed = true),
            getGroupPlatformMetrics = getGroupPlatformMetrics,
            calculateGroupHealthScore = calculateGroupHealthScore,
            deleteGroupFromPlatformUseCase = mockk<DeleteGroupFromPlatformUseCase>(relaxed = true),
            getPlatformDistributions = mockk<GetPlatformDistributionsUseCase>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `an accepted fetch publishes that group's metrics`() = runTest {
        val metrics = ActuarialMetrics(compositeRiskScore = 77)
        coEvery { getGroupPlatformMetrics("g-1") } returns Result.success(metrics)

        viewModel.fetchGroupMetrics("g-1")
        advanceUntilIdle()

        assertEquals(metrics, viewModel.state.value.selectedGroupMetrics)
    }

    @Test
    fun `a failed fetch cannot leave the previous group's metrics on screen`() = runTest {
        val firstGroup = ActuarialMetrics(compositeRiskScore = 77)
        coEvery { getGroupPlatformMetrics("g-1") } returns Result.success(firstGroup)
        viewModel.fetchGroupMetrics("g-1")
        advanceUntilIdle()
        assertEquals(firstGroup, viewModel.state.value.selectedGroupMetrics)

        // Now the admin taps a different row and that read is refused.
        coEvery { getGroupPlatformMetrics("g-2") } returns
            Result.failure(IllegalStateException("[SYS_001] GROUP_METRICS_NOT_FOUND"))

        viewModel.fetchGroupMetrics("g-2")
        advanceUntilIdle()

        assertNull(
            "the dialog titled g-2 must not render g-1's figures",
            viewModel.state.value.selectedGroupMetrics
        )
    }

    @Test
    fun `a failed fetch is reported to the admin`() = runTest {
        coEvery { getGroupPlatformMetrics("g-1") } returns
            Result.failure(IllegalStateException("[SYS_001] GROUP_METRICS_NOT_FOUND"))

        viewModel.fetchGroupMetrics("g-1")
        advanceUntilIdle()

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
    }

    // The score row and the "View full health report" button live behind this field, so a dialog that
    // never fills it is a dialog with no way into the report the admin is being pointed at.
    @Test
    fun `an accepted health score fetch publishes that group's score`() = runTest {
        val score = GroupHealthScore(
            groupId = "g-1",
            overallScore = 82,
            zone = RiskZone.GREEN,
            generatedAt = "2026-11-03T00:00:00Z"
        )
        coEvery { calculateGroupHealthScore("g-1") } returns Result.success(score)

        viewModel.fetchGroupHealthScore("g-1")
        advanceUntilIdle()

        assertEquals(score, viewModel.state.value.selectedGroupHealthScore)
        assertEquals(false, viewModel.state.value.isLoadingHealthScore)
    }

    @Test
    fun `a health score fetch for another group cannot keep the previous score on screen`() = runTest {
        val firstScore = GroupHealthScore("g-1", 82, RiskZone.GREEN, generatedAt = "2026-11-03T00:00:00Z")
        coEvery { calculateGroupHealthScore("g-1") } returns Result.success(firstScore)
        viewModel.fetchGroupHealthScore("g-1")
        advanceUntilIdle()
        assertEquals(firstScore, viewModel.state.value.selectedGroupHealthScore)

        val pending = CompletableDeferred<Result<GroupHealthScore>>()
        coEvery { calculateGroupHealthScore("g-2") } coAnswers { pending.await() }
        viewModel.fetchGroupHealthScore("g-2")
        advanceUntilIdle()

        // Held in flight, so the assertions below read the state the new fetch left between its
        // start and its answer - the point where the previous group's score used to survive.
        assertEquals(true, viewModel.state.value.isLoadingHealthScore)
        assertNull(
            "the earlier group's score cannot stay on screen under the new group's name",
            viewModel.state.value.selectedGroupHealthScore
        )

        pending.complete(Result.failure(IllegalStateException("[SYS_001] GROUP_NOT_FOUND")))
        advanceUntilIdle()
        assertNull(viewModel.state.value.selectedGroupHealthScore)
        assertEquals(false, viewModel.state.value.isLoadingHealthScore)
    }

    @Test
    fun `a refused health score fetch is reported`() = runTest {
        coEvery { calculateGroupHealthScore("g-1") } returns
            Result.failure(IOException("health score read failed"))

        viewModel.fetchGroupHealthScore("g-1")
        advanceUntilIdle()

        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
    }
}
