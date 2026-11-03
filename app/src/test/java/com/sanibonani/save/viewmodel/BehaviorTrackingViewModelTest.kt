package com.sanibonani.save.viewmodel

import com.sanibonani.save.domain.model.MemberBehaviorTrack
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.admin.FlagMemberForReviewUseCase
import com.sanibonani.save.domain.usecase.admin.GenerateBehaviorFinetuningDatasetUseCase
import com.sanibonani.save.domain.usecase.admin.GetMemberBehaviorTrackUseCase
import com.sanibonani.save.domain.usecase.admin.UnflagMemberUseCase
import com.sanibonani.save.domain.usecase.member.SuspendMemberUseCase
import com.sanibonani.save.domain.usecase.member.UnsuspendMemberUseCase
import com.sanibonani.save.domain.usecase.member.UpdateMemberStatusUseCase
import com.sanibonani.save.features.admin.presentation.BehaviorTrackingViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import io.mockk.*
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BehaviorTrackingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: BehaviorTrackingRepository
    private lateinit var memberRepository: MemberRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk<BehaviorTrackingRepository>(relaxed = true)
        memberRepository = mockk<MemberRepository>(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun viewModel() = BehaviorTrackingViewModel(
        getMemberBehaviorTrack = GetMemberBehaviorTrackUseCase(repository),
        generateBehaviorFinetuningDataset = GenerateBehaviorFinetuningDatasetUseCase(repository),
        flagMemberForReview = FlagMemberForReviewUseCase(repository),
        unflagMemberUseCase = UnflagMemberUseCase(repository),
        suspendMemberUseCase = SuspendMemberUseCase(UpdateMemberStatusUseCase(memberRepository), repository),
        unsuspendMemberUseCase = UnsuspendMemberUseCase(UpdateMemberStatusUseCase(memberRepository), repository)
    )

    @Test
    fun `loadTrack clears loading when refresh fails`() = runTest {
        val viewModel = viewModel()

        coEvery { repository.getMemberBehavior("member-1") } returns Result.failure(IllegalStateException("refresh failed"))

        viewModel.loadMemberData(memberId = "member-1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadTrack updates state on success`() = runTest {
        val viewModel = viewModel()
        val track = MemberBehaviorTrack(memberId = "m1", groupId = "g1")

        coEvery { repository.getMemberBehavior("m1") } returns Result.success(track)

        viewModel.loadMemberData("m1")
        advanceUntilIdle()

        assertEquals(track, viewModel.state.value.track)
    }

    // An empty panel means "this member has no behaviour history recorded". A read that was
    // refused cannot claim that, and it must not wipe the track the admin is reading - a flag or
    // a suspension refreshes this panel right after writing, so a failed refresh would look like
    // the history had been cleared.
    @Test
    fun `a refused behaviour track read is reported instead of blanking the panel`() = runTest {
        val track = MemberBehaviorTrack(memberId = "m1", groupId = "g1")
        val viewModel = viewModel()

        coEvery { repository.getMemberBehavior("m1") } returns Result.success(track)
        viewModel.loadMemberData("m1")
        advanceUntilIdle()
        assertEquals(track, viewModel.state.value.track)

        coEvery { repository.getMemberBehavior("m1") } returns
            Result.failure(IOException("HTTP 500 Internal Server Error"))
        viewModel.loadMemberData("m1")
        advanceUntilIdle()

        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
        assertEquals(
            "the last proven track has to stay on screen",
            track,
            viewModel.state.value.track
        )
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `a member with no track recorded still reads as no track`() = runTest {
        coEvery { repository.getMemberBehavior("m1") } returns Result.success(null)
        val viewModel = viewModel()

        viewModel.loadMemberData("m1")
        advanceUntilIdle()

        assertNull(viewModel.state.value.track)
    }

    @Test
    fun `flagging a member refreshes the panel and reports a refused refresh`() = runTest {
        val track = MemberBehaviorTrack(memberId = "m1", groupId = "g1")
        val viewModel = viewModel()

        coEvery { repository.flagMemberForReview("m1", any(), any()) } returns Result.success(Unit)
        coEvery { repository.getMemberBehavior("m1") } returnsMany listOf<Result<MemberBehaviorTrack?>>(
            Result.success(track),
            Result.failure(IOException("HTTP 503 Service Unavailable"))
        )

        viewModel.flagMember("m1", "Duplicate payout pattern")
        advanceUntilIdle()

        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
        assertEquals(track, viewModel.state.value.track)
    }
}
