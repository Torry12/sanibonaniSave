package com.sanibonani.save.features.member.presentation.coordination

import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.usecase.system.SendNotificationUseCase
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

class MemberMessagingCoordinator @Inject constructor(
    private val sendNotification: SendNotificationUseCase
) {

    fun updateText(host: MemberActionHost, text: String) {
        host.updateMemberState { it.copy(messageText = text) }
    }

    fun sendToAdmin(host: MemberActionHost) {
        val s = host.memberState.value
        val msg = s.messageText
        if (msg.isBlank()) return
        val groupId = s.selectedGroupId ?: return

        host.launchMemberJob("sending_message") {
            host.updateMemberState { it.copy(isSendingMessage = true) }
            sendNotification(
                groupId = groupId,
                message = msg,
                memberId = s.member?.id,
                channel = NotifChannel.PUSH,
                triggerEvent = NotifEvent.MEMBER_MESSAGE
            ).onSuccess {
                host.updateMemberState {
                    it.copy(isSendingMessage = false, messageSentSuccess = true, messageText = "")
                }
                delay(2000.milliseconds)
                host.updateMemberState { it.copy(messageSentSuccess = false) }
            }.onFailure {
                host.reportError(it)
            }
        }
    }
}
