package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.usecase.SendNotificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminMessagingViewModel @Inject constructor(
    private val notifRepo: NotificationRepository,
    private val sendNotificationUseCase: SendNotificationUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AdminMessagingUiState())
    val state: StateFlow<AdminMessagingUiState> = _state.asStateFlow()

    private val selectedGroupId = MutableStateFlow<String?>(null)
    private var observationJob: Job? = null

    fun setGroupId(groupId: String?) {
        if (selectedGroupId.value == groupId) return
        selectedGroupId.value = groupId
        if (groupId != null) {
            startObserving(groupId)
        } else {
            observationJob?.cancel()
            _state.update { AdminMessagingUiState() }
        }
    }

    private fun startObserving(groupId: String) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            notifRepo.observeNotifications(groupId).collect { res ->
                res.onSuccess { list ->
                    val (messages, system) = list.filter { 
                        it.memberId == null || it.triggerEvent == NotifEvent.MEMBER_MESSAGE 
                    }.partition { it.triggerEvent == NotifEvent.MEMBER_MESSAGE }
                    
                    _state.update { it.copy(
                        notifications = system.sortedByDescending { n -> n.id ?: "" },
                        memberMessages = messages.sortedByDescending { m -> m.id ?: "" }
                    ) }
                }
            }
        }
    }

    fun sendMessage(text: String, memberId: String? = null) {
        val groupId = selectedGroupId.value ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            sendNotificationUseCase(
                groupId = groupId,
                memberId = memberId,
                message = text,
                event = if (memberId == null) NotifEvent.CUSTOM else NotifEvent.ADMIN_DIRECT_MESSAGE
            ).onSuccess {
                _state.update { it.copy(isSending = false, sendSuccess = true) }
            }.onFailure { e ->
                _state.update { it.copy(isSending = false, error = e.toUserMessage()) }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(sendSuccess = false, error = null) }
    }
}

data class AdminMessagingUiState(
    val notifications: List<AppNotification> = emptyList(),
    val memberMessages: List<AppNotification> = emptyList(),
    val isSending: Boolean = false,
    val sendSuccess: Boolean = false,
    val error: String? = null
)
