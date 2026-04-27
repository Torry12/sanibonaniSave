package com.sanibonani.save.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sanibonani.save.MainActivity
import com.sanibonani.save.SanibonaniApp
import com.sanibonani.save.R
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.repository.SupabaseRepository
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SanibonaniFirebaseService : FirebaseMessagingService() {
    
    @Inject lateinit var supabaseRepo: SupabaseRepository
    @Inject lateinit var supabaseClient: SupabaseClient

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title   = message.notification?.title ?: message.data["title"] ?: "SanibonaniSave"
        val body    = message.notification?.body  ?: message.data["body"]  ?: ""
        val event   = message.data["event"] ?: ""
        val groupId = message.data["group_id"] ?: ""
        
        val channel = determineChannel(event)
        showNotification(title, body, channel, event, groupId)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppLogger.d("FCMService", "New FCM token received: ${token.take(20)}...")
        
        // Try to update Supabase immediately if user is logged in
        val userId = supabaseRepo.currentUserId
        if (userId != null) {
            updateTokenInSupabase(token, userId)
        } else {
            // User not logged in yet — store token in encrypted local storage
            // When user logs in, the stored token will be synced
            saveTokenLocally(token)
            AppLogger.i("FCMService", "User not logged in. Token stored locally for sync on login.")
        }
    }

    private fun updateTokenInSupabase(token: String, userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update the member record with the FCM token
                supabaseClient.postgrest["members"]
                    .update(buildJsonObject { put("fcm_token", token) }) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                AppLogger.i("FCMService", "FCM token synced to Supabase for user $userId")
                // Also clear the local backup since it's now synced
                clearLocalToken()
            } catch (e: Exception) {
                AppLogger.w(
                    "FCMService",
                    "Failed to sync FCM token to Supabase: ${e.message}. Will retry on next sync.",
                    e
                )
                // Keep local copy for retry later
                saveTokenLocally(token)
            }
        }
    }

    private fun saveTokenLocally(token: String) {
        try {
            val masterKey = MasterKey.Builder(applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                applicationContext,
                "fcm_tokens",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            encryptedPrefs.edit().putString("pending_token", token).apply()
            AppLogger.d("FCMService", "Token stored locally in encrypted storage")
        } catch (e: Exception) {
            AppLogger.e("FCMService", "Failed to save token locally: ${e.message}", e)
        }
    }

    private fun clearLocalToken() {
        try {
            val masterKey = MasterKey.Builder(applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                applicationContext,
                "fcm_tokens",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            encryptedPrefs.edit().remove("pending_token").apply()
        } catch (e: Exception) {
            AppLogger.d("FCMService", "Could not clear local token: ${e.message}")
        }
    }

    private fun determineChannel(event: String): String = when {
        event.contains("fee") || event.contains("suspend") || event.contains("restore")
             -> SanibonaniApp.CHANNEL_SUSPENSION
        event.contains("payment") || event.contains("contribution")
             -> SanibonaniApp.CHANNEL_PAYMENTS
        event.contains("actuarial") || event.contains("risk")
             -> SanibonaniApp.CHANNEL_ACTUARIAL
        else -> SanibonaniApp.CHANNEL_GENERAL
    }

    private fun showNotification(title: String, body: String, channelId: String, event: String = "", groupId: String = "") {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("notification_event", event)
            putExtra("notification_group_id", groupId)
            
            // Deep link logic: If it's a CUSTOM message, target Tab 4 (Messages)
            // If it's any other system notification, target Tab 5 (Notifications)
            val targetTab = if (event == "custom") 4 else 5
            putExtra("target_tab", targetTab)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.mipmap.ic_launcher_round) // Use app icon
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
        AppLogger.d("FCMService", "Notification displayed: $title (Tab: ${if (event == "custom") 4 else 5})")
    }
}
