package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * One ViewModel serves the discovery list and the group profile, so what it reads on entry decides
 * whether opening a profile costs a full public-group hydration, whether leaving a screen ends the
 * collector it started, and whether a group row that is not there is reported or rendered as blank.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupBrowseObservationTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val stokvel = Group(id = "g-1", name = "Savings Circle", type = GroupType.STOKVEL, province = "Gauteng")
    private val other = Group(id = "g-2", name = "Build It", type = GroupType.STOKVEL, province = "Western Cape")

    private val groupsFlow = MutableStateFlow<Result<List<Group>>>(Result.success(emptyList()))
    private val detailFlows = mutableMapOf<String, MutableStateFlow<Result<Group?>>>()
    private val getPublicGroups = mockk<GetPublicGroupsUseCase>()
    private val observeGroupDetail = mockk<ObserveGroupDetailUseCase>()
    private val testDispatcher = StandardTestDispatcher()

    private fun detailOf(id: String): MutableStateFlow<Result<Group?>> =
        detailFlows.getOrPut(id) { MutableStateFlow(Result.success(null)) }

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

        Dispatchers.setMain(testDispatcher)
        every { getPublicGroups() } returns groupsFlow
        every { observeGroupDetail(any()) } answers { detailOf(firstArg()) }
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun started(): GroupViewModel {
        val viewModel = GroupViewModel(FilterGroupsUseCase(), getPublicGroups, observeGroupDetail)
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `constructing the view model reads nothing`() = runTest {
        started()

        verify(exactly = 0) { getPublicGroups() }
        verify(exactly = 0) { observeGroupDetail(any()) }
    }

    @Test
    fun `reading a group profile does not read the public group list`() = runTest {
        val viewModel = started()
        viewModel.loadGroup("g-1")
        advanceUntilIdle()

        verify(exactly = 0) { getPublicGroups() }
        assertEquals(false, viewModel.state.value.isLoadingDetail)
    }

    @Test
    fun `entering discovery reads the public group list once`() = runTest {
        val viewModel = started()
        viewModel.setActive(true)
        advanceUntilIdle()

        verify(exactly = 1) { getPublicGroups() }
    }

    @Test
    fun `leaving discovery stops the group list collector`() = runTest {
        val viewModel = started()
        viewModel.setActive(true)
        groupsFlow.value = Result.success(listOf(stokvel))
        advanceUntilIdle()
        assertEquals(listOf(stokvel), viewModel.state.value.rawGroups)

        viewModel.setActive(false)
        groupsFlow.value = Result.success(emptyList())
        advanceUntilIdle()

        assertEquals(listOf(stokvel), viewModel.state.value.rawGroups)
    }

    @Test
    fun `reading another group profile ends the previous detail collector`() = runTest {
        val viewModel = started()
        viewModel.loadGroup("g-1")
        advanceUntilIdle()
        detailOf("g-1").value = Result.success(stokvel)
        advanceUntilIdle()
        assertEquals(stokvel, viewModel.state.value.selectedGroup)

        viewModel.loadGroup("g-2")
        advanceUntilIdle()
        detailOf("g-2").value = Result.success(other)
        advanceUntilIdle()
        assertEquals(other, viewModel.state.value.selectedGroup)

        detailOf("g-1").value = Result.success(stokvel.copy(name = "Late Arrival"))
        advanceUntilIdle()

        assertEquals(other, viewModel.state.value.selectedGroup)
    }

    @Test
    fun `leaving a group profile stops its detail collector`() = runTest {
        val viewModel = started()
        viewModel.loadGroup("g-1")
        advanceUntilIdle()
        detailOf("g-1").value = Result.success(stokvel)
        advanceUntilIdle()

        viewModel.setActive(false)
        detailOf("g-1").value = Result.success(stokvel.copy(name = "Late Arrival"))
        advanceUntilIdle()

        assertEquals(stokvel, viewModel.state.value.selectedGroup)
    }

    @Test
    fun `a group row that is not there is reported instead of left blank`() = runTest {
        val viewModel = started()
        viewModel.loadGroup("missing-group")
        detailOf("missing-group").value = Result.success(null)
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedGroup)
        assertNotNull(viewModel.state.value.detailError)
        assertEquals(false, viewModel.state.value.isLoadingDetail)
    }
}
