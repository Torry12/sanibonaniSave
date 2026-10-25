package com.sanibonani.save.features.admin.presentation.coordination

import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.usecase.system.SendNotificationUseCase
import javax.inject.Inject

class AdminMessagingCoordinator @Inject constructor(
    private val sendNotificationUseCase: SendNotificationUseCase
) {

    fun updateText(host: AdminActionHost, text: String) {
        host.updateAdminState { it.copy(messageText = text) }
    }

    fun selectChannel(host: AdminActionHost, channel: NotifChannel) {
        host.updateAdminState { it.copy(selectedNotifChannel = channel) }
    }

    fun broadcast(host: AdminActionHost) {
        val groupId = host.adminState.value.group?.id ?: return
        val message = host.adminState.value.messageText
        val channel = host.adminState.value.selectedNotifChannel
        if (message.isBlank()) return

        host.launchAdminSafeJob("broadcast_message", "Failed to send message") {
            host.updateAdminState { it.copy(isSendingMessage = true, messageSentSuccess = false) }
            host.runWithLoading {
                sendNotificationUseCase(groupId, message, memberId = null, channel = channel)
                    .onSuccess {
                        host.updateAdminState {
                            it.copy(isSendingMessage = false, messageSentSuccess = true, messageText = "")
                        }
                    }
                    .onFailure { e ->
                        host.updateAdminState { it.copy(isSendingMessage = false) }
                        host.reportError(e)
                    }
            }
        }
    }
}
