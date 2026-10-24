package com.sanibonani.save.domain.model

/**
 * A standard wrapper for asynchronous data states.
 * Replaces boolean flags like 'isLoading' and 'error' with an explicit type hierarchy.
 */
sealed interface AsyncResult<out T> {
    object Uninitialized : AsyncResult<Nothing>
    object Loading : AsyncResult<Nothing>
    data class Success<T>(val data: T) : AsyncResult<T>
    data class Error(
        val message: String,
        val errorType: String? = null,
        val throwable: Throwable? = null
    ) : AsyncResult<Nothing>

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): Throwable? = (this as? Error)?.throwable
}

inline fun <T> AsyncResult<T>.onSuccess(action: (T) -> Unit): AsyncResult<T> {
    if (this is AsyncResult.Success) action(data)
    return this
}

inline fun <T> AsyncResult<T>.onLoading(action: () -> Unit): AsyncResult<T> {
    if (this is AsyncResult.Loading) action()
    return this
}

inline fun <T> AsyncResult<T>.onError(action: (String, Throwable?) -> Unit): AsyncResult<T> {
    if (this is AsyncResult.Error) action(message, throwable)
    return this
}
