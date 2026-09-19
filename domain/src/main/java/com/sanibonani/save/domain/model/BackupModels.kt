package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class DatabaseBackup(
    val name: String,
    val size: Long,
    val lastModified: String,
    val publicUrl: String? = null
) : Parcelable
