package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.MemberBehaviorInsight
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.usecase.admin.BroadcastPlatformMessageUseCase
import com.sanibonani.save.domain.usecase.admin.RecordImpersonationAuditUseCase
import com.sanibonani.save.domain.usecase.admin.SearchMemberBehaviorInsightsUseCase
import com.sanibonani.save.domain.usecase.member.ObserveGroupMembersUseCase
import com.sanibonani.save.domain.usecase.member.SyncGroupMembersUseCase
import com.sanibonani.save.features.admin.presentation.PlatformSupportViewModel
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
 * The Support console is the platform-admin search over every member on the tier, and
 * `searchMemberInsights(query).onSuccess { ... }` had no failure leg. A refused lookup left the
 * previous term's hits - with their risk band and behaviour score - rendered under a new query,
 * which is the screen used to decide whether to impersonate or suspend someone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformSupportSearchTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val platformRepo = mockk<PlatformRepository>()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlatformSupportViewModel

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
        viewModel = PlatformSupportViewModel(
            syncGroupMembers = mockk<SyncGroupMembersUseCase>(relaxed = true),
            observeGroupMembers = mockk<ObserveGroupMembersUseCase>(relaxed = true),
            searchMemberInsights = SearchMemberBehaviorInsightsUseCase(platformRepo),
            recordImpersonationAudit = mockk<RecordImpersonationAuditUseCase>(relaxed = true),
            broadcastPlatformMessage = mockk<BroadcastPlatformMessageUseCase>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun insight(memberId: String, name: String) = MemberBehaviorInsight(
        memberId = memberId,
        groupId = "g-1",
        memberName = name,
        riskBand = "HIGH",
        overdueLoans = 2,
        behaviorScore = 31.0
    )

    @Test
    fun `a search that answers fills the console`() = runTest {
        coEvery { platformRepo.searchMembers("Thabo") } returns
            Result.success(listOf(insight("m-1", "Thabo Mokoena")))

        viewModel.searchAllMembers("Thabo")
        advanceUntilIdle()

        assertEquals(listOf("m-1"), viewModel.state.value.memberBehaviorInsights.map { it.memberId })
    }

    @Test
    fun `a refused search cannot leave the previous term's members on screen`() = runTest {
        coEvery { platformRepo.searchMembers("Thabo") } returns
            Result.success(listOf(insight("m-1", "Thabo Mokoena")))
        viewModel.searchAllMembers("Thabo")
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.memberBehaviorInsights.size)

        coEvery { platformRepo.searchMembers("Naledi") } returns
            Result.failure(IOException("member search failed"))

        viewModel.searchAllMembers("Naledi")
        advanceUntilIdle()

        assertTrue(
            "the hits for the old term must not be presented as the answer for the new one",
            viewModel.state.value.memberBehaviorInsights.isEmpty()
        )
        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
    }
}
