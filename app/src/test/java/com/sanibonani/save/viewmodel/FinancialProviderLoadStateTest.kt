package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.MainDispatcherRule
import com.sanibonani.save.domain.model.FinancialProvider
import com.sanibonani.save.domain.model.FinancialProviderType
import com.sanibonani.save.domain.usecase.payment.ObserveFinancialProvidersUseCase
import com.sanibonani.save.features.financial.presentation.FinancialProviderViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The Settings tab renders the bank list straight from `providers`, so a read the server refused
 * left the section exactly as empty as a platform with no bank providers configured. An admin
 * reading that sees "nothing to link" and starts none, while the real fault is an unread stream.
 * A refusal has to be its own answer, and an answered empty list must stay a refusal-free one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinancialProviderLoadStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val bankProvider = FinancialProvider(id = "p-1", name = "Capitec Business", type = FinancialProviderType.BANK)
    private val providersFlow = MutableStateFlow<Result<List<FinancialProvider>>>(Result.success(emptyList()))
    private val observeProviders = mockk<ObserveFinancialProvidersUseCase>()
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

        every { observeProviders() } returns providersFlow
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        unmockkAll()
    }

    private fun started(): FinancialProviderViewModel {
        val viewModel = mainDispatcherRule.track(FinancialProviderViewModel(observeProviders))
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `a refused read is recorded as a refusal and stops the spinner`() {
        providersFlow.value = Result.failure(IOException("providers read refused"))
        val viewModel = started()

        assertNotNull(viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `an answered empty list is not a refusal`() {
        val viewModel = started()

        assertNull(viewModel.state.value.error)
        assertEquals(emptyList<FinancialProvider>(), viewModel.state.value.providers)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `a refusal does not discard providers that already answered`() {
        providersFlow.value = Result.success(listOf(bankProvider))
        val viewModel = started()
        assertEquals(listOf(bankProvider), viewModel.state.value.providers)

        providersFlow.value = Result.failure(IOException("providers read refused"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(bankProvider), viewModel.state.value.providers)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun `an answer that arrives later clears the refusal`() {
        providersFlow.value = Result.failure(IOException("providers read refused"))
        val viewModel = started()
        assertNotNull(viewModel.state.value.error)

        providersFlow.value = Result.success(listOf(bankProvider))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(listOf(bankProvider), viewModel.state.value.providers)
    }

    @Test
    fun `reloading a refused read clears the stale refusal before the new answer`() {
        providersFlow.value = Result.failure(IOException("providers read refused"))
        val viewModel = started()
        assertNotNull(viewModel.state.value.error)

        providersFlow.value = Result.success(listOf(bankProvider))
        viewModel.loadProviders()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
    }
}
