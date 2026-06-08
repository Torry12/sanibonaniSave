package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.NotificationEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.remote.PostgrestColumns
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.data.remote.EdgeFunctionGateway
import com.sanibonani.save.domain.repository.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import javax.inject.Inject
import java.text.NumberFormat
import java.util.Locale

// Added imports for friendly error mapping / logging
import com.sanibonani.save.data.utils.logAndGetMessage

class NotificationRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val edgeFunctionGateway: EdgeFunctionGateway,
    private val db: SanibonaniDatabase
) : BaseRepository("NotificationRepository"), NotificationRepository {

    override fun observeNotifications(groupId: String): Flow<Result<List<AppNotification>>> = this.observeAndSync(
        dbFlow = db.notificationDao().observeNotifications(groupId),
        mapper = { entity: NotificationEntity -> entity.toModel() },
        toEntity = { model: AppNotification -> model.toEntity() },
        networkFetch = {
            supabase.postgrest["notifications"].select {
                filter { eq("group_id", groupId) }
            }.decodeList<AppNotification>()
        },
        cacheSync = { list: List<NotificationEntity> -> db.notificationDao().syncNotifications(groupId, list) }
    )

    override suspend fun syncNotifications(groupId: String): Result<Unit> = runCatching {
        val remoteNotifs = supabase.postgrest["notifications"].select {
            filter { eq("group_id", groupId) }
        }.decodeList<AppNotification>()

        db.notificationDao().syncNotifications(groupId, remoteNotifs.map { it.toEntity() })
    }

    override suspend fun sendNotification(notification: AppNotification): Result<Unit> = runCatching {
        val notificationId = notification.id
        if (!notificationId.isNullOrBlank()) {
            supabase.postgrest["notifications"].select {
                filter { eq("id", notificationId) }
            }.decodeSingleOrNull<AppNotification>()?.let { existing ->
                db.notificationDao().upsertNotifications(listOf(existing.toEntity()))
                    return@runCatching
            }
        }

        val insertData = buildJsonObject {
            notificationId?.let { put("id", it) }
            put("group_id", notification.groupId)
            notification.memberId?.let { put("member_id", it) }
            put("message", notification.message)
            put("channel", notification.channel.name.lowercase())
            put("trigger_event", notification.triggerEvent.name.lowercase())
        }

        try {
            val serverNotification = if (notificationId.isNullOrBlank()) {
                supabase.postgrest["notifications"].insert(insertData) {
                    select()
                }.decodeSingle<AppNotification>()
            } else {
                supabase.postgrest["notifications"].upsert(insertData) {
                    onConflict = "id"
                    select()
                }.decodeSingle<AppNotification>()
            }
            db.notificationDao().upsertNotifications(listOf(serverNotification.toEntity()))
        } catch (e: Exception) {
            // Log the error and surface a user-friendly message when appropriate.
            val userMsg = e.logAndGetMessage("NotificationRepo")
            AppLogger.e("NotificationRepo", "Postgrest insert failed: $userMsg")
            if (notification.channel == NotifChannel.EMAIL) throw IllegalStateException(userMsg)
            // For other channels, proceed with best-effort WhatsApp delivery; DB entry may be missing.
        }

        if (notification.channel == NotifChannel.WHATSAPP || notification.channel == NotifChannel.BOTH) {
            val memberId = notification.memberId
            if (routesToGroupAdmin(notification.triggerEvent)) {
                // Member inquiry, loan request, or payout request: send WhatsApp to group admin.
                val group = supabase.postgrest["groups"].select(columns = Columns.list("admin_user_id")) {
                    filter { eq("id", notification.groupId) }
                }.decodeSingle<Group>()

                val adminUserId = group.adminUserId
                if (!adminUserId.isNullOrBlank()) {
                    val admin = supabase.postgrest["members"].select(columns = Columns.list("phone")) {
                        filter {
                            eq("group_id", notification.groupId)
                            eq("user_id", adminUserId)
                        }
                    }.decodeSingleOrNull<Member>()

                    admin?.phone?.takeIf { it.isNotBlank() }?.let { sendWhatsAppViaEdge(it, notification.message) }
                }
            } else if (memberId != null) {
                // Direct notification to a specific member
                val member = supabase.postgrest["members"].select(columns = Columns.list("phone")) {
                    filter { eq("id", memberId) }
                }.decodeSingleOrNull<Member>()

                member?.phone?.takeIf { it.isNotBlank() }?.let { sendWhatsAppViaEdge(it, notification.message) }
            } else {
                // Broadcast to all members in the group
                val members = supabase.postgrest["members"].select(columns = Columns.list("phone", "notification_pref")) {
                    filter { eq("group_id", notification.groupId) }
                }.decodeList<Member>()

                supervisorScope {
                    members.filter {
                                it.phone.isNotBlank() &&
                        (it.notificationPref == NotificationPref.WHATSAPP || it.notificationPref == NotificationPref.BOTH)
                    }.forEach { m ->
                        launch(Dispatchers.IO) {
                            try {
                                sendWhatsAppViaEdge(m.phone, notification.message)
                            } catch (e: Exception) {
                                val userMsg = e.logAndGetMessage("NotificationRepo")
                                AppLogger.e("NotificationRepo", "Failed broadcast to ${m.phone}: $userMsg")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Sends a WhatsApp message by proxying through the [send-whatsapp] Supabase Edge Function.
     * The WHATSAPP_TOKEN never leaves the server — it is stored as a Supabase secret.
     */
    internal suspend fun sendWhatsAppViaEdge(phone: String, message: String) {
        val templatePayload = buildJsonObject {
            put("to", phone)
            put("message", message)
            put("template", buildJsonObject {
                put("name", "general_notification")
                put("language", buildJsonObject { put("code", "en_US") })
                putJsonArray("components") {
                    addJsonObject {
                        put("type", "body")
                        putJsonArray("parameters") {
                            addJsonObject {
                                put("type", "text")
                                put("text", message)
                            }
                        }
                    }
                }
            })
        }

        val textPayload = buildJsonObject {
            put("to", phone)
            put("message", message)
        }

        val templateResult = edgeFunctionGateway.invoke("send-whatsapp", templatePayload)
        if (templateResult.isSuccess) return

        val templateError = templateResult.exceptionOrNull()
        // If the error is not a template-delivery issue, surface a friendly message.
        if (templateError == null || !isTemplateDeliveryFailure(templateError)) {
            val message = templateError?.logAndGetMessage("NotificationRepo")
                ?: "Failed to send WhatsApp template message."
            throw IllegalStateException(message)
        }

        // Template failed (e.g., unapproved template) — retry as plain text and log the template failure.
        val templateMsg = templateError.logAndGetMessage("NotificationRepo")
        AppLogger.w("NotificationRepo", "Template delivery failed for $phone. Retrying as plain text: $templateMsg")

        val textResult = edgeFunctionGateway.invoke("send-whatsapp", textPayload)
        if (textResult.isFailure) {
            val textError = textResult.exceptionOrNull()
            val message = textError?.logAndGetMessage("NotificationRepo")
                ?: "Failed to send WhatsApp text-fallback message."
            throw IllegalStateException(message)
        }
    }

    private fun isTemplateDeliveryFailure(error: Throwable): Boolean {
        val message = error.message?.lowercase() ?: return false
        return message.contains("template") ||
            message.contains("parameter") ||
            message.contains("language") ||
            message.contains("not found") ||
            message.contains("unapproved") ||
            message.contains("rejected")
    }

    internal fun routesToGroupAdmin(event: NotifEvent): Boolean {
        return event == NotifEvent.MEMBER_MESSAGE ||
            event == NotifEvent.LOAN_REQUESTED ||
            event == NotifEvent.PAYOUT_REQUESTED
    }

    override suspend fun sendFeeEnforcementNotification(
        groupId: String,
        event: NotifEvent,
        memberCount: Int,
        amountDue: Double
    ): Result<Unit> = runCatching {
        val formattedAmount = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(amountDue)
        val message = when (event) {
            NotifEvent.PLATFORM_FEE_DUE -> "Monthly platform fee of $formattedAmount for $memberCount members is now due."
            NotifEvent.PLATFORM_FEE_WARNING -> "URGENT: Platform fee of $formattedAmount is overdue. 48h until suspension."
            NotifEvent.GROUP_SUSPENDED -> "Group SUSPENDED due to non-payment. Locked until $formattedAmount is cleared."
            else -> "Platform fee update: $event"
        }

        val group = supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)) {
            filter { eq("id", groupId) }
        }.decodeSingle<Group>()
        val adminUserId = group.adminUserId
        val admin = if (adminUserId != null) {
            supabase.postgrest["members"].select {
                filter { eq("user_id", adminUserId) }
            }.decodeSingleOrNull<Member>()
        } else null

        sendNotification(
            AppNotification(
                groupId = groupId,
                memberId = admin?.id,
                message = message,
                triggerEvent = event,
                channel = NotifChannel.BOTH
            )
        ).getOrThrow()

        // PLATFORM_FEE_DUE has an additional dedicated template, sent best-effort.
        if (event == NotifEvent.PLATFORM_FEE_DUE && admin != null && admin.phone.isNotBlank()) {
            val phone = admin.phone
            val feeDuePayload = buildJsonObject {
                put("to", phone)
                put("template", buildJsonObject {
                    put("name", "platform_fee_due")
                    put("language", buildJsonObject { put("code", "en_US") })
                    putJsonArray("components") {
                        addJsonObject {
                            put("type", "body")
                            putJsonArray("parameters") {
                                addJsonObject { put("type", "text"); put("text", "*$formattedAmount*") }
                                addJsonObject { put("type", "text"); put("text", memberCount.toString()) }
                            }
                        }
                    }
                })
            }
            try {
                edgeFunctionGateway.invoke("send-whatsapp", feeDuePayload).getOrThrow()
            } catch (e: Exception) {
                AppLogger.e("NotificationRepository", "Failed to send platform_fee_due template", e)
            }
        }
    }

    override suspend fun notifyPlatformAdmin(message: String): Result<Unit> = runCatching {
        val platformAdmins = supabase.postgrest["profiles"].select {
            filter { eq("role", "platform_admin") }
        }.decodeList<UserProfile>()

        platformAdmins.forEach { _ ->
            supabase.postgrest["notifications"].insert(AppNotification(
                message = message,
                triggerEvent = NotifEvent.CUSTOM,
                memberId = null,
                groupId = "platform"
            ))
        }
    }

    override suspend fun sendDirectWhatsAppMessage(phone: String, message: String): Result<Unit> = runCatching {
        require(phone.isNotBlank()) { "Please enter a WhatsApp number." }
        require(message.isNotBlank()) { "Please enter a message to send." }
        sendWhatsAppViaEdge(phone = phone, message = message)
    }

    override suspend fun sendPasswordResetWhatsApp(phone: String): Result<Unit> = runCatching {
        val resetPayload = buildJsonObject {
            put("to", phone)
            put("template", buildJsonObject {
                put("name", "password_reset")
                put("language", buildJsonObject { put("code", "en_US") })
            })
        }
        edgeFunctionGateway.invoke(
            functionName = "send-whatsapp",
            payload = resetPayload,
            requireAuth = false
        ).getOrThrow()
    }
}
