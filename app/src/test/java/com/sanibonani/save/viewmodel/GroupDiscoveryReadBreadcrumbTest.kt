package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.MainDispatcherRule
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.usecase.group.FilterGroupsUseCase
import com.sanibonani.save.domain.usecase.group.GetPublicGroupsUseCase
import com.sanibonani.save.domain.usecase.group.ObserveGroupDetailUseCase
import com.sanibonani.save.features.group.presentation.GroupViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Discovery renders cached rows the moment it opens, so a refresh that answered, a refresh the server
 * refused and a refresh nobody attempted all leave the same screen behind. Nothing on the list proves
 * which of the three happened, and the gesture is the only thing that starts the read - so the read
 * has to record its own verdict. These tests pin the words the device log carries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupDiscoveryReadBreadcrumbTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val stokvel = Group(id = "g-1", name = "Savings Circle", type = GroupType.STOKVEL, province = "Gauteng")
    private val other = Group(id = "g-2", name = "Build It", type = GroupType.STOKVEL, province = "Western Cape")

    private val groupsFlow = MutableStateFlow<Result<List<Group>>>(Result.success(emptyList()))
    private val detailFlows = mutableMapOf<String, MutableStateFlow<Result<Group?>>>()
    private val getPublicGroups = mockk<GetPublicGroupsUseCase>()
    private val observeGroupDetail = mockk<ObserveGroupDetailUseCase>()
    private val testDispatcher = mainDispatcherRule.dispatcher

    private fun detailOf(id: String): MutableStateFlow<Result<Group?>> =
        detailFlows.getOrPut(id) { MutableStateFlow(Result.success(null)) }

    private fun breadcrumbWith(fragment: String): Boolean =
        AppLogger.getBreadcrumbs().any { it.contains(fragment) }

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk<FirebaseCrashlytics>(relaxed = true)

        every { getPublicGroups() } returns groupsFlow
        every { observeGroupDetail(any()) } answers { detailOf(firstArg()) }
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        unmockkAll()
    }

    private fun started(): GroupViewModel {
        val viewModel = mainDispatcherRule.track(GroupViewModel(FilterGroupsUseCase(), getPublicGroups, observeGroupDetail))
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `entering discovery records that the read was attempted`() = runTest {
        val viewModel = started()
        viewModel.setActive(true)
        advanceUntilIdle()

        assertTrue(
            "a refresh that was never attempted has to be tellable from one that answered",
            breadcrumbWith("discovery read attempted")
        )
    }

    @Test
    fun `an answered discovery read records how many groups it brought back`() = runTest {
        val viewModel = started()
        viewModel.setActive(true)
        groupsFlow.value = Result.success(listOf(stokvel, other))
        advanceUntilIdle()

        assertTrue(
            "the count is what separates an answered list from an empty cache",
            breadcrumbWith("discovery read answered with 2 groups")
        )
    }

    @Test
    fun `a refused discovery read records the refusal`() = runTest {
        val viewModel = started()
        viewModel.setActive(true)
        groupsFlow.value = Result.success(listOf(stokvel))
        advanceUntilIdle()

        groupsFlow.value = Result.failure(IOException("server rejected the request"))
        advanceUntilIdle()

        assertTrue(
            "rows already on screen must not be allowed to look like a fresh answer",
            breadcrumbWith("discovery read refused")
        )
    }

    @Test
    fun `reading a group profile records the profile read`() = runTest {
        val viewModel = started()
        viewModel.loadGroup("g-1")
        advanceUntilIdle()

        assertTrue(
            "a profile that opens from cache alone has to say which read it made",
            breadcrumbWith("group profile read attempted for g-1")
        )
    }

    @Test
    fun `a profile whose row is not there records that verdict`() = runTest {
        val viewModel = started()
        viewModel.loadGroup("missing-group")
        detailOf("missing-group").value = Result.success(null)
        advanceUntilIdle()

        assertTrue(
            "the blank profile was the defect; the log has to name the absence",
            breadcrumbWith("group profile read found no row for missing-group")
        )
    }
}
