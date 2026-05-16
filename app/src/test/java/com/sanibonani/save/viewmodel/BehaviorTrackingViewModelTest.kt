package com.sanibonani.save.viewmodel

import com.sanibonani.save.domain.model.MemberBehaviorTrack
import com.sanibonani.save.domain.repository.BehaviorTrackingRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BehaviorTrackingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: BehaviorTrackingRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `flagMemberForReview clears loading when refresh fails`() = runTest {
        val viewModel = BehaviorTrackingViewModel(repository)

        coEvery { repository.flagMemberForReview("member-1", any(), any()) } returns Result.success(Unit)
        coEvery { repository.getMemberBehavior("member-1") } returns Result.failure(IllegalStateException("refresh failed"))

        viewModel.flagMemberForReview(memberId = "member-1", reason = "manual")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.error?.isNotBlank() == true)
    }

    @Test
    fun `observeGroupMembersBehavior uses latest selected group data`() = runTest {
        val firstFlow = MutableSharedFlow<Result<List<MemberBehaviorTrack>>>(replay = 1)
        val secondFlow = MutableSharedFlow<Result<List<MemberBehaviorTrack>>>(replay = 1)

        every { repository.observeGroupMembersBehavior("group-1") } returns firstFlow
        every { repository.observeGroupMembersBehavior("group-2") } returns secondFlow

        val viewModel = BehaviorTrackingViewModel(repository)
        viewModel.observeGroupMembersBehavior("group-1")

        val stale = MemberBehaviorTrack(memberId = "stale", groupId = "group-1")
        val latest = MemberBehaviorTrack(memberId = "latest", groupId = "group-2")

        firstFlow.tryEmit(Result.success(listOf(stale)))
        advanceUntilIdle()

        viewModel.observeGroupMembersBehavior("group-2")
        secondFlow.tryEmit(Result.success(listOf(latest)))
        advanceUntilIdle()

        val members = viewModel.state.value.groupMembers
        assertEquals(1, members.size)
        assertEquals("latest", members.first().memberId)
    }
}

