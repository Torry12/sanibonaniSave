package com.sanibonani.save.features.common.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.PillarRefreshRequestedEvent
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.DomainPillar
import com.sanibonani.save.domain.repository.system.SyncRepository
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.service.NetworkConnectivityService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for ViewModels that manage a single UI state and one-time events.
 */
abstract class BaseViewModel<S>(protected val initialState: S) : ViewModel() {

    protected val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    protected val _events = Channel<UIEvent>(Channel.BUFFERED)
    val events: Flow<UIEvent> = _events.receiveAsFlow()

    private val jobRegistry = ConcurrentHashMap<String, Job>()

    /**
     * Executes a job and cancels any previous job with the same key.
     */
    protected fun launchJob(key: String, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Job {
        cancelJob(key)
        val job = viewModelScope.launch(block = block)
        jobRegistry[key] = job
        return job
    }

    /**
     * Executes a job with error handling and cancels any previous job with the same key.
     */
    protected fun launchSafeJob(
        key: String,
        errorPrefix: String? = null,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Main,
        block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
    ): Job {
        cancelJob(key)
        val job = viewModelScope.launch(dispatcher) {
            try {
                block()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                handleError(e, errorPrefix?.let { "$it: ${e.message}" })
            }
        }
        jobRegistry[key] = job
        return job
    }

    /**
     * Cancels the job associated with the given key.
     */
    protected fun cancelJob(key: String) {
        jobRegistry[key]?.cancel()
        jobRegistry.remove(key)
    }

    override fun onCleared() {
        super.onCleared()
        jobRegistry.values.forEach { it.cancel() }
        jobRegistry.clear()
    }

    /**
     * Standard Network Observation
     * Call this in init to keep isSystemOnline and isAirplaneMode updated in state.
     * State S must have these fields.
     */
    protected fun observeNetwork(
        networkService: NetworkConnectivityService,
        update: (S, Boolean, Boolean) -> S
    ): Job {
        return launchJob("base_network_observation") {
            combine(
                networkService.isOnline,
                networkService.isAirplaneModeActive
            ) { online, airplane -> online to airplane }
                .collect { (online, airplane) ->
                    updateState { update(it, online, airplane) }
                }
        }
    }

    /**
     * Shared Group Context Observation
     * Reduces boilerplate across ViewModels by unifying the flatMapLatest + distinctUntilChanged pattern.
     */
    protected fun <T> observeGroupContext(
        groupIdFlow: Flow<String?>,
        contextSource: Flow<Map<String, T>>,
        onContextUpdated: (T?) -> Unit
    ): Job {
        return viewModelScope.launch {
            groupIdFlow
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) flowOf(null)
                    else contextSource.map { it[id] }
                }
                .distinctUntilChanged()
                .collect { onContextUpdated(it) }
        }
    }

    /**
     * Shared Support Mode Guard
     * Ensures that sensitive background updates are ignored if they conflict with an active impersonation.
     *
     * @param targetMemberId The specific member we are currently impersonating.
     * @param contextMemberId The member ID associated with the data currently being received.
     */
    protected fun shouldIgnoreUpdate(
        isImpersonating: Boolean,
        impersonatorId: String?,
        contextUserId: String?,
        targetMemberId: String? = null,
        contextMemberId: String? = null
    ): Boolean {
        if (isImpersonating && contextUserId == impersonatorId) {
            // 🛡️ Privacy Guard: If the data belongs to the admin, we only allow it if it
            // specifically matches the member record the admin is currently supporting.
            if (targetMemberId != null && contextMemberId != null && targetMemberId == contextMemberId) {
                AppLogger.d("BaseVM", "Allowing self-impersonation update for admin member $targetMemberId")
                return false
            }
            AppLogger.d("BaseVM", "Ignoring background update for admin's own record while support mode is active.")
            return true
        }
        return false
    }

    /**
     * Updates the UI state atomically.
     */
    protected fun updateState(reducer: (S) -> S) {
        _uiState.update(reducer)
    }

    /**
     * Resets the UI state to its initial value and cancels all active jobs.
     */
    protected fun resetState() {
        jobRegistry.values.forEach { it.cancel() }
        jobRegistry.clear()
        _uiState.value = initialState
    }

    /**
     * Sends a one-time event to the UI.
     */
    protected fun sendEvent(event: UIEvent) {
        viewModelScope.launch {
            _events.send(event)
        }
    }

    /**
     * Reports an error via GlobalErrorViewModel (if needed) or directly to the UI.
     */
    open fun handleError(throwable: Throwable, userMessage: String? = null) {
        // 🛡️ Hygiene Guard: Never report coroutine cancellations as errors to the user.
        if (throwable is kotlinx.coroutines.CancellationException) return

        val tag = this::class.simpleName ?: "BaseViewModel"
        val message = userMessage ?: throwable.toUserMessage()
        AppLogger.e(tag, "UI Error: $message", throwable)
        sendEvent(UIEvent.ShowError(message))
    }

    protected fun showMessage(message: String) {
        sendEvent(UIEvent.ShowMessage(message))
    }

    /**
     * Standardized Domain Pillar Refresh
     * Emits a global event to trigger background re-validation of a specific domain's data.
     */
    protected fun syncRefresh(pillar: DomainPillar, groupId: String? = null) {
        viewModelScope.launch {
            EventBus.emit(PillarRefreshRequestedEvent(pillar, groupId))
        }
    }

    /**
     * Standardized Outbox Status Observation
     */
    protected fun observeOutboxStatus(syncRepo: SyncRepository): Flow<Int> {
        return syncRepo.outboxCount
    }

    /**
     * Executes a block and updates a specific AsyncResult field in the state.
     */
    protected fun <T> runAsync(
        update: (S, AsyncResult<T>) -> S,
        block: suspend () -> Result<T>
    ): Job = viewModelScope.launch {
        updateState { state -> update(state, AsyncResult.Loading) }
        block().fold(
            onSuccess = { data -> updateState { state -> update(state, AsyncResult.Success(data)) } },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                val msg = error.toUserMessage()
                updateState { state -> update(state, AsyncResult.Error(msg, error.javaClass.simpleName)) }
            }
        )
    }

    /**
     * Executes a one-way synchronization from network to cache.
     */
    protected fun startLoading() {
        LoadingManager.start()
    }

    protected fun stopLoading() {
        LoadingManager.stop()
    }

    /**
     * Executes a block of code and shows the global loading spinner if it takes too long.
     */
    protected suspend fun <T> withLoading(block: suspend () -> T): T {
        startLoading()
        return try {
            block()
        } finally {
            stopLoading()
        }
    }

    /**
     * Collects a Result-based Flow and updates the state accordingly.
     * Reduces boilerplate for simple observer patterns.
     * Automatically handles isLoading if provided.
     */
    protected fun <T> Flow<Result<T>>.observeInto(
        onLoading: ((Boolean) -> Unit)? = null,
        onError: (Throwable) -> Unit = { handleError(it) },
        onSuccess: (T) -> Unit
    ): Job {
        onLoading?.invoke(true)
        return onEach { result ->
            onLoading?.invoke(false)
            result.fold(onSuccess, onError)
        }.launchIn(viewModelScope)
    }

    /**
     * Launch a coroutine with built-in error handling and logging.
     */
    protected fun launchSafe(
        errorPrefix: String? = null,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Main,
        block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
    ) = viewModelScope.launch(dispatcher) {
        try {
            block()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            handleError(e, errorPrefix?.let { "$it: ${e.message}" })
        }
    }

    /**
     * Executes a Result-producing block with standardized loading and error handling.
     */
    protected fun <T> runSafe(
        onLoading: ((Boolean) -> Unit)? = null,
        onError: (Throwable) -> Unit = { handleError(it) },
        onSuccess: (T) -> Unit = {},
        block: suspend () -> Result<T>
    ): Job = viewModelScope.launch {
        onLoading?.invoke(true)
        block().fold(
            onSuccess = {
                onLoading?.invoke(false)
                onSuccess(it)
            },
            onFailure = {
                onLoading?.invoke(false)
                if (it is kotlinx.coroutines.CancellationException) throw it
                onError(it)
            }
        )
    }
}
