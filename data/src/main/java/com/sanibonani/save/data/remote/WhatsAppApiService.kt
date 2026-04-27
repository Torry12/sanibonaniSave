package com.sanibonani.save.data.remote

import com.sanibonani.save.data.remote.model.WhatsAppMessageRequest
import com.sanibonani.save.data.remote.model.WhatsAppResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Direct interface for WhatsApp Business API.
 * Note: In production, these calls should ideally be routed through Supabase Edge Functions
 * to protect the WHATSAPP_TOKEN.
 */
interface WhatsAppApiService {
    @POST("{phone_number_id}/messages")
    suspend fun sendTemplateMessage(
        @Path("phone_number_id") phoneNumberId: String,
        @Header("Authorization") authHeader: String,
        @Body request: WhatsAppMessageRequest
    ): WhatsAppResponse
}
