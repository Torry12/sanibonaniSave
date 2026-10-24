package com.sanibonani.save.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RealtimeSyncStatus(
    val isSubscribed: Boolean = false,
    val lastUpdate: Long = 0,
    val lastError: String? = null
)

