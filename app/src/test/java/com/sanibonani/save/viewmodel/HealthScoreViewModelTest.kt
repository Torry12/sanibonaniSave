package com.sanibonani.save.viewmodel

import com.sanibonani.save.domain.repository.admin.HealthScoreRepository
import com.sanibonani.save.domain.repository.equity.MemberContributionRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.actuarial.CalculateGroupHealthScoreUseCase
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.admin.presentation.HealthScoreViewModel
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthScoreViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var groupRepository: GroupRepository
    private lateinit var memberRepository: MemberRepository
    private lateinit var contributionRepository: MemberContributionRepository
    private lateinit var healthScoreRepository: HealthScoreRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        groupRepository = mockk(relaxed = true)
        memberRepository = mockk(relaxed = true)
        contributionRepository = mockk(relaxed = true)
        healthScoreRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun viewModel() = HealthScoreViewModel(
        calculateGroupHealthScore = CalculateGroupHealthScoreUseCase(
            groupRepository = groupRepository,
            memberRepository = memberRepository,
            contributionRepository = contributionRepository,
            healthScoreRepository = healthScoreRepository
        )
    )

    // HealthScoreDetailScreen.kt:84-86 renders state.error, but the failure path only called
    // handleError() - and this screen collects no events - so a refused read showed the empty
    // branch instead of the error card.
    @Test
    fun `a refused health score read puts the cause on the state the screen renders`() = runTest {
        val failure = IOException("HTTP 503 Service Unavailable")
        coEvery { groupRepository.getGroupById("group-1") } returns Result.failure(failure)
        val viewModel = viewModel()

        viewModel.loadHealthScore("group-1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(failure.toUserMessage(), state.error)
        assertNull("a score that was never read cannot be claimed", state.score)
    }

    @Test
    fun `a second load drops the previous error before it asks the server again`() = runTest {
        coEvery { groupRepository.getGroupById("group-1") } returns
            Result.failure(IOException("HTTP 503 Service Unavailable"))
        val viewModel = viewModel()
        viewModel.loadHealthScore("group-1")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error != null)

        coEvery { groupRepository.getGroupById("group-1") } returns
            Result.failure(IOException("HTTP 504 Gateway Timeout"))
        viewModel.loadHealthScore("group-1")
        advanceUntilIdle()

        assertEquals(
            IOException("HTTP 504 Gateway Timeout").toUserMessage(),
            viewModel.state.value.error
        )
    }
}
