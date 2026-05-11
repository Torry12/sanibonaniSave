package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.NotificationEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
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
import javax.inject.Singleton
import java.text.NumberFormat
import java.util.Locale

class NotificationRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val edgeFunctionGateway: EdgeFunctionGateway,
    private val db: SanibonaniDatabase
) : BaseRepository("NotificationRepository"), NotificationRepository {

    private val GROUP_COLUMNS_SAFE = "id,name,type,province,city,township,description,logo_emoji,joining_fee,monthly_contribution,late_fee,late_fee_grace_days,probation_months,payment_due_day,max_members,current_members,is_public,allow_partial_payment,auto_suspend_after,bank_name,account_number,branch_code,account_type,yoco_public_key,balance,admin_user_id,fee_status,registration_paid,latitude,longitude,geohash,created_at,is_platform_suspended"

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
                return@runCatching Unit
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
            if (notificationId.isNullOrBlank()) {
                supabase.postgrest["notifications"].insert(insertData)
            } else {
                supabase.postgrest["notifications"].upsert(insertData) {
                    onConflict = "id"
                }
            }
            db.notificationDao().upsertNotifications(listOf(notification.toEntity()))
        } catch (e: Exception) {
            AppLogger.e("NotificationRepo", "Postgrest insert failed: ${e.message}")
            if (notification.channel == NotifChannel.EMAIL) throw e
        }

        if (notification.channel == NotifChannel.WHATSAPP || notification.channel == NotifChannel.BOTH) {
            val memberId = notification.memberId
            if (notification.triggerEvent == NotifEvent.MEMBER_MESSAGE || notification.triggerEvent == NotifEvent.LOAN_REQUESTED) {
                // Member inquiry or loan request: Send WhatsApp to group administrator
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

                    admin?.phone?.let { if (it.isNotBlank()) sendWhatsAppViaEdge(it, notification.message) }
                }
            } else if (memberId != null) {
                // Direct notification to a specific member
                val member = supabase.postgrest["members"].select(columns = Columns.list("phone")) {
                    filter { eq("id", memberId) }
                }.decodeSingleOrNull<Member>()

                member?.phone?.let { if (it.isNotBlank()) sendWhatsAppViaEdge(it, notification.message) }
            } else {
                // Broadcast to all members in the group
                val members = supabase.postgrest["members"].select(columns = Columns.list("phone", "notification_pref")) {
                    filter { eq("group_id", notification.groupId) }
                }.decodeList<Member>()

                supervisorScope {
                    members.filter {
                        !it.phone.isNullOrBlank() &&
                        (it.notificationPref == NotificationPref.WHATSAPP || it.notificationPref == NotificationPref.BOTH)
                    }.forEach { m ->
                        launch(Dispatchers.IO) {
                            try {
                                sendWhatsAppViaEdge(m.phone!!, notification.message)
                            } catch (e: Exception) {
                                AppLogger.e("NotificationRepo", "Failed broadcast to ${m.phone}: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
        Unit
    }

    /**
     * Sends a WhatsApp message by proxying through the [send-whatsapp] Supabase Edge Function.
     * The WHATSAPP_TOKEN never leaves the server — it is stored as a Supabase secret.
     */
    private suspend fun sendWhatsAppViaEdge(phone: String, message: String) {
        val payload = buildJsonObject {
            put("to", phone)
            put("message", message)
            // Optionally add a template; for now we rely on the fallback text message path
            // in the Edge Function (template required for business-initiated conversations).
            putJsonArray("template_components") {
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
        }

        // Build proper template payload so the Edge Function uses template mode
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

        edgeFunctionGateway.invoke("send-whatsapp", templatePayload).getOrThrow()
    }

    override suspend fun sendFeeEnforcementNotification(
        groupId: String,
        event: NotifEvent,
        memberCount: Int,
        amountDue: Double
    ): Result<Unit> = runCatching {
        val formattedAmount = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).format(amountDue)
        val message = when (event) {
            NotifEvent.PLATFORM_FEE_DUE -> "Monthly platform fee of $formattedAmount for $memberCount members is now due."
            NotifEvent.PLATFORM_FEE_WARNING -> "URGENT: Platform fee of $formattedAmount is overdue. 48h until suspension."
            NotifEvent.GROUP_SUSPENDED -> "Group SUSPENDED due to non-payment. Locked until $formattedAmount is cleared."
            else -> "Platform fee update: $event"
        }

        val group = supabase.postgrest["groups"].select(columns = Columns.raw(GROUP_COLUMNS_SAFE)) {
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
        if (event == NotifEvent.PLATFORM_FEE_DUE && admin != null && !admin.phone.isNullOrBlank()) {
            val phone = admin.phone!!
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
