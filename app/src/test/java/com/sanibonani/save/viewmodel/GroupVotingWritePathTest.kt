package com.sanibonani.save.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.repository.group.VotingRepository
import com.sanibonani.save.domain.usecase.group.CastGroupPollVoteUseCase
import com.sanibonani.save.domain.usecase.group.CloseGroupPollUseCase
import com.sanibonani.save.domain.usecase.group.CreateGroupPollUseCase
import com.sanibonani.save.domain.usecase.group.ObserveGroupPollsUseCase
import com.sanibonani.save.features.group.presentation.GroupVotingViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
 * Casting a vote and creating a poll are the two writes this screen offers, and both used to have
 * an onSuccess leg only. Every refusal the use case or the database answers with - no member
 * context, a poll with one option, `[SEC_002]` not-a-member, `[FIN_003]` the poll is already
 * closed - left the screen unchanged, so the member believed their vote was counted. The voting
 * screen renders `state.error`, so each refusal has to reach that field.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupVotingWritePathTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val votingRepo = mockk<VotingRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: GroupVotingViewModel

    private val groupId = "11111111-1111-1111-1111-111111111111"
    private val memberId = "33333333-3333-3333-3333-333333333333"
    private val pollId = "22222222-2222-2222-2222-222222222222"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { votingRepo.observePolls(groupId, memberId) } returns flowOf(Result.success(emptyList()))
        viewModel = GroupVotingViewModel(
            observeGroupPolls = ObserveGroupPollsUseCase(votingRepo),
            createGroupPoll = CreateGroupPollUseCase(votingRepo),
            castGroupPollVote = CastGroupPollVoteUseCase(votingRepo),
            closeGroupPoll = CloseGroupPollUseCase(votingRepo)
        )
        // loadPolls writes groupId/memberId into the state synchronously; only the poll
        // collection needs the scheduler, and each test advances it.
        viewModel.loadPolls(groupId, memberId)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an accepted vote reports success`() = runTest {
        coEvery { votingRepo.castVote(groupId, pollId, "option-1", memberId) } returns Result.success(Unit)

        viewModel.castVote(pollId, "option-1")
        advanceUntilIdle()

        assertEquals("Vote submitted", viewModel.state.value.message)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a vote the database refuses is shown instead of dropped`() = runTest {
        coEvery { votingRepo.castVote(groupId, pollId, "option-1", memberId) } returns
            Result.failure(IllegalStateException("[FIN_003] POLL_ALREADY_CLOSED"))

        viewModel.castVote(pollId, "option-1")
        advanceUntilIdle()

        val error = viewModel.state.value.error
        assertTrue("expected the refusal to reach the UI, got: $error", !error.isNullOrBlank())
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `a vote refused before it is sent is shown instead of dropped`() = runTest {
        // The mirror of the real path: a member context that is blank never reaches the RPC, and
        // the use case answers with a Result rather than throwing at the caller.
        viewModel.loadPolls(groupId, "")
        advanceUntilIdle()

        viewModel.castVote(pollId, "option-1")
        advanceUntilIdle()

        assertTrue(
            "expected the local refusal to reach the UI",
            viewModel.state.value.error?.contains("Member context is required") == true
        )
    }

    @Test
    fun `an accepted poll creation reports success`() = runTest {
        coEvery {
            votingRepo.createPoll(groupId, memberId, "Which date?", null, listOf("Fri", "Sat"), false, null)
        } returns Result.success(pollId)

        viewModel.createPoll("Which date?", null, listOf("Fri", "Sat"))
        advanceUntilIdle()

        assertEquals("Poll created", viewModel.state.value.message)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a poll creation the database refuses is shown instead of dropped`() = runTest {
        coEvery {
            votingRepo.createPoll(groupId, memberId, "Which date?", null, listOf("Fri", "Sat"), false, null)
        } returns Result.failure(IllegalStateException("[SEC_002] POLL_CREATE_ACCESS_DENIED"))

        viewModel.createPoll("Which date?", null, listOf("Fri", "Sat"))
        advanceUntilIdle()

        val error = viewModel.state.value.error
        assertTrue("expected the refusal to reach the UI, got: $error", !error.isNullOrBlank())
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `a poll refused for having one option is shown instead of dropped`() = runTest {
        viewModel.createPoll("Which date?", null, listOf("Friday only"))
        advanceUntilIdle()

        assertTrue(
            "expected the validation refusal to reach the UI",
            viewModel.state.value.error?.contains("at least two options") == true
        )
    }
}
