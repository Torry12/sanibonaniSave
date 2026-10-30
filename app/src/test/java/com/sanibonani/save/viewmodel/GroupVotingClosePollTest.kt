package com.sanibonani.save.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.repository.group.VotingRepository
import com.sanibonani.save.domain.usecase.group.CastGroupPollVoteUseCase
import com.sanibonani.save.domain.usecase.group.CloseGroupPollUseCase
import com.sanibonani.save.domain.usecase.group.CreateGroupPollUseCase
import com.sanibonani.save.domain.usecase.group.ObserveGroupPollsUseCase
import com.sanibonani.save.features.group.presentation.GroupVotingViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Closing a poll is now decided by the database: `close_poll_v1` (SYNC_DELTA_20261029B) refuses a
 * caller who is not that group's admin, and the voting screen renders the Close button for every
 * viewer. The call used to have only an onSuccess leg, so all four of the function's refusals -
 * not found, not entitled, wrong status, MFA - looked like a button that does nothing. Each
 * outcome has to reach the state the screen reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupVotingClosePollTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val votingRepo = mockk<VotingRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: GroupVotingViewModel

    private val pollId = "22222222-2222-2222-2222-222222222222"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = GroupVotingViewModel(
            observeGroupPolls = ObserveGroupPollsUseCase(votingRepo),
            createGroupPoll = mockk<CreateGroupPollUseCase>(relaxed = true),
            castGroupPollVote = mockk<CastGroupPollVoteUseCase>(relaxed = true),
            closeGroupPoll = CloseGroupPollUseCase(votingRepo)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an accepted close reports success`() = runTest {
        coEvery { votingRepo.closePoll(pollId) } returns Result.success(Unit)

        viewModel.closePoll(pollId)
        advanceUntilIdle()

        assertEquals("Poll closed", viewModel.state.value.message)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a refusal from the database is shown instead of dropped`() = runTest {
        coEvery { votingRepo.closePoll(pollId) } returns
            Result.failure(IllegalStateException("[SEC_002] POLL_CLOSE_ACCESS_DENIED"))

        viewModel.closePoll(pollId)
        advanceUntilIdle()

        val error = viewModel.state.value.error
        assertTrue("expected the refusal to reach the UI, got: $error", !error.isNullOrBlank())
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `a close that the repository cannot even send is reported`() = runTest {
        // The mirror of the real path: VotingRepositoryImpl refuses a blank id before the RPC,
        // and that failure travels back as a Result, not as an exception in the caller.
        coEvery { votingRepo.closePoll("") } returns
            Result.failure(IllegalArgumentException("This poll cannot be closed: it has no identifier."))

        viewModel.closePoll("")
        advanceUntilIdle()

        assertTrue(
            "expected the local refusal to reach the UI",
            viewModel.state.value.error?.contains("no identifier") == true
        )
    }
}
