package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.partitionMemberNotifications
import com.sanibonani.save.data.utils.filterNotificationsForMember
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.SendNotificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberMessagingUiState(
    val notifications: List<AppNotification> = emptyList(),
    val messages: List<AppNotification> = emptyList(),
    val messageText: String = "",
    val isSendingMessage: Boolean = false,
    val messageSentSuccess: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MemberMessagingViewModel @Inject constructor(
    private val notificationRepo: NotificationRepository,
    private val sendNotificationUseCase: SendNotificationUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(MemberMessagingUiState())
    val state: StateFlow<MemberMessagingUiState> = _state.asStateFlow()

    private var observationJob: Job? = null
    private var currentGroupId: String? = null
    private var currentMemberId: String? = null

    fun setContext(groupId: String, memberId: String) {
        if (currentGroupId == groupId && currentMemberId == memberId) return
        currentGroupId = groupId
        currentMemberId = memberId
        startObserving(groupId, memberId)
    }

    private fun startObserving(groupId: String, memberId: String) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            notificationRepo.observeNotifications(groupId).collect { res ->
                res.onSuccess { allNotifs ->
                    val myNotifs = filterNotificationsForMember(allNotifs, memberId)
                    val (messages, systemNotifs) = partitionMemberNotifications(myNotifs)

                    _state.update { it.copy(
                        notifications = systemNotifs,
                        messages = messages,
                        isLoading = false
                    ) }
                }.onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
            }
        }
    }

    fun updateMessageText(text: String) {
        _state.update { it.copy(messageText = text) }
    }

    fun sendMessageToAdmin(groupId: String, memberId: String) {
        val text = _state.value.messageText
        if (text.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isSendingMessage = true, error = null) }
            sendNotificationUseCase(
                groupId = groupId,
                memberId = memberId,
                message = "MEMBER INQUIRY: $text",
                triggerEvent = NotifEvent.MEMBER_MESSAGE
            ).onSuccess {
                _state.update { it.copy(isSendingMessage = false, messageSentSuccess = true, messageText = "") }
            }.onFailure { e ->
                _state.update { it.copy(isSendingMessage = false, error = e.toUserMessage()) }
            }
        }
    }

    fun dismissMessageSuccess() {
        _state.update { it.copy(messageSentSuccess = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        observationJob?.cancel()
    }
}
