package com.sanibonani.save.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.concurrent.atomic.AtomicLong

/**
 * A simple debouncer to prevent rapid multiple clicks from firing multiple events.
 */
class ClickDebouncer(private val delayMillis: Long = 1000L) {
    private val lastClickTime = AtomicLong(0L)

    fun processClick(onClick: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastClickTime.get()
        if (currentTime - lastTime > delayMillis) {
            if (lastClickTime.compareAndSet(lastTime, currentTime)) {
                onClick()
            }
        }
    }
}

/**
 * rembers a ClickDebouncer in the composition.
 */
@Composable
fun rememberClickDebouncer(delayMillis: Long = 1000L): ClickDebouncer {
    return remember { ClickDebouncer(delayMillis) }
}
