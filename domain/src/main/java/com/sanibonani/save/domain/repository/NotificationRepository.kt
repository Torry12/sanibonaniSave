package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.NotifEvent
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeNotifications(groupId: String): Flow<Result<List<AppNotification>>>
    suspend fun sendNotification(notification: AppNotification): Result<Unit>
    suspend fun sendDirectWhatsAppMessage(phone: String, message: String): Result<Unit>
    suspend fun sendFeeEnforcementNotification(
        groupId: String, 
        event: NotifEvent, 
        memberCount: Int, 
        amountDue: Double
    ): Result<Unit>
    suspend fun notifyPlatformAdmin(message: String): Result<Unit>
    suspend fun sendPasswordResetWhatsApp(phone: String): Result<Unit>
    suspend fun syncNotifications(groupId: String): Result<Unit>
}
