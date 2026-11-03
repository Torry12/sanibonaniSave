package com.sanibonani.save.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.gateway.BankBackendSimulationModule
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.usecase.group.GetPublicGroupsUseCase
import com.sanibonani.save.domain.usecase.member.ObserveGroupMembersUseCase
import com.sanibonani.save.features.financial.presentation.PaymentSandboxViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
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
 * The sandbox has two reads - the group list and the selected group's roster - and both had an
 * onSuccess leg only. The screen renders `state.error` as a modal, so a refused read left the
 * operator with no answer at all, and a refused roster read left the *previous* group's members in
 * the state that every simulate method books contributions against.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentSandboxReadPathTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val getPublicGroups = mockk<GetPublicGroupsUseCase>()
    private val observeGroupMembers = mockk<ObserveGroupMembersUseCase>()
    private val bankSimulation = mockk<BankBackendSimulationModule>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { getPublicGroups() } returns flowOf(Result.success(listOf(Group(id = "g-1", name = "First"))))
        every { observeGroupMembers(any()) } returns flowOf(Result.success(listOf(Member(fullName = "Thandi"))))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() =
        PaymentSandboxViewModel(getPublicGroups, observeGroupMembers, bankSimulation)

    @Test
    fun `a group list the read refuses reaches the dialog instead of vanishing`() = runTest {
        every { getPublicGroups() } returns flowOf(Result.failure(IOException("groups read failed")))

        val viewModel = newViewModel()
        advanceUntilIdle()

        val error = viewModel.state.value.error
        assertTrue("expected the failed group read to surface, got: $error", !error.isNullOrBlank())
        assertTrue(viewModel.state.value.groups.isEmpty())
    }

    @Test
    fun `a group list that answers fills the selector`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(listOf("g-1"), viewModel.state.value.groups.map { it.id })
        assertEquals("g-1", viewModel.state.value.selectedGroupId)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a member read that fails cannot leave the previous group's roster`() = runTest {
        every { observeGroupMembers("g-2") } returns flowOf(Result.failure(IOException("members read failed")))
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(listOf("Thandi"), viewModel.state.value.selectedGroupMembers.map { it.fullName })

        viewModel.selectGroup("g-2")
        advanceUntilIdle()

        assertTrue(
            "g-1's roster must not be booked as g-2's",
            viewModel.state.value.selectedGroupMembers.isEmpty()
        )
        assertTrue(
            "expected the failed member read to surface, got: ${viewModel.state.value.error}",
            !viewModel.state.value.error.isNullOrBlank()
        )
    }

    @Test
    fun `a member read that answers fills the selected group`() = runTest {
        every { observeGroupMembers("g-2") } returns
            flowOf(Result.success(listOf(Member(fullName = "Sipho"), Member(fullName = "Lerato"))))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.selectGroup("g-2")
        advanceUntilIdle()

        assertEquals(listOf("Sipho", "Lerato"), viewModel.state.value.selectedGroupMembers.map { it.fullName })
        assertNull(viewModel.state.value.error)
    }

    // The simulate buttons read as if they had run: with nothing selected, and with a selection that
    // no longer exists in the list, each method returned before it touched the state, so the operator
    // got neither a transaction nor a reason.
    @Test
    fun `simulating a transfer with no group selected says why`() = runTest {
        every { getPublicGroups() } returns flowOf(Result.success(emptyList()))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.simulateBankTransfer("100", "REF-1")
        advanceUntilIdle()

        assertTrue(
            "the tap has to be answered: ${viewModel.state.value.error}",
            viewModel.state.value.error?.contains("group") == true
        )
        coVerify(exactly = 0) { bankSimulation.simulateSinglePayment(any(), any(), any()) }
    }

    @Test
    fun `simulating collections with no group selected says why`() = runTest {
        every { getPublicGroups() } returns flowOf(Result.success(emptyList()))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.simulateMonthlyCollections()
        advanceUntilIdle()

        assertTrue(
            "the tap has to be answered: ${viewModel.state.value.error}",
            viewModel.state.value.error?.contains("group") == true
        )
        coVerify(exactly = 0) { bankSimulation.simulateMonthOfActivity(any(), any(), any()) }
    }

    @Test
    fun `a selection that has left the group list is reported instead of dropped`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.selectGroup("g-9")
        advanceUntilIdle()

        viewModel.simulateDeepHistoricalRun(months = 2)
        advanceUntilIdle()

        assertTrue(
            "a selection nothing can bill has to be named: ${viewModel.state.value.error}",
            viewModel.state.value.error?.contains("group") == true
        )
        coVerify(exactly = 0) { bankSimulation.simulateMonthOfActivity(any(), any(), any()) }
    }
}
