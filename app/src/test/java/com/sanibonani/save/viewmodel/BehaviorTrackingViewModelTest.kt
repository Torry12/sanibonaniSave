package com.sanibonani.save.viewmodel

import com.sanibonani.save.domain.model.MemberBehaviorTrack
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.admin.FlagMemberForReviewUseCase
import com.sanibonani.save.domain.usecase.admin.GenerateBehaviorFinetuningDatasetUseCase
import com.sanibonani.save.domain.usecase.admin.GetMemberBehaviorTrackUseCase
import com.sanibonani.save.domain.usecase.admin.ObserveFraudEventsByMemberUseCase
import com.sanibonani.save.domain.usecase.admin.UnflagMemberUseCase
import com.sanibonani.save.domain.usecase.member.GetMemberByIdUseCase
import com.sanibonani.save.domain.usecase.member.SuspendMemberUseCase
import com.sanibonani.save.domain.usecase.member.UnsuspendMemberUseCase
import com.sanibonani.save.domain.usecase.member.UpdateMemberStatusUseCase
import com.sanibonani.save.features.admin.presentation.BehaviorTrackingViewModel
import io.mockk.*
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
        getMemberById = GetMemberByIdUseCase(memberRepository),
        observeFraudEventsByMember = ObserveFraudEventsByMemberUseCase(repository),
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
        coEvery { memberRepository.getMemberById("member-1") } returns Result.failure(IllegalStateException("not found"))

        viewModel.loadMemberData(memberId = "member-1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        // Note: BaseViewModel's handleError might set error state. Check implementation if needed.
    }

    @Test
    fun `loadTrack updates state on success`() = runTest {
        val viewModel = viewModel()
        val track = MemberBehaviorTrack(memberId = "m1", groupId = "g1")
        val member = com.sanibonani.save.domain.model.Member(id = "m1", groupId = "g1", fullName = "Test User")

        coEvery { repository.getMemberBehavior("m1") } returns Result.success(track)
        coEvery { memberRepository.getMemberById("m1") } returns Result.success(member)
        coEvery { repository.observeFraudEventsByMember("m1") } returns kotlinx.coroutines.flow.flowOf(Result.success(emptyList()))

        viewModel.loadMemberData("m1")
        advanceUntilIdle()

        assertEquals(track, viewModel.state.value.track)
        assertEquals(member, viewModel.state.value.member)
    }
}
