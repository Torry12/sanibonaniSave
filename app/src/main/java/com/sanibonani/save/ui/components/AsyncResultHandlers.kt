package com.sanibonani.save.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sanibonani.save.domain.model.AsyncResult

@Composable
fun <T> AsyncResultHandler(
    result: AsyncResult<T>,
    onLoading: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    },
    onUninitialized: @Composable () -> Unit = {},
    onError: @Composable (String, String?) -> Unit = { message, _ ->
        InfoBox(message, type = InfoType.ERROR)
    },
    onSuccess: @Composable (T) -> Unit
) {
    when (result) {
        is AsyncResult.Loading -> onLoading()
        is AsyncResult.Success -> onSuccess(result.data)
        is AsyncResult.Error -> onError(result.message, result.errorType)
        is AsyncResult.Uninitialized -> onUninitialized()
    }
}

