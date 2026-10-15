package com.sanibonani.save.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DatabaseBackup(
    val name: String,
    val size: Long,
    val lastModified: String,
    val publicUrl: String? = null
)
