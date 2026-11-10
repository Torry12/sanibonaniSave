package com.sanibonani.save.viewmodel

import com.sanibonani.save.MainDispatcherRule
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.usecase.auth.RequestPasswordResetUseCase
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.auth.presentation.PasswordRecoveryViewModel
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordRecoveryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var supabaseRepository: SupabaseRepository

    @Before
    fun setUp() {
        supabaseRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun viewModel() = mainDispatcherRule.track(PasswordRecoveryViewModel(
        requestPasswordReset = RequestPasswordResetUseCase(supabaseRepository)
    ))

    // PasswordRecoveryScreen.kt:97-100 renders state.error and the screen collects no events, so
    // a refused reset request stopped the spinner and said nothing at all.
    @Test
    fun `a refused reset request puts the cause on the state the screen renders`() = runTest {
        val failure = IOException("HTTP 503 Service Unavailable")
        coEvery { supabaseRepository.sendPasswordResetEmail("member@example.com") } returns
            Result.failure(failure)
        val viewModel = viewModel()
        viewModel.updateInput("member@example.com")

        viewModel.sendRecovery()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse("a refused request cannot claim the mail was sent", state.success)
        assertEquals(failure.toUserMessage(), state.error)
    }

    @Test
    fun `a successful reset request reports success and clears the input`() = runTest {
        coEvery { supabaseRepository.sendPasswordResetEmail("member@example.com") } returns
            Result.success(Unit)
        val viewModel = viewModel()
        viewModel.updateInput("member@example.com")

        viewModel.sendRecovery()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(true, state.success)
        assertEquals("", state.input)
    }
}
