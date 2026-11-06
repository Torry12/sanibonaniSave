package com.sanibonani.save.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.MainDispatcherRule
import com.sanibonani.save.features.common.presentation.BaseViewModel
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The shared group-context collector has to be single-flight per ViewModel: every live collector
 * re-subscribes whatever flows its callback starts, so a screen that observes the context again
 * without stopping the first observer ends up with two readers that never end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelGroupContextTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @After
    fun tearDown() {
        unmockkAll()
    }

    private class ContextProbe : BaseViewModel<String>("") {
        val selectedGroupId = MutableStateFlow<String?>(null)
        val contexts = MutableStateFlow<Map<String, String>>(emptyMap())
        val deliveries = mutableListOf<String?>()

        fun watch() = observeGroupContext(
            groupIdFlow = selectedGroupId,
            contextSource = contexts
        ) { delivery -> deliveries.add(delivery) }

        fun stop() = cancelJob("group_context_observation")

        fun abandon() = viewModelScope.cancel()
    }

    @Test
    fun `observing the group context again replaces the previous collector`() = runTest {
        val probe = mainDispatcherRule.track(ContextProbe())
        try {
            probe.watch()
            probe.watch()
            advanceUntilIdle()

            probe.selectedGroupId.value = "g-1"
            probe.contexts.value = mapOf("g-1" to "the book")
            advanceUntilIdle()

            assertEquals(
                "one snapshot must reach one collector, not one per watch",
                1,
                probe.deliveries.count { it == "the book" }
            )

            probe.deliveries.clear()
            probe.contexts.value = mapOf("g-1" to "the book, swept again")
            advanceUntilIdle()

            assertEquals(listOf("the book, swept again"), probe.deliveries)
        } finally {
            probe.abandon()
        }
    }

    @Test
    fun `stopping the context observation by key ends the subscription`() = runTest {
        val probe = mainDispatcherRule.track(ContextProbe())
        try {
            probe.watch()
            probe.selectedGroupId.value = "g-1"
            probe.contexts.value = mapOf("g-1" to "the book")
            advanceUntilIdle()

            probe.stop()
            advanceUntilIdle()
            probe.deliveries.clear()

            probe.contexts.value = mapOf("g-1" to "the book, swept")
            advanceUntilIdle()

            assertEquals(emptyList<String?>(), probe.deliveries)
        } finally {
            probe.abandon()
        }
    }
}
