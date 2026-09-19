package com.sanibonani.save.features.common.ui

import androidx.compose.runtime.Composable
import com.sanibonani.save.domain.model.AsyncResult

/**
 * Standardized handler for AsyncResult states in the UI.
 * Reduces boilerplate in screens by providing a unified way to handle loading, error, and success states.
 */
@Composable
fun <T> AsyncResultHandler(
    result: AsyncResult<T>,
    onLoading: @Composable () -> Unit = {},
    onError: @Composable (String) -> Unit = { message -> InfoBox(message, InfoType.ERROR) },
    onSuccess: @Composable (T) -> Unit
) {
    when (result) {
        is AsyncResult.Loading -> onLoading()
        is AsyncResult.Error -> onError(result.message)
        is AsyncResult.Success -> onSuccess(result.data)
        is AsyncResult.Uninitialized -> { /* Do nothing or handle as needed */ }
    }
}
