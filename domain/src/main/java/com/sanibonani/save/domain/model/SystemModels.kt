package com.sanibonani.save.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PendingOperation(
    val id: String,
    val type: String,
    val payload: String,
    val groupId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)
