package com.sanibonani.save.viewmodel.state.admin

import com.sanibonani.save.domain.model.AppNotification

data class AdminMessagingState(
    val notifications: List<AppNotification> = emptyList(),
    val memberMessages: List<AppNotification> = emptyList(),
    val messageText: String = "",
    val isSendingMessage: Boolean = false,
    val messageSentSuccess: Boolean = false,
    val isSendingWhatsAppTest: Boolean = false,
    val whatsAppTestResult: String? = null
)
