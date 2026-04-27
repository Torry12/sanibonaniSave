package com.sanibonani.save.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppMessageRequest(
    @SerialName("messaging_product") val messagingProduct: String = "whatsapp",
    val to: String,
    val type: String = "template",
    val template: WhatsAppTemplate
)

@Serializable
data class WhatsAppTemplate(
    val name: String,
    val language: WhatsAppLanguage,
    val components: List<WhatsAppTemplateComponent>? = null
)

@Serializable
data class WhatsAppLanguage(
    val code: String = "en_US"
)

@Serializable
data class WhatsAppTemplateComponent(
    val type: String,
    val parameters: List<WhatsAppParameter>
)

@Serializable
data class WhatsAppParameter(
    val type: String = "text",
    val text: String
)

@Serializable
data class WhatsAppResponse(
    @SerialName("messaging_product") val messagingProduct: String? = null,
    val contacts: List<WhatsAppContact>? = null,
    val messages: List<WhatsAppMessageId>? = null
)

@Serializable
data class WhatsAppContact(
    val input: String,
    val wa_id: String
)

@Serializable
data class WhatsAppMessageId(
    val id: String
)
