package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.MainDispatcherRule
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
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Discover Groups renders "No groups found" from an empty list, and the browse screen collects no
 * events, so a read the server refused used to be reported as a platform with no groups - and the
 * `error` field that did record it was read by nothing. The refusal has to land in the one place
 * the screen can render, while an answered empty list must stay distinguishable from it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupBrowseReadVerdictTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val stokvel = Group(id = "g-1", name = "Savings Circle", type = GroupType.STOKVEL, province = "Gauteng")
    private val groupsFlow = MutableStateFlow<Result<List<Group>>>(Result.success(emptyList()))
    private val getPublicGroups = mockk<GetPublicGroupsUseCase>()
    private val testDispatcher = mainDispatcherRule.dispatcher

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
        every { FirebaseCrashlytics.getInstance() } returns mockk<FirebaseCrashlytics>(relaxed = true)

        every { getPublicGroups() } returns groupsFlow
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        unmockkAll()
    }

    private fun started(): GroupViewModel {
        val viewModel = mainDispatcherRule.track(GroupViewModel(FilterGroupsUseCase(), getPublicGroups, mockk<ObserveGroupDetailUseCase>(relaxed = true)))
        // Entering the discovery screen is what starts the read; the view model itself reads nothing.
        viewModel.setActive(true)
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `a refused group read is recorded where the screen can render it`() = runTest {
        groupsFlow.value = Result.failure(IOException("IO Error: HTTP request to rest/v1/groups failed"))
        val viewModel = started()

        assertNotNull(viewModel.state.value.error)
        assertEquals(emptyList<Group>(), viewModel.state.value.rawGroups)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `an answered empty list does not keep a previous refusal`() = runTest {
        groupsFlow.value = Result.failure(IOException("IO Error: HTTP request to rest/v1/groups failed"))
        val viewModel = started()
        assertNotNull(viewModel.state.value.error)

        groupsFlow.value = Result.success(emptyList())
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(emptyList<Group>(), viewModel.state.value.filteredGroups)
    }

    @Test
    fun `a refusal after an answered list keeps the groups that answered`() = runTest {
        groupsFlow.value = Result.success(listOf(stokvel))
        val viewModel = started()
        assertEquals(listOf(stokvel), viewModel.state.value.rawGroups)

        groupsFlow.value = Result.failure(IOException("IO Error: HTTP request to rest/v1/groups failed"))
        advanceUntilIdle()

        assertEquals(listOf(stokvel), viewModel.state.value.rawGroups)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun `a successful reload clears the refusal`() = runTest {
        groupsFlow.value = Result.failure(IOException("IO Error: HTTP request to rest/v1/groups failed"))
        val viewModel = started()
        assertNotNull(viewModel.state.value.error)

        groupsFlow.value = Result.success(listOf(stokvel))
        viewModel.loadGroups()
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(listOf(stokvel), viewModel.state.value.filteredGroups)
    }
}
