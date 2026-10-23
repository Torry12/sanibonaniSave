package com.sanibonani.save.features.member.presentation.coordination

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.repository.system.NotificationRepository
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

class MemberMessagingCoordinator @Inject constructor(
    private val notifRepo: NotificationRepository
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
            val notification = AppNotification(
                groupId = groupId,
                memberId = s.member?.id,
                message = msg,
                triggerEvent = NotifEvent.MEMBER_MESSAGE
            )
            notifRepo.sendNotification(notification).onSuccess {
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
