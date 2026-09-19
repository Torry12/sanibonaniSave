package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
enum class SyncState : Parcelable {
    SYNCED,
    PENDING,
    FAILED
}

@Serializable
@Parcelize
data class PendingOperation(
    val id: String,
    val type: String,
    val payload: String,
    val groupId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
) : Parcelable
