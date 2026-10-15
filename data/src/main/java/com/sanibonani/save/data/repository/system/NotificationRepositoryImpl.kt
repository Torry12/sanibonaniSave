package com.sanibonani.save.data.repository.system

import com.sanibonani.save.data.repository.BaseRepository

import com.sanibonani.save.data.local.*
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.data.remote.PostgrestColumns
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.data.remote.EdgeFunctionGateway
import com.sanibonani.save.domain.repository.auth.*

import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.system.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import javax.inject.Inject
import java.util.Locale

// Added imports for friendly error mapping / logging
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.utils.safeRunCatching

class NotificationRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase,
    private val whatsAppChannel: WhatsAppChannel,
    private val router: NotificationRouter,
    private val taskQueue: NotificationTaskQueue,
    private val edgeFunctionGateway: EdgeFunctionGateway,
    private val payloadFactory: com.sanibonani.save.data.repository.mappers.DataPayloadFactory
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

    override suspend fun syncNotifications(groupId: String): Result<Unit> = safeRunCatching {
        val remoteNotifs = supabase.postgrest["notifications"].select {
            filter { eq("group_id", groupId) }
        }.decodeList<AppNotification>()

        db.notificationDao().syncNotifications(groupId, remoteNotifs.map { it.toEntity() })
    }

    override suspend fun sendNotification(notification: AppNotification): Result<Unit> = safeRunCatching {
        val notifId = if (notification.id.isNullOrBlank()) "notif_${java.util.UUID.randomUUID()}" else notification.id
        val nowStr = notification.createdAt ?: java.time.Instant.now().toString()
        val notifToSave = notification.copy(id = notifId, createdAt = nowStr)

        // 1. Local Room DB upsert for instant local-first UI reactivity
        db.notificationDao().upsertNotifications(listOf(notifToSave.toEntity()))

        // 2. Sync to Supabase remote database
        val insertData = payloadFactory.createNotificationPayload(notifToSave)

        try {
            val serverNotification = supabase.postgrest["notifications"].upsert(insertData) {
                onConflict = "id"
                select()
            }.decodeSingleOrNull<AppNotification>()

            if (serverNotification != null) {
                db.notificationDao().upsertNotifications(listOf(serverNotification.toEntity()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val userMsg = e.toUserMessage()
            AppLogger.e("NotificationRepo", "Postgrest notification insert failed: $userMsg", e)
            if (notification.channel == NotifChannel.EMAIL) throw IllegalStateException(userMsg)
        }

        if (notification.channel == NotifChannel.WHATSAPP || notification.channel == NotifChannel.BOTH) {
            val memberId = notification.memberId
            if (router.shouldRouteToAdmin(notification.triggerEvent)) {
                // Member inquiry, loan request, or payout request: send WhatsApp to group admin.
                val group = supabase.postgrest["groups"].select(columns = Columns.list("admin_user_id")) {
                    filter { eq("id", notification.groupId ?: "") }
                }.decodeSingle<Group>()

                val adminUserId = group.adminUserId
                if (!adminUserId.isNullOrBlank()) {
                    val admin = supabase.postgrest["members"].select(columns = Columns.list("phone")) {
                        filter {
                            eq("group_id", notification.groupId ?: "")
                            eq("user_id", adminUserId)
                        }
                    }.decodeSingleOrNull<Member>()

                    admin?.phone?.takeIf { it.isNotBlank() }?.let { taskQueue.enqueue(it, notification.message) }
                }
            } else if (memberId != null) {
                // Direct notification to a specific member
                val member = supabase.postgrest["members"].select(columns = Columns.list("phone")) {
                    filter { eq("id", memberId) }
                }.decodeSingleOrNull<Member>()

                member?.phone?.takeIf { it.isNotBlank() }?.let { taskQueue.enqueue(it, notification.message) }
            } else {
                // Broadcast to all members in the group
                val members = supabase.postgrest["members"].select(columns = Columns.list("phone", "notification_pref")) {
                    filter { eq("group_id", notification.groupId ?: "") }
                }.decodeList<Member>()

                members.filter {
                    it.phone.isNotBlank() &&
                    (it.notificationPref == NotificationPref.WHATSAPP || it.notificationPref == NotificationPref.BOTH)
                }.forEach { m ->
                    taskQueue.enqueue(m.phone, notification.message)
                }
            }
        }
    }

    override suspend fun sendFeeEnforcementNotification(
        groupId: String,
        event: NotifEvent,
        memberCount: Int,
        amountDue: Money
    ): Result<Unit> = safeRunCatching {
        val formattedAmount = amountDue.format()
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
                filter {
                    eq("group_id", groupId)
                    eq("user_id", adminUserId)
                }
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
            val currentLang = Locale.getDefault().language
            val whatsappLang = when(currentLang) {
                "zu" -> "zu"
                "st" -> "st"
                else -> "en_US"
            }
            val feeDuePayload = buildJsonObject {
                put("to", phone)
                put("template", buildJsonObject {
                    put("name", "platform_fee_due")
                    put("language", buildJsonObject { put("code", whatsappLang) })
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
                AppLogger.w("NotificationRepository", "platform_fee_due WhatsApp template not sent; notification still recorded locally.")
            }
        }
    }

    override suspend fun notifyPlatformAdmin(message: String): Result<Unit> = safeRunCatching {
        val platformAdmins = supabase.postgrest["profiles"].select {
            filter { eq("role", "platform_admin") }
        }.decodeList<UserProfile>()

        platformAdmins.forEach { _ ->
            supabase.postgrest["notifications"].insert(buildJsonObject {
                put("message", message)
                put("trigger_event", NotifEvent.CUSTOM.name.lowercase())
                put("member_id", JsonNull)
                put("group_id", "platform")
            })
        }
    }

    override suspend fun sendDirectWhatsAppMessage(phone: String, message: String): Result<Unit> = safeRunCatching {
        require(phone.isNotBlank()) { "Please enter a WhatsApp number." }
        require(message.isNotBlank()) { "Please enter a message to send." }
        whatsAppChannel.deliver(phone, message).getOrThrow()
    }

    override suspend fun sendPasswordResetWhatsApp(phone: String): Result<Unit> = safeRunCatching {
        val currentLang = Locale.getDefault().language
        val whatsappLang = when(currentLang) {
            "zu" -> "zu"
            "st" -> "st"
            else -> "en_US"
        }
        val resetPayload = buildJsonObject {
            put("to", phone)
            put("template", buildJsonObject {
                put("name", "password_reset")
                put("language", buildJsonObject { put("code", whatsappLang) })
            })
        }
        edgeFunctionGateway.invoke(
            functionName = "send-whatsapp",
            payload = resetPayload,
            requireAuth = false
        ).getOrThrow()
    }
}

