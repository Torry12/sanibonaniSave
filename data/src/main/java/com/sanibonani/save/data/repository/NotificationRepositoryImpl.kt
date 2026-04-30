package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.NotificationEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.data.remote.model.WhatsAppMessageRequest
import com.sanibonani.save.data.remote.model.WhatsAppTemplate
import com.sanibonani.save.data.remote.model.WhatsAppLanguage
import com.sanibonani.save.data.remote.model.WhatsAppTemplateComponent
import com.sanibonani.save.data.remote.model.WhatsAppParameter
import com.sanibonani.save.data.remote.WhatsAppApiService
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.data.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import java.text.NumberFormat
import java.util.Locale

class NotificationRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val whatsappApi: WhatsAppApiService,
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
        val insertData = buildJsonObject {
            put("group_id", notification.groupId)
            notification.memberId?.let { put("member_id", it) }
            put("message", notification.message)
            put("channel", notification.channel.name.lowercase())
            put("trigger_event", notification.triggerEvent.name.lowercase())
        }
        
        try {
            supabase.postgrest["notifications"].insert(insertData)
        } catch (e: Exception) {
            AppLogger.e("NotificationRepo", "Postgrest insert failed: ${e.message}")
            // If it's just a broadcast/message, we still want to try WhatsApp even if DB log fails
            if (notification.channel == NotifChannel.EMAIL) throw e
        }
        
        if (notification.channel == NotifChannel.WHATSAPP || notification.channel == NotifChannel.BOTH) {
            val memberId = notification.memberId
            if (notification.triggerEvent == NotifEvent.MEMBER_MESSAGE) {
                // Member inquiry: Send WhatsApp to group administrator
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
                    
                    admin?.phone?.let { if (it.isNotBlank()) sendWhatsAppDirect(it, notification.message) }
                }
            } else if (memberId != null) {
                // Direct notification to a specific member
                val member = supabase.postgrest["members"].select(columns = Columns.list("phone")) { 
                    filter { eq("id", memberId) } 
                }.decodeSingleOrNull<Member>()
                
                member?.phone?.let { if (it.isNotBlank()) sendWhatsAppDirect(it, notification.message) }
            } else {
                // Broadcast to all members in the group (Mass Messaging Optimization)
                // 1. Fetch only required columns to speed up DB query
                val members = supabase.postgrest["members"].select(columns = Columns.list("phone", "notification_pref")) { 
                    filter { eq("group_id", notification.groupId) } 
                }.decodeList<Member>()
                
                // 2. Parallel sending with supervisorScope to prevent one failure from cancelling others
                supervisorScope {
                    members.filter { 
                        !it.phone.isNullOrBlank() && 
                        (it.notificationPref == NotificationPref.WHATSAPP || it.notificationPref == NotificationPref.BOTH)
                    }.forEach { m ->
                        launch(Dispatchers.IO) {
                            try {
                                sendWhatsAppDirect(m.phone!!, notification.message)
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

    private suspend fun sendWhatsAppDirect(phone: String, message: String) {
        val request = WhatsAppMessageRequest(
            to = phone.filter { it.isDigit() }.let { if (it.startsWith("0")) "27${it.drop(1)}" else it },
            template = WhatsAppTemplate(
                name = "general_notification",
                language = WhatsAppLanguage(),
                components = listOf(WhatsAppTemplateComponent("body", listOf(WhatsAppParameter(text = message))))
            )
        )
        whatsappApi.sendTemplateMessage(BuildConfig.WHATSAPP_PHONE_NUMBER_ID, "Bearer ${BuildConfig.WHATSAPP_TOKEN}", request)
    }

    override suspend fun sendFeeEnforcementNotification(groupId: String, event: NotifEvent, memberCount: Int, amountDue: Double): Result<Unit> {
        val formattedAmount = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).format(amountDue)
        val message = when (event) {
            NotifEvent.PLATFORM_FEE_DUE -> "Monthly platform fee of $formattedAmount for $memberCount members is now due."
            NotifEvent.PLATFORM_FEE_WARNING -> "URGENT: Platform fee of $formattedAmount is overdue. 48h until suspension."
            NotifEvent.GROUP_SUSPENDED -> "Group SUSPENDED due to non-payment. Locked until $formattedAmount is cleared."
            else -> "Platform fee update: $event"
        }
        
        val group = supabase.postgrest["groups"].select(columns = Columns.raw(GROUP_COLUMNS_SAFE)) { filter { eq("id", groupId) } }.decodeSingle<Group>()
        val adminUserId = group.adminUserId
        val admin = if (adminUserId != null) supabase.postgrest["members"].select { filter { eq("user_id", adminUserId) } }.decodeSingleOrNull<Member>() else null

        // Send App & WhatsApp Notification
        val res = sendNotification(AppNotification(groupId = groupId, memberId = admin?.id, message = message, triggerEvent = event, channel = NotifChannel.BOTH))
        
        // Specifically for PLATFORM_FEE_DUE, ensure WhatsApp template is used if available
        if (event == NotifEvent.PLATFORM_FEE_DUE && admin != null && !admin.phone.isNullOrBlank()) {
            try {
                val request = WhatsAppMessageRequest(
                    to = admin.phone!!.filter { it.isDigit() }.let { if (it.startsWith("0")) "27${it.drop(1)}" else it },
                    template = WhatsAppTemplate(
                        name = "platform_fee_due",
                        language = WhatsAppLanguage(),
                        components = listOf(
                            WhatsAppTemplateComponent("body", listOf(
                                WhatsAppParameter(text = "*$formattedAmount*"),
                                WhatsAppParameter(text = memberCount.toString())
                            ))
                        )
                    )
                )
                whatsappApi.sendTemplateMessage(BuildConfig.WHATSAPP_PHONE_NUMBER_ID, "Bearer ${BuildConfig.WHATSAPP_TOKEN}", request)
            } catch (e: Exception) { AppLogger.e("NotificationRepository", "Failed to send platform_fee_due template", e) }
        }
        
        return res
    }


    override suspend fun notifyPlatformAdmin(message: String): Result<Unit> = runCatching {
        // Find platform admins
        val platformAdmins = supabase.postgrest["profiles"].select {
            filter { eq("role", "platform_admin") }
        }.decodeList<UserProfile>()
        
        platformAdmins.forEach { admin ->
            supabase.postgrest["notifications"].insert(AppNotification(
                message = message,
                triggerEvent = NotifEvent.CUSTOM,
                memberId = null, // Global or system notification
                groupId = "platform" // Special group ID for platform-level notices
            ))
        }
    }

    override suspend fun sendPasswordResetWhatsApp(phone: String): Result<Unit> = runCatching {
        val formattedPhone = phone.filter { it.isDigit() }.let { 
            if (it.startsWith("0")) "27${it.drop(1)}" 
            else if (!it.startsWith("27")) "27$it"
            else it 
        }

        val request = WhatsAppMessageRequest(
            to = formattedPhone,
            template = WhatsAppTemplate(
                name = "password_reset", // Ensure this template exists in your Meta Business Suite
                language = WhatsAppLanguage(code = "en_US"),
                components = null // Template might not need parameters if it's just a generic link/info
            )
        )
        
        whatsappApi.sendTemplateMessage(
            BuildConfig.WHATSAPP_PHONE_NUMBER_ID, 
            "Bearer ${BuildConfig.WHATSAPP_TOKEN}", 
            request
        )
        Unit
    }
}
