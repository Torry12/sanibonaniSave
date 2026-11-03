package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.domain.repository.admin.PlatformRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The landing dashboard reads two things: the platform counters and every group on the platform,
 * and only the first of those had a failure leg. `getAllPlatformGroups().getOrNull() ?: emptyList()`
 * flattened a refusal into "there are no groups", so the province and segment charts rendered an
 * empty platform under a green analytics verdict - a wrong answer, not a missing one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformAnalyticsLoadTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val platformRepo = mockk<PlatformRepository>()
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
        coEvery { platformRepo.getPlatformAnalytics() } returns Result.success(PlatformAnalytics())
        coEvery { platformRepo.getAllGroups() } returns Result.success(
            listOf(
                Group(id = "g-1", name = "Alpha", province = "Gauteng"),
                Group(id = "g-2", name = "Beta", province = "KwaZulu-Natal")
            )
        )
        viewModel = PlatformAnalyticsViewModel(
            getPlatformAnalytics = GetPlatformAnalyticsUseCase(platformRepo),
            getAllPlatformGroups = GetAllPlatformGroupsUseCase(platformRepo),
            computeSystemWideMetrics = mockk<ComputeSystemWideMetricsUseCase>(relaxed = true),
            getGroupPlatformMetrics = mockk<GetGroupPlatformMetricsUseCase>(relaxed = true),
            deleteGroupFromPlatformUseCase = mockk<DeleteGroupFromPlatformUseCase>(relaxed = true),
            getPlatformDistributions = GetPlatformDistributionsUseCase()
        )
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `a group read that answers fills the selector and the charts`() = runTest {
        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf("g-1", "g-2"), viewModel.state.value.groups.map { it.id })
        assertEquals(
            mapOf("g-1" to "Alpha", "g-2" to "Beta"),
            viewModel.state.value.groupNames
        )
        assertEquals(2, viewModel.state.value.provinceDistribution.size)
        assertTrue(viewModel.state.value.analytics is AsyncResult.Success)
    }

    @Test
    fun `a refused group read is not presented as a platform with no groups`() = runTest {
        coEvery { platformRepo.getAllGroups() } returns Result.failure(IOException("group roster read failed"))

        viewModel.load()
        advanceUntilIdle()

        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue(
            "the refused group read must be reported, not folded into an empty list",
            event is UIEvent.ShowError
        )
    }

    @Test
    fun `a refused group read keeps the analytics verdict it actually got`() = runTest {
        coEvery { platformRepo.getAllGroups() } returns Result.failure(IOException("group roster read failed"))
        val counters = PlatformAnalytics(totalGroups = 19, totalMembers = 240)
        coEvery { platformRepo.getPlatformAnalytics() } returns Result.success(counters)

        viewModel.load()
        advanceUntilIdle()

        val analytics = viewModel.state.value.analytics
        assertTrue(analytics is AsyncResult.Success)
        assertEquals(counters, (analytics as AsyncResult.Success).data)
    }

    @Test
    fun `a refused analytics read says so instead of showing defaults`() = runTest {
        coEvery { platformRepo.getPlatformAnalytics() } returns
            Result.failure(IOException("analytics read failed"))

        viewModel.load()
        advanceUntilIdle()

        val analytics = viewModel.state.value.analytics
        assertTrue("expected an error verdict, got: $analytics", analytics is AsyncResult.Error)
    }
}
